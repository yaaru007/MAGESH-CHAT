package com.magesh.chat.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.magesh.chat.audio.AudioPlaybackState
import com.magesh.chat.audio.VoiceRecorderPlayer
import com.magesh.chat.data.local.ChatDatabase
import com.magesh.chat.data.local.ChatMessageEntity
import com.magesh.chat.data.local.MessageType
import com.magesh.chat.data.network.NtfyRoomTransport
import com.magesh.chat.data.preferences.UserPreferences
import com.magesh.chat.data.preferences.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        ChatDatabase::class.java,
        "magesh_chat.db"
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    private val chatDao = db.chatDao()
    private val prefs = UserPreferences(application)
    private val transport = NtfyRoomTransport(application, chatDao)
    private val voice = VoiceRecorderPlayer(application)

    val profile: StateFlow<UserProfile> = prefs.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    val isConnected: StateFlow<Boolean> = transport.isConnected
    val isRecording: StateFlow<Boolean> = voice.isRecording
    val recordingDurationMs: StateFlow<Long> = voice.recordingDurationMs
    val audioPlaybackState: StateFlow<AudioPlaybackState> = voice.playbackState

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _liveVoiceMode = MutableStateFlow(false)
    val liveVoiceMode: StateFlow<Boolean> = _liveVoiceMode.asStateFlow()

    private val _isTalkingLive = MutableStateFlow(false)
    val isTalkingLive: StateFlow<Boolean> = _isTalkingLive.asStateFlow()

    private val _activeSongMessage = MutableStateFlow<ChatMessageEntity?>(null)
    val activeSongMessage: StateFlow<ChatMessageEntity?> = _activeSongMessage.asStateFlow()

    private var liveTalkJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = profile
        .map { it.activeRoomCode to it.senderId }
        .distinctUntilChanged()
        .flatMapLatest { (room, sid) ->
            if (room.isBlank()) {
                transport.disconnect()
                flowOf(emptyList())
            } else {
                transport.connectRoom(room, sid)
                chatDao.observeMessages(room)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            transport.incomingMessageNotice.collect {
                if (_soundEnabled.value) {
                    voice.playNotificationSound()
                }
            }
        }

        viewModelScope.launch {
            transport.incomingVoiceChunks.collect { chunkFile ->
                voice.enqueueLiveVoiceChunk(chunkFile)
            }
        }

        viewModelScope.launch {
            transport.songSyncEvents.collect { ev ->
                val msg = chatDao.getByRemoteId(ev.remoteId) ?: return@collect
                val path = msg.localAudioPath ?: return@collect
                _activeSongMessage.value = msg
                when (ev.action) {
                    "PLAY" -> voice.syncPlay(ev.remoteId, path, ev.positionMs)
                    "PAUSE" -> voice.syncPause(ev.remoteId, ev.positionMs)
                    "SEEK" -> voice.syncSeek(ev.remoteId, ev.positionMs)
                }
            }
        }
    }

    fun joinRoom(displayName: String, roomCode: String) {
        viewModelScope.launch { prefs.joinRoom(displayName, roomCode) }
    }

    fun leaveRoom() {
        viewModelScope.launch {
            stopLiveTalk()
            _liveVoiceMode.value = false
            _activeSongMessage.value = null
            voice.stopPlayback()
            transport.disconnect()
            prefs.leaveRoom()
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch { prefs.setDarkMode(!profile.value.darkMode) }
    }

    fun toggleSound() {
        _soundEnabled.value = !_soundEnabled.value
    }

    fun toggleLiveVoiceMode() {
        val next = !_liveVoiceMode.value
        _liveVoiceMode.value = next
        if (!next) stopLiveTalk()
    }

    fun toggleLiveTalk() {
        if (_isTalkingLive.value) {
            stopLiveTalk()
        } else {
            startLiveTalk()
        }
    }

    private fun startLiveTalk() {
        val current = profile.value
        if (current.activeRoomCode.isBlank()) return
        _isTalkingLive.value = true
        liveTalkJob?.cancel()
        liveTalkJob = viewModelScope.launch {
            val sid = current.senderId.ifBlank { prefs.ensureSenderId() }
            val sname = current.displayName.ifBlank { "Friend" }
            while (_isTalkingLive.value) {
                if (!voice.startRecording()) break
                delay(2200L)
                val res = voice.stopRecording(cancel = false) ?: continue
                transport.sendVoiceNote(
                    roomCode = current.activeRoomCode,
                    senderId = sid,
                    senderName = sname,
                    audioFile = res.first,
                    durationMs = res.second,
                    isLiveChunk = true
                )
                res.first.delete()
            }
        }
    }

    fun stopLiveTalk() {
        _isTalkingLive.value = false
        liveTalkJob?.cancel()
        liveTalkJob = null
        voice.stopRecording(cancel = true)
    }

    fun sendText(text: String) {
        val current = profile.value
        if (current.activeRoomCode.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val sid = current.senderId.ifBlank { prefs.ensureSenderId() }
            transport.sendText(
                roomCode = current.activeRoomCode,
                senderId = sid,
                senderName = current.displayName.ifBlank { "Friend" },
                text = text
            )
        }
    }

    fun sendMedia(uri: Uri, type: MessageType) {
        val current = profile.value
        if (current.activeRoomCode.isBlank()) return
        viewModelScope.launch {
            val sid = current.senderId.ifBlank { prefs.ensureSenderId() }
            val result = transport.sendMediaOrFile(
                roomCode = current.activeRoomCode,
                senderId = sid,
                senderName = current.displayName.ifBlank { "Friend" },
                sourceUri = uri,
                messageType = type
            )
            result.onFailure { err ->
                Toast.makeText(
                    getApplication(),
                    err.message ?: "Failed to send file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun startVoiceRecording(): Boolean = voice.startRecording()

    fun stopAndSendVoiceRecording() {
        val result = voice.stopRecording(cancel = false) ?: return
        val (file, durationMs) = result
        val current = profile.value
        if (current.activeRoomCode.isBlank()) {
            file.delete()
            return
        }
        viewModelScope.launch {
            val sid = current.senderId.ifBlank { prefs.ensureSenderId() }
            transport.sendVoiceNote(
                roomCode = current.activeRoomCode,
                senderId = sid,
                senderName = current.displayName.ifBlank { "Friend" },
                audioFile = file,
                durationMs = durationMs,
                isLiveChunk = false
            )
        }
    }

    fun cancelVoiceRecording() {
        voice.stopRecording(cancel = true)
    }

    fun togglePlayAudioOrSong(message: ChatMessageEntity) {
        val path = message.localAudioPath ?: return
        val before = audioPlaybackState.value
        val willPlay = !(before.activeMessageId == message.remoteId && before.isPlaying)
        voice.togglePlay(message.remoteId, path)

        if (message.type == MessageType.SONG) {
            _activeSongMessage.value = message
            val pos = if (before.activeMessageId == message.remoteId) before.positionMs else 0L
            viewModelScope.launch {
                val sid = profile.value.senderId.ifBlank { prefs.ensureSenderId() }
                transport.sendSongSync(
                    roomCode = profile.value.activeRoomCode,
                    senderId = sid,
                    remoteId = message.remoteId,
                    action = if (willPlay) "PLAY" else "PAUSE",
                    positionMs = pos
                )
            }
        }
    }

    override fun onCleared() {
        stopLiveTalk()
        transport.disconnect()
        voice.release()
        super.onCleared()
    }
}
