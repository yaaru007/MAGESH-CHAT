package com.magesh.chat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.magesh.chat.audio.AudioPlaybackState
import com.magesh.chat.data.local.ChatMessageEntity
import com.magesh.chat.data.local.MessageType
import com.magesh.chat.data.preferences.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QUICK_EMOJIS = listOf(
    "😀", "😂", "😍", "🥰", "😎", "🥳", "😭", "😡",
    "👍", "👎", "👏", "🙏", "❤️", "🔥", "💯", "🎉",
    "✨", "🎶", "🎧", "🎤", "🤝", "🤩", "😜", "🤔",
    "😴", "🤯", "😇", "🫶", "💪", "🌟", "☕", "🚀"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    profile: UserProfile,
    messages: List<ChatMessageEntity>,
    isConnected: Boolean,
    isRecording: Boolean,
    recordingDurationMs: Long,
    audioPlaybackState: AudioPlaybackState,
    onSendText: (String) -> Unit,
    onStartRecording: () -> Boolean,
    onStopAndSendRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onTogglePlayVoice: (ChatMessageEntity) -> Unit,
    onToggleDarkMode: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showEmojiPanel by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFF59E0B))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Room: ${profile.activeRoomCode}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isConnected) "Connected • ${profile.displayName}" else "Connecting...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Room Code", profile.activeRoomCode))
                        Toast.makeText(context, "Copied ${profile.activeRoomCode}", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Room Code")
                    }
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            if (profile.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    IconButton(onClick = onLeaveRoom) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = "Switch Room")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Quick emoji strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_EMOJIS.take(10).forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clickable { inputText += emoji }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isRecording) {
                        // Live voice recording bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCancelRecording) {
                                Icon(Icons.Rounded.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
                            }
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Recording ${formatMs(recordingDurationMs)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            FilledIconButton(
                                onClick = onStopAndSendRecording,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Rounded.Stop, "Send Voice")
                            }
                        }
                    } else {
                        // Standard WhatsApp/Instagram-style input bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Message...") },
                                leadingIcon = {
                                    IconButton(onClick = { showEmojiPanel = !showEmojiPanel }) {
                                        Icon(Icons.Rounded.EmojiEmotions, contentDescription = "Emoji")
                                    }
                                },
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                maxLines = 4,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            if (inputText.isNotBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        onSendText(inputText)
                                        inputText = ""
                                        showEmojiPanel = false
                                    },
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                                }
                            } else {
                                FilledIconButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onStartRecording()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.Rounded.Mic, contentDescription = "Record Voice")
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showEmojiPanel && !isRecording) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            items(QUICK_EMOJIS) { emoji ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clickable { inputText += emoji }
                                        .padding(6.dp)
                                ) {
                                    Text(text = emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(messages, key = { it.remoteId }) { msg ->
                val isMine = msg.senderId == profile.senderId
                MessageBubble(
                    message = msg,
                    isMine = isMine,
                    playbackState = audioPlaybackState,
                    onTogglePlayVoice = { onTogglePlayVoice(msg) }
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    isMine: Boolean,
    playbackState: AudioPlaybackState,
    onTogglePlayVoice: () -> Unit
) {
    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isMine) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (message.type == MessageType.TEXT) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                } else {
                    val isThisPlaying = playbackState.activeMessageId == message.remoteId && playbackState.isPlaying
                    val progress = if (playbackState.activeMessageId == message.remoteId && playbackState.durationMs > 0) {
                        playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()
                    } else 0f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(210.dp)
                    ) {
                        IconButton(
                            onClick = onTogglePlayVoice,
                            enabled = message.localAudioPath != null
                        ) {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play voice note",
                                tint = textColor
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (message.localAudioPath == null) "Downloading..."
                                else formatMs(message.audioDurationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatClock(message.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(sec / 60L, sec % 60L)
}

private fun formatClock(ms: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(ms))
}
