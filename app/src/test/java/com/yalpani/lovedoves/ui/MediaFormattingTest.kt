package com.yalpani.lovedoves.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFormattingTest {
    @Test
    fun videoDurationUsesCompactClockFormat() {
        assertEquals("0:00", formatMediaDuration(0))
        assertEquals("0:09", formatMediaDuration(9_999))
        assertEquals("1:05", formatMediaDuration(65_000))
    }
}
