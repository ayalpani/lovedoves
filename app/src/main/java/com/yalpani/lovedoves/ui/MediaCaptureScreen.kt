package com.yalpani.lovedoves.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yalpani.lovedoves.domain.PreparedVideo

private enum class MediaCaptureMode { PHOTO, VIDEO }

@Composable
internal fun MediaCaptureScreen(
    onClose: () -> Unit,
    onPickGallery: () -> Unit,
    onSystemPermissionPrompt: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onUsePhoto: (ByteArray, Int) -> Unit,
    onUseVideo: (PreparedVideo) -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(MediaCaptureMode.PHOTO) }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSystemPermissionPrompt(false)
        if (granted) {
            mode = MediaCaptureMode.VIDEO
        } else {
            onError("Für Videos braucht Love Doves Zugriff auf das Mikrofon.")
        }
    }
    val selectVideo = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            mode = MediaCaptureMode.VIDEO
        } else {
            onSystemPermissionPrompt(true)
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val chrome: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        MediaModeSelector(
            mode = mode,
            onPhoto = { mode = MediaCaptureMode.PHOTO },
            onVideo = selectVideo,
            onGallery = onPickGallery,
        )
    }

    when (mode) {
        MediaCaptureMode.PHOTO -> PhotoCameraScreen(
            onBack = onClose,
            onUsePhoto = onUsePhoto,
            portraitControlsBottomOffset = MediaModeControlsOffset,
            cameraChrome = chrome,
        )
        MediaCaptureMode.VIDEO -> VideoCameraScreen(
            onClose = onClose,
            onError = onError,
            onVideoAccepted = onUseVideo,
            portraitControlsBottomOffset = MediaModeControlsOffset,
            cameraChrome = chrome,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MediaModeSelector(
    mode: MediaCaptureMode,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onGallery: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.height(46.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MediaModeTab("Kamera", mode == MediaCaptureMode.PHOTO, onPhoto)
            MediaModeTab("Video", mode == MediaCaptureMode.VIDEO, onVideo)
            MediaModeTab("Galerie", false, onGallery)
        }
    }
}

@Composable
private fun MediaModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (selected) 1f else 0.62f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private val MediaModeControlsOffset = 56.dp
