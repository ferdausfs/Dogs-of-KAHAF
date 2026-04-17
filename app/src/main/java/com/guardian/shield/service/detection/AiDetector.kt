package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFLite-based screen content classifier.
 *
 * NOT continuous — called only when triggered by accessibility events.
 * Model is user-supplied (not bundled in APK).
 * Supports 2-class [safe, unsafe] and 5-class [drawings, hentai, neutral, porn, sexy].
 *
 * Thread safety: synchronized on `this` for inference.
 */
@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MODEL_FILENAME = "guardian_model.tflite"
        private const val INPUT_SIZE = 224
        private const val TAG = "Guardian_AI"

        fun modelFile(ctx: Context): File =
            File(ctx.filesDir, MODEL_FILENAME)

        fun isModelAvailable(ctx: Context): Boolean =
            modelFile(ctx).let { it.exists() && it.length() > 1024 }
    }

    data class AiResult(
        val isUnsafe: Boolean,
        val unsafeScore: Float,
        val label: String
    )

    // Pre-allocated buffers to avoid GC pressure per inference
    private val pixelBuf = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val inputBuf = ByteBuffer
        .allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        .apply { order(ByteOrder.nativeOrder()) }

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var loaded = false

    fun isLoaded(): Boolean = loaded

    fun load(): Boolean {
        return try {
            val file = modelFile(context)
            if (!file.exists()) return false
            val buf = mapFile(file)
            val options = Interpreter.Options().apply {
                numThreads = 2
                setUseXNNPACK(true)
            }
            synchronized(this) {
                interpreter?.close()
                interpreter = Interpreter(buf, options)
                loaded = true
            }
            Timber.d("$TAG model loaded: ${file.length() / 1024}KB")
            true
        } catch (e: Exception) {
            Timber.e(e, "$TAG model load failed")
            loaded = false
            false
        }
    }

    fun unload() {
        synchronized(this) {
            interpreter?.close()
            interpreter = null
            loaded = false
        }
    }

    /**
     * Classify a bitmap.
     * MUST be called from a background thread.
     *
     * @param bitmap  source bitmap (any size — scaled internally)
     * @param threshold  unsafe score threshold (0.0–1.0)
     * @return AiResult
     */
    fun classify(bitmap: Bitmap, threshold: Float = 0.40f): AiResult {
        return try {
            val outputSize: Int
            val output: Array<FloatArray>
            synchronized(this) {
                val interp = interpreter
                    ?: return AiResult(false, 0f, "Model not loaded")
                val input = bitmapToBuffer(bitmap)
                val shape = interp.getOutputTensor(0).shape()
                outputSize = shape[1]
                output = Array(1) { FloatArray(outputSize) }
                interp.run(input, output)
            }
            parseOutput(output[0], outputSize, threshold)
        } catch (e: Exception) {
            Timber.e(e, "$TAG classify error")
            AiResult(false, 0f, "Error: ${e.message}")
        }
    }

    // ── Uniform frame skip (avoid false positives on loading/black screens) ──

    fun shouldSkipFrame(bitmap: Bitmap): Boolean {
        val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, false)
        val pixels = IntArray(32 * 32)
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        if (sample !== bitmap) sample.recycle()

        // Welford single-pass mean + variance
        var rM = 0.0; var rM2 = 0.0
        var gM = 0.0; var gM2 = 0.0
        var bM = 0.0; var bM2 = 0.0
        var brightness = 0L

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toDouble()
            val g = ((p shr 8) and 0xFF).toDouble()
            val b = (p and 0xFF).toDouble()
            brightness += (r + g + b).toLong()
            val n = (i + 1).toDouble()
            val dr = r - rM; rM += dr / n; rM2 += dr * (r - rM)
            val dg = g - gM; gM += dg / n; gM2 += dg * (g - gM)
            val db = b - bM; bM += db / n; bM2 += db * (b - bM)
        }
        val avg = brightness / (pixels.size * 3)
        if (avg < 15) return true  // Mostly black
        val variance = ((rM2 + gM2 + bM2) / pixels.size).toFloat()
        return variance < 150f     // Uniform color (white screen / splash)
    }

    // ── Output parsing ────────────────────────────────────────────────

    private fun parseOutput(scores: FloatArray, size: Int, threshold: Float): AiResult {
        return when (size) {
            2 -> {
                // 2-class: [safe, unsafe]
                val unsafe = scores[1]
                AiResult(
                    isUnsafe    = unsafe >= threshold,
                    unsafeScore = unsafe,
                    label       = if (unsafe >= threshold) "Unsafe (${(unsafe * 100).toInt()}%)" else "Safe"
                )
            }
            5 -> {
                // 5-class: [drawings, hentai, neutral, porn, sexy]
                val sexy    = scores[4]
                val sexyContrib = when {
                    sexy > 0.35f -> sexy * 1.5f
                    sexy > 0.20f -> sexy * 0.6f
                    else         -> sexy * 0.1f
                }
                val unsafe = scores[1] + scores[3] + sexyContrib
                val isUnsafe = unsafe >= threshold || (sexy >= 0.45f)
                AiResult(
                    isUnsafe    = isUnsafe,
                    unsafeScore = maxOf(unsafe, if (sexy >= 0.45f) sexy else 0f),
                    label       = if (isUnsafe) "Unsafe (${(unsafe * 100).toInt()}%)" else "Safe"
                )
            }
            else -> {
                val unsafe = scores.last()
                AiResult(
                    isUnsafe    = unsafe >= threshold,
                    unsafeScore = unsafe,
                    label       = "Unsafe (${(unsafe * 100).toInt()}%)"
                )
            }
        }
    }

    // ── Buffer helpers ────────────────────────────────────────────────

    private fun bitmapToBuffer(src: Bitmap): ByteBuffer {
        val bmp = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true)
        inputBuf.rewind()
        bmp.getPixels(pixelBuf, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (bmp !== src) bmp.recycle()
        for (p in pixelBuf) {
            inputBuf.putFloat(((p shr 16) and 0xFF) / 255f)
            inputBuf.putFloat(((p shr 8) and 0xFF) / 255f)
            inputBuf.putFloat((p and 0xFF) / 255f)
        }
        inputBuf.rewind()
        return inputBuf
    }

    private fun mapFile(f: File): MappedByteBuffer =
        FileInputStream(f).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
}
