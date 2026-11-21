package com.magesh.chat

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magesh.chat.ui.ChatScreen
import com.magesh.chat.ui.ChatViewModel
import com.magesh.chat.ui.JoinRoomScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val profile by vm.profile.collectAsStateWithLifecycle()
            val messages by vm.messages.collectAsStateWithLifecycle()
            val isConnected by vm.isConnected.collectAsStateWithLifecycle()
            val isRecording by vm.isRecording.collectAsStateWithLifecycle()
            val recordingDurationMs by vm.recordingDurationMs.collectAsStateWithLifecycle()
            val audioPlaybackState by vm.audioPlaybackState.collectAsStateWithLifecycle()

            val context = LocalContext.current
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (profile.darkMode) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } else {
                if (profile.darkMode) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface {
                    if (profile.activeRoomCode.isBlank()) {
                        JoinRoomScreen(
                            savedName = profile.displayName,
                            recentRooms = profile.recentRooms,
                            onJoin = vm::joinRoom
                        )
                    } else {
                        ChatScreen(
                            profile = profile,
                            messages = messages,
                            isConnected = isConnected,
                            isRecording = isRecording,
                            recordingDurationMs = recordingDurationMs,
                            audioPlaybackState = audioPlaybackState,
                            onSendText = vm::sendText,
                            onStartRecording = vm::startVoiceRecording,
                            onStopAndSendRecording = vm::stopAndSendVoiceRecording,
                            onCancelRecording = vm::cancelVoiceRecording,
                            onTogglePlayVoice = vm::togglePlayVoice,
                            onToggleDarkMode = vm::toggleDarkMode,
                            onLeaveRoom = vm::leaveRoom
                        )
                    }
                }
            }
        }
    }
}
