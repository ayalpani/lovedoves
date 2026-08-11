package com.yalpani.lovedoves.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PreparedVideo
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal const val ROUND_VIDEO_MAX_DURATION_MILLIS = 30_000L

internal class MemoryRoundVideoRecorder(
    private val context: Context,
) : Closeable {
    internal enum class State { IDLE, WAITING_FOR_CAMERA, RECORDING, PAUSED, FINALIZING }

    var state by mutableStateOf(State.IDLE)
        private set
    var elapsedMillis by mutableLongStateOf(0L)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var memory: MemoryMediaFile? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var startRequested = false
    private var finishRequested = false
    private var discardRequested = false
    private var onReady: ((PreparedVideo) -> Unit)? = null
    private var onFailure: ((String) -> Unit)? = null

    fun attach(nextCapture: VideoCapture<Recorder>) {
        capture = nextCapture
        startIfReady()
    }

    fun detach(currentCapture: VideoCapture<Recorder>) {
        if (capture === currentCapture) capture = null
    }

    fun start(onReady: (PreparedVideo) -> Unit, onFailure: (String) -> Unit) {
        if (state != State.IDLE) {
            onFailure("Die Videokamera ist noch beschäftigt.")
            return
        }
        this.onReady = onReady
        this.onFailure = onFailure
        startRequested = true
        finishRequested = false
        discardRequested = false
        elapsedMillis = 0L
        state = State.WAITING_FOR_CAMERA
        startIfReady()
    }

    fun finish() {
        when (state) {
            State.WAITING_FOR_CAMERA -> finishRequested = true
            State.RECORDING, State.PAUSED -> {
                state = State.FINALIZING
                recording?.stop()
            }
            else -> Unit
        }
    }

    fun cancel() {
        discardRequested = true
        startRequested = false
        finishRequested = false
        onReady = null
        onFailure = null
        if (recording != null) {
            state = State.FINALIZING
            recording?.stop()
        } else {
            clearPending()
            state = State.IDLE
        }
    }

    fun pause() {
        if (state != State.RECORDING) return
        recording?.pause()
        state = State.PAUSED
    }

    fun resume() {
        if (state != State.PAUSED) return
        recording?.resume()
        state = State.RECORDING
    }

    private fun startIfReady() {
        val activeCapture = capture ?: return
        if (!startRequested || recording != null) return
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            state = State.IDLE
            resetRequests()
            fail("Für runde Videos braucht Love Doves Zugriff auf Kamera und Mikrofon.")
            return
        }
        val nextMemory = MemoryMediaFile.create()
        val nextDescriptor = nextMemory.duplicate()
        val output = FileDescriptorOutputOptions.Builder(nextDescriptor)
            .setDurationLimitMillis(ROUND_VIDEO_MAX_DURATION_MILLIS)
            .setFileSizeLimit((LoveDovesRepository.MAX_VIDEO_BYTES - FILE_SIZE_MARGIN).toLong())
            .build()
        memory = nextMemory
        descriptor = nextDescriptor
        startRequested = false
        runCatching {
            recording = activeCapture.output
                .prepareRecording(context, output)
                .withAudioEnabled()
                .start(mainExecutor) { handleRecordingEvent(it) }
            state = State.RECORDING
            if (finishRequested) finish()
        }.onFailure {
            clearPending()
            state = State.IDLE
            fail("Die runde Videoaufnahme konnte nicht gestartet werden.")
        }
    }

    private fun handleRecordingEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Status -> {
                elapsedMillis = event.recordingStats.recordedDurationNanos / 1_000_000L
            }
            is VideoRecordEvent.Finalize -> finalizeRecording(event)
        }
    }

    private fun finalizeRecording(event: VideoRecordEvent.Finalize) {
        recording = null
        runCatching { descriptor?.close() }
        descriptor = null
        val currentMemory = memory
        memory = null
        val reachedLimit = event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
            event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
        val successful = currentMemory != null && currentMemory.size > 0L &&
            (!event.hasError() || reachedLimit)
        if (discardRequested || !successful) {
            runCatching { currentMemory?.close() }
            state = State.IDLE
            if (!discardRequested) fail("Das runde Video konnte nicht aufgenommen werden.")
            resetRequests()
            return
        }
        val preparedMemory = requireNotNull(currentMemory)
        state = State.FINALIZING
        scope.launch {
            val result = runCatching {
                val bytes = preparedMemory.readBytes(LoveDovesRepository.MAX_VIDEO_BYTES)
                preparedMemory.close()
                VideoProcessor.fromCamera(bytes)
            }
            state = State.IDLE
            resetRequests()
            result.onSuccess { video ->
                val callback = onReady
                onReady = null
                onFailure = null
                if (callback == null) video.clear() else callback(video)
            }.onFailure {
                runCatching { preparedMemory.close() }
                fail(it.message ?: "Das runde Video konnte nicht verarbeitet werden.")
            }
        }
    }

    private fun fail(message: String) {
        val callback = onFailure
        onReady = null
        onFailure = null
        callback?.invoke(message)
    }

    private fun resetRequests() {
        startRequested = false
        finishRequested = false
        discardRequested = false
        elapsedMillis = 0L
    }

    private fun clearPending() {
        runCatching { descriptor?.close() }
        descriptor = null
        runCatching { memory?.close() }
        memory = null
        recording = null
        resetRequests()
    }

    override fun close() {
        cancel()
        scope.cancel()
    }

    private companion object {
        const val VIDEO_BIT_RATE = 4_000_000
        const val FILE_SIZE_MARGIN = 64 * 1_024

        fun recorder(): Recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
                ),
            )
            .setTargetVideoEncodingBitRate(VIDEO_BIT_RATE)
            .build()
    }

    internal fun createCapture(): VideoCapture<Recorder> = VideoCapture.withOutput(recorder())
}

@Composable
internal fun RoundVideoCaptureOverlay(
    recorder: MemoryRoundVideoRecorder,
    onUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = androidx.compose.runtime.remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainExecutor = androidx.compose.runtime.remember(context) {
        ContextCompat.getMainExecutor(context)
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var capture: VideoCapture<Recorder>? = null
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                previewView.doOnLayout {
                    if (disposed) return@doOnLayout
                    runCatching {
                        provider = providerFuture.get()
                        val targetRotation = cameraTargetRotation(previewView.display?.rotation)
                        preview = Preview.Builder().setTargetRotation(targetRotation).build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        capture = recorder.createCapture().also { it.targetRotation = targetRotation }
                        val group = UseCaseGroup.Builder()
                            .addUseCase(requireNotNull(preview))
                            .addUseCase(requireNotNull(capture))
                            .setViewPort(requireNotNull(previewView.viewPort))
                            .build()
                        provider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            group,
                        )
                        recorder.attach(requireNotNull(capture))
                    }.onFailure {
                        onUnavailable("Die Selfie-Kamera konnte nicht geöffnet werden.")
                    }
                }
            },
            mainExecutor,
        )
        onDispose {
            disposed = true
            capture?.let(recorder::detach)
            val currentPreview = preview
            val currentCapture = capture
            if (currentPreview != null && currentCapture != null) {
                runCatching { provider?.unbind(currentPreview, currentCapture) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Vorschau des runden Selfie-Videos" },
            )
            val progress = (
                recorder.elapsedMillis / ROUND_VIDEO_MAX_DURATION_MILLIS.toFloat()
                ).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            if (
                recorder.state == MemoryRoundVideoRecorder.State.WAITING_FOR_CAMERA ||
                recorder.state == MemoryRoundVideoRecorder.State.FINALIZING
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
