package com.yalpani.lovedoves.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PreparedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun VideoCameraScreen(
    onClose: () -> Unit,
    onError: (String) -> Unit,
    onVideoAccepted: (PreparedVideo) -> Unit,
    portraitControlsBottomOffset: Dp = 0.dp,
    cameraChrome: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val landscape = CameraOrientation()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val discardRequested = remember { AtomicBoolean(false) }
    val closeAfterDiscard = remember { AtomicBoolean(false) }
    val accepted = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraPreview by remember { mutableStateOf<Preview?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var pendingMemory by remember { mutableStateOf<MemoryMediaFile?>(null) }
    var pendingDescriptor by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }
    var capturedVideo by remember { mutableStateOf<PreparedVideo?>(null) }
    var recordedDurationMillis by remember { mutableLongStateOf(0L) }
    var isFinalizing by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusMarkerVersion by remember { mutableLongStateOf(0L) }
    val currentRecording by rememberUpdatedState(recording)
    val currentPendingMemory by rememberUpdatedState(pendingMemory)
    val currentPendingDescriptor by rememberUpdatedState(pendingDescriptor)
    val currentCapturedVideo by rememberUpdatedState(capturedVideo)

    CameraSystemBars()

    fun discardAndClose() {
        discardRequested.set(true)
        closeAfterDiscard.set(true)
        capturedVideo?.let {
            it.clear()
            capturedVideo = null
            onClose()
            return
        }
        recording?.let {
            isFinalizing = true
            it.stop()
            return
        }
        if (!isFinalizing) {
            pendingDescriptor?.close()
            pendingDescriptor = null
            pendingMemory?.close()
            pendingMemory = null
            onClose()
        }
    }

    BackHandler(onBack = ::discardAndClose)

    LaunchedEffect(focusMarkerVersion) {
        if (focusMarkerVersion == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(1_200)
        focusPoint = null
    }

    DisposableEffect(lifecycleOwner, lensFacing, previewView, landscape, capturedVideo) {
        if (capturedVideo != null) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var bindingDisposed = false

            fun bindCamera() {
                if (bindingDisposed) return
                runCatching {
                    val provider = providerFuture.get()
                    val targetRotation = cameraTargetRotation(previewView.display?.rotation)
                    val preview = Preview.Builder()
                        .setTargetRotation(targetRotation)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                Quality.HD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
                            ),
                        )
                        .setTargetVideoEncodingBitRate(VIDEO_BIT_RATE)
                        .build()
                    val capture = VideoCapture.withOutput(recorder).also {
                        it.targetRotation = targetRotation
                    }
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    val useCases = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(capture)
                        .setViewPort(requireNotNull(previewView.viewPort))
                        .build()

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, useCases)
                    cameraPreview = preview
                    videoCapture = capture
                }.onFailure { onError("Die Videokamera konnte nicht geöffnet werden.") }
            }

            providerFuture.addListener({ previewView.doOnLayout { bindCamera() } }, mainExecutor)

            onDispose {
                bindingDisposed = true
                camera = null
                cameraPreview = null
                videoCapture = null
                if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    LaunchedEffect(landscape, cameraPreview, videoCapture, recording) {
        val targetRotation = cameraTargetRotation(previewView.display?.rotation)
        cameraPreview?.targetRotation = targetRotation
        if (recording == null) videoCapture?.targetRotation = targetRotation
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            discardRequested.set(true)
            currentRecording?.close()
            runCatching { currentPendingDescriptor?.close() }
            runCatching { currentPendingMemory?.close() }
            if (!accepted.get()) currentCapturedVideo?.clear()
        }
    }

    fun startRecording() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("Für Videos braucht Love Doves Zugriff auf Kamera und Mikrofon.")
            return
        }
        val capture = videoCapture ?: return
        val memory = MemoryMediaFile.create()
        val descriptor = memory.duplicate()
        val output = FileDescriptorOutputOptions.Builder(descriptor)
            .setDurationLimitMillis(MAX_VIDEO_DURATION_MILLIS)
            .setFileSizeLimit((LoveDovesRepository.MAX_VIDEO_BYTES - FILE_SIZE_MARGIN_BYTES).toLong())
            .build()
        discardRequested.set(false)
        closeAfterDiscard.set(false)
        pendingMemory = memory
        pendingDescriptor = descriptor
        recordedDurationMillis = 0L
        runCatching {
            recording = capture.output
                .prepareRecording(context, output)
                .withAudioEnabled()
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Status -> {
                            recordedDurationMillis =
                                event.recordingStats.recordedDurationNanos / 1_000_000L
                        }
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            pendingDescriptor = null
                            runCatching { descriptor.close() }
                            pendingMemory = null
                            val usableLimit = event.error ==
                                VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                                event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                            val successful = (!event.hasError() || usableLimit) && memory.size > 0L
                            if (discardRequested.get() || !successful) {
                                memory.close()
                                isFinalizing = false
                                if (!successful && !disposed.get()) {
                                    onError("Das Video konnte nicht aufgenommen werden.")
                                }
                                if (closeAfterDiscard.get() && !disposed.get()) onClose()
                            } else {
                                isFinalizing = true
                                scope.launch {
                                    val prepared = runCatching {
                                        val bytes = withContext(Dispatchers.IO) {
                                            memory.readBytes(LoveDovesRepository.MAX_VIDEO_BYTES)
                                        }
                                        memory.close()
                                        VideoProcessor.fromCamera(bytes)
                                    }
                                    isFinalizing = false
                                    prepared.onSuccess { video ->
                                        if (discardRequested.get() || disposed.get()) {
                                            video.clear()
                                        } else {
                                            capturedVideo = video
                                        }
                                    }.onFailure {
                                        memory.close()
                                        if (!disposed.get()) {
                                            onError(it.message ?: "Das Video konnte nicht verarbeitet werden.")
                                        }
                                    }
                                    if (closeAfterDiscard.get() && !disposed.get()) onClose()
                                }
                            }
                        }
                    }
                }
        }.onFailure {
            pendingDescriptor = null
            pendingMemory = null
            descriptor.close()
            memory.close()
            onError("Die Videoaufnahme konnte nicht gestartet werden.")
        }
    }

    val video = capturedVideo
    if (video == null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = "Videokameravorschau"
                },
            )
            Box(
                Modifier.fillMaxSize().pointerInput(camera) {
                    detectTapGestures { tap ->
                        val activeCamera = camera ?: return@detectTapGestures
                        val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                        ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                        activeCamera.cameraControl.startFocusAndMetering(action)
                        focusPoint = tap
                        focusMarkerVersion++
                    }
                },
            )
            focusPoint?.let { CameraFocusMarker(it) }
            CameraCloseButton("Videokamera schließen", ::discardAndClose)
            if (recording == null && !isFinalizing) {
                CameraSwitchButton(
                    contentDescription = "Videokamera wechseln",
                    landscape = landscape,
                    portraitBottomOffset = portraitControlsBottomOffset,
                ) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            }
            if (recording != null || isFinalizing) {
                Text(
                    text = if (isFinalizing) {
                        "Wird geschützt …"
                    } else {
                        formatMediaDuration(recordedDurationMillis)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 28.dp)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            CameraCaptureButton(
                enabled = videoCapture != null && !isFinalizing,
                contentDescription = when {
                    isFinalizing -> "Video wird geschützt"
                    recording != null -> "Videoaufnahme beenden"
                    else -> "Videoaufnahme starten"
                },
                landscape = landscape,
                portraitBottomOffset = portraitControlsBottomOffset,
                color = if (isFinalizing) Color.Gray else MaterialTheme.colorScheme.error,
                shape = if (recording != null) RoundedCornerShape(8.dp) else CircleShape,
                innerSize = if (recording != null) 34.dp else 64.dp,
                onClick = {
                    if (recording == null) startRecording() else {
                        isFinalizing = true
                        recording?.stop()
                    }
                },
            )
            if (recording == null && !isFinalizing) cameraChrome()
        }
    } else {
        VideoConfirmationSurface(
            video = video,
            landscape = landscape,
            onDiscard = {
                video.clear()
                capturedVideo = null
            },
            onAccept = {
                accepted.set(true)
                capturedVideo = null
                onVideoAccepted(video)
            },
        )
    }
}

private const val VIDEO_BIT_RATE = 4_000_000
private const val MAX_VIDEO_DURATION_MILLIS = 30_000L
private const val FILE_SIZE_MARGIN_BYTES = 64 * 1_024
