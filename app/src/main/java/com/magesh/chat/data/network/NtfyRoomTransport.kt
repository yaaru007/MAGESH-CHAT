package com.magesh.chat.data.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.magesh.chat.data.local.ChatDao
import com.magesh.chat.data.local.ChatMessageEntity
import com.magesh.chat.data.local.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SongSyncEvent(
    val senderId: String,
    val remoteId: String,
    val action: String, // PLAY, PAUSE, SEEK
    val positionMs: Long
)

class NtfyRoomTransport(
    private val context: Context,
    private val chatDao: ChatDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var activeRoomCode: String = ""
    private var mySenderId: String = ""

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingMessageNotice = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val incomingMessageNotice: SharedFlow<Unit> = _incomingMessageNotice.asSharedFlow()

    private val _songSyncEvents = MutableSharedFlow<SongSyncEvent>(extraBufferCapacity = 16)
    val songSyncEvents: SharedFlow<SongSyncEvent> = _songSyncEvents.asSharedFlow()

    private val _incomingVoiceChunks = MutableSharedFlow<File>(extraBufferCapacity = 16)
    val incomingVoiceChunks: SharedFlow<File> = _incomingVoiceChunks.asSharedFlow()

    fun connectRoom(roomCode: String, currentSenderId: String) {
        val cleanCode = roomCode.trim().uppercase()
        if (cleanCode.isBlank()) return
        mySenderId = currentSenderId
        if (cleanCode == activeRoomCode && webSocket != null) return

        disconnect()
        activeRoomCode = cleanCode
        val topic = hashRoomToTopic(cleanCode)
        val wsUrl = "wss://ntfy.sh/$topic/ws?since=all"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingJson(cleanCode, text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Room left")
        webSocket = null
        activeRoomCode = ""
        _isConnected.value = false
    }

    suspend fun sendText(
        roomCode: String,
        senderId: String,
        senderName: String,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext false

        val remoteId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()
        val cleanRoom = roomCode.trim().uppercase()
        val topic = hashRoomToTopic(cleanRoom)

        val payload = JSONObject().apply {
            put("topic", topic)
            put(
                "message",
                JSONObject().apply {
                    put("rid", remoteId)
                    put("sid", senderId)
                    put("sname", senderName)
                    put("type", "TEXT")
                    put("text", cleanText)
                    put("ts", nowMs)
                }.toString()
            )
        }

        chatDao.insertIgnore(
            ChatMessageEntity(
                remoteId = remoteId,
                roomCode = cleanRoom,
                senderId = senderId,
                senderName = senderName,
                type = MessageType.TEXT,
                text = cleanText,
                timestampMs = nowMs
            )
        )

        postJson(payload)
    }

    suspend fun sendSongSync(
        roomCode: String,
        senderId: String,
        remoteId: String,
        action: String,
        positionMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val topic = hashRoomToTopic(roomCode.trim().uppercase())
        val payload = JSONObject().apply {
            put("topic", topic)
            put(
                "message",
                JSONObject().apply {
                    put("sid", senderId)
                    put("type", "SONG_SYNC")
                    put("rid", remoteId)
                    put("action", action)
                    put("pos", positionMs)
                }.toString()
            )
        }
        postJson(payload)
    }

    suspend fun sendVoiceNote(
        roomCode: String,
        senderId: String,
        senderName: String,
        audioFile: File,
        durationMs: Long,
        isLiveChunk: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!audioFile.exists()) return@withContext false

        val remoteId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()
        val cleanRoom = roomCode.trim().uppercase()
        val topic = hashRoomToTopic(cleanRoom)

        if (!isLiveChunk) {
            chatDao.insertIgnore(
                ChatMessageEntity(
                    remoteId = remoteId,
                    roomCode = cleanRoom,
                    senderId = senderId,
                    senderName = senderName,
                    type = MessageType.AUDIO,
                    localAudioPath = audioFile.absolutePath,
                    audioDurationMs = durationMs,
                    fileName = audioFile.name,
                    fileSizeBytes = audioFile.length(),
                    timestampMs = nowMs
                )
            )
        }

        val metaJson = JSONObject().apply {
            put("rid", remoteId)
            put("sid", senderId)
            put("sname", senderName)
            put("type", if (isLiveChunk) "LIVE_VOICE" else "AUDIO")
            put("dur", durationMs)
            put("ts", nowMs)
        }.toString()

        val req = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .put(audioFile.asRequestBody("audio/mp4".toMediaType()))
            .header("Filename", "voice_${remoteId.take(8)}.m4a")
            .header("Title", metaJson)
            .build()

        runCatching {
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Uploads an image, pre-downloaded song, or general file (up to 15 MB ntfy limit)
     * by copying to app local storage first so sender + receiver both keep an offline copy.
     */
    suspend fun sendMediaOrFile(
        roomCode: String,
        senderId: String,
        senderName: String,
        sourceUri: Uri,
        messageType: MessageType
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = queryFileName(sourceUri) ?: when (messageType) {
                MessageType.IMAGE -> "photo_${System.currentTimeMillis()}.jpg"
                MessageType.SONG -> "song_${System.currentTimeMillis()}.mp3"
                else -> "file_${System.currentTimeMillis()}"
            }

            val dir = File(context.filesDir, "shared_media").apply { mkdirs() }
            val localFile = File(dir, "${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read selected file")

            if (localFile.length() > 14_500_000L) {
                localFile.delete()
                error("File exceeds 14.5 MB limit")
            }

            val remoteId = UUID.randomUUID().toString()
            val nowMs = System.currentTimeMillis()
            val cleanRoom = roomCode.trim().uppercase()
            val topic = hashRoomToTopic(cleanRoom)

            chatDao.insertIgnore(
                ChatMessageEntity(
                    remoteId = remoteId,
                    roomCode = cleanRoom,
                    senderId = senderId,
                    senderName = senderName,
                    type = messageType,
                    text = fileName,
                    localAudioPath = localFile.absolutePath,
                    fileName = fileName,
                    fileSizeBytes = localFile.length(),
                    timestampMs = nowMs
                )
            )

            val metaJson = JSONObject().apply {
                put("rid", remoteId)
                put("sid", senderId)
                put("sname", senderName)
                put("type", messageType.name)
                put("fname", fileName)
                put("fsize", localFile.length())
                put("ts", nowMs)
            }.toString()

            val mime = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
            val req = Request.Builder()
                .url("https://ntfy.sh/$topic")
                .put(localFile.asRequestBody(mime.toMediaType()))
                .header("Filename", fileName)
                .header("Title", metaJson)
                .build()

            val ok = client.newCall(req).execute().use { it.isSuccessful }
            if (!ok) error("Upload failed")
            remoteId
        }
    }

    private fun postJson(payload: JSONObject): Boolean {
        val req = Request.Builder()
            .url("https://ntfy.sh/")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun handleIncomingJson(roomCode: String, raw: String) {
        scope.launch {
            runCatching {
                val root = JSONObject(raw)
                if (root.optString("event") != "message") return@runCatching

                val attachment = root.optJSONObject("attachment")
                if (attachment != null) {
                    val meta = JSONObject(root.optString("title", "{}"))
                    val typeStr = meta.optString("type", "FILE")
                    val remoteId = meta.optString("rid").ifBlank { root.optString("id") }
                    val senderId = meta.optString("sid")
                    val senderName = meta.optString("sname", "Friend")
                    val dur = meta.optLong("dur", 1000L)
                    val ts = meta.optLong("ts", root.optLong("time") * 1000L)
                    val fname = meta.optString("fname", attachment.optString("name", "attachment"))
                    val fsize = meta.optLong("fsize", attachment.optLong("size", 0L))
                    val url = attachment.optString("url")

                    if (typeStr == "LIVE_VOICE") {
                        if (senderId != mySenderId && url.isNotBlank()) {
                            downloadTempFile(url, "live_${remoteId.take(8)}.m4a")?.let {
                                _incomingVoiceChunks.emit(it)
                            }
                        }
                        return@runCatching
                    }

                    val msgType = runCatching { MessageType.valueOf(typeStr) }.getOrDefault(MessageType.FILE)
                    val inserted = chatDao.insertIgnore(
                        ChatMessageEntity(
                            remoteId = remoteId,
                            roomCode = roomCode,
                            senderId = senderId,
                            senderName = senderName,
                            type = msgType,
                            text = fname,
                            audioUrl = url,
                            audioDurationMs = dur,
                            fileName = fname,
                            fileSizeBytes = fsize,
                            timestampMs = ts
                        )
                    )
                    if (inserted != -1L) {
                        if (senderId.isNotBlank() && senderId != mySenderId) {
                            _incomingMessageNotice.emit(Unit)
                        }
                        if (url.isNotBlank()) {
                            downloadAndSaveAttachment(remoteId, url, fname)
                        }
                    }
                } else {
                    val body = JSONObject(root.optString("message", "{}"))
                    val typeStr = body.optString("type", "TEXT")
                    val senderId = body.optString("sid")

                    if (typeStr == "SONG_SYNC") {
                        if (senderId != mySenderId) {
                            _songSyncEvents.emit(
                                SongSyncEvent(
                                    senderId = senderId,
                                    remoteId = body.optString("rid"),
                                    action = body.optString("action"),
                                    positionMs = body.optLong("pos", 0L)
                                )
                            )
                        }
                        return@runCatching
                    }

                    val remoteId = body.optString("rid").ifBlank { root.optString("id") }
                    val senderName = body.optString("sname", "Friend")
                    val text = body.optString("text")
                    val ts = body.optLong("ts", root.optLong("time") * 1000L)
                    if (text.isNotBlank()) {
                        val inserted = chatDao.insertIgnore(
                            ChatMessageEntity(
                                remoteId = remoteId,
                                roomCode = roomCode,
                                senderId = senderId,
                                senderName = senderName,
                                type = MessageType.TEXT,
                                text = text,
                                timestampMs = ts
                            )
                        )
                        if (inserted != -1L && senderId.isNotBlank() && senderId != mySenderId) {
                            _incomingMessageNotice.emit(Unit)
                        }
                    }
                }
            }
        }
    }

    private fun downloadAndSaveAttachment(remoteId: String, url: String, fileName: String) {
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bytes = resp.body?.bytes() ?: return
                val dir = File(context.filesDir, "shared_media").apply { mkdirs() }
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(dir, "rx_${remoteId.take(8)}_$safeName")
                file.writeBytes(bytes)
                scope.launch {
                    chatDao.updateLocalAudioPath(remoteId, file.absolutePath)
                }
            }
        }
    }

    private fun downloadTempFile(url: String, name: String): File? {
        return runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                val dir = File(context.cacheDir, "live_voice").apply { mkdirs() }
                val file = File(dir, name)
                file.writeBytes(bytes)
                file
            }
        }.getOrNull()
    }

    private fun queryFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }

    private fun hashRoomToTopic(roomCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("magesh_chat_v1:$roomCode".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
        return "mageshchat_$hex"
    }
}
