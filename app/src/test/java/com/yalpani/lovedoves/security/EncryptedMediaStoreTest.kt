package com.yalpani.lovedoves.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EncryptedMediaStoreTest {
    @Test
    fun encryptedFileRoundTripsWithoutPlaintextOnDisk() {
        val root = Files.createTempDirectory("love-doves-media").toFile()
        try {
            val store = EncryptedMediaStore(root, forTesting = true)
            val plaintext = "Nur für uns zwei".encodeToByteArray()

            val media = store.encrypt(plaintext)
            val diskBytes = File(root, media.relativePath).readBytes()

            assertArrayEquals(plaintext, store.decrypt(media))
            assertTrue(!diskBytes.toString(Charsets.UTF_8).contains("Nur für uns zwei"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tamperingIsRejected() {
        val root = Files.createTempDirectory("love-doves-media").toFile()
        try {
            val store = EncryptedMediaStore(root, forTesting = true)
            val media = store.encrypt(ByteArray(1_024) { it.toByte() })
            val file = File(root, media.relativePath)
            val bytes = file.readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            file.writeBytes(bytes)

            assertThrows(IllegalArgumentException::class.java) { store.decrypt(media) }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
