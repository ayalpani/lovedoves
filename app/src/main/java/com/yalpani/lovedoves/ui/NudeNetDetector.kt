package com.yalpani.lovedoves.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

internal enum class ReceivedPhotoSafety {
    SAFE,
    EXPLICIT,
    UNAVAILABLE,
}

internal class NudeNetDetector(
    private val context: Context,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var model: MappedByteBuffer? = null
    private var interpreter: Interpreter? = null
    private val input = ByteBuffer.allocateDirect(MODEL_SIZE * MODEL_SIZE * RGB_CHANNELS * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(OUTPUT_CHANNELS * OUTPUT_ANCHORS * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
    private val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
    private val scores = FloatArray(OUTPUT_CHANNELS * OUTPUT_ANCHORS)

    suspend fun classify(bitmap: Bitmap): ReceivedPhotoSafety = withContext(Dispatchers.Default) {
        synchronized(lock) {
            if (closed) return@synchronized ReceivedPhotoSafety.UNAVAILABLE
            try {
                val score = infer(bitmap)
                if (score >= RECEIVED_PHOTO_EXPLICIT_SCORE_THRESHOLD) {
                    ReceivedPhotoSafety.EXPLICIT
                } else {
                    ReceivedPhotoSafety.SAFE
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ReceivedPhotoSafety.UNAVAILABLE
            }
        }
    }

    private fun infer(bitmap: Bitmap): Float {
        val runtime = interpreter ?: createInterpreter().also { interpreter = it }
        prepareInput(bitmap)
        output.clear()
        runtime.run(input, output)
        output.rewind()
        output.asFloatBuffer().get(scores)
        return maleGenitaliaScore(scores, runtime.getOutputTensor(0).shape())
    }

    private fun createInterpreter(): Interpreter {
        val mappedModel = mapModel(context).also { model = it }
        return Interpreter(
            mappedModel,
            Interpreter.Options().setNumThreads(INFERENCE_THREADS),
        ).also { runtime ->
            check(runtime.getInputTensor(0).shape().contentEquals(INPUT_SHAPE))
            check(runtime.getOutputTensor(0).shape().contentEquals(OUTPUT_SHAPE))
        }
    }

    private fun prepareInput(source: Bitmap) {
        val longest = maxOf(source.width, source.height).coerceAtLeast(1)
        val scale = MODEL_SIZE.toFloat() / longest
        val width = (source.width * scale).toInt().coerceIn(1, MODEL_SIZE)
        val height = (source.height * scale).toInt().coerceIn(1, MODEL_SIZE)
        val square = Bitmap.createBitmap(MODEL_SIZE, MODEL_SIZE, Bitmap.Config.ARGB_8888)
        try {
            Canvas(square).run {
                drawColor(Color.BLACK)
                drawBitmap(
                    source,
                    null,
                    Rect(0, 0, width, height),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
            }
            square.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        } finally {
            square.recycle()
        }

        input.clear()
        pixels.forEach { pixel ->
            input.putFloat(Color.red(pixel) / 255f)
            input.putFloat(Color.green(pixel) / 255f)
            input.putFloat(Color.blue(pixel) / 255f)
        }
        input.rewind()
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            interpreter?.close()
            interpreter = null
            model = null
            input.clear()
            output.clear()
            scores.fill(0f)
            pixels.fill(0)
        }
    }

    private companion object {
        const val MODEL_ASSET = "nudenet_320n_fp16.tflite"
        const val MODEL_SIZE = 320
        const val RGB_CHANNELS = 3
        const val FLOAT_BYTES = 4
        const val OUTPUT_CHANNELS = 22
        const val OUTPUT_ANCHORS = 2_100
        const val INFERENCE_THREADS = 2
        val INPUT_SHAPE = intArrayOf(1, MODEL_SIZE, MODEL_SIZE, RGB_CHANNELS)
        val OUTPUT_SHAPE = intArrayOf(1, OUTPUT_CHANNELS, OUTPUT_ANCHORS)

        fun mapModel(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL_ASSET).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
    }
}

internal const val RECEIVED_PHOTO_EXPLICIT_SCORE_THRESHOLD = 0.25f

internal fun maleGenitaliaScore(output: FloatArray, shape: IntArray): Float {
    require(shape.size == 3 && shape[0] == 1)
    val channelFirst = shape[1] == NUDENET_OUTPUT_CHANNELS
    require(channelFirst || shape[2] == NUDENET_OUTPUT_CHANNELS)
    val anchors = if (channelFirst) shape[2] else shape[1]
    require(output.size == NUDENET_OUTPUT_CHANNELS * anchors)

    var maximum = 0f
    repeat(anchors) { anchor ->
        val index = if (channelFirst) {
            NUDENET_MALE_GENITALIA_CHANNEL * anchors + anchor
        } else {
            anchor * NUDENET_OUTPUT_CHANNELS + NUDENET_MALE_GENITALIA_CHANNEL
        }
        maximum = maxOf(maximum, output[index])
    }
    return maximum
}

private const val NUDENET_OUTPUT_CHANNELS = 22
private const val NUDENET_MALE_GENITALIA_CHANNEL = 18
