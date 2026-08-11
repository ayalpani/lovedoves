package com.yalpani.lovedoves.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryVoiceRecorderTest {
    @Before
    fun grantMicrophonePermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "pm grant com.yalpani.lovedoves android.permission.RECORD_AUDIO",
            )
            .close()
    }

    @Test
    fun recordsPausesAndReturnsAnInMemoryMpeg4Payload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = MemoryVoiceRecorder(context)

        recorder.start()
        Thread.sleep(450L)
        recorder.pause()
        val pausedAt = recorder.elapsedMillis()
        Thread.sleep(150L)
        assertEquals(pausedAt, recorder.elapsedMillis())
        recorder.resume()
        Thread.sleep(300L)
        val voice = recorder.finish()

        assertTrue(voice.durationMillis >= 650L)
        assertTrue(voice.m4a.size > 32)
        assertEquals("ftyp", voice.m4a.copyOfRange(4, 8).decodeToString())

        voice.clear()
        assertTrue(voice.m4a.all { it == 0.toByte() })
    }
}
