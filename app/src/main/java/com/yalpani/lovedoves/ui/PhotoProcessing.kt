package com.yalpani.lovedoves.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
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

    suspend fun fromCamera(
        jpeg: ByteArray,
        leftQuarterTurns: Int,
    ): PreparedPhoto {
        try {
            return withContext(Dispatchers.Default) {
                normalize(
                    source = ImageDecoder.createSource(ByteBuffer.wrap(jpeg)),
                    leftQuarterTurns = leftQuarterTurns,
                )
            }
        } finally {
            jpeg.fill(0)
        }
    }

    suspend fun previewFromCamera(jpeg: ByteArray, leftQuarterTurns: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            runCatching {
                val decoded = decodeBitmap(
                    source = ImageDecoder.createSource(ByteBuffer.wrap(jpeg)),
                    maxEdge = PREVIEW_MAX_EDGE,
                )
                rotateLeft(decoded, leftQuarterTurns)
            }.getOrNull()
        }

    suspend fun forDisplay(jpeg: ByteArray, maxEdge: Int): Bitmap {
        try {
            return withContext(Dispatchers.Default) {
                decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(jpeg)), maxEdge)
            }
        } finally {
            jpeg.fill(0)
        }
    }

    private fun normalize(
        source: ImageDecoder.Source,
        leftQuarterTurns: Int = 0,
    ): PreparedPhoto {
        val decoded = decodeBitmap(source, MAX_EDGE)
        val rotated = rotateLeft(decoded, leftQuarterTurns)
        val bitmap = if (rotated.hasAlpha()) {
            Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).run {
                    drawColor(Color.rgb(255, 249, 247))
                    drawBitmap(rotated, 0f, 0f, null)
                }
                rotated.recycle()
            }
        } else {
            rotated
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

    private fun decodeBitmap(source: ImageDecoder.Source, maxEdge: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val longest = maxOf(width, height)
            if (longest > maxEdge) {
                val scale = maxEdge.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            }
        }

    private fun rotateLeft(source: Bitmap, leftQuarterTurns: Int): Bitmap {
        val turns = Math.floorMod(leftQuarterTurns, 4)
        if (turns == 0) return source
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(leftRotationDegrees(turns)) },
            true,
        )
        if (rotated !== source) source.recycle()
        return rotated
    }

    private const val PREVIEW_MAX_EDGE = 2048
    private const val MAX_EDGE = 4096
}

internal fun leftRotationDegrees(leftQuarterTurns: Int): Float {
    val turns = Math.floorMod(leftQuarterTurns, 4)
    return if (turns == 0) 0f else -90f * turns
}
