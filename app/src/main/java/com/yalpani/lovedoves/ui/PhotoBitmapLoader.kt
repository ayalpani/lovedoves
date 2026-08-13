package com.yalpani.lovedoves.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.yalpani.lovedoves.domain.LoveDovesController
import kotlinx.coroutines.CancellationException

internal class PhotoBitmapLoader(
    context: Context,
    private val controller: LoveDovesController,
) : AutoCloseable {
    private val detector = NudeNetDetector(context.applicationContext)
    private val safety = mutableMapOf<String, ReceivedPhotoSafety>()
    private val revealed = mutableSetOf<String>()
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

    suspend fun safety(mediaId: String): ReceivedPhotoSafety {
        synchronized(safety) { safety[mediaId] }?.let { return it }
        val verdict = try {
            detector.classify(thumbnail(mediaId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ReceivedPhotoSafety.UNAVAILABLE
        }
        synchronized(safety) { safety.putIfAbsent(mediaId, verdict) }
        return verdict
    }

    fun isRevealed(mediaId: String): Boolean = synchronized(revealed) { mediaId in revealed }

    fun reveal(mediaId: String) {
        synchronized(revealed) { revealed += mediaId }
    }

    fun evict(mediaIds: Collection<String>) {
        mediaIds.forEach { mediaId ->
            thumbnails.remove(mediaId)?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            synchronized(safety) { safety.remove(mediaId) }
            synchronized(revealed) { revealed.remove(mediaId) }
        }
    }

    override fun close() {
        detector.close()
        synchronized(safety) { safety.clear() }
        synchronized(revealed) { revealed.clear() }
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
