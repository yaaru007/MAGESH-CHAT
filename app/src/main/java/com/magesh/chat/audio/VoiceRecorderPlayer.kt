package com.magesh.chat.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

data class AudioPlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class VoiceRecorderPlayer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var recordTimerJob: Job? = null

    private var player: MediaPlayer? = null
    private var playTimerJob: Job? = null

    // Live voice chat sequential queue
    private val liveQueue = ArrayDeque<File>()
    private var livePlayer: MediaPlayer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    fun playNotificationSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        recordingFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            recordingStartMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L
            recordTimerJob = scope.launch {
                while (_isRecording.value) {
                    _recordingDurationMs.value = System.currentTimeMillis() - recordingStartMs
                    delay(100L)
                }
            }
            true
        } catch (_: Exception) {
            runCatching { mr.release() }
            recorder = null
            recordingFile = null
            false
        }
    }

    fun stopRecording(cancel: Boolean = false): Pair<File, Long>? {
        recordTimerJob?.cancel()
        recordTimerJob = null
        val elapsed = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(500L)
        _isRecording.value = false
        _recordingDurationMs.value = 0L

        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null

        val file = recordingFile
        recordingFile = null
        if (cancel || file == null || !file.exists() || file.length() < 256L) {
            file?.delete()
            return null
        }
        return file to elapsed
    }

    fun togglePlay(messageId: String, filePath: String) {
        val current = _playbackState.value
        if (current.activeMessageId == messageId && player != null) {
            if (current.isPlaying) {
                player?.pause()
                _playbackState.value = current.copy(isPlaying = false)
            } else {
                player?.start()
                _playbackState.value = current.copy(isPlaying = true)
            }
            return
        }
        playFromStart(messageId, filePath, 0L)
    }

    fun syncPlay(messageId: String, filePath: String, positionMs: Long) {
        val current = _playbackState.value
        if (current.activeMessageId == messageId && player != null) {
            runCatching {
                player?.seekTo(positionMs.toInt())
                if (player?.isPlaying != true) player?.start()
            }
            _playbackState.value = current.copy(isPlaying = true, positionMs = positionMs)
        } else {
            playFromStart(messageId, filePath, positionMs)
        }
    }

    fun syncPause(messageId: String, positionMs: Long) {
        val current = _playbackState.value
        if (current.activeMessageId == messageId && player != null) {
            runCatching {
                player?.pause()
                player?.seekTo(positionMs.toInt())
            }
            _playbackState.value = current.copy(isPlaying = false, positionMs = positionMs)
        }
    }

    fun syncSeek(messageId: String, positionMs: Long) {
        val current = _playbackState.value
        if (current.activeMessageId == messageId && player != null) {
            runCatching { player?.seekTo(positionMs.toInt()) }
            _playbackState.value = current.copy(positionMs = positionMs)
        }
    }

    private fun playFromStart(messageId: String, filePath: String, startPosMs: Long) {
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) return

        val mp = MediaPlayer()
        try {
            mp.setDataSource(filePath)
            mp.prepare()
            if (startPosMs > 0L) mp.seekTo(startPosMs.toInt())
            mp.start()
            player = mp
            _playbackState.value = AudioPlaybackState(
                activeMessageId = messageId,
                isPlaying = true,
                positionMs = startPosMs,
                durationMs = mp.duration.toLong().coerceAtLeast(1L)
            )
            mp.setOnCompletionListener {
                stopPlayback()
            }
            playTimerJob = scope.launch {
                while (player != null) {
                    val p = player ?: break
                    if (p.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            positionMs = p.currentPosition.toLong(),
                            durationMs = p.duration.toLong().coerceAtLeast(1L)
                        )
                    }
                    delay(150L)
                }
            }
        } catch (_: Exception) {
            runCatching { mp.release() }
            stopPlayback()
        }
    }

    fun enqueueLiveVoiceChunk(file: File) {
        liveQueue.addLast(file)
        if (livePlayer == null) {
            playNextLiveChunk()
        }
    }

    private fun playNextLiveChunk() {
        val next = liveQueue.pollFirst()
        if (next == null) {
            livePlayer = null
            return
        }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(next.absolutePath)
            mp.prepare()
            mp.start()
            livePlayer = mp
            mp.setOnCompletionListener {
                runCatching { mp.release() }
                livePlayer = null
                next.delete()
                playNextLiveChunk()
            }
        } catch (_: Exception) {
            runCatching { mp.release() }
            livePlayer = null
            next.delete()
            playNextLiveChunk()
        }
    }

    fun stopPlayback() {
        playTimerJob?.cancel()
        playTimerJob = null
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _playbackState.value = AudioPlaybackState()
    }

    fun release() {
        stopRecording(cancel = true)
        stopPlayback()
        runCatching { livePlayer?.release() }
        livePlayer = null
        liveQueue.clear()
    }
}
