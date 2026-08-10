package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Ported from Spur's CameraScreen; encrypted-app policy keeps captured media in memory.
@Composable
internal fun PhotoCameraScreen(
    onBack: () -> Unit,
    onUsePhoto: (jpeg: ByteArray, leftQuarterTurns: Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val landscape = CameraOrientation()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraPreview by remember { mutableStateOf<Preview?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedPhoto by remember { mutableStateOf<CapturedCameraPhoto?>(null) }
    var leftQuarterTurns by remember { mutableStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusMarkerVersion by remember { mutableIntStateOf(0) }
    val transferred = remember { AtomicBoolean(false) }

    CameraSystemBars()

    fun discardAndClose() {
        capturedPhoto?.jpeg?.fill(0)
        capturedPhoto = null
        onBack()
    }

    BackHandler { discardAndClose() }

    LaunchedEffect(focusMarkerVersion) {
        if (focusMarkerVersion == 0) return@LaunchedEffect
        delay(1_200)
        focusPoint = null
    }

    DisposableEffect(lifecycleOwner, lensFacing, previewView, landscape, capturedPhoto) {
        if (capturedPhoto != null) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            var disposed = false

            fun bindCamera() {
                if (disposed) return
                runCatching {
                    val provider = providerFuture.get()
                    val targetRotation = cameraTargetRotation(previewView.display?.rotation)
                    val preview = Preview.Builder()
                        .setTargetRotation(targetRotation)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                        .setTargetRotation(targetRotation)
                        .build()
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
                    imageCapture = capture
                }
            }

            providerFuture.addListener({
                previewView.doOnLayout { bindCamera() }
            }, mainExecutor)

            onDispose {
                disposed = true
                camera = null
                cameraPreview = null
                imageCapture = null
                if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!transferred.get()) capturedPhoto?.jpeg?.fill(0)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val photo = capturedPhoto
        if (photo == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Kameravorschau" },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { tap ->
                            val activeCamera = camera ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                            ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                            activeCamera.cameraControl.startFocusAndMetering(action)
                            focusPoint = tap
                            focusMarkerVersion += 1
                        }
                    },
            )
            focusPoint?.let { point -> CameraFocusMarker(point) }
            CameraCloseButton(
                contentDescription = "Kamera schließen",
                onClick = ::discardAndClose,
            )
            CameraSwitchButton(
                contentDescription = "Kamera wechseln",
                landscape = landscape,
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
            )
            CameraCaptureButton(
                enabled = imageCapture != null && !isCapturing,
                contentDescription = "Foto aufnehmen",
                landscape = landscape,
                onClick = cameraCapture@{
                    val capture = imageCapture ?: return@cameraCapture
                    val targetRotation = cameraTargetRotation(previewView.display?.rotation)
                    cameraPreview?.targetRotation = targetRotation
                    capture.targetRotation = targetRotation
                    isCapturing = true
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                try {
                                    val buffer = image.planes.first().buffer
                                    val jpeg = ByteArray(buffer.remaining()).also(buffer::get)
                                    transferred.set(false)
                                    leftQuarterTurns = 0
                                    capturedPhoto = CapturedCameraPhoto(jpeg)
                                } finally {
                                    image.close()
                                    isCapturing = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                            }
                        },
                    )
                },
            )
        } else {
            PhotoConfirmationSurface(
                photo = photo,
                leftQuarterTurns = leftQuarterTurns,
                landscape = landscape,
                onRotate = { leftQuarterTurns = (leftQuarterTurns + 1) % 4 },
                onDiscard = {
                    photo.jpeg.fill(0)
                    capturedPhoto = null
                    leftQuarterTurns = 0
                },
                onAccept = {
                    transferred.set(true)
                    capturedPhoto = null
                    onUsePhoto(photo.jpeg, leftQuarterTurns)
                },
            )
        }
    }
}

@Composable
private fun CameraFocusMarker(point: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        val side = 48.dp.toPx()
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(point.x - side / 2f, point.y - side / 2f),
            size = Size(side, side),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun PhotoConfirmationSurface(
    photo: CapturedCameraPhoto,
    leftQuarterTurns: Int,
    landscape: Boolean,
    onRotate: () -> Unit,
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
) {
    if (landscape) {
        Row(Modifier.fillMaxSize()) {
            PhotoConfirmationPreview(
                photo = photo,
                leftQuarterTurns = leftQuarterTurns,
                landscape = true,
                onRotate = onRotate,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AnimatedMediaConfirmationPanel(
                landscape = true,
                onDiscard = onDiscard,
                onAccept = onAccept,
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            PhotoConfirmationPreview(
                photo = photo,
                leftQuarterTurns = leftQuarterTurns,
                landscape = false,
                onRotate = onRotate,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            AnimatedMediaConfirmationPanel(
                landscape = false,
                onDiscard = onDiscard,
                onAccept = onAccept,
            )
        }
    }
}

@Composable
private fun PhotoConfirmationPreview(
    photo: CapturedCameraPhoto,
    leftQuarterTurns: Int,
    landscape: Boolean,
    onRotate: () -> Unit,
    modifier: Modifier,
) {
    var bitmap by remember(photo) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo, leftQuarterTurns) {
        bitmap = PhotoProcessor.previewFromCamera(photo.jpeg, leftQuarterTurns)
    }
    DisposableEffect(bitmap) {
        val rendered = bitmap
        onDispose { rendered?.recycle() }
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { renderedBitmap ->
            ZoomablePhoto(
                bitmap = renderedBitmap,
                contentDescription = "Aufgenommenes Foto",
                modifier = Modifier.fillMaxSize(),
            )
        }
        CameraRotateButton(landscape = landscape, onClick = onRotate)
    }
}

private class CapturedCameraPhoto(val jpeg: ByteArray)
