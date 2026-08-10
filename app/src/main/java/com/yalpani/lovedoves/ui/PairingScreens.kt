package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.yalpani.lovedoves.BuildConfig
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.LoveMist
import com.yalpani.lovedoves.LovePaper
import com.yalpani.lovedoves.domain.PairingMode
import com.yalpani.lovedoves.domain.PairingSnapshot

@Composable
internal fun ProfileSetupScreen(busy: Boolean, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        HeartIcon(modifier = Modifier.size(52.dp))
        Text(
            "Wie darf dein Lieblingsmensch dich hier nennen?",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Der Name bleibt in eurem verschlüsselten Raum.",
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 40) name = it },
            label = { Text("Dein Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LovePrimaryButton(
            "Weiter",
            onClick = { onSave(name) },
            enabled = name.isNotBlank() && !busy,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
internal fun PairingHomeScreen(
    busy: Boolean,
    onCreate: (PairingMode, String) -> Unit,
    onScan: () -> Unit,
    onPaste: (String) -> Unit,
) {
    var bootstrap by remember { mutableStateOf(BuildConfig.RELAY_BOOTSTRAP_TOKEN) }
    var invitation by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { HeartIcon(modifier = Modifier.size(34.dp)) }
        }
        Text(
            "Euer Raum beginnt zu zweit.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Kein Konto, keine Kontaktliste. Eine Einladung verbindet genau diese beiden Geräte.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (BuildConfig.RELAY_BOOTSTRAP_TOKEN.isBlank()) {
            OutlinedTextField(
                value = bootstrap,
                onValueChange = { bootstrap = it.trim() },
                label = { Text("Einmaliges Server-Starttoken") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        LovePrimaryButton(
            "QR-Code vor Ort",
            onClick = { onCreate(PairingMode.IN_PERSON, bootstrap) },
            enabled = bootstrap.isNotBlank() && !busy,
            icon = { QrIcon() },
        )
        LoveSecondaryButton(
            "Link aus der Ferne",
            onClick = { onCreate(PairingMode.REMOTE, bootstrap) },
            enabled = bootstrap.isNotBlank() && !busy,
            icon = { SendIcon() },
        )
        Text(
            "Oder du wurdest eingeladen",
            modifier = Modifier.padding(top = 22.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LoveSecondaryButton("Einladungs-QR scannen", onClick = onScan, icon = { QrIcon() })
        OutlinedTextField(
            value = invitation,
            onValueChange = { invitation = it },
            label = { Text("Einladungslink einfügen") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        LoveSecondaryButton(
            "Einladung öffnen",
            onClick = { onPaste(invitation.trim()) },
            enabled = invitation.isNotBlank() && !busy,
        )
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun PairingPendingScreen(
    snapshot: PairingSnapshot,
    busy: Boolean,
    onScanResponse: () -> Unit,
    onFetchResponse: () -> Unit,
    onShare: (String) -> Unit,
    onConfirm: () -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    val isInviter = snapshot.role == "INVITER"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                snapshot.safetyWords != null -> "Vergleicht eure Wörter"
                snapshot.recovery -> "Stellt euren Raum wieder her"
                else -> "Verbindet eure Geräte"
            },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                snapshot.safetyWords != null ->
                    if (snapshot.recovery) {
                        "Sprecht die sechs Wörter über einen unabhängigen Kanal ab. Sind sie gleich, bestätigt ihr biometrisch; danach wird der Verlauf übertragen."
                    } else {
                        "Sprecht die sechs Wörter miteinander ab. Nur wenn sie auf beiden Handys gleich sind, bestätigt ihr die Verbindung."
                    }
                isInviter && snapshot.mode == PairingMode.IN_PERSON ->
                    "Dein Lieblingsmensch scannt diesen Code und zeigt dir danach einen Antwort-Code."
                isInviter -> "Teile den Link direkt mit deinem Lieblingsmenschen."
                snapshot.mode == PairingMode.IN_PERSON ->
                    "Zeige diesen Antwort-Code jetzt dem ersten Handy."
                else -> "Deine verschlüsselte Antwort wurde übermittelt."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        if (snapshot.safetyWords == null) {
            when {
                isInviter && snapshot.mode == PairingMode.IN_PERSON -> {
                    QrCard(snapshot.invitation)
                    LovePrimaryButton(
                        "Antwort-QR scannen",
                        onClick = onScanResponse,
                        enabled = !busy,
                        icon = { QrIcon() },
                    )
                }
                isInviter && snapshot.mode == PairingMode.REMOTE -> {
                    QrCard(snapshot.remoteLink)
                    LovePrimaryButton(
                        "Einladungslink teilen",
                        onClick = { onShare(snapshot.remoteLink) },
                        enabled = !busy,
                        icon = { SendIcon() },
                    )
                    LoveSecondaryButton(
                        "Antwort abrufen",
                        onClick = onFetchResponse,
                        enabled = !busy,
                        icon = { RefreshIcon() },
                    )
                }
                !isInviter && snapshot.mode == PairingMode.IN_PERSON -> {
                    QrCard(requireNotNull(snapshot.response))
                }
            }
        } else {
            if (!isInviter && snapshot.mode == PairingMode.IN_PERSON) {
                Text(
                    "Lass zuerst diesen Antwort-Code vom anderen Handy scannen.",
                    fontWeight = FontWeight.Medium,
                )
                QrCard(requireNotNull(snapshot.response))
            }
            SafetyWordCard(snapshot.safetyWords)
            if (!snapshot.localConfirmed) {
                LovePrimaryButton(
                    "Wörter stimmen",
                    onClick = onConfirm,
                    enabled = !busy,
                    icon = { LockIcon() },
                )
            } else {
                Surface(color = LoveMist, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Auf diesem Handy bestätigt", fontWeight = FontWeight.Medium)
                        HeartIcon(modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    if (snapshot.remoteConfirmed) {
                        "Beide Bestätigungen sind da. Der gemeinsame Raum wird geöffnet."
                    } else {
                        "Warte noch auf die Bestätigung des anderen Handys."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LoveSecondaryButton(
                    "Jetzt synchronisieren",
                    onClick = onSync,
                    enabled = !busy,
                    icon = { RefreshIcon() },
                )
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        LoveSecondaryButton("Verbindung abbrechen", onClick = onCancel, enabled = !busy)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SafetyWordCard(words: String) {
    val rows = words.split(' ').chunked(2)
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { word ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = LovePaper,
                            shape = CircleShape,
                        ) {
                            Text(
                                word,
                                modifier = Modifier.padding(14.dp, 12.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCard(payload: String) {
    val bitmap = remember(payload) { qrBitmap(payload) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 3.dp,
    ) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "Love-Doves-QR-Code",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}

private fun qrBitmap(payload: String): Bitmap {
    val matrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        QR_SIZE,
        QR_SIZE,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        ),
    )
    val pixels = IntArray(QR_SIZE * QR_SIZE)
    for (y in 0 until QR_SIZE) {
        for (x in 0 until QR_SIZE) {
            pixels[y * QR_SIZE + x] = if (matrix[x, y]) 0xff2b2124.toInt() else 0xffffffff.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
}

private const val QR_SIZE = 768
