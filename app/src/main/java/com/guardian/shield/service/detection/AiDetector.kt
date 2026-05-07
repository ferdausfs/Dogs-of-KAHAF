package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MODEL_FILENAME = "guardian_model.tflite"
        private const val INPUT_SIZE = 224
        private const val TAG = "Guardian_AI"

        private const val PORN_HARD_THRESHOLD = 0.20f
        private const val HENTAI_HARD_THRESHOLD = 0.25f
        private const val SEXY_HARD_THRESHOLD = 0.40f

        fun modelFile(ctx: Context): File =
            File(ctx.filesDir, MODEL_FILENAME)

        fun isModelAvailable(ctx: Context): Boolean {
            val file = modelFile(ctx)
            val exists = file.exists() && file.length() > 1024
            Timber.d("$TAG isModelAvailable: $exists, size=${file.length()}")
            return exists
        }
    }

    data class AiResult(
        val isUnsafe: Boolean,
        val unsafeScore: Float,
        val label: String,
        val rawScores: FloatArray = floatArrayOf()
    )

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var nnapiDelegate: NnApiDelegate? = null
    @Volatile private var loaded = false
    @Volatile private var outputSize = 0
    @Volatile private var inputSize = INPUT_SIZE
    @Volatile private var isQuantized = false

    fun isLoaded(): Boolean = loaded

    fun load(): Boolean {
        synchronized(this) {
            interpreter?.close()
            interpreter = null
            try { nnapiDelegate?.close() } catch (_: Exception) {}
            nnapiDelegate = null
        }

        return try {
            val file = modelFile(context)
            if (!file.exists() || file.length() < 1024) {
                Timber.w("$TAG model missing/too small: ${file.length()} bytes")
                return false
            }

            Timber.d("$TAG loading model: ${file.length() / 1024}KB")
            val buf = mapFile(file)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    try {
                        val delegate = NnApiDelegate()
                        addDelegate(delegate)
                        nnapiDelegate = delegate
                    } catch (e: Throwable) {
                        Timber.w(e, "$TAG NNAPI not available")
                    }
                }
            }

            synchronized(this) {
                val interp = Interpreter(buf, options)
                interpreter = interp

                val inputTensor = interp.getInputTensor(0)
                val shape = inputTensor.shape()
                inputSize = if (shape.size >= 3) shape[1] else INPUT_SIZE
                isQuantized = inputTensor.dataType().toString().contains("UINT8", ignoreCase = true)

                outputSize = interp.getOutputTensor(0).shape()[1]
                loaded = true

                Timber.d("$TAG loaded: input=${inputSize}x${inputSize}, " +
                        "outputSize=$outputSize, quantized=$isQuantized, type=${getModelType()}")
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "$TAG load FAILED")
            loaded = false
            false
        }
    }

    fun unload() {
        synchronized(this) {
            interpreter?.close()
            interpreter = null
            try { nnapiDelegate?.close() } catch (_: Exception) {}
            nnapiDelegate = null
            loaded = false
            outputSize = 0
        }
        Timber.d("$TAG unloaded")
    }

    fun reload(): Boolean {
        unload()
        return load()
    }

    fun getModelType(): String = when (outputSize) {
        2 -> "2-class [safe, unsafe]"
        5 -> "5-class NSFW [drawings, hentai, neutral, porn, sexy]"
        else -> "$outputSize-class"
    }

    fun classify(bitmap: Bitmap, threshold: Float = 0.30f): AiResult {
        val input = bitmapToBuffer(bitmap)

        return synchronized(this) {
            val interp = interpreter
                ?: return@synchronized AiResult(false, 0f, "Model not loaded")
            if (!loaded)
                return@synchronized AiResult(false, 0f, "Model not loaded")

            val capturedSize = outputSize
            val output = Array(1) { FloatArray(capturedSize) }

            try {
                val t0 = System.currentTimeMillis()
                interp.run(input, output)
                val dt = System.currentTimeMillis() - t0

                val scores = normalizeScores(output[0])
                val result = parseOutput(scores, capturedSize, threshold)
                Timber.d("$TAG classify ${dt}ms — ${result.label} | raw=${scores.joinToString(",") { "%.2f".format(it) }}")
                result
            } catch (e: Exception) {
                Timber.e(e, "$TAG classify error")
                AiResult(false, 0f, "Error: ${e.message}")
            }
        }
    }

    private fun normalizeScores(scores: FloatArray): FloatArray {
        val sum = scores.sum()
        val hasNegative = scores.any { it < 0f }
        val needsSoftmax = hasNegative || sum < 0.5f || sum > 1.5f

        return if (needsSoftmax) {
            val maxVal = scores.max()
            val exp = FloatArray(scores.size) { kotlin.math.exp((scores[it] - maxVal).toDouble()).toFloat() }
            val expSum = exp.sum()
            FloatArray(scores.size) { exp[it] / expSum }
        } else {
            scores
        }
    }

    fun shouldSkipFrame(bitmap: Bitmap): Boolean {
        // CRITICAL FIX: ONLY skip completely black/blank screens.
        // DO NOT use variance-based skipping — adult/skin-tone content has
        // LOW variance by nature (uniform flesh tones) and was being skipped
        // before AI could ever analyse it. Only skip truly empty frames.
        val sample = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
        val pixels = IntArray(16 * 16)
        sample.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (sample !== bitmap) sample.recycle()

        var brightness = 0L
        for (p in pixels) {
            brightness += ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
        }
        val avg = brightness / (pixels.size * 3)
        // Only skip near-black screens (loading screens, screen-off, etc.)
        return avg < 10
    }

    private fun parseOutput(scores: FloatArray, size: Int, threshold: Float): AiResult {
        return when (size) {
            2 -> {
                val unsafe = scores[1]
                AiResult(
                    isUnsafe = unsafe >= threshold,
                    unsafeScore = unsafe,
                    label = if (unsafe >= threshold)
                        "Unsafe (${(unsafe * 100).toInt()}%)"
                    else
                        "Safe (${((1 - unsafe) * 100).toInt()}%)",
                    rawScores = scores
                )
            }
            5 -> {
                // NsfwJS order: [drawings, hentai, neutral, porn, sexy]
                val drawings = scores[0]
                val hentai = scores[1]
                val neutral = scores[2]
                val porn = scores[3]
                val sexy = scores[4]

                if (porn >= PORN_HARD_THRESHOLD) {
                    return AiResult(true, porn,
                        "🔞 PORN (${(porn * 100).toInt()}%)", scores)
                }
                if (hentai >= HENTAI_HARD_THRESHOLD) {
                    return AiResult(true, hentai,
                        "🔞 HENTAI (${(hentai * 100).toInt()}%)", scores)
                }
                if (sexy >= SEXY_HARD_THRESHOLD) {
                    return AiResult(true, sexy,
                        "⚠️ SEXY (${(sexy * 100).toInt()}%)", scores)
                }

                val combinedUnsafe = (porn + hentai + (sexy * 0.7f)).coerceIn(0f, 1f)
                val isUnsafe = combinedUnsafe >= threshold

                val labels = listOf("drawings", "hentai", "neutral", "porn", "sexy")
                val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 2
                val dominant = labels[maxIdx]

                AiResult(
                    isUnsafe = isUnsafe,
                    unsafeScore = combinedUnsafe,
                    label = if (isUnsafe)
                        "NSFW: $dominant (${(combinedUnsafe * 100).toInt()}%)"
                    else
                        "Safe: $dominant (n=${(neutral * 100).toInt()}%)",
                    rawScores = scores
                )
            }
            else -> {
                val unsafe = scores.last()
                AiResult(
                    isUnsafe = unsafe >= threshold,
                    unsafeScore = unsafe,
                    label = "Score: ${(unsafe * 100).toInt()}%",
                    rawScores = scores
                )
            }
        }
    }

    private fun bitmapToBuffer(src: Bitmap): ByteBuffer {
        val targetSize = inputSize
        val bmp = if (src.width != targetSize || src.height != targetSize)
            Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
        else src

        val pixels = IntArray(targetSize * targetSize)
        bmp.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        if (bmp !== src) bmp.recycle()

        val bytesPerChannel = if (isQuantized) 1 else 4
        val buf = ByteBuffer
            .allocateDirect(targetSize * targetSize * 3 * bytesPerChannel)
            .apply { order(ByteOrder.nativeOrder()) }

        if (isQuantized) {
            for (p in pixels) {
                buf.put(((p shr 16) and 0xFF).toByte())
                buf.put(((p shr 8) and 0xFF).toByte())
                buf.put((p and 0xFF).toByte())
            }
        } else {
            // FIX: [-1,1] normalization — Keras MobileNet preprocess_input standard
            // Previous [0,1] (÷255) gave completely wrong predictions
            val inv127_5 = 1f / 127.5f
            for (p in pixels) {
                buf.putFloat(((p shr 16) and 0xFF) * inv127_5 - 1f)
                buf.putFloat(((p shr 8) and 0xFF) * inv127_5 - 1f)
                buf.putFloat((p and 0xFF) * inv127_5 - 1f)
            }
        }
        buf.rewind()
        return buf
    }

    private fun mapFile(f: File): MappedByteBuffer {
        RandomAccessFile(f, "r").use { raf ->
            raf.channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
        }
    }
}