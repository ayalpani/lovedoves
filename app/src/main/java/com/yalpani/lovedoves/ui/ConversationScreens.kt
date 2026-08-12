package com.yalpani.lovedoves.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.yalpani.lovedoves.LoveBlush
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.LoveMist
import com.yalpani.lovedoves.LovePaper
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.data.PairStateEntity
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PairingMode
import com.yalpani.lovedoves.domain.PreparedVideo
import com.yalpani.lovedoves.domain.PreparedVoice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ConversationScreen(
    pair: PairStateEntity,
    messages: List<ConversationEventEntity>,
    photoBitmaps: PhotoBitmapLoader,
    voiceBytes: suspend (String) -> ByteArray,
    chatBackground: Color,
    busy: Boolean,
    replyToMessageId: String?,
    onReplyToMessage: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    onSendVoice: (PreparedVoice) -> Unit,
    onSendRoundVideo: (PreparedVideo) -> Unit,
    onAttachment: () -> Unit,
    onSettings: () -> Unit,
    onRetry: (String) -> Unit,
    onMedia: (String) -> Unit,
    onDeleteMessages: (Set<String>) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onMessagesSeen: (List<String>) -> Unit,
    onSystemPermissionPrompt: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var text by remember { mutableStateOf(TextFieldValue()) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var inputTransition by remember { mutableStateOf(ComposerInputTransition.NONE) }
    var openAttachmentWhenImeCloses by remember { mutableStateOf(false) }
    var mediaToOpenWhenImeCloses by remember { mutableStateOf<String?>(null) }
    var lastKeyboardHeightPx by remember(context, configuration.orientation) {
        mutableIntStateOf(
            ComposerInputPreferences.keyboardHeightPx(context, configuration.orientation),
        )
    }
    var selectedMessageIds by remember { mutableStateOf(emptySet<String>()) }
    var actionMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var deleteTargetIds by remember { mutableStateOf(emptySet<String>()) }
    var scrollToMessageId by remember { mutableStateOf<String?>(null) }
    val deleteSelectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val voiceRecorder = remember(context) { MemoryVoiceRecorder(context.applicationContext) }
    val roundVideoRecorder = remember(context) {
        MemoryRoundVideoRecorder(context.applicationContext)
    }
    val recordingCues = remember { RecordingCuePlayer() }
    var captureType by remember { mutableStateOf(ComposerCaptureType.VOICE) }
    var voiceMode by remember { mutableStateOf(VoiceRecordingMode.IDLE) }
    var voiceElapsedMillis by remember { mutableLongStateOf(0L) }
    var voicePermissionPending by remember { mutableStateOf(false) }
    var roundVideoPermissionPending by remember { mutableStateOf(false) }
    var roundVideoVisible by remember { mutableStateOf(false) }

    fun cancelVoiceRecording() {
        val wasRecording = voiceRecorder.isRecording
        voiceRecorder.cancel()
        if (wasRecording) recordingCues.playStop()
        voiceMode = VoiceRecordingMode.IDLE
        voiceElapsedMillis = 0L
    }

    fun startVoiceRecording(mode: VoiceRecordingMode) {
        if (voiceRecorder.isRecording || busy) return
        recordingCues.playStart()
        runCatching { voiceRecorder.start() }
            .onSuccess {
                voiceElapsedMillis = 0L
                voiceMode = mode
            }
            .onFailure {
                cancelVoiceRecording()
                onError(it.message ?: "Die Sprachaufnahme konnte nicht gestartet werden.")
            }
    }

    fun finishVoiceRecording() {
        if (!voiceRecorder.isRecording) return
        val result = runCatching { voiceRecorder.finish() }
        recordingCues.playStop()
        result
            .onSuccess(onSendVoice)
            .onFailure { onError(it.message ?: "Die Sprachaufnahme konnte nicht gespeichert werden.") }
        voiceMode = VoiceRecordingMode.IDLE
        voiceElapsedMillis = 0L
    }

    fun startRoundVideoRecording(mode: VoiceRecordingMode) {
        if (roundVideoRecorder.state != MemoryRoundVideoRecorder.State.IDLE || busy) return
        showEmojiPicker = false
        inputTransition = ComposerInputTransition.NONE
        focusManager.clearFocus()
        keyboard?.hide()
        recordingCues.playStart()
        roundVideoVisible = true
        voiceMode = mode
        roundVideoRecorder.start(
            onReady = { video ->
                if (voiceMode != VoiceRecordingMode.IDLE) recordingCues.playStop()
                roundVideoVisible = false
                voiceMode = VoiceRecordingMode.IDLE
                onSendRoundVideo(video)
            },
            onFailure = { message ->
                if (voiceMode != VoiceRecordingMode.IDLE) recordingCues.playStop()
                roundVideoVisible = false
                voiceMode = VoiceRecordingMode.IDLE
                onError(message)
            },
        )
    }

    fun cancelActiveRecording() {
        if (captureType == ComposerCaptureType.ROUND_VIDEO) {
            val wasRecording = roundVideoVisible
            roundVideoRecorder.cancel()
            roundVideoVisible = false
            if (wasRecording) recordingCues.playStop()
            voiceMode = VoiceRecordingMode.IDLE
        } else {
            cancelVoiceRecording()
        }
    }

    fun finishActiveRecording() {
        if (captureType == ComposerCaptureType.ROUND_VIDEO) {
            if (!roundVideoVisible) return
            roundVideoRecorder.finish()
            recordingCues.playStop()
            voiceMode = VoiceRecordingMode.FINALIZING
        } else {
            finishVoiceRecording()
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSystemPermissionPrompt(false)
        val shouldStart = voicePermissionPending
        voicePermissionPending = false
        if (granted && shouldStart) {
            startVoiceRecording(VoiceRecordingMode.LOCKED)
        } else if (!granted && shouldStart) {
            onError("Für Sprachnachrichten braucht Love Doves Zugriff auf das Mikrofon.")
        }
    }
    val roundVideoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        onSystemPermissionPrompt(false)
        val shouldStart = roundVideoPermissionPending
        roundVideoPermissionPending = false
        val granted = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
        if (granted && shouldStart) {
            startRoundVideoRecording(VoiceRecordingMode.LOCKED)
        } else if (shouldStart) {
            onError("Für runde Videos braucht Love Doves Zugriff auf Kamera und Mikrofon.")
        }
    }
    DisposableEffect(voiceRecorder, roundVideoRecorder, recordingCues) {
        onDispose {
            voiceRecorder.close()
            roundVideoRecorder.close()
            recordingCues.close()
        }
    }
    fun retainKeyboardHeight(heightPx: Int) {
        if (heightPx <= 0 || heightPx == lastKeyboardHeightPx) return
        lastKeyboardHeightPx = heightPx
        ComposerInputPreferences.persistKeyboardHeightPx(
            context = context,
            orientation = configuration.orientation,
            heightPx = heightPx,
        )
    }

    val imeHeightPx = (
        WindowInsets.ime.getBottom(density) -
            WindowInsets.navigationBars.getBottom(density)
        ).coerceAtLeast(0)
    val fallbackEmojiHeight = if (
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        220.dp
    } else {
        320.dp
    }
    val targetInputHeightPx = lastKeyboardHeightPx.takeIf { it > 0 }
        ?: with(density) { fallbackEmojiHeight.toPx().roundToInt() }
    val inputSurfaceHeightPx = when {
        showEmojiPicker || inputTransition != ComposerInputTransition.NONE -> targetInputHeightPx
        imeHeightPx > 0 -> imeHeightPx
        else -> 0
    }
    val inputSurfaceHeight = with(density) { inputSurfaceHeightPx.toDp() }
    val emojiSearchImeVisible =
        showEmojiPicker && inputTransition == ComposerInputTransition.NONE && imeHeightPx > 0
    val listState = rememberLazyListState()
    val messagesById = remember(messages) { messages.associateBy(ConversationEventEntity::id) }
    val replyTarget = replyToMessageId?.let(messagesById::get)
    val editingMessage = editingMessageId?.let(messagesById::get)
    val pinnedMessage = messages.asSequence()
        .filter(ConversationEventEntity::pinned)
        .maxByOrNull(ConversationEventEntity::pinUpdatedAtEpochMillis)
    var initialMessagesPositioned by remember { mutableStateOf(false) }
    val newestMessage = messages.lastOrNull()
    LaunchedEffect(newestMessage?.id) {
        if (
            newestMessage != null &&
            shouldPinNewestMessage(newestMessage.outgoing, listState.firstVisibleItemIndex)
        ) {
            if (initialMessagesPositioned) {
                listState.animateScrollToItem(0)
            } else {
                listState.requestScrollToItem(0)
                initialMessagesPositioned = true
            }
        }
    }
    LaunchedEffect(scrollToMessageId, messages) {
        val targetId = scrollToMessageId ?: return@LaunchedEffect
        val index = messages.asReversed().indexOfFirst { it.id == targetId }
        if (index >= 0) listState.animateScrollToItem(index)
        scrollToMessageId = null
    }
    val unreadIncomingIds = messages
        .filter { !it.outgoing && it.deliveryState != LoveDovesRepository.DELIVERY_READ }
        .map { it.id }
    LaunchedEffect(unreadIncomingIds) {
        if (unreadIncomingIds.isNotEmpty()) onMessagesSeen(unreadIncomingIds)
    }
    LaunchedEffect(editingMessageId, editingMessage) {
        if (editingMessageId != null && editingMessage == null) editingMessageId = null
    }
    BackHandler(enabled = selectedMessageIds.isNotEmpty()) {
        selectedMessageIds = emptySet()
    }
    BackHandler(enabled = showEmojiPicker) {
        showEmojiPicker = false
        inputTransition = ComposerInputTransition.NONE
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = voiceMode != VoiceRecordingMode.IDLE || roundVideoVisible) {
        cancelActiveRecording()
    }
    LaunchedEffect(voiceMode, captureType) {
        while (
            captureType == ComposerCaptureType.VOICE &&
            voiceMode != VoiceRecordingMode.IDLE
        ) {
            voiceElapsedMillis = voiceRecorder.elapsedMillis()
            if (voiceElapsedMillis >= LoveDovesRepository.MAX_VOICE_DURATION_MILLIS) {
                finishVoiceRecording()
                break
            }
            delay(VOICE_TIMER_INTERVAL_MILLIS)
        }
    }
    LaunchedEffect(
        imeHeightPx,
        showEmojiPicker,
        inputTransition,
        openAttachmentWhenImeCloses,
        mediaToOpenWhenImeCloses,
    ) {
        if (
            !showEmojiPicker &&
            inputTransition == ComposerInputTransition.NONE &&
            !openAttachmentWhenImeCloses &&
            mediaToOpenWhenImeCloses == null &&
            imeHeightPx > 0
        ) {
            delay(INPUT_SETTLE_MILLIS)
            retainKeyboardHeight(imeHeightPx)
        }
    }
    LaunchedEffect(inputTransition) {
        if (inputTransition == ComposerInputTransition.TO_KEYBOARD) {
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboard?.show()
        }
    }
    LaunchedEffect(showEmojiPicker, inputTransition) {
        if (showEmojiPicker && inputTransition != ComposerInputTransition.TO_KEYBOARD) {
            withFrameNanos { }
            keyboard?.hide()
        }
    }
    LaunchedEffect(inputTransition, imeHeightPx) {
        when {
            inputTransition == ComposerInputTransition.TO_EMOJI && imeHeightPx == 0 -> {
                inputTransition = ComposerInputTransition.NONE
            }
            inputTransition == ComposerInputTransition.TO_KEYBOARD && imeHeightPx > 0 -> {
                delay(INPUT_SETTLE_MILLIS)
                retainKeyboardHeight(imeHeightPx)
                showEmojiPicker = false
                delay(EMOJI_EXIT_MILLIS.toLong())
                inputTransition = ComposerInputTransition.NONE
            }
        }
    }
    LaunchedEffect(openAttachmentWhenImeCloses, imeHeightPx) {
        if (openAttachmentWhenImeCloses && imeHeightPx == 0) {
            withFrameNanos { }
            openAttachmentWhenImeCloses = false
            onAttachment()
        }
    }
    LaunchedEffect(
        mediaToOpenWhenImeCloses,
        imeHeightPx,
        showEmojiPicker,
        inputTransition,
    ) {
        val mediaId = mediaToOpenWhenImeCloses
        if (
            mediaId != null &&
            imeHeightPx == 0 &&
            !showEmojiPicker &&
            inputTransition == ComposerInputTransition.NONE
        ) {
            withFrameNanos { }
            mediaToOpenWhenImeCloses = null
            onMedia(mediaId)
        }
    }
    fun toggleSelection(messageId: String) {
        selectedMessageIds = if (messageId in selectedMessageIds) {
            selectedMessageIds - messageId
        } else {
            selectedMessageIds + messageId
        }
    }
    fun beginSelection(messageId: String) {
        cancelActiveRecording()
        showEmojiPicker = false
        inputTransition = ComposerInputTransition.NONE
        focusManager.clearFocus()
        keyboard?.hide()
        selectedMessageIds = selectedMessageIds + messageId
    }
    fun focusComposer() {
        showEmojiPicker = false
        inputTransition = ComposerInputTransition.TO_KEYBOARD
        focusRequester.requestFocus()
        keyboard?.show()
    }
    fun beginReply(messageId: String) {
        actionMessageId = null
        editingMessageId = null
        onReplyToMessage(messageId)
        focusComposer()
    }
    fun beginEdit(message: ConversationEventEntity) {
        val body = message.body ?: return
        actionMessageId = null
        onCancelReply()
        editingMessageId = message.id
        text = TextFieldValue(body, selection = TextRange(body.length))
        focusComposer()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selectedMessageIds.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedMessageIds = emptySet() }) {
                    CloseIcon("Auswahl beenden")
                }
                Text(
                    "${selectedMessageIds.size} ausgewählt",
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val selectedText = messages
                    .filter { it.id in selectedMessageIds }
                    .mapNotNull { it.body }
                    .joinToString("\n\n")
                IconButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Love Doves", selectedText))
                        selectedMessageIds = emptySet()
                    },
                    enabled = selectedText.isNotEmpty(),
                ) {
                    CopyIcon("Text kopieren")
                }
                IconButton(onClick = { deleteTargetIds = selectedMessageIds }) {
                    DeleteIcon("Ausgewählte Nachrichten löschen")
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = LoveBlush,
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        HeartIcon(modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        pair.partnerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Nur für euch zwei",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onSettings) {
                    SettingsIcon("Einstellungen", Modifier.size(28.dp))
                }
            }
        }
        if (selectedMessageIds.isEmpty() && pinnedMessage != null) {
            PinnedMessageBanner(
                message = pinnedMessage,
                onClick = { scrollToMessageId = pinnedMessage.id },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(chatBackground),
        ) {
            if (messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(36.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(color = LoveBlush, shape = CircleShape, modifier = Modifier.size(92.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            HeartIcon(modifier = Modifier.size(42.dp))
                        }
                    }
                    Text(
                        "Hier beginnt euer kleiner Raum.",
                        modifier = Modifier.padding(top = 24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Die erste Nachricht kann nur auf eure beiden Geräte entschlüsselt werden.",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(messages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            replyTarget = message.replyToId?.let(messagesById::get),
                            photoBitmaps = photoBitmaps,
                            voiceBytes = voiceBytes,
                            selected = message.id in selectedMessageIds,
                            selectionMode = selectedMessageIds.isNotEmpty(),
                            actionMenuVisible = actionMessageId == message.id,
                            onShowActions = { actionMessageId = message.id },
                            onDismissActions = { actionMessageId = null },
                            onReply = { beginReply(message.id) },
                            onPin = {
                                actionMessageId = null
                                onSetPinned(message.id, !message.pinned)
                            },
                            onEdit = { beginEdit(message) },
                            onDelete = {
                                actionMessageId = null
                                deleteTargetIds = setOf(message.id)
                            },
                            onRetry = onRetry,
                            onOpenMedia = {
                                cancelActiveRecording()
                                showEmojiPicker = false
                                inputTransition = ComposerInputTransition.NONE
                                mediaToOpenWhenImeCloses = message.id
                                focusManager.clearFocus()
                                keyboard?.hide()
                            },
                            onToggleSelection = { toggleSelection(message.id) },
                            onLongPress = { beginSelection(message.id) },
                        )
                    }
                }
            }
            if (roundVideoVisible) {
                RoundVideoCaptureOverlay(
                    recorder = roundVideoRecorder,
                    onUnavailable = { message ->
                        cancelActiveRecording()
                        onError(message)
                    },
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(LovePaper)
                .navigationBarsPadding()
                .then(if (emojiSearchImeVisible) Modifier.imePadding() else Modifier),
        ) {
            when {
                editingMessage != null -> ComposerContextBar(
                    title = "Edit",
                    preview = messagePreview(editingMessage),
                    onClose = {
                        editingMessageId = null
                        text = TextFieldValue()
                    },
                )
                replyTarget != null -> ComposerContextBar(
                    title = "Reply",
                    preview = messagePreview(replyTarget),
                    onClose = onCancelReply,
                )
            }
            MessageComposer(
                text = text,
                busy = busy,
                attachmentEnabled = editingMessage == null,
                emojiPickerVisible = showEmojiPicker,
                captureType = captureType,
                voiceMode = voiceMode,
                voiceElapsedMillis = if (captureType == ComposerCaptureType.ROUND_VIDEO) {
                    roundVideoRecorder.elapsedMillis
                } else {
                    voiceElapsedMillis
                },
                focusRequester = focusRequester,
                onTextChange = {
                    if (it.text.length <= LoveDovesRepository.MAX_TEXT_LENGTH) text = it
                },
                onTextFocus = {
                    if (showEmojiPicker && inputTransition != ComposerInputTransition.TO_KEYBOARD) {
                        inputTransition = ComposerInputTransition.TO_KEYBOARD
                    }
                },
                onEmoji = {
                    if (showEmojiPicker) {
                        inputTransition = ComposerInputTransition.TO_KEYBOARD
                    } else {
                        retainKeyboardHeight(imeHeightPx)
                        focusRequester.requestFocus()
                        showEmojiPicker = true
                        inputTransition = if (imeHeightPx > 0) {
                            ComposerInputTransition.TO_EMOJI
                        } else {
                            ComposerInputTransition.NONE
                        }
                        keyboard?.hide()
                    }
                },
                onAttachment = {
                    showEmojiPicker = false
                    inputTransition = ComposerInputTransition.NONE
                    openAttachmentWhenImeCloses = true
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
                onSend = {
                    val message = text.text
                    text = TextFieldValue()
                    val editId = editingMessageId
                    if (editId == null) {
                        onSend(message)
                    } else {
                        editingMessageId = null
                        onEditMessage(editId, message)
                    }
                },
                onCaptureTap = {
                    cancelActiveRecording()
                    captureType = if (captureType == ComposerCaptureType.VOICE) {
                        ComposerCaptureType.ROUND_VIDEO
                    } else {
                        ComposerCaptureType.VOICE
                    }
                },
                onVoiceStart = {
                    if (captureType == ComposerCaptureType.ROUND_VIDEO) {
                        val hasCamera = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasAudio = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamera && hasAudio) {
                            startRoundVideoRecording(VoiceRecordingMode.HOLDING)
                        } else {
                            roundVideoPermissionPending = true
                            onSystemPermissionPrompt(true)
                            roundVideoPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO,
                                ),
                            )
                        }
                    } else {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startVoiceRecording(VoiceRecordingMode.HOLDING)
                        } else {
                            voicePermissionPending = true
                            onSystemPermissionPrompt(true)
                            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onVoiceCancel = ::cancelActiveRecording,
                onVoiceLock = {
                    if (voiceMode == VoiceRecordingMode.HOLDING) {
                        voiceMode = VoiceRecordingMode.LOCKED
                    }
                },
                onVoiceRelease = {
                    if (voiceMode == VoiceRecordingMode.HOLDING) finishActiveRecording()
                },
                onVoicePauseToggle = {
                    runCatching {
                        if (voiceMode == VoiceRecordingMode.PAUSED) {
                            if (captureType == ComposerCaptureType.ROUND_VIDEO) {
                                roundVideoRecorder.resume()
                            } else {
                                voiceRecorder.resume()
                            }
                            voiceMode = VoiceRecordingMode.LOCKED
                        } else if (voiceMode == VoiceRecordingMode.LOCKED) {
                            if (captureType == ComposerCaptureType.ROUND_VIDEO) {
                                roundVideoRecorder.pause()
                            } else {
                                voiceRecorder.pause()
                                voiceElapsedMillis = voiceRecorder.elapsedMillis()
                            }
                            voiceMode = VoiceRecordingMode.PAUSED
                        }
                    }.onFailure {
                        cancelActiveRecording()
                        onError(it.message ?: "Die Aufnahme konnte nicht pausiert werden.")
                    }
                },
                onVoiceSend = ::finishActiveRecording,
            )
            if (inputSurfaceHeightPx > 0) {
                Box(Modifier.fillMaxWidth().height(inputSurfaceHeight)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showEmojiPicker,
                        modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(EMOJI_EXIT_MILLIS)),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            HorizontalDivider(color = LoveInk.copy(alpha = 0.12f))
                            EmojiPickerKeyboard(
                                modifier = Modifier.weight(1f),
                                onEmojiPicked = { emoji ->
                                    val updated = text.insertAtSelection(emoji)
                                    if (updated.text.length <= LoveDovesRepository.MAX_TEXT_LENGTH) {
                                        text = updated
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (deleteTargetIds.isNotEmpty()) {
        LoveModalBottomSheet(
            onDismissRequest = { deleteTargetIds = emptySet() },
            sheetState = deleteSelectionSheetState,
        ) {
            SettingsSheetContent(
                title = if (deleteTargetIds.size == 1) {
                    "Nachricht löschen?"
                } else {
                    "${deleteTargetIds.size} Nachrichten löschen?"
                },
                description = "Die Auswahl wird nur von diesem Gerät gelöscht. Bereits empfangene Inhalte bleiben auf dem Partnergerät erhalten.",
            ) {
                LovePrimaryButton(
                    label = "Lokal löschen",
                    destructive = true,
                    enabled = !busy,
                    onClick = {
                        val ids = deleteTargetIds
                        photoBitmaps.evict(
                            messages.filter { it.id in ids }.mapNotNull { it.mediaId },
                        )
                        deleteTargetIds = emptySet()
                        selectedMessageIds = selectedMessageIds - ids
                        onDeleteMessages(ids)
                    },
                )
                LoveSecondaryButton(
                    label = "Abbrechen",
                    onClick = { deleteTargetIds = emptySet() },
                )
            }
        }
    }
}

@Composable
internal fun MessageComposer(
    text: TextFieldValue,
    busy: Boolean,
    attachmentEnabled: Boolean = true,
    emojiPickerVisible: Boolean,
    captureType: ComposerCaptureType,
    voiceMode: VoiceRecordingMode,
    voiceElapsedMillis: Long,
    focusRequester: FocusRequester,
    onTextChange: (TextFieldValue) -> Unit,
    onTextFocus: () -> Unit,
    onEmoji: () -> Unit,
    onAttachment: () -> Unit,
    onSend: () -> Unit,
    onCaptureTap: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceCancel: () -> Unit,
    onVoiceLock: () -> Unit,
    onVoiceRelease: () -> Unit,
    onVoicePauseToggle: () -> Unit,
    onVoiceSend: () -> Unit,
) {
    val canSend = text.text.isNotBlank() && !busy
    val recording = voiceMode != VoiceRecordingMode.IDLE
    var composerWidthPx by remember { mutableIntStateOf(0) }
    var cancelProgress by remember { mutableFloatStateOf(0f) }
    var captureHint by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val fallbackWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val cancelThresholdPx = (composerWidthPx.takeIf { it > 0 }?.toFloat() ?: fallbackWidthPx) *
        VOICE_CANCEL_WIDTH_FRACTION
    val lockThresholdPx = with(density) { VOICE_LOCK_GESTURE_THRESHOLD.toPx() }
    LaunchedEffect(voiceMode) {
        if (voiceMode != VoiceRecordingMode.HOLDING) cancelProgress = 0f
    }
    LaunchedEffect(captureHint) {
        if (captureHint != null) {
            delay(CAPTURE_HINT_DURATION_MILLIS)
            captureHint = null
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { composerWidthPx = it.width }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.16f)),
            ) {
                Box(Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onEmoji,
                            enabled = !busy && !recording,
                            modifier = Modifier.size(44.dp),
                        ) {
                            if (emojiPickerVisible) {
                                KeyboardIcon("Tastatur öffnen", modifier = Modifier.size(26.dp))
                            } else {
                                SmileIcon("Emoji wählen", modifier = Modifier.size(26.dp))
                            }
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { if (!recording) onTextChange(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) onTextFocus() }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LoveInk),
                            cursorBrush = SolidColor(LoveInk),
                            keyboardOptions = KeyboardOptions(
                                showKeyboardOnFocus = !emojiPickerVisible,
                            ),
                            maxLines = 5,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (text.text.isEmpty()) {
                                        Text(
                                            "Etwas nur für euch …",
                                            color = LoveInk.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        IconButton(
                            onClick = onAttachment,
                            enabled = attachmentEnabled && !busy && !recording,
                            modifier = Modifier.size(44.dp),
                        ) {
                            PaperclipIcon("Medien anhängen", modifier = Modifier.size(26.dp))
                        }
                    }
                    if (recording) {
                        VoiceRecordingBar(
                            mode = voiceMode,
                            elapsedMillis = voiceElapsedMillis,
                            cancelProgress = cancelProgress,
                            onCancel = onVoiceCancel,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
        ComposerVoiceAction(
            captureType = captureType,
            mode = voiceMode,
            canSendText = canSend,
            enabled = !busy,
            cancelThresholdPx = cancelThresholdPx,
            lockThresholdPx = lockThresholdPx,
            onSendText = onSend,
            captureHint = captureHint,
            onCaptureTap = {
                captureHint = if (captureType == ComposerCaptureType.VOICE) {
                    "Hold to record video. Tap to switch to audio."
                } else {
                    "Hold to record audio. Tap to switch to video."
                }
                onCaptureTap()
            },
            onVoiceStart = {
                cancelProgress = 0f
                onVoiceStart()
            },
            onCancelProgress = { cancelProgress = it },
            onVoiceCancel = {
                cancelProgress = 0f
                onVoiceCancel()
            },
            onVoiceLock = {
                cancelProgress = 0f
                onVoiceLock()
            },
            onVoiceRelease = {
                cancelProgress = 0f
                onVoiceRelease()
            },
            onPauseToggle = onVoicePauseToggle,
            onSendVoice = onVoiceSend,
        )
    }
}

@Composable
private fun VoiceRecordingBar(
    mode: VoiceRecordingMode,
    elapsedMillis: Long,
    cancelProgress: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "recording pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "recording dot",
    )
    Row(
        modifier = modifier
            .height(48.dp)
            .background(Color.White)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(0xFFD94C4C).copy(alpha = pulse), CircleShape),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                formatVoiceRecordingDuration(elapsedMillis),
                color = LoveInk.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (mode == VoiceRecordingMode.HOLDING) {
                val hintColor = LoveInk.copy(alpha = 0.62f * (1f - cancelProgress))
                Row(
                    modifier = Modifier.weight(1f).clipToBounds(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        3.dp,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    ChevronLeftIcon(
                        modifier = Modifier.size(16.dp),
                        color = hintColor,
                    )
                    Text(
                        "Verwerfen",
                        color = hintColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

@Composable
private fun ComposerVoiceAction(
    captureType: ComposerCaptureType,
    mode: VoiceRecordingMode,
    canSendText: Boolean,
    enabled: Boolean,
    cancelThresholdPx: Float,
    lockThresholdPx: Float,
    onSendText: () -> Unit,
    captureHint: String?,
    onCaptureTap: () -> Unit,
    onVoiceStart: () -> Unit,
    onCancelProgress: (Float) -> Unit,
    onVoiceCancel: () -> Unit,
    onVoiceLock: () -> Unit,
    onVoiceRelease: () -> Unit,
    onPauseToggle: () -> Unit,
    onSendVoice: () -> Unit,
) {
    val recording = mode != VoiceRecordingMode.IDLE
    val locked = mode == VoiceRecordingMode.LOCKED || mode == VoiceRecordingMode.PAUSED
    val finalizing = mode == VoiceRecordingMode.FINALIZING
    val pulse by rememberInfiniteTransition(label = "microphone pulse").animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), repeatMode = RepeatMode.Reverse),
        label = "microphone background",
    )
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (captureHint != null && !recording) {
            val hintOffset = with(LocalDensity.current) {
                IntOffset(0, -CAPTURE_HINT_OVERLAY_OFFSET.roundToPx())
            }
            Popup(
                alignment = Alignment.TopEnd,
                offset = hintOffset,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false,
                ),
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 310.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.16f)),
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = captureHint,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = LoveInk,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (recording && !finalizing) {
            val lockOffset = with(LocalDensity.current) {
                IntOffset(0, -VOICE_LOCK_OVERLAY_OFFSET.roundToPx())
            }
            Popup(
                alignment = Alignment.TopCenter,
                offset = lockOffset,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false,
                ),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.16f)),
                ) {
                    Box(
                        modifier = if (locked) {
                            Modifier.clickable(onClick = onPauseToggle)
                        } else {
                            Modifier
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (mode) {
                            VoiceRecordingMode.PAUSED -> PlayIcon(
                                "Aufnahme fortsetzen",
                                Modifier.size(18.dp),
                            )
                            VoiceRecordingMode.LOCKED -> PauseIcon(
                                "Aufnahme pausieren",
                                Modifier.size(18.dp),
                            )
                            else -> LockIcon(
                                "Nach oben ziehen zum Verriegeln",
                                Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        val actionModifier = Modifier
            .size(48.dp)
            .background(
                when {
                    recording -> LoveInk.copy(alpha = pulse)
                    canSendText -> LoveInk
                    enabled -> LoveInk
                    else -> LoveMist
                },
                CircleShape,
            )
            .then(
                if (finalizing) {
                    Modifier
                } else if (!canSendText && !locked) {
                    Modifier.voiceRecordGesture(
                        enabled = enabled,
                        contentDescription = if (captureType == ComposerCaptureType.VOICE) {
                            "Sprachnachricht aufnehmen"
                        } else {
                            "Rundes Video aufnehmen"
                        },
                        tapThresholdMillis = CAPTURE_TAP_THRESHOLD_MILLIS,
                        cancelThresholdPx = cancelThresholdPx,
                        lockThresholdPx = lockThresholdPx,
                        onTap = onCaptureTap,
                        onStart = onVoiceStart,
                        onCancelProgress = onCancelProgress,
                        onCancel = onVoiceCancel,
                        onLock = onVoiceLock,
                        onRelease = onVoiceRelease,
                    )
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        onClick = if (locked) onSendVoice else onSendText,
                    )
                },
            )
        Box(actionModifier, contentAlignment = Alignment.Center) {
            if (finalizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else if (canSendText || locked) {
                SendHorizontalIcon(
                    when {
                        !locked -> "Nachricht senden"
                        captureType == ComposerCaptureType.ROUND_VIDEO -> "Rundes Video senden"
                        else -> "Sprachnachricht senden"
                    },
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                )
            } else if (captureType == ComposerCaptureType.ROUND_VIDEO) {
                VideoIcon(
                    "Zu Sprachnachrichten wechseln",
                    modifier = Modifier.size(26.dp),
                    color = Color.White,
                )
            } else {
                MicrophoneIcon(
                    "Zu runden Videos wechseln",
                    modifier = Modifier.size(26.dp),
                    color = if (enabled) Color.White else LoveInk.copy(alpha = 0.35f),
                )
            }
        }
    }
}

internal fun TextFieldValue.insertAtSelection(insertedText: String): TextFieldValue {
    val selectionStart = min(selection.start, selection.end).coerceIn(0, text.length)
    val selectionEnd = max(selection.start, selection.end).coerceIn(selectionStart, text.length)
    val updatedText = text.replaceRange(selectionStart, selectionEnd, insertedText)
    val updatedCursor = selectionStart + insertedText.length
    return copy(
        text = updatedText,
        selection = TextRange(updatedCursor),
        composition = null,
    )
}

private enum class ComposerInputTransition { NONE, TO_EMOJI, TO_KEYBOARD }
internal enum class ComposerCaptureType { VOICE, ROUND_VIDEO }
internal enum class VoiceRecordingMode { IDLE, HOLDING, LOCKED, PAUSED, FINALIZING }

private const val INPUT_SETTLE_MILLIS = 120L
private const val EMOJI_EXIT_MILLIS = 100
private const val VOICE_TIMER_INTERVAL_MILLIS = 100L
private const val CAPTURE_TAP_THRESHOLD_MILLIS = 500L
private const val CAPTURE_HINT_DURATION_MILLIS = 2_800L
private const val MESSAGE_PREVIEW_LENGTH = 120
private const val VOICE_CANCEL_WIDTH_FRACTION = 0.3f
private val VOICE_LOCK_GESTURE_THRESHOLD = 78.dp
private val VOICE_LOCK_OVERLAY_OFFSET = 50.dp
private val CAPTURE_HINT_OVERLAY_OFFSET = 58.dp

internal fun formatVoiceRecordingDuration(durationMillis: Long): String {
    val clamped = durationMillis.coerceAtLeast(0L)
    val minutes = clamped / 60_000L
    val seconds = (clamped / 1_000L) % 60L
    val tenths = (clamped / 100L) % 10L
    return "%d:%02d,%d".format(minutes, seconds, tenths)
}

internal fun shouldPinNewestMessage(
    newestOutgoing: Boolean,
    firstVisibleItemIndex: Int,
): Boolean = newestOutgoing || firstVisibleItemIndex <= 1

internal fun messagePreview(message: ConversationEventEntity): String = when (message.kind) {
    LoveDovesRepository.KIND_TEXT -> message.body.orEmpty()
        .replace('\n', ' ')
        .take(MESSAGE_PREVIEW_LENGTH)
    LoveDovesRepository.KIND_PHOTO -> "Photo"
    LoveDovesRepository.KIND_VIDEO -> "Video"
    LoveDovesRepository.KIND_ROUND_VIDEO -> "Video message"
    LoveDovesRepository.KIND_VOICE -> "Voice message"
    else -> "Message"
}

@Composable
internal fun MessageActionMenu(
    expanded: Boolean,
    outgoing: Boolean,
    pinned: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(196.dp),
        offset = DpOffset(if (outgoing) 4.dp else (-4).dp, (-10).dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.1f)),
    ) {
        MessageActionItem("Reply", { ReplyIcon(modifier = Modifier.size(23.dp)) }, onReply)
        MessageActionItem(
            if (pinned) "Unpin" else "Pin",
            { PinIcon(modifier = Modifier.size(23.dp)) },
            onPin,
        )
        if (canEdit) {
            MessageActionItem("Edit", { EditIcon(modifier = Modifier.size(23.dp)) }, onEdit)
        }
        MessageActionItem("Delete", { DeleteIcon(modifier = Modifier.size(23.dp)) }, onDelete)
    }
}

@Composable
private fun MessageActionItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        leadingIcon = icon,
        modifier = Modifier.height(52.dp),
    )
}

@Composable
private fun ReplyReference(
    message: ConversationEventEntity?,
    outgoing: Boolean,
) {
    Surface(
        modifier = Modifier.widthIn(max = 286.dp).padding(bottom = 3.dp),
        color = if (outgoing) LoveBlush else LoveMist,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.width(3.dp).height(30.dp).background(LoveInk, CircleShape))
            Text(
                message?.let(::messagePreview) ?: "Original message unavailable",
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = LoveInk.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PinnedMessageBanner(
    message: ConversationEventEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PinIcon(modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text("Pinned message", style = MaterialTheme.typography.labelLarge)
            Text(
                messagePreview(message),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LoveInk.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ComposerContextBar(title: String, preview: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(3.dp).height(34.dp).background(LoveInk, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                preview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LoveInk.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
            CloseIcon("Close $title", Modifier.size(20.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    message: ConversationEventEntity,
    replyTarget: ConversationEventEntity?,
    photoBitmaps: PhotoBitmapLoader,
    voiceBytes: suspend (String) -> ByteArray,
    selected: Boolean,
    selectionMode: Boolean,
    actionMenuVisible: Boolean,
    onShowActions: () -> Unit,
    onDismissActions: () -> Unit,
    onReply: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: (String) -> Unit,
    onOpenMedia: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
) {
    val sideTapInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) LoveInk.copy(alpha = 0.1f) else Color.Transparent)
            .padding(vertical = 2.dp),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = sideTapInteraction,
                    indication = null,
                    onClickLabel = "Message actions",
                    onClick = {
                        if (selectionMode) onToggleSelection() else onShowActions()
                    },
                ),
        )
        Box(
            Modifier
                .fillMaxWidth(0.18f)
                .fillMaxHeight()
                .align(if (message.outgoing) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            MessageActionMenu(
                expanded = actionMenuVisible,
                outgoing = message.outgoing,
                pinned = message.pinned,
                canEdit = message.outgoing && message.kind == LoveDovesRepository.KIND_TEXT,
                onDismiss = onDismissActions,
                onReply = onReply,
                onPin = onPin,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(0.82f),
                contentAlignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Column(
                    horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            when {
                                selectionMode -> onToggleSelection()
                                message.kind == LoveDovesRepository.KIND_PHOTO ||
                                    message.kind == LoveDovesRepository.KIND_VIDEO ||
                                    message.kind == LoveDovesRepository.KIND_ROUND_VIDEO -> onOpenMedia()
                            }
                        },
                        onLongClick = onLongPress,
                    ),
                ) {
                    if (message.replyToId != null) {
                        ReplyReference(
                            message = replyTarget,
                            outgoing = message.outgoing,
                        )
                    }
                    if (message.kind == LoveDovesRepository.KIND_ROUND_VIDEO) {
                        RoundVideoMessageBubble(message, photoBitmaps)
                    } else {
                        MessageSurface(message, photoBitmaps, voiceBytes)
                    }
                }
            }
            if (message.outgoing && message.deliveryState == LoveDovesRepository.DELIVERY_FAILED) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(
                        "Erneut senden",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { onRetry(message.id) }.padding(4.dp),
                    )
                }
            }
        }
        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .align(if (message.outgoing) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 8.dp)
                    .size(26.dp),
                shape = CircleShape,
                color = if (selected) LoveInk else Color.White,
                border = BorderStroke(1.5.dp, LoveInk.copy(alpha = 0.45f)),
                contentColor = if (selected) Color.White else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SelectionCheckIcon(modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageSurface(
    message: ConversationEventEntity,
    photoBitmaps: PhotoBitmapLoader,
    voiceBytes: suspend (String) -> ByteArray,
) {
    Surface(
        color = if (message.outgoing) LoveBlush else LoveMist,
        shape = RoundedCornerShape(
            topStart = 22.dp,
            topEnd = 22.dp,
            bottomStart = if (message.outgoing) 22.dp else 6.dp,
            bottomEnd = if (message.outgoing) 6.dp else 22.dp,
        ),
    ) {
        when (message.kind) {
            LoveDovesRepository.KIND_PHOTO -> MessagePhoto(message, photoBitmaps)
            LoveDovesRepository.KIND_VIDEO -> MessageVideo(message, photoBitmaps)
            LoveDovesRepository.KIND_VOICE -> VoiceMessageContent(
                mediaId = requireNotNull(message.mediaId),
                declaredDurationMillis = 0L,
                mediaBytes = voiceBytes,
                footer = { CompactMessageMetadata(message) },
            )
            else -> MessageText(message)
        }
    }
}

@Composable
private fun MessagePhoto(message: ConversationEventEntity, photoBitmaps: PhotoBitmapLoader) {
    Box {
        EncryptedPhotoImage(
            mediaId = requireNotNull(message.mediaId),
            photoBitmaps = photoBitmaps,
            contentDescription = if (message.outgoing) "Gesendetes Foto" else "Empfangenes Foto",
            modifier = Modifier.fillMaxWidth().height(260.dp),
            contentScale = ContentScale.Crop,
        )
        MessageMediaMetadata(message, Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

@Composable
private fun MessageVideo(message: ConversationEventEntity, photoBitmaps: PhotoBitmapLoader) {
    Box(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        EncryptedPhotoImage(
            mediaId = requireNotNull(message.mediaId),
            photoBitmaps = photoBitmaps,
            contentDescription = if (message.outgoing) "Gesendetes Video" else "Empfangenes Video",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f),
            contentColor = Color.White,
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlayIcon(modifier = Modifier.size(26.dp), color = Color.White)
            }
        }
        MessageMediaMetadata(message, Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

@Composable
private fun RoundVideoMessageBubble(
    message: ConversationEventEntity,
    photoBitmaps: PhotoBitmapLoader,
) {
    val mediaId = requireNotNull(message.mediaId)
    Box(Modifier.size(252.dp)) {
        Surface(
            modifier = Modifier.size(240.dp).align(Alignment.TopStart),
            color = if (message.outgoing) LoveBlush else LoveMist,
            shape = CircleShape,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EncryptedPhotoImage(
                    mediaId = mediaId,
                    photoBitmaps = photoBitmaps,
                    contentDescription = if (message.outgoing) {
                        "Gesendetes rundes Video"
                    } else {
                        "Empfangenes rundes Video"
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PlayIcon(modifier = Modifier.size(24.dp), color = Color.White)
                    }
                }
            }
        }
        MessageMediaMetadata(message, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun MessageText(message: ConversationEventEntity) {
    val metadata = messageMetadata(message)
    val inlineId = "message-metadata"
    val metadataWidthSp = (if (metadata.visual == null) 38 else 58) +
        (if (message.editedAtEpochMillis > 0L) 38 else 0) +
        (if (message.pinned) 16 else 0)
    Text(
        text = buildAnnotatedString {
            append(message.body.orEmpty())
            append("\u00A0\u00A0")
            appendInlineContent(
                id = inlineId,
                alternateText = listOf(
                    formatTime(message.createdAtEpochMillis),
                    "edited".takeIf { message.editedAtEpochMillis > 0L }.orEmpty(),
                    "pinned".takeIf { message.pinned }.orEmpty(),
                    metadata.description,
                ).filter(String::isNotEmpty).joinToString(" "),
            )
        },
        inlineContent = mapOf(
            inlineId to InlineTextContent(
                Placeholder(
                    width = metadataWidthSp.sp,
                    height = 18.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    MessageMetadataRow(
                        message = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onDarkSurface = false,
                    )
                }
            },
        ),
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .semantics {
                if (metadata.description.isNotEmpty()) stateDescription = metadata.description
            },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun MessageMediaMetadata(
    message: ConversationEventEntity,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.52f),
        shape = RoundedCornerShape(10.dp),
    ) {
        MessageMetadataRow(
            message = message,
            color = Color.White,
            onDarkSurface = true,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CompactMessageMetadata(message: ConversationEventEntity) {
    MessageMetadataRow(
        message = message,
        color = LoveInk.copy(alpha = 0.58f),
        onDarkSurface = false,
    )
}

@Composable
private fun MessageMetadataRow(
    message: ConversationEventEntity,
    color: Color,
    onDarkSurface: Boolean,
    modifier: Modifier = Modifier,
) {
    val metadata = messageMetadata(message)
    Row(
        modifier = modifier.semantics {
            if (metadata.description.isNotEmpty()) stateDescription = metadata.description
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (message.pinned) {
            PinIcon(modifier = Modifier.size(12.dp), color = color)
        }
        if (message.editedAtEpochMillis > 0L) {
            Text(
                "edited",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            formatTime(message.createdAtEpochMillis),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        metadata.visual?.let {
            DeliveryStateIcon(
                visual = it,
                modifier = Modifier.size(width = 17.dp, height = 12.dp),
                onDarkSurface = onDarkSurface,
            )
        }
    }
}

@Composable
private fun DeliveryStateIcon(
    visual: DeliveryVisual,
    modifier: Modifier,
    onDarkSurface: Boolean = false,
) {
    val color = when (visual) {
        DeliveryVisual.READ -> if (onDarkSurface) Color(0xFF8EE292) else Color(0xFF50B356)
        DeliveryVisual.FAILED -> MaterialTheme.colorScheme.error
        else -> if (onDarkSurface) Color.White else LoveInk.copy(alpha = 0.52f)
    }
    when (visual) {
        DeliveryVisual.SENDING -> DeliveryClockIcon(modifier = modifier, color = color)
        DeliveryVisual.SENT -> DeliveryCheckIcon(modifier = modifier, color = color)
        DeliveryVisual.DELIVERED,
        DeliveryVisual.READ,
        -> DeliveryCheckCheckIcon(modifier = modifier, color = color)
        DeliveryVisual.FAILED -> DeliveryErrorIcon(modifier = modifier, color = color)
    }
}

private enum class DeliveryVisual { SENDING, SENT, DELIVERED, READ, FAILED }

private data class MessageMetadata(
    val visual: DeliveryVisual?,
    val description: String,
)

private fun messageMetadata(message: ConversationEventEntity): MessageMetadata =
    if (!message.outgoing) {
        MessageMetadata(null, "")
    } else {
        when (message.deliveryState) {
            LoveDovesRepository.DELIVERY_SENDING -> {
                MessageMetadata(DeliveryVisual.SENDING, "Wird gesendet")
            }
            LoveDovesRepository.DELIVERY_SENT -> {
                MessageMetadata(DeliveryVisual.SENT, "Gesendet")
            }
            LoveDovesRepository.DELIVERY_DELIVERED -> {
                MessageMetadata(DeliveryVisual.DELIVERED, "Zugestellt")
            }
            LoveDovesRepository.DELIVERY_READ -> {
                MessageMetadata(DeliveryVisual.READ, "Gelesen")
            }
            else -> MessageMetadata(DeliveryVisual.FAILED, "Nicht gesendet")
        }
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    pair: PairStateEntity,
    busy: Boolean,
    chatBackground: ChatBackgroundOption,
    onChatBackground: (ChatBackgroundOption) -> Unit,
    onBack: () -> Unit,
    onRecovery: (PairingMode) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var recoveryStep by remember { mutableStateOf<RecoveryStep?>(null) }
    val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recoverySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(enabled = !confirmDelete && recoveryStep == null, onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(LovePaper),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(60.dp)) {
                    ChevronLeftIcon("Zurück zur Unterhaltung")
                }
                Text(
                    "Einstellungen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = LoveInk.copy(alpha = 0.12f))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item {
                SettingsSectionHeader("Verbindung")
            }
            item {
                SettingsInfoItem(
                    title = pair.partnerName,
                    detail = "Verbunden",
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            }
            item {
                SettingsInfoItem(
                    title = "Sicherheitswörter",
                    detail = pair.safetyWords,
                )
            }
            item {
                SettingsSectionHeader("Chat")
            }
            item {
                ChatBackgroundSetting(
                    selected = chatBackground,
                    onSelected = onChatBackground,
                )
            }
            item {
                SettingsSectionHeader("Gerät")
            }
            item {
                SettingsNavigationItem(
                    label = "Partnergerät ersetzen",
                    enabled = !busy,
                    onClick = { recoveryStep = RecoveryStep.CONFIRM },
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item {
                SettingsDeleteItem(
                    label = "Verbindung löschen",
                    enabled = !busy,
                    onClick = { confirmDelete = true },
                )
            }
        }
    }
    if (confirmDelete) {
        LoveModalBottomSheet(
            onDismissRequest = { confirmDelete = false },
            sheetState = deleteSheetState,
        ) {
            SettingsSheetContent(
                title = "Wirklich alles auf diesem Handy löschen?",
                description = "Das andere Handy behält bereits empfangene Inhalte. Diese Aktion kann nicht rückgängig gemacht werden.",
            ) {
                LovePrimaryButton(
                    "Verbindung und Daten löschen",
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    destructive = true,
                )
                LoveSecondaryButton("Abbrechen", onClick = { confirmDelete = false })
            }
        }
    }
    recoveryStep?.let { currentStep ->
        LoveModalBottomSheet(
            onDismissRequest = { recoveryStep = null },
            sheetState = recoverySheetState,
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideInHorizontally(tween(220)) { width -> direction * width } +
                        fadeIn(tween(220)) togetherWith
                        slideOutHorizontally(tween(220)) { width -> -direction * width } +
                        fadeOut(tween(220))
                },
                label = "recovery-step",
            ) { step ->
                when (step) {
                    RecoveryStep.CONFIRM -> SettingsSheetContent(
                        title = "Verlorenes Partnergerät ersetzen?",
                        description = "Das bisherige Partnergerät verliert den Zugang. Danach verbindet ihr das neue Handy und vergleicht neue Sicherheitswörter.",
                    ) {
                        LovePrimaryButton(
                            "Weiter",
                            onClick = { recoveryStep = RecoveryStep.METHOD },
                        )
                        LoveSecondaryButton("Abbrechen", onClick = { recoveryStep = null })
                    }

                    RecoveryStep.METHOD -> SettingsSheetContent(
                        title = "Wo seid ihr gerade?",
                        description = "Das bestimmt nur, wie ihr das neue Handy verbindet.",
                    ) {
                        LovePrimaryButton(
                            "Am selben Ort",
                            onClick = {
                                recoveryStep = null
                                onRecovery(PairingMode.IN_PERSON)
                            },
                        )
                        LoveSecondaryButton(
                            "An verschiedenen Orten",
                            onClick = {
                                recoveryStep = null
                                onRecovery(PairingMode.REMOTE)
                            },
                        )
                        LoveSecondaryButton(
                            "Zurück",
                            onClick = { recoveryStep = RecoveryStep.CONFIRM },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBackgroundSetting(
    selected: ChatBackgroundOption,
    onSelected: (ChatBackgroundOption) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
        SettingsRowTitle("Hintergrund")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChatBackgroundOption.entries.forEach { option ->
                Surface(
                    onClick = { onSelected(option) },
                    modifier = Modifier.size(42.dp).semantics {
                        contentDescription = "Chat-Hintergrund ${option.label}"
                        if (option == selected) stateDescription = "Ausgewählt"
                    },
                    shape = CircleShape,
                    color = option.color,
                    border = BorderStroke(
                        if (option == selected) 2.dp else 1.dp,
                        if (option == selected) LoveInk else LoveInk.copy(alpha = 0.2f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (option == selected) {
                            SelectionCheckIcon(
                                description = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(label: String) {
    Row(
        Modifier.fillMaxWidth().background(LoveMist).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = LoveInk,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsInfoItem(
    title: String,
    detail: String,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
        SettingsRowTitle(title)
        Text(
            detail,
            modifier = Modifier.padding(top = 2.dp),
            color = LoveInk.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SettingsRowTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LoveInk,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SettingsNavigationItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = Color.Transparent,
        contentColor = LoveInk,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsRowTitle(
                text = label,
                modifier = Modifier.weight(1f),
            )
            ChevronRightIcon(null)
        }
    }
}

@Composable
private fun SettingsDeleteItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.error
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = Color.Transparent,
        contentColor = color,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeleteIcon(modifier = Modifier.size(24.dp), color = color)
            SettingsRowTitle(
                text = label,
                modifier = Modifier.weight(1f),
                color = color,
            )
        }
    }
}

@Composable
private fun SettingsSheetContent(
    title: String,
    description: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        actions()
    }
}

private enum class RecoveryStep { CONFIRM, METHOD }

@Composable
internal fun EncryptedPhotoImage(
    mediaId: String,
    photoBitmaps: PhotoBitmapLoader,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    zoomable: Boolean = false,
    backgroundColor: Color = LoveMist,
) {
    val bitmap by produceState<Bitmap?>(null, mediaId, photoBitmaps, zoomable) {
        value = if (zoomable) {
            photoBitmaps.fullSize(mediaId)
        } else {
            photoBitmaps.thumbnail(mediaId)
        }
    }
    val rendered = bitmap
    DisposableEffect(rendered, zoomable) {
        onDispose { if (zoomable) rendered?.recycle() }
    }
    Box(modifier.background(backgroundColor), contentAlignment = Alignment.Center) {
        if (rendered == null) {
            MessageLoadingPlaceholder(Modifier.fillMaxSize())
        } else if (zoomable) {
            ZoomablePhoto(
                bitmap = rendered,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Image(
                rendered.asImageBitmap(),
                contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

@Composable
internal fun MessageLoadingPlaceholder(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "message loading pulse").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "message loading alpha",
    )
    Box(
        modifier = modifier
            .background(LoveInk.copy(alpha = alpha))
            .semantics { stateDescription = "Wird geladen" },
    )
}

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(epochMillis: Long): String = TimeFormatter.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)
