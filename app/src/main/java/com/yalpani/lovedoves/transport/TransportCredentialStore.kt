package com.yalpani.lovedoves.transport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class TransportCredentials(
    val relayUrl: String,
    val mailboxId: String,
    val readCapability: ByteArray,
)

internal class TransportCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun put(credentials: TransportCredentials) {
        write(CREDENTIALS, encode(credentials))
    }

    fun get(): TransportCredentials? = read(CREDENTIALS)?.let(::decode)

    fun putPendingPushToken(token: String) {
        require(token.length <= 4096)
        write(PENDING_PUSH_TOKEN, token.encodeToByteArray())
    }

    fun pendingPushToken(): String? = read(PENDING_PUSH_TOKEN)?.let { bytes ->
        try {
            bytes.decodeToString()
        } finally {
            bytes.fill(0)
        }
    }

    fun clearPendingPushToken() {
        preferences.edit().remove(PENDING_PUSH_TOKEN).commit()
    }

    fun clear() {
        preferences.edit().clear().commit()
        keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun write(name: String, plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(plaintext)
        val encoded = cipher.iv + encrypted
        preferences.edit().putString(name, CapabilityCodec.encode(encoded)).commit()
        plaintext.fill(0)
        encrypted.fill(0)
        encoded.fill(0)
    }

    private fun read(name: String): ByteArray? {
        val encoded = preferences.getString(name, null)?.let(CapabilityCodec::decode) ?: return null
        require(encoded.size >= NONCE_BYTES + TAG_BYTES) { "Invalid transport credential" }
        val nonce = encoded.copyOfRange(0, NONCE_BYTES)
        val ciphertext = encoded.copyOfRange(NONCE_BYTES, encoded.size)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, nonce))
            doFinal(ciphertext)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(credentials: TransportCredentials): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeUTF(credentials.relayUrl)
                data.writeUTF(credentials.mailboxId)
                data.writeInt(credentials.readCapability.size)
                data.write(credentials.readCapability)
            }
            output.toByteArray()
        }

    private fun decode(value: ByteArray): TransportCredentials =
        DataInputStream(ByteArrayInputStream(value)).use { input ->
            val relayUrl = input.readUTF()
            val mailboxId = input.readUTF()
            val capabilitySize = input.readInt()
            require(capabilitySize == 32) { "Invalid read capability" }
            val capability = ByteArray(capabilitySize).also(input::readFully)
            require(input.available() == 0) { "Unexpected credential data" }
            TransportCredentials(relayUrl, mailboxId, capability)
        }.also { value.fill(0) }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "love-doves-transport-v1"
        private const val PREFERENCES = "love-doves-transport"
        private const val CREDENTIALS = "credentials"
        private const val PENDING_PUSH_TOKEN = "pending-push-token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
    }
}
