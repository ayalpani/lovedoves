package com.yalpani.lovedoves.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.yalpani.lovedoves.domain.LoveDovesRepository
import com.yalpani.lovedoves.domain.PreparedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal object VideoProcessor {
    suspend fun fromCamera(mp4: ByteArray): PreparedVideo = withContext(Dispatchers.Default) {
        runCatching { inspect(mp4) }
            .onFailure { mp4.fill(0) }
            .getOrThrow()
    }

    suspend fun fromPicker(context: Context, uri: Uri): PreparedVideo =
        withContext(Dispatchers.IO) {
            val remuxed = remuxWithoutMetadata(context, uri)
            runCatching { inspect(remuxed) }
                .onFailure { remuxed.fill(0) }
                .getOrThrow()
        }

    private fun inspect(mp4: ByteArray): PreparedVideo {
        require(mp4.size <= LoveDovesRepository.MAX_VIDEO_BYTES) { "Das Video ist größer als 20 MiB." }
        MemoryMediaFile.fromBytes(mp4).use { memory ->
            val descriptor = memory.duplicate()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(descriptor.fileDescriptor)
                val rawWidth = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val rawHeight = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotation = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull()?.coerceAtLeast(1L) ?: error("Das Video hat keine gültige Dauer.")
                require(rawWidth > 0 && rawHeight > 0) { "Das Video hat keine gültige Größe." }
                val targetScale = minOf(1f, THUMBNAIL_MAX_EDGE.toFloat() / maxOf(rawWidth, rawHeight))
                val frame = retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    (rawWidth * targetScale).toInt().coerceAtLeast(1),
                    (rawHeight * targetScale).toInt().coerceAtLeast(1),
                ) ?: error("Für das Video konnte kein Vorschaubild erstellt werden.")
                val thumbnailWidth = frame.width
                val thumbnailHeight = frame.height
                val thumbnail = try {
                    ByteArrayOutputStream().use { output ->
                        check(frame.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, output))
                        output.toByteArray()
                    }
                } finally {
                    frame.recycle()
                }
                val rotated = rotation == 90 || rotation == 270
                return PreparedVideo(
                    mp4 = mp4,
                    thumbnailJpeg = thumbnail,
                    width = if (rotated) rawHeight else rawWidth,
                    height = if (rotated) rawWidth else rawHeight,
                    durationMillis = duration,
                    thumbnailWidth = thumbnailWidth,
                    thumbnailHeight = thumbnailHeight,
                )
            } finally {
                retriever.release()
                descriptor.close()
            }
        }
    }

    private fun remuxWithoutMetadata(context: Context, uri: Uri): ByteArray {
        val extractor = MediaExtractor()
        val output = MemoryMediaFile.create()
        var muxer: MediaMuxer? = null
        var muxerDescriptor: android.os.ParcelFileDescriptor? = null
        try {
            extractor.setDataSource(context, uri, null)
            val tracks = mutableMapOf<Int, Int>()
            var orientation = 0
            var bufferSize = DEFAULT_SAMPLE_BUFFER_BYTES
            muxerDescriptor = output.duplicate()
            muxer = MediaMuxer(
                muxerDescriptor.fileDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            repeat(extractor.trackCount) { trackIndex ->
                val format = extractor.getTrackFormat(trackIndex)
                val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                    extractor.selectTrack(trackIndex)
                    tracks[trackIndex] = muxer.addTrack(format)
                    if (mimeType.startsWith("video/")) {
                        orientation = format.intOrNull(MediaFormat.KEY_ROTATION) ?: orientation
                    }
                    bufferSize = maxOf(
                        bufferSize,
                        format.intOrNull(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0,
                    )
                }
            }
            require(tracks.keys.any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }) { "Die Auswahl enthält kein unterstütztes Video." }
            if (orientation in setOf(0, 90, 180, 270)) muxer.setOrientationHint(orientation)
            muxer.start()
            val sampleBuffer = ByteBuffer.allocateDirect(
                bufferSize.coerceIn(DEFAULT_SAMPLE_BUFFER_BYTES, MAX_SAMPLE_BUFFER_BYTES),
            )
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                sampleBuffer.clear()
                val size = extractor.readSampleData(sampleBuffer, 0)
                if (size < 0) break
                val outputTrack = tracks[extractor.sampleTrackIndex]
                if (outputTrack != null) {
                    val sampleFlags = extractor.sampleFlags
                    require(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                        "Verschlüsselte Galerie-Videos werden nicht unterstützt."
                    }
                    var codecFlags = 0
                    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    }
                    bufferInfo.set(0, size, extractor.sampleTime, codecFlags)
                    muxer.writeSampleData(outputTrack, sampleBuffer, bufferInfo)
                    require(output.size <= LoveDovesRepository.MAX_VIDEO_BYTES) {
                        "Das Video ist größer als 20 MiB."
                    }
                }
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            muxer = null
            muxerDescriptor.close()
            muxerDescriptor = null
            return output.readBytes(LoveDovesRepository.MAX_VIDEO_BYTES)
        } finally {
            runCatching { muxer?.release() }
            runCatching { muxerDescriptor?.close() }
            extractor.release()
            output.close()
        }
    }

    private fun MediaMetadataRetriever.metadataInt(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private const val THUMBNAIL_MAX_EDGE = 1_024
    private const val THUMBNAIL_QUALITY = 85
    private const val DEFAULT_SAMPLE_BUFFER_BYTES = 1 * 1_024 * 1_024
    private const val MAX_SAMPLE_BUFFER_BYTES = 8 * 1_024 * 1_024
}
