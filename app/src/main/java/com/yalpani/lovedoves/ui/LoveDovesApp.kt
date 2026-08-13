package com.yalpani.lovedoves.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yalpani.lovedoves.VaultSession
import com.yalpani.lovedoves.data.PairStateEntity
import com.yalpani.lovedoves.domain.AppContentState
import com.yalpani.lovedoves.domain.LoveDovesController
import kotlinx.coroutines.launch

private const val PanelMotionDurationMillis = 400
private const val PanelFadeDurationMillis = 200

private sealed interface Overlay {
    data object Scanner : Overlay
    data object MediaCapture : Overlay
    data class Settings(val pair: PairStateEntity) : Overlay
    data class Media(val eventId: String) : Overlay
}

private enum class ContentRoute { LOADING, PROFILE, PAIRING_HOME, PAIRING, CONVERSATION }

private val AppContentState.route: ContentRoute
    get() = when (this) {
        AppContentState.Loading -> ContentRoute.LOADING
        AppContentState.ProfileSetup -> ContentRoute.PROFILE
        AppContentState.PairingHome -> ContentRoute.PAIRING_HOME
        is AppContentState.Pairing -> ContentRoute.PAIRING
        is AppContentState.Conversation -> ContentRoute.CONVERSATION
    }

@Composable
internal fun LoveDovesApp(
    session: VaultSession,
    incomingLink: String?,
    pickedMedia: Uri?,
    onLinkConsumed: () -> Unit,
    onPickMedia: () -> Unit,
    onMediaConsumed: (Uri) -> Unit,
    onSystemPermissionPrompt: (Boolean) -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
    onDeleteAll: suspend () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember(session) {
        LoveDovesController(context.applicationContext as Application, session)
    }
    DisposableEffect(controller) { onDispose(controller::close) }
    val photoBitmaps = remember(controller) { PhotoBitmapLoader(controller) }
    DisposableEffect(photoBitmaps) { onDispose(photoBitmaps::close) }
    val content by controller.content.collectAsStateWithLifecycle()
    val controllerBusy by controller.busy.collectAsStateWithLifecycle()
    val controllerError by controller.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var mediaBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var replyToMessageId by remember { mutableStateOf<String?>(null) }
    var chatBackground by remember(context) {
        mutableStateOf(ChatBackgroundOption.load(context))
    }
    val busy = controllerBusy || mediaBusy

    LaunchedEffect(pickedMedia) {
        if (pickedMedia != null) {
            mediaBusy = true
            val mimeType = context.contentResolver.getType(pickedMedia).orEmpty()
            if (mimeType.startsWith("video/")) {
                runCatching { VideoProcessor.fromPicker(context, pickedMedia) }
                    .onSuccess {
                        controller.sendVideo(it, replyToMessageId)
                        replyToMessageId = null
                    }
                    .onFailure { localError = it.message ?: "Das Video konnte nicht gelesen werden." }
            } else {
                runCatching { PhotoProcessor.fromPicker(context.contentResolver, pickedMedia) }
                    .onSuccess {
                        controller.sendPhoto(it, replyToMessageId)
                        replyToMessageId = null
                    }
                    .onFailure { localError = it.message ?: "Das Foto konnte nicht gelesen werden." }
            }
            mediaBusy = false
            onMediaConsumed(pickedMedia)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSystemPermissionPrompt(false) }

    LaunchedEffect(incomingLink, content) {
        if (
            !incomingLink.isNullOrBlank() &&
            content !is AppContentState.Loading &&
            content !is AppContentState.ProfileSetup
        ) {
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
    LaunchedEffect(content, replyToMessageId) {
        val conversation = content as? AppContentState.Conversation
        if (
            replyToMessageId != null &&
            conversation?.messages?.none { it.id == replyToMessageId } != false
        ) {
            replyToMessageId = null
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = content,
                contentKey = { it.route },
                transitionSpec = {
                    if (
                        initialState.route == ContentRoute.LOADING ||
                        targetState.route == ContentRoute.LOADING
                    ) {
                        fadeIn(tween(PanelFadeDurationMillis)) togetherWith
                            fadeOut(tween(PanelFadeDurationMillis))
                    } else {
                        val forward = targetState.route.ordinal > initialState.route.ordinal
                        val enter = slideInHorizontally(tween(PanelMotionDurationMillis)) {
                            if (forward) it else -it
                        } + fadeIn(tween(PanelFadeDurationMillis))
                        val exit = slideOutHorizontally(tween(PanelMotionDurationMillis)) {
                            if (forward) -it else it
                        } + fadeOut(tween(PanelFadeDurationMillis))
                        enter togetherWith exit
                    }
                },
                label = "content panel",
            ) { state ->
                when (state) {
                    AppContentState.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
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
                        photoBitmaps = photoBitmaps,
                        voiceBytes = controller::mediaBytes,
                        chatBackground = chatBackground.color,
                        busy = busy,
                        replyToMessageId = replyToMessageId,
                        onReplyToMessage = { replyToMessageId = it },
                        onCancelReply = { replyToMessageId = null },
                        onSend = {
                            controller.sendText(it, replyToMessageId)
                            replyToMessageId = null
                        },
                        onSendVoice = {
                            controller.sendVoice(it, replyToMessageId)
                            replyToMessageId = null
                        },
                        onSendRoundVideo = {
                            controller.sendRoundVideo(it, replyToMessageId)
                            replyToMessageId = null
                        },
                        onEditMessage = controller::editTextMessage,
                        onSetPinned = controller::setMessagePinned,
                        onAttachment = { overlay = Overlay.MediaCapture },
                        onSettings = { overlay = Overlay.Settings(state.pair) },
                        onRetry = controller::retryMessage,
                        onMedia = { overlay = Overlay.Media(it) },
                        onDeleteMessages = controller::deleteMessages,
                        onMessagesSeen = controller::markMessagesRead,
                        onSystemPermissionPrompt = onSystemPermissionPrompt,
                        onError = { localError = it },
                    )
                }
            }

            AnimatedContent(
                targetState = overlay,
                contentKey = {
                    when (it) {
                        Overlay.MediaCapture -> "media-capture"
                        Overlay.Scanner -> "scanner"
                        is Overlay.Settings -> "settings"
                        is Overlay.Media -> "media"
                        null -> "none"
                    }
                },
                transitionSpec = {
                    if (targetState != null) {
                        val enter = slideInHorizontally(tween(PanelMotionDurationMillis)) { it } +
                            fadeIn(tween(PanelFadeDurationMillis))
                        enter togetherWith fadeOut(tween(PanelFadeDurationMillis))
                    } else {
                        val exit = slideOutHorizontally(tween(PanelMotionDurationMillis)) { it } +
                            fadeOut(tween(PanelFadeDurationMillis))
                        fadeIn(tween(PanelFadeDurationMillis)) togetherWith exit
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "overlay panel",
            ) { destination ->
                Box(Modifier.fillMaxSize()) {
                    when (destination) {
                        Overlay.Scanner -> CameraPermissionGate(onSystemPermissionPrompt) {
                            QrScannerScreen(
                                onBack = { overlay = null },
                                onScanned = {
                                    overlay = null
                                    controller.acceptPayload(it)
                                },
                            )
                        }
                        Overlay.MediaCapture -> CameraPermissionGate(onSystemPermissionPrompt) {
                            MediaCaptureScreen(
                                onClose = { overlay = null },
                                onPickGallery = {
                                    overlay = null
                                    onPickMedia()
                                },
                                onSystemPermissionPrompt = onSystemPermissionPrompt,
                                onError = { localError = it },
                                onUsePhoto = { jpeg, leftQuarterTurns ->
                                    overlay = null
                                    scope.launch {
                                        mediaBusy = true
                                        runCatching {
                                            PhotoProcessor.fromCamera(jpeg, leftQuarterTurns)
                                        }
                                            .onSuccess {
                                                controller.sendPhoto(it, replyToMessageId)
                                                replyToMessageId = null
                                            }
                                            .onFailure {
                                                localError = it.message ?:
                                                    "Das Foto konnte nicht verarbeitet werden."
                                            }
                                        mediaBusy = false
                                    }
                                },
                                onUseVideo = { video ->
                                    overlay = null
                                    controller.sendVideo(video, replyToMessageId)
                                    replyToMessageId = null
                                },
                            )
                        }
                        is Overlay.Settings -> SettingsScreen(
                            pair = destination.pair,
                            busy = busy,
                            chatBackground = chatBackground,
                            onChatBackground = {
                                chatBackground = it
                                it.persist(context)
                                overlay = null
                            },
                            onBack = { overlay = null },
                            onRecovery = { mode ->
                                onAuthenticate {
                                    overlay = null
                                    controller.createRecoveryInvitation(mode)
                                }
                            },
                            onDelete = {
                                onAuthenticate {
                                    controller.prepareDelete { onDeleteAll() }
                                }
                            },
                        )
                        is Overlay.Media -> {
                            val conversation = content as? AppContentState.Conversation
                            if (conversation != null) {
                                MediaDetailScreen(
                                    messages = conversation.messages,
                                    initialEventId = destination.eventId,
                                    photoBitmaps = photoBitmaps,
                                    controller = controller,
                                    onBack = { overlay = null },
                                )
                            }
                        }
                        null -> Unit
                    }
                }
            }

            FeatureAnnouncementHost(
                visible = content is AppContentState.Conversation && overlay == null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 72.dp),
            )
        }
    }

    val error = localError ?: controllerError
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            localError = null
            controller.clearError()
        }
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
