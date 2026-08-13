package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NudeNetDetectorInstrumentedTest {
    @Test
    fun bundledModelRunsFullyOnDevice() = runBlocking {
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(96, 112, 128))
        }
        val detector = NudeNetDetector(ApplicationProvider.getApplicationContext())
        try {
            assertEquals(ReceivedPhotoSafety.SAFE, detector.classify(bitmap))
        } finally {
            detector.close()
            bitmap.recycle()
        }
    }
}
