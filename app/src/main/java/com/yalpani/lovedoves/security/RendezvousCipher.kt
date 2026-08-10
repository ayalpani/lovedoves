package com.yalpani.lovedoves.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object RendezvousCipher {
    fun encrypt(secret: ByteArray, inviteId: String, plaintext: ByteArray): ByteArray {
        require(secret.size == KEY_BYTES)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val encrypted = cipher(Cipher.ENCRYPT_MODE, secret, nonce, inviteId).doFinal(plaintext)
        return MAGIC + nonce + encrypted
    }

    fun decrypt(secret: ByteArray, inviteId: String, payload: ByteArray): ByteArray {
        require(secret.size == KEY_BYTES)
        require(payload.size >= MAGIC.size + NONCE_BYTES + TAG_BYTES) { "Response is truncated" }
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Unknown pairing response"
        }
        val nonce = payload.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)
        return cipher(Cipher.DECRYPT_MODE, secret, nonce, inviteId).doFinal(
            payload.copyOfRange(MAGIC.size + NONCE_BYTES, payload.size),
        )
    }

    private fun cipher(mode: Int, secret: ByteArray, nonce: ByteArray, inviteId: String): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            updateAAD("LoveDovesRendezvousV1:$inviteId".encodeToByteArray())
        }

    private val MAGIC = byteArrayOf(0x4c, 0x44, 0x52, 0x31)
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
}
