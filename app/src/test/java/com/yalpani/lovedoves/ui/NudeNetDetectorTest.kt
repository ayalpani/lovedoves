package com.yalpani.lovedoves.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NudeNetDetectorTest {
    @Test
    fun readsMaleGenitaliaScoreFromChannelFirstOutput() {
        val output = FloatArray(22 * 3)
        output[18 * 3 + 1] = 0.72f

        assertEquals(0.72f, maleGenitaliaScore(output, intArrayOf(1, 22, 3)), 0f)
    }

    @Test
    fun readsMaleGenitaliaScoreFromChannelLastOutput() {
        val output = FloatArray(3 * 22)
        output[2 * 22 + 18] = RECEIVED_PHOTO_EXPLICIT_SCORE_THRESHOLD

        assertEquals(
            RECEIVED_PHOTO_EXPLICIT_SCORE_THRESHOLD,
            maleGenitaliaScore(output, intArrayOf(1, 3, 22)),
            0f,
        )
    }
}
