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
import androidx.compose.foundation.layout.Spacer
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
    onSync: () -> Unit,
    onRecovery: (PairingMode) -> Unit,
    onResendHistory: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { ChevronLeftIcon("Zurück") }
            Text(
                "Sicherheit",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LockIcon()
                Text("Eure Sicherheitswörter", fontWeight = FontWeight.SemiBold)
                Text(pair.safetyWords, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Der Relay kennt weder diese Wörter noch eure Schlüssel oder Inhalte.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Nachrichten und Fotos liegen lokal verschlüsselt. Love Doves sperrt sich sofort, wenn du die App verlässt.",
            modifier = Modifier.padding(vertical = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        LoveSecondaryButton(
            "Jetzt synchronisieren",
            onClick = onSync,
            enabled = !busy,
            icon = { RefreshIcon() },
        )
        LoveSecondaryButton(
            "Neues Partnergerät wiederherstellen",
            onClick = { confirmRecovery = true },
            enabled = !busy,
            icon = { RefreshIcon() },
            modifier = Modifier.padding(top = 12.dp),
        )
        LoveSecondaryButton(
            "Verlauf erneut übertragen",
            onClick = onResendHistory,
            enabled = !busy,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        LovePrimaryButton(
            "Verbindung und lokale Daten löschen",
            onClick = { confirmDelete = true },
            enabled = !busy,
            destructive = true,
            icon = { TrashIcon() },
        )
        Spacer(Modifier.height(20.dp))
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
    if (confirmRecovery) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmRecovery = false }) {
            Surface(color = LovePaper, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Verlorenes Partnergerät ersetzen?",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Der bisherige Relay-Briefkasten wird sofort widerrufen. Nach dem Vergleich der neuen Sicherheitswörter überträgt dieses Handy euren Verlauf neu verschlüsselt.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LovePrimaryButton(
                        "QR-Code vor Ort",
                        onClick = {
                            confirmRecovery = false
                            onRecovery(PairingMode.IN_PERSON)
                        },
                    )
                    LoveSecondaryButton(
                        "Link aus der Ferne",
                        onClick = {
                            confirmRecovery = false
                            onRecovery(PairingMode.REMOTE)
                        },
                    )
                    LoveSecondaryButton("Abbrechen", onClick = { confirmRecovery = false })
                }
            }
        }
    }
}

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
