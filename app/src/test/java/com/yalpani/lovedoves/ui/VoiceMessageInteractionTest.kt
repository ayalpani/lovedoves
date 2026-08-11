package com.yalpani.lovedoves.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMessageInteractionTest {
    @Test
    fun horizontalDragCancels() {
        assertEquals(
            VoiceGestureDecision.CANCEL,
            voiceGestureDecision(deltaX = -90f, deltaY = -20f, threshold = 78f),
        )
    }

    @Test
    fun verticalDragLocks() {
        assertEquals(
            VoiceGestureDecision.LOCK,
            voiceGestureDecision(deltaX = -20f, deltaY = -90f, threshold = 78f),
        )
    }

    @Test
    fun diagonalDragUsesDominantDirection() {
        assertEquals(
            VoiceGestureDecision.LOCK,
            voiceGestureDecision(deltaX = -80f, deltaY = -100f, threshold = 78f),
        )
        assertEquals(
            VoiceGestureDecision.CANCEL,
            voiceGestureDecision(deltaX = -100f, deltaY = -80f, threshold = 78f),
        )
    }

    @Test
    fun shortDragKeepsRecording() {
        assertEquals(
            VoiceGestureDecision.NONE,
            voiceGestureDecision(deltaX = -77f, deltaY = -77f, threshold = 78f),
        )
    }

    @Test
    fun recordingDurationUsesTenths() {
        assertEquals("0:00,0", formatVoiceRecordingDuration(0L))
        assertEquals("0:09,9", formatVoiceRecordingDuration(9_999L))
        assertEquals("1:05,4", formatVoiceRecordingDuration(65_499L))
    }
}
