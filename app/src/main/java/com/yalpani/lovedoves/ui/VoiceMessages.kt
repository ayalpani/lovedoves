package com.yalpani.lovedoves.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PreparedVoice
import java.io.Closeable
import kotlinx.coroutines.delay

internal enum class VoiceGestureDecision { NONE, CANCEL, LOCK }

internal fun voiceGestureDecision(
    deltaX: Float,
    deltaY: Float,
    threshold: Float,
): VoiceGestureDecision = when {
    deltaX <= -threshold && -deltaX >= -deltaY -> VoiceGestureDecision.CANCEL
    deltaY <= -threshold -> VoiceGestureDecision.LOCK
    else -> VoiceGestureDecision.NONE
}

internal fun Modifier.voiceRecordGesture(
    enabled: Boolean,
    thresholdPx: Float,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
): Modifier = this
    .semantics { contentDescription = "Sprachnachricht aufnehmen" }
    .pointerInput(enabled, thresholdPx) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onStart()
            var completed = false
            while (!completed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                when (
                    voiceGestureDecision(
                        deltaX = change.position.x - down.position.x,
                        deltaY = change.position.y - down.position.y,
                        threshold = thresholdPx,
                    )
                ) {
                    VoiceGestureDecision.CANCEL -> {
                        onCancel()
                        completed = true
                    }
                    VoiceGestureDecision.LOCK -> {
                        onLock()
                        completed = true
                    }
                    VoiceGestureDecision.NONE -> if (!change.pressed) {
                        onRelease()
                        completed = true
                    }
                }
                change.consume()
            }
        }
    }

/** MediaRecorder session whose encoded AAC data never receives a filesystem path. */
internal class MemoryVoiceRecorder(
    private val context: Context,
) : Closeable {
    private var recorder: MediaRecorder? = null
    private var memory: MemoryMediaFile? = null
    private var outputDescriptor: ParcelFileDescriptor? = null
    private var accumulatedMillis = 0L
    private var activeSinceMillis = 0L
    private var paused = false

    val isRecording: Boolean get() = recorder != null
    val isPaused: Boolean get() = paused

    fun start() {
        check(recorder == null)
        val nextMemory = MemoryMediaFile.create()
        val nextDescriptor = nextMemory.duplicate()
        val nextRecorder = createMediaRecorder(context)
        try {
            nextRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                setOutputFile(nextDescriptor.fileDescriptor)
                setMaxFileSize(LoveDovesRepository.MAX_VOICE_BYTES.toLong())
                prepare()
                start()
            }
            memory = nextMemory
            outputDescriptor = nextDescriptor
            recorder = nextRecorder
            accumulatedMillis = 0L
            activeSinceMillis = SystemClock.elapsedRealtime()
            paused = false
        } catch (failure: Throwable) {
            runCatching { nextRecorder.release() }
            runCatching { nextDescriptor.close() }
            runCatching { nextMemory.close() }
            throw failure
        }
    }

    fun pause() {
        val current = requireNotNull(recorder)
        if (paused) return
        accumulatedMillis = elapsedMillis()
        current.pause()
        paused = true
    }

    fun resume() {
        val current = requireNotNull(recorder)
        if (!paused) return
        current.resume()
        activeSinceMillis = SystemClock.elapsedRealtime()
        paused = false
    }

    fun elapsedMillis(): Long = if (recorder == null || paused) {
        accumulatedMillis
    } else {
        accumulatedMillis + (SystemClock.elapsedRealtime() - activeSinceMillis)
    }

    fun finish(): PreparedVoice {
        val current = requireNotNull(recorder)
        val currentMemory = requireNotNull(memory)
        val duration = elapsedMillis().coerceAtLeast(1L)
        try {
            current.stop()
        } catch (failure: RuntimeException) {
            cancel()
            throw IllegalStateException("Die Aufnahme war zu kurz. Halte das Mikrofon etwas länger.", failure)
        }
        releaseRecorder()
        return try {
            PreparedVoice(
                m4a = currentMemory.readBytes(LoveDovesRepository.MAX_VOICE_BYTES),
                durationMillis = duration,
            )
        } finally {
            runCatching { currentMemory.close() }
            memory = null
        }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        releaseRecorder()
        runCatching { memory?.close() }
        memory = null
    }

    override fun close() = cancel()

    private fun releaseRecorder() {
        runCatching { recorder?.release() }
        recorder = null
        runCatching { outputDescriptor?.close() }
        outputDescriptor = null
        accumulatedMillis = 0L
        activeSinceMillis = 0L
        paused = false
    }

    private companion object {
        const val AUDIO_BIT_RATE = 128_000
        const val AUDIO_SAMPLE_RATE = 44_100

        @Suppress("DEPRECATION")
        fun createMediaRecorder(context: Context): MediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
    }
}

@Composable
internal fun VoiceMessageContent(
    mediaId: String,
    declaredDurationMillis: Long,
    mediaBytes: suspend (String) -> ByteArray,
    footer: @Composable () -> Unit,
) {
    val bytes by produceState<ByteArray?>(null, mediaId, mediaBytes) {
        value = runCatching { mediaBytes(mediaId) }.getOrNull()
    }
    val loadedBytes = bytes
    DisposableEffect(loadedBytes) {
        onDispose { loadedBytes?.fill(0) }
    }
    if (loadedBytes == null) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        return
    }
    MemoryVoicePlayer(
        bytes = loadedBytes,
        declaredDurationMillis = declaredDurationMillis,
        footer = footer,
    )
}

@Composable
private fun MemoryVoicePlayer(
    bytes: ByteArray,
    declaredDurationMillis: Long,
    footer: @Composable () -> Unit,
) {
    var player by remember(bytes) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(bytes) { mutableStateOf(false) }
    var playing by remember(bytes) { mutableStateOf(false) }
    var positionMillis by remember(bytes) { mutableFloatStateOf(0f) }
    var durationMillis by remember(bytes) {
        mutableFloatStateOf(declaredDurationMillis.coerceAtLeast(1L).toFloat())
    }

    DisposableEffect(bytes) {
        val memory = MemoryMediaFile.fromBytes(bytes)
        val descriptor = memory.duplicate()
        val current = MediaPlayer().apply {
            setDataSource(descriptor.fileDescriptor, 0L, bytes.size.toLong())
            setOnPreparedListener {
                durationMillis = it.duration.coerceAtLeast(1).toFloat()
                prepared = true
                player = it
            }
            setOnCompletionListener {
                playing = false
                positionMillis = 0f
                it.seekTo(0)
            }
            prepareAsync()
        }
        onDispose {
            playing = false
            player = null
            runCatching { current.release() }
            runCatching { descriptor.close() }
            runCatching { memory.close() }
        }
    }
    LaunchedEffect(playing, player) {
        while (playing) {
            positionMillis = player?.currentPosition?.toFloat() ?: 0f
            delay(100L)
        }
    }

    Column(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 286.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = {
                    val current = player ?: return@IconButton
                    if (current.isPlaying) {
                        current.pause()
                        playing = false
                    } else {
                        current.start()
                        playing = true
                    }
                },
                enabled = prepared,
                modifier = Modifier.size(42.dp).background(LoveInk, CircleShape),
            ) {
                if (playing) {
                    PauseIcon("Wiedergabe pausieren", Modifier.size(20.dp), Color.White)
                } else {
                    PlayIcon("Sprachnachricht abspielen", Modifier.size(20.dp), Color.White)
                }
            }
            Slider(
                value = positionMillis.coerceIn(0f, durationMillis),
                onValueChange = { value ->
                    positionMillis = value
                    player?.seekTo(value.toInt())
                },
                valueRange = 0f..durationMillis,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = LoveInk,
                    activeTrackColor = LoveInk,
                    inactiveTrackColor = LoveInk.copy(alpha = 0.2f),
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatMediaDuration(
                    if (positionMillis > 0f) positionMillis.toLong() else durationMillis.toLong(),
                ),
                color = LoveInk.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
            )
            footer()
        }
    }
}
