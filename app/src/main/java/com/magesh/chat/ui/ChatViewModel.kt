package com.magesh.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.magesh.chat.audio.AudioPlaybackState
import com.magesh.chat.audio.VoiceRecorderPlayer
import com.magesh.chat.data.local.ChatDatabase
import com.magesh.chat.data.local.ChatMessageEntity
import com.magesh.chat.data.network.NtfyRoomTransport
import com.magesh.chat.data.preferences.UserPreferences
import com.magesh.chat.data.preferences.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    ).build()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = profile
        .map { it.activeRoomCode }
        .distinctUntilChanged()
        .flatMapLatest { room ->
            if (room.isBlank()) {
                transport.disconnect()
                flowOf(emptyList())
            } else {
                transport.connectRoom(room)
                chatDao.observeMessages(room)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun joinRoom(displayName: String, roomCode: String) {
        viewModelScope.launch {
            prefs.joinRoom(displayName, roomCode)
        }
    }

    fun leaveRoom() {
        viewModelScope.launch {
            voice.stopPlayback()
            transport.disconnect()
            prefs.leaveRoom()
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            prefs.setDarkMode(!profile.value.darkMode)
        }
    }

    fun sendText(text: String) {
        val current = profile.value
        if (current.activeRoomCode.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val sid = current.senderId.ifBlank { prefs.ensureSenderId() }
            transport.sendText(
                roomCode = current.activeRoomCode,
                senderId = sid,
                senderName = current.displayName.ifBlank { "User" },
                text = text
            )
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
                senderName = current.displayName.ifBlank { "User" },
                audioFile = file,
                durationMs = durationMs
            )
        }
    }

    fun cancelVoiceRecording() {
        voice.stopRecording(cancel = true)
    }

    fun togglePlayVoice(message: ChatMessageEntity) {
        val path = message.localAudioPath ?: return
        voice.togglePlay(message.remoteId, path)
    }

    override fun onCleared() {
        transport.disconnect()
        voice.release()
        super.onCleared()
    }
}
