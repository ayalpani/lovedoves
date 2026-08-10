package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yalpani.lovedoves.LoveBlush
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.LoveMist
import com.yalpani.lovedoves.LovePaper
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.data.PairStateEntity
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PairingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ConversationScreen(
    pair: PairStateEntity,
    messages: List<ConversationEventEntity>,
    photoBitmaps: PhotoBitmapLoader,
    busy: Boolean,
    onSend: (String) -> Unit,
    onAttachment: () -> Unit,
    onSettings: () -> Unit,
    onRetry: (String) -> Unit,
    onPhoto: (String) -> Unit,
    onVideo: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = LoveBlush,
                shape = CircleShape,
                modifier = Modifier.size(46.dp),
            ) { Box(contentAlignment = Alignment.Center) { HeartIcon(modifier = Modifier.size(22.dp)) } }
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
            IconButton(onClick = onSettings) { SettingsIcon("Einstellungen") }
        }
        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(36.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = LoveBlush, shape = CircleShape, modifier = Modifier.size(92.dp)) {
                    Box(contentAlignment = Alignment.Center) { HeartIcon(modifier = Modifier.size(42.dp)) }
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message, photoBitmaps, onRetry, onPhoto, onVideo)
                }
            }
        }
        MessageComposer(
            text = text,
            busy = busy,
            onTextChange = { if (it.length <= LoveDovesRepository.MAX_TEXT_LENGTH) text = it },
            onEmoji = {
                keyboard?.hide()
                focusManager.clearFocus()
                showEmojiPicker = true
            },
            onAttachment = onAttachment,
            onSend = {
                val message = text
                text = ""
                onSend(message)
            },
        )
    }
    if (showEmojiPicker) {
        EmojiPickerBottomSheet(
            sheetState = emojiSheetState,
            onDismiss = { showEmojiPicker = false },
            onEmojiPicked = { emoji ->
                if (text.length + emoji.length <= LoveDovesRepository.MAX_TEXT_LENGTH) {
                    text += emoji
                }
            },
        )
    }
}

@Composable
private fun MessageComposer(
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onEmoji: () -> Unit,
    onAttachment: () -> Unit,
    onSend: () -> Unit,
) {
    val canSend = text.isNotBlank() && !busy
    Row(
        Modifier
            .fillMaxWidth()
            .background(LovePaper)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.16f)),
        ) {
            Row(
                Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onEmoji,
                    enabled = !busy,
                    modifier = Modifier.size(44.dp),
                ) {
                    SmileIcon("Emoji wählen", modifier = Modifier.size(22.dp))
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = LoveInk),
                    cursorBrush = SolidColor(LoveInk),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
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
                    enabled = !busy,
                    modifier = Modifier.size(44.dp),
                ) {
                    PaperclipIcon("Medien anhängen", modifier = Modifier.size(22.dp))
                }
            }
        }
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .background(if (canSend) LoveInk else LoveMist, CircleShape),
        ) {
            SendIcon(
                "Nachricht senden",
                modifier = Modifier.size(20.dp),
                color = if (canSend) Color.White else LoveInk.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ConversationEventEntity,
    photoBitmaps: PhotoBitmapLoader,
    onRetry: (String) -> Unit,
    onPhoto: (String) -> Unit,
    onVideo: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.82f),
            contentAlignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart,
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
                    LoveDovesRepository.KIND_PHOTO -> {
                        val mediaId = requireNotNull(message.mediaId)
                        EncryptedPhotoImage(
                            mediaId = mediaId,
                            photoBitmaps = photoBitmaps,
                            contentDescription = if (message.outgoing) {
                                "Gesendetes Foto"
                            } else {
                                "Empfangenes Foto"
                            },
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                                .clickable { onPhoto(mediaId) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                    LoveDovesRepository.KIND_VIDEO -> {
                        val mediaId = requireNotNull(message.mediaId)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clickable { onVideo(mediaId) },
                            contentAlignment = Alignment.Center,
                        ) {
                            EncryptedPhotoImage(
                                mediaId = mediaId,
                                photoBitmaps = photoBitmaps,
                                contentDescription = if (message.outgoing) {
                                    "Gesendetes Video"
                                } else {
                                    "Empfangenes Video"
                                },
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
                        }
                    }
                    else -> Text(
                        message.body.orEmpty(),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatTime(message.createdAtEpochMillis),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            if (message.outgoing) {
                Text(
                    when (message.deliveryState) {
                        LoveDovesRepository.DELIVERY_SENDING -> "Wird gesendet …"
                        LoveDovesRepository.DELIVERY_SENT -> "Gesendet"
                        LoveDovesRepository.DELIVERY_DELIVERED -> "Zugestellt"
                        else -> "Nicht gesendet"
                    },
                    color = if (message.deliveryState == LoveDovesRepository.DELIVERY_FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                if (message.deliveryState == LoveDovesRepository.DELIVERY_FAILED) {
                    Text(
                        "Erneut senden",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { onRetry(message.id) }.padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    pair: PairStateEntity,
    busy: Boolean,
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
internal fun PhotoDetailScreen(
    mediaId: String,
    photoBitmaps: PhotoBitmapLoader,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EncryptedPhotoImage(
            mediaId,
            photoBitmaps,
            "Foto in voller Größe",
            Modifier.fillMaxSize(),
            ContentScale.Fit,
            zoomable = true,
            backgroundColor = Color.Black,
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) { CloseIcon("Foto schließen") }
    }
}

@Composable
private fun EncryptedPhotoImage(
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
            CircularProgressIndicator()
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

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(epochMillis: Long): String = TimeFormatter.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)
