package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    var naming by rememberSaveable { mutableStateOf(false) }
    BackHandler(naming) { naming = false }
    if (!naming) {
        WelcomeScreen { naming = true }
        return
    }

    var name by remember { mutableStateOf("") }
    PairingStepLayout(
        title = "Wie darf dein Lieblingsmensch dich nennen?",
        description = "Der Name bleibt in eurem verschlüsselten Raum.",
        onBack = { naming = false },
    ) {
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
        )
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { HeartIcon(modifier = Modifier.size(42.dp)) }
        }
        Text(
            "Ein privater Raum nur für euch zwei.",
            modifier = Modifier.padding(top = 30.dp),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Keine Konten, keine Kontaktliste. Eure Nachrichten und Fotos bleiben verschlüsselt zwischen euren beiden Geräten.",
            modifier = Modifier.padding(top = 14.dp, bottom = 32.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        LovePrimaryButton("Los geht’s", onClick = onContinue)
    }
}

private enum class PairingEntryStep { ROLE, INVITE_CODE, INVITE_METHOD, JOIN_METHOD, JOIN_LINK }

@Composable
internal fun PairingHomeScreen(
    busy: Boolean,
    onCreate: (PairingMode, String) -> Unit,
    onScan: () -> Unit,
    onPaste: (String) -> Unit,
) {
    var bootstrap by remember { mutableStateOf(BuildConfig.RELAY_BOOTSTRAP_TOKEN) }
    var invitation by remember { mutableStateOf("") }
    var step by rememberSaveable { mutableStateOf(PairingEntryStep.ROLE) }
    BackHandler(step != PairingEntryStep.ROLE) {
        step = when (step) {
            PairingEntryStep.INVITE_METHOD -> if (BuildConfig.RELAY_BOOTSTRAP_TOKEN.isBlank()) {
                PairingEntryStep.INVITE_CODE
            } else {
                PairingEntryStep.ROLE
            }
            PairingEntryStep.JOIN_LINK -> PairingEntryStep.JOIN_METHOD
            else -> PairingEntryStep.ROLE
        }
    }

    when (step) {
        PairingEntryStep.ROLE -> PairingStepLayout(
            title = "Wie möchtet ihr starten?",
            description = "Wähle nur das aus, was gerade auf dich zutrifft.",
        ) {
            LovePrimaryButton(
                "Ich möchte einladen",
                onClick = {
                    step = if (bootstrap.isBlank()) {
                        PairingEntryStep.INVITE_CODE
                    } else {
                        PairingEntryStep.INVITE_METHOD
                    }
                },
            )
            LoveSecondaryButton(
                "Ich wurde eingeladen",
                onClick = { step = PairingEntryStep.JOIN_METHOD },
            )
        }

        PairingEntryStep.INVITE_CODE -> PairingStepLayout(
            title = "Einladung vorbereiten",
            description = "Füge einmalig den Freischaltcode eures Love-Doves-Servers ein. Du bekommst ihn von der Person, die den privaten Server eingerichtet hat.",
            onBack = { step = PairingEntryStep.ROLE },
        ) {
            OutlinedTextField(
                value = bootstrap,
                onValueChange = { bootstrap = it.trim() },
                label = { Text("Einmaliger Freischaltcode") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LovePrimaryButton(
                "Weiter",
                onClick = { step = PairingEntryStep.INVITE_METHOD },
                enabled = bootstrap.isNotBlank(),
            )
        }

        PairingEntryStep.INVITE_METHOD -> PairingStepLayout(
            title = "Seid ihr gerade zusammen?",
            description = "Vor Ort ist der QR-Code am einfachsten. Sonst teilst du einen sicheren Einladungslink.",
            onBack = {
                step = if (BuildConfig.RELAY_BOOTSTRAP_TOKEN.isBlank()) {
                    PairingEntryStep.INVITE_CODE
                } else {
                    PairingEntryStep.ROLE
                }
            },
        ) {
            LovePrimaryButton(
                "Ja, QR-Code zeigen",
                onClick = { onCreate(PairingMode.IN_PERSON, bootstrap) },
                enabled = !busy,
                icon = { QrIcon() },
            )
            LoveSecondaryButton(
                "Nein, Link teilen",
                onClick = { onCreate(PairingMode.REMOTE, bootstrap) },
                enabled = !busy,
                icon = { SendHorizontalIcon() },
            )
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }

        PairingEntryStep.JOIN_METHOD -> PairingStepLayout(
            title = "Wie hast du die Einladung bekommen?",
            description = "Wähle den QR-Code oder den Link, den dein Lieblingsmensch dir gezeigt oder geschickt hat.",
            onBack = { step = PairingEntryStep.ROLE },
        ) {
            LovePrimaryButton(
                "QR-Code scannen",
                onClick = onScan,
                enabled = !busy,
                icon = { QrIcon() },
            )
            LoveSecondaryButton(
                "Einladungslink einfügen",
                onClick = { step = PairingEntryStep.JOIN_LINK },
                enabled = !busy,
                icon = { SendHorizontalIcon() },
            )
        }

        PairingEntryStep.JOIN_LINK -> PairingStepLayout(
            title = "Einladungslink einfügen",
            description = "Kopiere den vollständigen Love-Doves-Link aus der Nachricht hier hinein.",
            onBack = { step = PairingEntryStep.JOIN_METHOD },
        ) {
            OutlinedTextField(
                value = invitation,
                onValueChange = { invitation = it },
                label = { Text("Einladungslink") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            LovePrimaryButton(
                "Einladung öffnen",
                onClick = { onPaste(invitation.trim()) },
                enabled = invitation.isNotBlank() && !busy,
            )
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PairingStepLayout(
    title: String,
    description: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                ChevronLeftIcon("Zurück")
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    HeartIcon(modifier = Modifier.size(34.dp))
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            description,
            modifier = Modifier.padding(bottom = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        content()
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
                        icon = { SendHorizontalIcon() },
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
