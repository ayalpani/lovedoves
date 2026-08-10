package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import android.util.LruCache
import com.yalpani.lovedoves.domain.LoveDovesController

internal class PhotoBitmapLoader(
    private val controller: LoveDovesController,
) : AutoCloseable {
    private val thumbnails = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun thumbnail(mediaId: String): Bitmap {
        thumbnails.get(mediaId)?.takeUnless(Bitmap::isRecycled)?.let { return it }
        return PhotoProcessor.forDisplay(
            jpeg = controller.thumbnailBytes(mediaId),
            maxEdge = THUMBNAIL_MAX_EDGE,
        ).also { thumbnails.put(mediaId, it) }
    }

    suspend fun fullSize(mediaId: String): Bitmap = PhotoProcessor.forDisplay(
        jpeg = controller.mediaBytes(mediaId),
        maxEdge = FULL_SIZE_MAX_EDGE,
    )

    override fun close() {
        val cached = thumbnails.snapshot().values
        thumbnails.evictAll()
        cached.forEach { if (!it.isRecycled) it.recycle() }
    }

    private companion object {
        const val THUMBNAIL_MAX_EDGE = 1_024
        const val FULL_SIZE_MAX_EDGE = 4_096
        const val THUMBNAIL_CACHE_KIB = 32 * 1_024
    }
}
