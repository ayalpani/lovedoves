package com.yalpani.lovedoves.security

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class EncryptedMedia(
    val id: String,
    val relativePath: String,
    val key: ByteArray,
    val nonce: ByteArray,
    val encryptedSize: Long,
    val cipherSha256: ByteArray,
)

internal class EncryptedMediaStore private constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, "vault/media"))

    internal constructor(rootDirectory: File, forTesting: Boolean) : this(
        File(rootDirectory, if (forTesting) "media" else "vault/media"),
    )

    fun encrypt(plaintext: ByteArray): EncryptedMedia {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Photo exceeds encrypted relay limit" }
        val id = UUID.randomUUID().toString()
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, id)
        val encrypted = cipher.doFinal(plaintext)
        val finalFile = File(directory, "$id.ldv")
        val partialFile = File(directory, "$id.part")
        directory.mkdirs()
        try {
            partialFile.outputStream().use { output ->
                output.write(MAGIC)
                output.write(nonce)
                output.write(encrypted)
                output.fdSync()
            }
            check(partialFile.renameTo(finalFile)) { "Could not commit encrypted photo" }
            val hash = MessageDigest.getInstance("SHA-256").digest(finalFile.readBytes())
            return EncryptedMedia(
                id = id,
                relativePath = "media/${finalFile.name}",
                key = key,
                nonce = nonce,
                encryptedSize = finalFile.length(),
                cipherSha256 = hash,
            )
        } catch (failure: Throwable) {
            partialFile.delete()
            finalFile.delete()
            key.fill(0)
            throw failure
        } finally {
            encrypted.fill(0)
        }
    }

    fun decrypt(media: EncryptedMedia): ByteArray {
        val bytes = resolve(media.relativePath).readBytes()
        require(MessageDigest.isEqual(media.cipherSha256, sha256(bytes))) {
            "Encrypted photo failed integrity check"
        }
        require(bytes.size >= MAGIC.size + NONCE_BYTES + GCM_TAG_BYTES) {
            "Encrypted photo is truncated"
        }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Unknown encrypted photo format"
        }
        val storedNonce = bytes.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)
        require(storedNonce.contentEquals(media.nonce)) { "Photo nonce mismatch" }
        val ciphertext = bytes.copyOfRange(MAGIC.size + NONCE_BYTES, bytes.size)
        return cipher(Cipher.DECRYPT_MODE, media.key, storedNonce, media.id).doFinal(ciphertext)
    }

    fun readCiphertext(relativePath: String): ByteArray = resolve(relativePath).readBytes()

    fun delete(relativePath: String) {
        resolve(relativePath).delete()
    }

    fun importCiphertext(id: String, bytes: ByteArray, expectedHash: ByteArray): String {
        require(bytes.size <= MAX_CIPHERTEXT_BYTES) { "Encrypted photo exceeds limit" }
        require(MessageDigest.isEqual(expectedHash, sha256(bytes))) { "Photo hash mismatch" }
        val relativePath = "media/$id.ldv"
        val target = resolve(relativePath)
        val partial = File(directory, "$id.part")
        directory.mkdirs()
        if (target.isFile) {
            require(MessageDigest.isEqual(expectedHash, sha256(target.readBytes()))) {
                "Conflicting encrypted photo"
            }
            return relativePath
        }
        try {
            partial.outputStream().use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(partial.renameTo(target)) { "Could not commit downloaded photo" }
        } finally {
            partial.delete()
        }
        return relativePath
    }

    fun deleteAll() {
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
    }

    private fun resolve(relativePath: String): File {
        require(relativePath.matches(Regex("media/[0-9a-f-]{36}\\.ldv"))) {
            "Invalid media path"
        }
        return File(directory.parentFile, relativePath)
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, id: String): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            updateAAD("LoveDovesMediaV1:$id".encodeToByteArray())
        }

    private fun java.io.FileOutputStream.fdSync() = fd.sync()

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        private val MAGIC = byteArrayOf(0x4c, 0x44, 0x56, 0x31)
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16
        const val MAX_CIPHERTEXT_BYTES = 20 * 1024 * 1024
        const val MAX_PLAINTEXT_BYTES =
            MAX_CIPHERTEXT_BYTES - 4 - NONCE_BYTES - GCM_TAG_BYTES
    }
}
