package com.yalpani.lovedoves

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yalpani.lovedoves.security.VaultKeyStore
import com.yalpani.lovedoves.ui.HeartIcon
import com.yalpani.lovedoves.ui.LockIcon
import com.yalpani.lovedoves.ui.LoveDovesApp
import com.yalpani.lovedoves.ui.LovePrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val pendingLink = MutableStateFlow<String?>(null)
    private val pendingPickedMedia = MutableStateFlow<Uri?>(null)
    private val authenticationError = MutableStateFlow<String?>(null)
    private var promptShowing = false
    private var systemPermissionPromptShowing = false
    private var promptedThisForeground = false
    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        systemPermissionPromptShowing = false
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pendingPickedMedia.value = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        pendingLink.value = intent?.dataString
        val application = application as LoveDovesApplication
        setContent {
            LoveDovesTheme {
                val vaultState by application.vaults.state.collectAsStateWithLifecycle()
                val authError by authenticationError.collectAsStateWithLifecycle()
                when (val state = vaultState) {
                    is VaultState.Locked -> LockedScreen(
                        hasVault = state.hasVault,
                        error = authError,
                        onUnlock = ::authenticateVault,
                    )
                    VaultState.Opening -> LoadingScreen()
                    is VaultState.Failed -> LockedScreen(
                        hasVault = true,
                        error = state.message,
                        onUnlock = ::authenticateVault,
                    )
                    is VaultState.Open -> {
                        val link by pendingLink.collectAsStateWithLifecycle()
                        val pickedMedia by pendingPickedMedia.collectAsStateWithLifecycle()
                        LoveDovesApp(
                            session = state.session,
                            incomingLink = link,
                            pickedMedia = pickedMedia,
                            onLinkConsumed = { pendingLink.value = null },
                            onPickMedia = {
                                systemPermissionPromptShowing = true
                                mediaPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                    ),
                                )
                            },
                            onMediaConsumed = ::consumePickedMedia,
                            onSystemPermissionPrompt = { systemPermissionPromptShowing = it },
                            onAuthenticate = ::authenticateAction,
                            onDeleteAll = { application.vaults.deleteAll() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!promptedThisForeground) {
            promptedThisForeground = true
            window.decorView.post {
                val state = (application as LoveDovesApplication).vaults.state.value
                if (state is VaultState.Locked) authenticateVault()
            }
        }
    }

    override fun onPause() {
        if (!promptShowing && !systemPermissionPromptShowing) {
            promptedThisForeground = false
            (application as LoveDovesApplication).vaults.lock()
        }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink.value = intent.dataString
    }

    private fun consumePickedMedia(uri: Uri) {
        if (pendingPickedMedia.value == uri) pendingPickedMedia.value = null
        runCatching {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun authenticateVault() {
        if (promptShowing) return
        val application = application as LoveDovesApplication
        val availability = BiometricManager.from(this).canAuthenticate(
            VaultKeyStore.ALLOWED_AUTHENTICATORS,
        )
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            authenticationError.value =
                "Bitte richte zuerst Fingerabdruck oder eine sichere Geräte-PIN in Android ein."
            return
        }
        val prepared = runCatching { application.vaults.keyStore.prepareUnlock() }
            .onFailure {
                authenticationError.value =
                    "Der Geräteschlüssel ist nicht mehr gültig. Die lokalen Daten können nicht entschlüsselt werden."
            }
            .getOrNull() ?: return
        promptShowing = true
        authenticationError.value = null
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    val vaultKey = runCatching { prepared.complete(result) }
                        .onFailure { authenticationError.value = "Entsperren fehlgeschlagen." }
                        .getOrNull() ?: return
                    lifecycleScope.launch { application.vaults.unlock(vaultKey) }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    prepared.cancel()
                    if (
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) authenticationError.value = errString.toString()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (application.vaults.keyStore.hasVault) "Love Doves entsperren" else "Privaten Raum anlegen")
                .setSubtitle("Fingerabdruck oder Geräte-PIN")
                .setAllowedAuthenticators(VaultKeyStore.ALLOWED_AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build(),
            prepared.cryptoObject,
        )
    }

    private fun authenticateAction(onSuccess: () -> Unit) {
        if (promptShowing) return
        promptShowing = true
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Aktion bestätigen")
                .setSubtitle("Nur du kannst diese Entscheidung bestätigen")
                .setAllowedAuthenticators(VaultKeyStore.ALLOWED_AUTHENTICATORS)
                .build(),
        )
    }
}

@androidx.compose.runtime.Composable
private fun LockedScreen(hasVault: Boolean, error: String?, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    if (hasVault) LockIcon() else HeartIcon()
                }
            }
            Text(
                if (hasVault) "Euer Raum ist geschützt" else "Ein Raum nur für euch zwei",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                error ?: if (hasVault) {
                    "Entsperre Love Doves mit deinem Fingerabdruck oder der Geräte-PIN."
                } else {
                    "Lege den verschlüsselten Speicher mit deinem Android-Geräteschutz an."
                },
                modifier = Modifier.padding(vertical = 18.dp),
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            LovePrimaryButton(if (hasVault) "Entsperren" else "Sicher starten", onClick = onUnlock)
        }
    }
}

@androidx.compose.runtime.Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
