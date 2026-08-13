package com.yalpani.lovedoves.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MemoryMediaFileTest {
    @Test
    fun anonymousMediaFileRoundTripsWithoutAPath() {
        val bytes = ByteArray(32 * 1_024) { (it % 251).toByte() }

        MemoryMediaFile.fromBytes(bytes).use { memory ->
            assertArrayEquals(bytes, memory.readBytes(bytes.size))
        }
    }
}
