package com.yalpani.lovedoves.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class VaultKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    val hasVault: Boolean
        get() = preferences.contains(WRAPPED_VAULT_KEY)

    fun prepareUnlock(): PreparedVaultUnlock {
        val wrappingKey = getOrCreateWrappingKey()
        return if (hasVault) {
            val nonce = preferences.getString(VAULT_KEY_NONCE, null)?.fromBase64()
                ?: error("Vault nonce is missing")
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            }
            PreparedVaultUnlock(cipher = cipher, newVaultKey = null, keyStore = this)
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, wrappingKey)
            }
            PreparedVaultUnlock(
                cipher = cipher,
                newVaultKey = ByteArray(VAULT_KEY_BYTES).also(SecureRandom()::nextBytes),
                keyStore = this,
            )
        }
    }

    fun deleteVaultKey() {
        preferences.edit().clear().commit()
        keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val authenticators = KeyProperties.AUTH_BIOMETRIC_STRONG or
            KeyProperties.AUTH_DEVICE_CREDENTIAL
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, authenticators)
            .setUnlockedDeviceRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun persistWrappedKey(cipher: Cipher, vaultKey: ByteArray) {
        val wrapped = cipher.doFinal(vaultKey)
        preferences.edit()
            .putString(VAULT_KEY_NONCE, cipher.iv.toBase64())
            .putString(WRAPPED_VAULT_KEY, wrapped.toBase64())
            .commit()
        wrapped.fill(0)
    }

    internal fun complete(prepared: PreparedVaultUnlock, authenticatedCipher: Cipher): VaultKey {
        val key = prepared.newVaultKey?.also { persistWrappedKey(authenticatedCipher, it) }
            ?: authenticatedCipher.doFinal(
                preferences.getString(WRAPPED_VAULT_KEY, null)?.fromBase64()
                    ?: error("Wrapped vault key is missing"),
            )
        require(key.size == VAULT_KEY_BYTES) { "Invalid vault key length" }
        return VaultKey(key)
    }

    companion object {
        const val ALLOWED_AUTHENTICATORS =
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "love-doves-vault-wrap-v1"
        private const val PREFERENCES = "love-doves-vault-bootstrap"
        private const val WRAPPED_VAULT_KEY = "wrapped-vault-key"
        private const val VAULT_KEY_NONCE = "vault-key-nonce"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val VAULT_KEY_BYTES = 32
    }
}

internal class PreparedVaultUnlock internal constructor(
    val cipher: Cipher,
    internal val newVaultKey: ByteArray?,
    private val keyStore: VaultKeyStore,
) {
    val cryptoObject: BiometricPrompt.CryptoObject
        get() = BiometricPrompt.CryptoObject(cipher)

    fun complete(result: BiometricPrompt.AuthenticationResult): VaultKey {
        val authenticatedCipher = result.cryptoObject?.cipher
            ?: error("Biometric authentication returned no cipher")
        return keyStore.complete(this, authenticatedCipher)
    }

    fun cancel() {
        newVaultKey?.fill(0)
    }
}

internal class VaultKey internal constructor(private val bytes: ByteArray) : AutoCloseable {
    fun copyBytes(): ByteArray = bytes.copyOf()

    override fun close() {
        bytes.fill(0)
    }
}

private fun ByteArray.toBase64(): String =
    android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

private fun String.fromBase64(): ByteArray =
    android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
