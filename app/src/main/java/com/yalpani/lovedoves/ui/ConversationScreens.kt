package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yalpani.lovedoves.LoveBlush
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.LoveMist
import com.yalpani.lovedoves.LovePaper
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.data.PairStateEntity
import com.yalpani.lovedoves.domain.LoveDovesController
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PairingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ConversationScreen(
    pair: PairStateEntity,
    messages: List<ConversationEventEntity>,
    controller: LoveDovesController,
    busy: Boolean,
    onSend: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSettings: () -> Unit,
    onRetry: (String) -> Unit,
    onPhoto: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
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
                    MessageBubble(message, controller, onRetry, onPhoto)
                }
            }
        }
        MessageComposer(
            text = text,
            busy = busy,
            onTextChange = { if (it.length <= LoveDovesRepository.MAX_TEXT_LENGTH) text = it },
            onCamera = onCamera,
            onGallery = onGallery,
            onSend = {
                val message = text
                text = ""
                onSend(message)
            },
        )
    }
}

@Composable
private fun MessageComposer(
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
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
                    onClick = onGallery,
                    enabled = !busy,
                    modifier = Modifier.size(44.dp),
                ) {
                    ImageIcon("Foto auswählen", modifier = Modifier.size(20.dp))
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
                    onClick = onCamera,
                    enabled = !busy,
                    modifier = Modifier.size(44.dp),
                ) {
                    CameraIcon("Foto aufnehmen", modifier = Modifier.size(20.dp))
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
    controller: LoveDovesController,
    onRetry: (String) -> Unit,
    onPhoto: (String) -> Unit,
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
                            controller = controller,
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
internal fun SettingsScreen(
    pair: PairStateEntity,
    busy: Boolean,
    onBack: () -> Unit,
    onRecovery: (PairingMode) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var recoveryStep by remember { mutableStateOf<RecoveryStep?>(null) }
    BackHandler(onBack = onBack)
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
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmDelete = false }) {
            Surface(color = Color.White, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Wirklich alles auf diesem Handy löschen?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Das andere Handy behält bereits empfangene Inhalte. Diese Aktion kann nicht rückgängig gemacht werden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LovePrimaryButton(
                        "Verbindung und Daten löschen",
                        onClick = onDelete,
                        destructive = true,
                    )
                    LoveSecondaryButton("Abbrechen", onClick = { confirmDelete = false })
                }
            }
        }
    }
    if (recoveryStep == RecoveryStep.CONFIRM) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { recoveryStep = null }) {
            Surface(color = Color.White, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Verlorenes Partnergerät ersetzen?",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Das bisherige Partnergerät verliert den Zugang. Danach verbindet ihr das neue Handy und vergleicht neue Sicherheitswörter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LovePrimaryButton(
                        "Weiter",
                        onClick = { recoveryStep = RecoveryStep.METHOD },
                    )
                    LoveSecondaryButton("Abbrechen", onClick = { recoveryStep = null })
                }
            }
        }
    }
    if (recoveryStep == RecoveryStep.METHOD) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { recoveryStep = null }) {
            Surface(color = Color.White, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Wo seid ihr gerade?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Das bestimmt nur, wie ihr das neue Handy verbindet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    LoveSecondaryButton("Zurück", onClick = { recoveryStep = RecoveryStep.CONFIRM })
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
            style = MaterialTheme.typography.titleMedium,
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
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp),
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
            DeleteIcon(modifier = Modifier.size(32.dp), color = color)
            SettingsRowTitle(
                text = label,
                modifier = Modifier.weight(1f),
                color = color,
            )
        }
    }
}

private enum class RecoveryStep { CONFIRM, METHOD }

@Composable
internal fun PhotoDetailScreen(
    mediaId: String,
    controller: LoveDovesController,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EncryptedPhotoImage(
            mediaId,
            controller,
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
    controller: LoveDovesController,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    zoomable: Boolean = false,
    backgroundColor: Color = LoveMist,
) {
    val bitmap by produceState<Bitmap?>(null, mediaId, controller) {
        val bytes = controller.photoBytes(mediaId)
        value = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            bytes.fill(0)
        }
    }
    val rendered = bitmap
    DisposableEffect(rendered) {
        onDispose { rendered?.recycle() }
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
