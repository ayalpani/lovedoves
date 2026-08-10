package com.yalpani.lovedoves.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RendezvousCipherTest {
    @Test
    fun responseIsBoundToInviteAndRejectsTampering() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "pair response".encodeToByteArray()
        val encrypted = RendezvousCipher.encrypt(key, "invite-one", plaintext)

        assertArrayEquals(plaintext, RendezvousCipher.decrypt(key, "invite-one", encrypted))
        assertThrows(Exception::class.java) {
            RendezvousCipher.decrypt(key, "invite-two", encrypted)
        }
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()
        assertThrows(Exception::class.java) {
            RendezvousCipher.decrypt(key, "invite-one", encrypted)
        }
    }
}
