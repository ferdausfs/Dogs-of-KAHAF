package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
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
        private const val INPUT_SIZE = 192
        private const val TAG = "Guardian_AI"

        // FIX: Named constants instead of magic numbers
        private const val HARD_PORN_THRESHOLD   = 0.25f
        private const val HARD_HENTAI_THRESHOLD = 0.30f
        private const val HARD_SEXY_THRESHOLD   = 0.45f

        // FIX: Named sexy weighting constants
        private const val SEXY_HIGH_WEIGHT  = 1.5f
        private const val SEXY_MED_WEIGHT   = 0.6f
        private const val SEXY_LOW_WEIGHT   = 0.1f
        private const val SEXY_HIGH_CUTOFF  = 0.30f
        private const val SEXY_MED_CUTOFF   = 0.15f

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
        val label: String
    )

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var nnapiDelegate: NnApiDelegate? = null
    @Volatile private var loaded = false
    @Volatile private var outputSize = 0

    fun isLoaded(): Boolean = loaded

    fun load(): Boolean {
        // FIX: Cleanup old resources before loading new ones
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
                        Timber.d("$TAG NNAPI delegate enabled")
                    } catch (e: Throwable) {
                        Timber.w(e, "$TAG NNAPI not available — CPU+XNNPACK fallback")
                    }
                }
            }

            synchronized(this) {
                interpreter = Interpreter(buf, options)
                outputSize  = interpreter!!.getOutputTensor(0).shape()[1]
                loaded      = true
            }

            Timber.d("$TAG loaded — outputSize=$outputSize (${getModelType()})")
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
            loaded     = false
            outputSize = 0
        }
        Timber.d("$TAG unloaded")
    }

    fun reload(): Boolean {
        unload()
        return load()
    }

    fun getModelType(): String = when (outputSize) {
        2    -> "2-class [safe, unsafe]"
        5    -> "5-class [drawings, hentai, neutral, porn, sexy]"
        else -> "$outputSize-class (unknown)"
    }

    fun classify(bitmap: Bitmap, threshold: Float = 0.30f): AiResult {
        // Pre-processing outside lock — thread-safe (local variables)
        val input = bitmapToBuffer(bitmap)

        // FIX: All checks inside synchronized block — prevents race condition
        // where unload() is called between the loaded check and interp.run()
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
                val result = parseOutput(output[0], capturedSize, threshold)
                Timber.d("$TAG classify ${dt}ms — ${result.label}")
                result
            } catch (e: Exception) {
                Timber.e(e, "$TAG classify error")
                AiResult(false, 0f, "Error: ${e.message}")
            }
        }
    }

    fun shouldSkipFrame(bitmap: Bitmap): Boolean {
        val sample = Bitmap.createScaledBitmap(bitmap, 24, 24, false)
        val pixels = IntArray(24 * 24)
        sample.getPixels(pixels, 0, 24, 0, 0, 24, 24)
        if (sample !== bitmap) sample.recycle()

        var brightness = 0L
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var sumR2 = 0L; var sumG2 = 0L; var sumB2 = 0L

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8)  and 0xFF
            val b =  p         and 0xFF
            brightness += (r + g + b)
            sumR += r;  sumG += g;  sumB += b
            sumR2 += r * r; sumG2 += g * g; sumB2 += b * b
        }
        val n   = pixels.size
        val avg = brightness / (n * 3)
        if (avg < 12) return true  // nearly black screen

        val varR = (sumR2.toDouble() / n) - (sumR.toDouble() / n).let { it * it }
        val varG = (sumG2.toDouble() / n) - (sumG.toDouble() / n).let { it * it }
        val varB = (sumB2.toDouble() / n) - (sumB.toDouble() / n).let { it * it }
        return (varR + varG + varB).toFloat() < 120f  // uniform/flat screen
    }

    // ── Output parsing ─────────────────────────────────────────────────

    private fun parseOutput(scores: FloatArray, size: Int, threshold: Float): AiResult {
        return when (size) {
            2 -> {
                val unsafe = scores[1]
                AiResult(
                    isUnsafe    = unsafe >= threshold,
                    unsafeScore = unsafe,
                    label       = if (unsafe >= threshold)
                        "Unsafe (${(unsafe * 100).toInt()}%)"
                    else
                        "Safe (${((1 - unsafe) * 100).toInt()}%)"
                )
            }
            5 -> {
                val hentai = scores[1]
                val porn   = scores[3]
                val sexy   = scores[4]

                // Hard early-exit
                if (porn   >= HARD_PORN_THRESHOLD   ||
                    hentai >= HARD_HENTAI_THRESHOLD ||
                    sexy   >= HARD_SEXY_THRESHOLD) {

                    val dominant = when {
                        porn   >= HARD_PORN_THRESHOLD   -> "porn"
                        hentai >= HARD_HENTAI_THRESHOLD -> "hentai"
                        else                            -> "sexy"
                    }
                    val score = maxOf(porn, hentai, sexy)
                    return AiResult(
                        isUnsafe    = true,
                        unsafeScore = score,
                        label       = "NSFW: $dominant (${(score * 100).toInt()}%)"
                    )
                }

                // FIX: Named constants instead of magic numbers
                val sexyContrib = when {
                    sexy > SEXY_HIGH_CUTOFF -> sexy * SEXY_HIGH_WEIGHT
                    sexy > SEXY_MED_CUTOFF  -> sexy * SEXY_MED_WEIGHT
                    else                    -> sexy * SEXY_LOW_WEIGHT
                }
                val unsafeScore = (hentai + porn + sexyContrib).coerceIn(0f, 1f)
                val isUnsafe    = unsafeScore >= threshold

                val labels   = listOf("drawings", "hentai", "neutral", "porn", "sexy")
                val maxIdx   = scores.indices.maxByOrNull { scores[it] } ?: 2
                val dominant = labels[maxIdx]

                AiResult(
                    isUnsafe    = isUnsafe,
                    unsafeScore = unsafeScore,
                    label       = if (isUnsafe)
                        "NSFW: $dominant (${(unsafeScore * 100).toInt()}%)"
                    else
                        "Safe: $dominant"
                )
            }
            else -> {
                val unsafe = scores.last()
                AiResult(
                    isUnsafe    = unsafe >= threshold,
                    unsafeScore = unsafe,
                    label       = "Score: ${(unsafe * 100).toInt()}%"
                )
            }
        }
    }

    // ── Buffer helpers ─────────────────────────────────────────────────

    private fun bitmapToBuffer(src: Bitmap): ByteBuffer {
        val bmp = if (src.width != INPUT_SIZE || src.height != INPUT_SIZE)
            Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, false)
        else src

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bmp.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (bmp !== src) bmp.recycle()

        val buf = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .apply { order(ByteOrder.nativeOrder()) }

        val inv255 = 1f / 255f
        for (p in pixels) {
            buf.putFloat(((p shr 16) and 0xFF) * inv255) // R
            buf.putFloat(((p shr 8)  and 0xFF) * inv255) // G
            buf.putFloat(( p         and 0xFF) * inv255) // B
            // Alpha channel intentionally skipped — model expects RGB only
        }
        buf.rewind()
        return buf
    }

    // FIX: FileInputStream intentionally not closed here —
    // MappedByteBuffer maintains its own file mapping reference.
    // Closing the stream/channel invalidates the mapping on some JVMs.
    @Suppress("unused")
    private fun mapFile(f: File): MappedByteBuffer {
        val fis     = FileInputStream(f)
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    }
}