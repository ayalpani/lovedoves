package com.yalpani.lovedoves.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun CameraPermissionGate(
    onSystemPermissionPrompt: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        granted = it
        onSystemPermissionPrompt(false)
    }
    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CameraIcon(modifier = Modifier.size(52.dp))
            Text(
                "Die Kamera wird nur zum Fotografieren und Scannen eurer QR-Codes verwendet.",
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            LovePrimaryButton("Kamera erlauben", onClick = {
                onSystemPermissionPrompt(true)
                launcher.launch(Manifest.permission.CAMERA)
            })
        }
    }
}

@Composable
internal fun PhotoCameraScreen(
    onBack: () -> Unit,
    onUsePhoto: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val transferred = remember { AtomicBoolean(false) }
    BackHandler { onBack() }

    DisposableEffect(lifecycleOwner, previewView, capturedBytes) {
        if (capturedBytes != null) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            var disposed = false
            providerFuture.addListener({
                if (!disposed) runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                    capture = imageCapture
                }
            }, executor)
            onDispose {
                disposed = true
                capture = null
                if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!transferred.get()) capturedBytes?.fill(0)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bytes = capturedBytes
        if (bytes == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "Kameravorschau" },
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(
                    Color.Black.copy(alpha = 0.45f),
                    CircleShape,
                ),
            ) { CloseIcon("Kamera schließen") }
            IconButton(
                onClick = {
                    val imageCapture = capture ?: return@IconButton
                    capturing = true
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                try {
                                    val buffer = image.planes.first().buffer
                                    capturedBytes = ByteArray(buffer.remaining()).also(buffer::get)
                                    transferred.set(false)
                                } finally {
                                    image.close()
                                    capturing = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                            }
                        },
                    )
                },
                enabled = capture != null && !capturing,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(72.dp)
                    .background(Color.White, CircleShape)
                    .semantics { contentDescription = "Foto aufnehmen" },
            ) {
                Box(Modifier.size(56.dp).background(Color.Black, CircleShape))
            }
        } else {
            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Aufgenommenes Foto",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LovePrimaryButton("Foto verwenden", onClick = {
                    transferred.set(true)
                    capturedBytes = null
                    onUsePhoto(bytes)
                })
                LoveSecondaryButton("Nochmal", onClick = {
                    bytes.fill(0)
                    capturedBytes = null
                })
            }
        }
    }
}

@Composable
@SuppressLint("UnsafeOptInUsageError") // ImageAnalysis exposes the frame through this guarded API.
internal fun QrScannerScreen(onBack: () -> Unit, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val delivered = remember { AtomicBoolean(false) }
    BackHandler { onBack() }
    DisposableEffect(lifecycleOwner, previewView) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val processing = AtomicBoolean(false)
        var disposed = false
        providerFuture.addListener({
            if (!disposed) runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val mediaImage = proxy.image
                    if (mediaImage == null || !processing.compareAndSet(false, true)) {
                        proxy.close()
                    } else {
                        scanner.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                                if (value != null && delivered.compareAndSet(false, true)) onScanned(value)
                            }
                            .addOnCompleteListener {
                                processing.set(false)
                                proxy.close()
                            }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, executor)
        onDispose {
            disposed = true
            scanner.close()
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "QR-Code-Scanner" },
        )
        Text(
            "QR-Code in den Rahmen halten",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(16.dp, 10.dp),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) { CloseIcon("Scanner schließen") }
    }
}
