package com.yalpani.lovedoves.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yalpani.lovedoves.VaultSession
import com.yalpani.lovedoves.domain.AppContentState
import com.yalpani.lovedoves.domain.LoveDovesController
import kotlinx.coroutines.launch

private sealed interface Overlay {
    data object Scanner : Overlay
    data object Camera : Overlay
    data object Settings : Overlay
    data class Photo(val mediaId: String) : Overlay
}

@Composable
internal fun LoveDovesApp(
    session: VaultSession,
    incomingLink: String?,
    pickedPhoto: Uri?,
    onLinkConsumed: () -> Unit,
    onPickPhoto: () -> Unit,
    onPhotoConsumed: (Uri) -> Unit,
    onSystemPermissionPrompt: (Boolean) -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
    onDeleteAll: suspend () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember(session) {
        LoveDovesController(context.applicationContext as Application, session)
    }
    DisposableEffect(controller) { onDispose(controller::close) }
    val content by controller.content.collectAsStateWithLifecycle()
    val controllerBusy by controller.busy.collectAsStateWithLifecycle()
    val controllerError by controller.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val busy = controllerBusy || photoBusy

    LaunchedEffect(pickedPhoto) {
        if (pickedPhoto != null) {
            photoBusy = true
            runCatching { PhotoProcessor.fromPicker(context.contentResolver, pickedPhoto) }
                .onSuccess(controller::sendPhoto)
                .onFailure { localError = it.message ?: "Das Foto konnte nicht gelesen werden." }
            photoBusy = false
            onPhotoConsumed(pickedPhoto)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSystemPermissionPrompt(false) }

    LaunchedEffect(incomingLink) {
        if (!incomingLink.isNullOrBlank()) {
            controller.acceptPayload(incomingLink)
            onLinkConsumed()
        }
    }
    LaunchedEffect(content) {
        if (
            content is AppContentState.Conversation &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onSystemPermissionPrompt(true)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = content) {
            AppContentState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            AppContentState.ProfileSetup -> ProfileSetupScreen(busy, controller::saveProfile)
            AppContentState.PairingHome -> PairingHomeScreen(
                busy = busy,
                onCreate = controller::createInvitation,
                onScan = { overlay = Overlay.Scanner },
                onPaste = controller::acceptPayload,
            )
            is AppContentState.Pairing -> PairingPendingScreen(
                snapshot = state.snapshot,
                busy = busy,
                onScanResponse = { overlay = Overlay.Scanner },
                onFetchResponse = controller::fetchRemoteResponse,
                onShare = { shareText(context, it) },
                onConfirm = { onAuthenticate(controller::confirmPairing) },
                onSync = controller::sync,
                onCancel = controller::cancelPairing,
            )
            is AppContentState.Conversation -> ConversationScreen(
                pair = state.pair,
                messages = state.messages,
                controller = controller,
                busy = busy,
                onSend = controller::sendText,
                onCamera = { overlay = Overlay.Camera },
                onGallery = onPickPhoto,
                onSettings = { overlay = Overlay.Settings },
                onRetry = controller::retryMessage,
                onPhoto = { overlay = Overlay.Photo(it) },
            )
        }
    }

    when (val destination = overlay) {
        Overlay.Scanner -> CameraPermissionGate(onSystemPermissionPrompt) {
            QrScannerScreen(
                onBack = { overlay = null },
                onScanned = {
                    overlay = null
                    controller.acceptPayload(it)
                },
            )
        }
        Overlay.Camera -> CameraPermissionGate(onSystemPermissionPrompt) {
            PhotoCameraScreen(
                onBack = { overlay = null },
                onUsePhoto = { jpeg ->
                    overlay = null
                    scope.launch {
                        photoBusy = true
                        runCatching { PhotoProcessor.fromCamera(jpeg) }
                            .onSuccess(controller::sendPhoto)
                            .onFailure { localError = it.message ?: "Das Foto konnte nicht verarbeitet werden." }
                        photoBusy = false
                    }
                },
            )
        }
        Overlay.Settings -> {
            val conversation = content as? AppContentState.Conversation
            if (conversation != null) SettingsScreen(
                pair = conversation.pair,
                busy = busy,
                onBack = { overlay = null },
                onSync = controller::sync,
                onRecovery = { mode ->
                    onAuthenticate {
                        overlay = null
                        controller.createRecoveryInvitation(mode)
                    }
                },
                onResendHistory = controller::resendHistory,
                onDelete = {
                    onAuthenticate {
                        controller.prepareDelete { onDeleteAll() }
                    }
                },
            )
        }
        is Overlay.Photo -> PhotoDetailScreen(destination.mediaId, controller) { overlay = null }
        null -> Unit
    }

    val error = localError ?: controllerError
    if (error != null) {
        AlertDialog(
            onDismissRequest = {
                localError = null
                controller.clearError()
            },
            title = { Text("Das hat noch nicht geklappt") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    localError = null
                    controller.clearError()
                }) { Text("Okay") }
            },
        )
    }
}

private fun shareText(context: android.content.Context, value: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, value)
            },
            "Love-Doves-Einladung teilen",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
