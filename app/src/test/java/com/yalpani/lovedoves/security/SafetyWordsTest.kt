package com.yalpani.lovedoves.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyWordsTest {
    @Test
    fun fingerprintIsSymmetricAndBounded() {
        val first = ByteArray(33) { it.toByte() }
        val second = ByteArray(33) { (it + 40).toByte() }
        val nonce = ByteArray(32) { (it * 3).toByte() }

        val forward = SafetyWords.indices(first, second, nonce)
        val reverse = SafetyWords.indices(second, first, nonce)

        assertEquals(forward, reverse)
        assertEquals(6, forward.size)
        assertTrue(forward.all { it in 0 until 2048 })
        assertEquals(listOf(1597, 82, 1083, 258, 39, 20), forward)
    }

    @Test
    fun changingTheInviteChangesTheFingerprint() {
        val identityA = ByteArray(33) { 1 }
        val identityB = ByteArray(33) { 2 }
        val firstNonce = ByteArray(32) { 3 }
        val secondNonce = firstNonce.copyOf().also { it[31] = 4 }

        assertTrue(
            SafetyWords.indices(identityA, identityB, firstNonce) !=
                SafetyWords.indices(identityA, identityB, secondNonce),
        )
    }
}
