package com.magesh.chat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.magesh.chat.audio.AudioPlaybackState
import com.magesh.chat.data.local.ChatMessageEntity
import com.magesh.chat.data.local.MessageType
import com.magesh.chat.data.preferences.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    soundEnabled: Boolean,
    liveVoiceMode: Boolean,
    isTalkingLive: Boolean,
    isRecording: Boolean,
    recordingDurationMs: Long,
    audioPlaybackState: AudioPlaybackState,
    activeSongMessage: ChatMessageEntity?,
    onSendText: (String) -> Unit,
    onSendMedia: (Uri, MessageType) -> Unit,
    onStartRecording: () -> Boolean,
    onStopAndSendRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onTogglePlayAudio: (ChatMessageEntity) -> Unit,
    onToggleSound: () -> Unit,
    onToggleLiveVoiceMode: () -> Unit,
    onToggleLiveTalk: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Microphone permission is needed for voice chat", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSendMedia(uri, MessageType.IMAGE)
    }

    val songPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSendMedia(uri, MessageType.SONG)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSendMedia(uri, MessageType.FILE)
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
            Column {
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
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onToggleLiveVoiceMode()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }) {
                            Icon(
                                Icons.Rounded.Call,
                                contentDescription = "Live Voice Chat",
                                tint = if (liveVoiceMode) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onToggleSound) {
                            Icon(
                                if (soundEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = "Notification Sound"
                            )
                        }
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

                // Live Walkie-Talkie Voice Chat Banner
                AnimatedVisibility(visible = liveVoiceMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isTalkingLive) "Live Mic ON — Others hear you..." else "Voice Chat Ready",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onToggleLiveTalk,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTalkingLive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (isTalkingLive) "Mute Mic" else "Talk Live")
                            }
                        }
                    }
                }

                // Listen-Together Shared Song Banner
                activeSongMessage?.let { song ->
                    val isPlaying = audioPlaybackState.activeMessageId == song.remoteId && audioPlaybackState.isPlaying
                    val progress = if (audioPlaybackState.activeMessageId == song.remoteId && audioPlaybackState.durationMs > 0) {
                        audioPlaybackState.positionMs.toFloat() / audioPlaybackState.durationMs.toFloat()
                    } else 0f

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.MusicNote, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Listen Together: ${song.fileName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onTogglePlayAudio(song) }) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause Song"
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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

                    if (isRecording && !liveVoiceMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCancelRecording) {
                                Icon(Icons.Rounded.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
                            }
                            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.error)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(onClick = { showAttachMenu = true }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Attach Media or File")
                                }
                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Photo / Image") },
                                        leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                        onClick = {
                                            showAttachMenu = false
                                            imagePicker.launch(arrayOf("image/*"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Play Song Together") },
                                        leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
                                        onClick = {
                                            showAttachMenu = false
                                            songPicker.launch(arrayOf("audio/*"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Send Any File") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null) },
                                        onClick = {
                                            showAttachMenu = false
                                            filePicker.launch(arrayOf("*/*"))
                                        }
                                    )
                                }
                            }

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
                                        if (granted) onStartRecording()
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                    onTogglePlayAudio = { onTogglePlayAudio(msg) },
                    onPreviewImage = { previewImagePath = it },
                    onOpenFile = { path -> openLocalFile(context, path) }
                )
            }
        }
    }

    previewImagePath?.let { imgPath ->
        Dialog(onDismissRequest = { previewImagePath = null }) {
            LocalBitmapImage(
                path = imgPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { previewImagePath = null }
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    isMine: Boolean,
    playbackState: AudioPlaybackState,
    onTogglePlayAudio: () -> Unit,
    onPreviewImage: (String) -> Unit,
    onOpenFile: (String) -> Unit
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }

                when (message.type) {
                    MessageType.TEXT -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                    MessageType.IMAGE -> {
                        val path = message.localAudioPath
                        if (path != null) {
                            LocalBitmapImage(
                                path = path,
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPreviewImage(path) }
                            )
                        } else {
                            Text("Downloading image...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MessageType.FILE -> {
                        val path = message.localAudioPath
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(enabled = path != null) { path?.let(onOpenFile) }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = textColor)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.fileName.ifBlank { "File" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = textColor
                                )
                                Text(
                                    text = if (path == null) "Downloading..." else "${(message.fileSizeBytes / 1024).coerceAtLeast(1)} KB • Tap to open",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                    MessageType.AUDIO, MessageType.SONG -> {
                        val isThisPlaying = playbackState.activeMessageId == message.remoteId && playbackState.isPlaying
                        val progress = if (playbackState.activeMessageId == message.remoteId && playbackState.durationMs > 0) {
                            playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()
                        } else 0f

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(220.dp)
                        ) {
                            IconButton(
                                onClick = onTogglePlayAudio,
                                enabled = message.localAudioPath != null
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = textColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (message.type == MessageType.SONG) {
                                    Text(
                                        text = message.fileName.ifBlank { "Shared Song" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = textColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        message.localAudioPath == null -> "Downloading..."
                                        message.type == MessageType.SONG -> "Tap Play to sync with room"
                                        else -> formatMs(message.audioDurationMs)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.75f)
                                )
                            }
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

@Composable
private fun LocalBitmapImage(path: String, modifier: Modifier = Modifier) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bmp = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    val loaded = bmp
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Shared photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private fun openLocalFile(context: Context, path: String) {
    runCatching {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file"))
    }.onFailure {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
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
