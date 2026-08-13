package com.yalpani.lovedoves.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMessageInteractionTest {
    @Test
    fun horizontalDragCancels() {
        assertEquals(
            VoiceGestureDecision.CANCEL,
            voiceGestureDecision(
                deltaX = -120f,
                deltaY = -20f,
                cancelThreshold = 108f,
                lockThreshold = 78f,
            ),
        )
    }

    @Test
    fun verticalDragLocks() {
        assertEquals(
            VoiceGestureDecision.LOCK,
            voiceGestureDecision(
                deltaX = -20f,
                deltaY = -90f,
                cancelThreshold = 108f,
                lockThreshold = 78f,
            ),
        )
    }

    @Test
    fun diagonalDragUsesDominantDirection() {
        assertEquals(
            VoiceGestureDecision.LOCK,
            voiceGestureDecision(
                deltaX = -80f,
                deltaY = -100f,
                cancelThreshold = 108f,
                lockThreshold = 78f,
            ),
        )
        assertEquals(
            VoiceGestureDecision.CANCEL,
            voiceGestureDecision(
                deltaX = -120f,
                deltaY = -80f,
                cancelThreshold = 108f,
                lockThreshold = 78f,
            ),
        )
    }

    @Test
    fun shortDragKeepsRecording() {
        assertEquals(
            VoiceGestureDecision.NONE,
            voiceGestureDecision(
                deltaX = -107f,
                deltaY = -20f,
                cancelThreshold = 108f,
                lockThreshold = 78f,
            ),
        )
    }

    @Test
    fun horizontalDragShowsProgressBeforeCancelling() {
        assertEquals(0.5f, voiceCancelProgress(-54f, -10f, 108f))
        assertEquals(1f, voiceCancelProgress(-140f, -10f, 108f))
        assertEquals(0f, voiceCancelProgress(-20f, -60f, 108f))
    }

    @Test
    fun recordingDurationUsesTenths() {
        assertEquals("0:00,0", formatVoiceRecordingDuration(0L))
        assertEquals("0:09,9", formatVoiceRecordingDuration(9_999L))
        assertEquals("1:05,4", formatVoiceRecordingDuration(65_499L))
    }

    @Test
    fun onlyCapturesShorterThanHalfASecondSwitchMode() {
        assertEquals(true, isQuickCapture(499L, 500L))
        assertEquals(false, isQuickCapture(500L, 500L))
    }
}
