package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
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
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onCamera, enabled = !busy) { CameraIcon("Foto aufnehmen") }
            IconButton(onClick = onGallery, enabled = !busy) { ImageIcon("Foto auswählen") }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= LoveDovesRepository.MAX_TEXT_LENGTH) text = it },
                placeholder = { Text("Etwas nur für euch …") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
            )
            IconButton(
                onClick = {
                    val message = text
                    text = ""
                    onSend(message)
                },
                enabled = text.isNotBlank() && !busy,
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            ) { SendIcon("Nachricht senden", color = Color.White) }
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
        Surface(
            color = if (message.outgoing) LoveBlush else LoveMist,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = if (message.outgoing) 22.dp else 6.dp,
                bottomEnd = if (message.outgoing) 6.dp else 22.dp,
            ),
            modifier = Modifier.fillMaxWidth(0.82f),
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
        Modifier.fillMaxSize().background(LovePaper).statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { ChevronLeftIcon("Zurück zur Unterhaltung") }
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Eure Verbindung",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = LoveBlush,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) { LockIcon() }
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    "Sicher mit ${pair.partnerName} verbunden",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Neue Nachrichten kommen automatisch an.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Surface(color = LoveMist, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    "Sicherheitswörter",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    pair.safetyWords,
                                    modifier = Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Text(
                            "Alles bleibt verschlüsselt auf euren Geräten.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Text(
                    "Geräte",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                SettingsAction(
                    title = "Partnergerät ersetzen",
                    detail = "Nur wenn das andere Handy verloren oder kaputt ist.",
                    enabled = !busy,
                    onClick = { recoveryStep = RecoveryStep.CONFIRM },
                )
            }
            item {
                SettingsAction(
                    title = "Verbindung und Daten löschen",
                    detail = "Löscht Love Doves auf diesem Handy.",
                    enabled = !busy,
                    destructive = true,
                    onClick = { confirmDelete = true },
                )
            }
        }
    }
    if (confirmDelete) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmDelete = false }) {
            Surface(color = LovePaper, shape = RoundedCornerShape(28.dp)) {
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
            Surface(color = LovePaper, shape = RoundedCornerShape(28.dp)) {
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
            Surface(color = LovePaper, shape = RoundedCornerShape(28.dp)) {
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
private fun SettingsAction(
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (destructive) MaterialTheme.colorScheme.error else LoveInk,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    detail,
                    modifier = Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ChevronRightIcon(null)
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
    Box(modifier.background(LoveMist), contentAlignment = Alignment.Center) {
        if (rendered == null) {
            CircularProgressIndicator()
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
