package com.yalpani.lovedoves.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PreparedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal object PhotoProcessor {
    suspend fun fromPicker(contentResolver: ContentResolver, uri: Uri): PreparedPhoto =
        withContext(Dispatchers.Default) {
            normalize(ImageDecoder.createSource(contentResolver, uri))
        }

    suspend fun fromCamera(jpeg: ByteArray): PreparedPhoto = withContext(Dispatchers.Default) {
        try {
            normalize(ImageDecoder.createSource(ByteBuffer.wrap(jpeg)))
        } finally {
            jpeg.fill(0)
        }
    }

    private fun normalize(source: ImageDecoder.Source): PreparedPhoto {
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val longest = maxOf(width, height)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        val bitmap = if (decoded.hasAlpha()) {
            Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).run {
                    drawColor(Color.rgb(255, 249, 247))
                    drawBitmap(decoded, 0f, 0f, null)
                }
                decoded.recycle()
            }
        } else {
            decoded
        }
        try {
            for (quality in listOf(90, 85, 80)) {
                val output = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                val jpeg = output.toByteArray()
                if (jpeg.size <= LoveDovesRepository.MAX_PHOTO_BYTES) {
                    return PreparedPhoto(jpeg, bitmap.width, bitmap.height)
                }
                jpeg.fill(0)
            }
            error("Das Foto ist auch nach der sicheren Optimierung größer als 20 MiB.")
        } finally {
            bitmap.recycle()
        }
    }

    private const val MAX_EDGE = 4096
}
