package com.yalpani.lovedoves.ui

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.Closeable

/** Seekable, anonymous RAM storage for codecs that require a file descriptor. */
internal class MemoryMediaFile private constructor(
    private val descriptor: ParcelFileDescriptor,
) : Closeable {
    val fileDescriptor get() = descriptor.fileDescriptor
    val size: Long get() = descriptor.statSize

    fun duplicate(): ParcelFileDescriptor = descriptor.dup()

    fun readBytes(maxBytes: Int): ByteArray {
        val byteCount = size
        require(byteCount in 1..maxBytes.toLong()) { "Das Medium ist zu groß." }
        val duplicate = duplicate()
        Os.lseek(duplicate.fileDescriptor, 0L, OsConstants.SEEK_SET)
        return ParcelFileDescriptor.AutoCloseInputStream(duplicate).use { input ->
            ByteArray(byteCount.toInt()).also { result ->
                var offset = 0
                while (offset < result.size) {
                    val read = input.read(result, offset, result.size - offset)
                    check(read > 0) { "Das Medium konnte nicht vollständig gelesen werden." }
                    offset += read
                }
            }
        }
    }

    override fun close() = descriptor.close()

    companion object {
        fun create(): MemoryMediaFile {
            val raw = Os.memfd_create("lovedoves-media", OsConstants.MFD_CLOEXEC)
            return try {
                MemoryMediaFile(ParcelFileDescriptor.dup(raw))
            } finally {
                Os.close(raw)
            }
        }

        fun fromBytes(bytes: ByteArray): MemoryMediaFile = create().also { memory ->
            val duplicate = memory.duplicate()
            Os.ftruncate(duplicate.fileDescriptor, 0L)
            Os.lseek(duplicate.fileDescriptor, 0L, OsConstants.SEEK_SET)
            ParcelFileDescriptor.AutoCloseOutputStream(duplicate).use { it.write(bytes) }
        }
    }
}
