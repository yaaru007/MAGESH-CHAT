package com.magesh.chat.data.network

import android.content.Context
import com.magesh.chat.data.local.ChatDao
import com.magesh.chat.data.local.ChatMessageEntity
import com.magesh.chat.data.local.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * Real-time room-code chat transport using ntfy.sh WebSocket + HTTP PUT attachments.
 * Each Room Code is SHA-256 hashed into an unguessable topic so users who share
 * the exact same Room Code connect to the same live chat room with zero server setup.
 */
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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun connectRoom(roomCode: String) {
        val cleanCode = roomCode.trim().uppercase()
        if (cleanCode.isBlank()) return
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
        val topic = hashRoomToTopic(roomCode.trim().uppercase())

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

        // Optimistically store locally first
        chatDao.insertIgnore(
            ChatMessageEntity(
                remoteId = remoteId,
                roomCode = roomCode.trim().uppercase(),
                senderId = senderId,
                senderName = senderName,
                type = MessageType.TEXT,
                text = cleanText,
                timestampMs = nowMs
            )
        )

        val req = Request.Builder()
            .url("https://ntfy.sh/")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun sendVoiceNote(
        roomCode: String,
        senderId: String,
        senderName: String,
        audioFile: File,
        durationMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (!audioFile.exists()) return@withContext false

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
                type = MessageType.AUDIO,
                localAudioPath = audioFile.absolutePath,
                audioDurationMs = durationMs,
                timestampMs = nowMs
            )
        )

        val metaJson = JSONObject().apply {
            put("rid", remoteId)
            put("sid", senderId)
            put("sname", senderName)
            put("type", "AUDIO")
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

    private fun handleIncomingJson(roomCode: String, raw: String) {
        scope.launch {
            runCatching {
                val root = JSONObject(raw)
                if (root.optString("event") != "message") return@runCatching

                val attachment = root.optJSONObject("attachment")
                if (attachment != null) {
                    val meta = JSONObject(root.optString("title", "{}"))
                    val remoteId = meta.optString("rid").ifBlank { root.optString("id") }
                    val senderId = meta.optString("sid")
                    val senderName = meta.optString("sname", "Friend")
                    val dur = meta.optLong("dur", 1000L)
                    val ts = meta.optLong("ts", root.optLong("time") * 1000L)
                    val audioUrl = attachment.optString("url")

                    val inserted = chatDao.insertIgnore(
                        ChatMessageEntity(
                            remoteId = remoteId,
                            roomCode = roomCode,
                            senderId = senderId,
                            senderName = senderName,
                            type = MessageType.AUDIO,
                            audioUrl = audioUrl,
                            audioDurationMs = dur,
                            timestampMs = ts
                        )
                    )
                    if (inserted != -1L && audioUrl.isNotBlank()) {
                        downloadVoiceNote(remoteId, audioUrl)
                    }
                } else {
                    val body = JSONObject(root.optString("message", "{}"))
                    val remoteId = body.optString("rid").ifBlank { root.optString("id") }
                    val senderId = body.optString("sid")
                    val senderName = body.optString("sname", "Friend")
                    val text = body.optString("text")
                    val ts = body.optLong("ts", root.optLong("time") * 1000L)
                    if (text.isNotBlank()) {
                        chatDao.insertIgnore(
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
                    }
                }
            }
        }
    }

    private fun downloadVoiceNote(remoteId: String, url: String) {
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bytes = resp.body?.bytes() ?: return
                val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
                val file = File(dir, "rx_${remoteId.take(12)}.m4a")
                file.writeBytes(bytes)
                scope.launch {
                    chatDao.updateLocalAudioPath(remoteId, file.absolutePath)
                }
            }
        }
    }

    private fun hashRoomToTopic(roomCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("magesh_chat_v1:$roomCode".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
        return "mageshchat_$hex"
    }
}
