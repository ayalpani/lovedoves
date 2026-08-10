package com.yalpani.lovedoves.ui

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraRotationTest {
    @Test
    fun cameraTargetRotationKeepsSupportedDisplayRotations() {
        listOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270,
        ).forEach { rotation ->
            assertEquals(rotation, cameraTargetRotation(rotation))
        }
        assertEquals(Surface.ROTATION_0, cameraTargetRotation(null))
    }

    @Test
    fun manualRotationWrapsAfterFourLeftTurns() {
        assertEquals(0f, leftRotationDegrees(0))
        assertEquals(-90f, leftRotationDegrees(1))
        assertEquals(-270f, leftRotationDegrees(3))
        assertEquals(0f, leftRotationDegrees(4))
    }
}
