package com.yalpani.lovedoves.transport

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class InboundSpool(context: Context) {
    private val directory = File(context.filesDir, "transport-inbox")

    fun contains(objectId: String): Boolean = synchronized(StorageLock) { file(objectId).isFile }

    fun put(objectId: String, bytes: ByteArray, expectedSHA256Hex: String) {
        require(bytes.size <= MAX_BYTES)
        require(expectedSHA256Hex.matches(Regex("[0-9a-fA-F]{64}")))
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        require(actual.equals(expectedSHA256Hex, ignoreCase = true)) { "Transport hash mismatch" }
        synchronized(StorageLock) {
            directory.mkdirs()
            val partial = File(directory, "$objectId.part")
            val target = file(objectId)
            if (target.isFile) {
                val existingHash = MessageDigest.getInstance("SHA-256").digest(target.readBytes()).toHex()
                require(existingHash.equals(expectedSHA256Hex, ignoreCase = true)) {
                    "Conflicting transport object"
                }
                return
            }
            try {
                partial.outputStream().use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                check(partial.renameTo(target)) { "Could not commit incoming object" }
                val newest = directory.listFiles()
                    .orEmpty()
                    .filter { it != target && it.extension == "packet" }
                    .maxOfOrNull(File::lastModified)
                    ?: 0L
                target.setLastModified(maxOf(System.currentTimeMillis(), newest + 1))
            } finally {
                partial.delete()
            }
        }
    }

    fun objectIds(): List<String> = synchronized(StorageLock) {
        directory.listFiles()
            .orEmpty()
            .filter { it.extension == "packet" }
            .sortedWith(compareBy(File::lastModified, File::getName))
            .map { it.nameWithoutExtension }
    }

    fun get(objectId: String): ByteArray = synchronized(StorageLock) { file(objectId).readBytes() }

    fun delete(objectId: String) {
        synchronized(StorageLock) { file(objectId).delete() }
    }

    fun clear() {
        synchronized(StorageLock) {
            directory.listFiles()?.forEach(File::delete)
            directory.delete()
        }
    }

    private fun file(objectId: String): File {
        require(objectId.matches(Regex("[A-Za-z0-9_-]{16,128}"))) { "Invalid object id" }
        return File(directory, "$objectId.packet")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024
        private val StorageLock = Any()
    }
}
