package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences
) {
    companion object {
        const val MODEL_FILE = "guardian_model.tflite"
        const val INPUT_SIZE = 224
    }

    private data class UnsafeScores(
        val unsafe: Float = 0f,
        val porn: Float = 0f,
        val hentai: Float = 0f,
        val sexy: Float = 0f
    )

    private var interpreter: Interpreter? = null
    private var outputClasses: Int = 2
    private val inferenceLock = Mutex()

    private val resizer = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    fun isModelAvailable(): Boolean =
        File(context.filesDir, MODEL_FILE).exists() || modelExistsInAssets()

    private fun modelExistsInAssets(): Boolean = runCatching {
        context.assets.open(MODEL_FILE).use { true }
    }.getOrDefault(false)

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (interpreter != null) return true

        val externalFile = File(context.filesDir, MODEL_FILE)
        val buffer: ByteBuffer? = when {
            externalFile.exists() -> runCatching {
                val bytes = externalFile.readBytes()
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    .also { it.put(bytes); it.rewind() }
            }.onFailure { Timber.e(it, "Failed to read model from filesDir") }.getOrNull()

            else -> runCatching {
                context.assets.open(MODEL_FILE).use { stream ->
                    val bytes = stream.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                        .also { it.put(bytes); it.rewind() }
                }
            }.onFailure { Timber.e(it, "Failed to read model from assets") }.getOrNull()
        }
        if (buffer == null) return false

        return runCatching {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val options = Interpreter.Options().apply { setNumThreads(threads) }
            val created = Interpreter(buffer, options)
            outputClasses = created.getOutputTensor(0).shape().last().coerceAtLeast(2)
            interpreter = created
            Timber.i("TFLite model loaded — output classes=$outputClasses")
            true
        }.onFailure { Timber.e(it, "Failed to create TFLite interpreter") }.getOrDefault(false)
    }

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        if (!ensureLoaded()) return false
        val threshold = prefs.aiThreshold.first().coerceIn(0.2f, 0.95f)

        return inferenceLock.withLock {
            runCatching {
                val variants = buildBitmapVariants(bitmap)
                try {
                    var strongest = UnsafeScores()
                    for (candidate in variants) {
                        val scores = runInference(candidate)
                        strongest = strongest.merge(scores)
                        if (isUnsafe(scores, threshold)) return@runCatching true
                    }
                    isUnsafe(strongest, threshold)
                } finally {
                    variants.forEach { if (it !== bitmap) it.recycle() }
                }
            }.onFailure { Timber.e(it, "TFLite inference failed") }
                .getOrDefault(false)
        }
    }

    private fun runInference(bitmap: Bitmap): UnsafeScores {
        val current = interpreter ?: return UnsafeScores()
        val tensor = TensorImage(org.tensorflow.lite.DataType.FLOAT32).apply { load(bitmap) }
        val processed = resizer.process(tensor)

        return when (outputClasses) {
            2 -> {
                val out = Array(1) { FloatArray(2) }
                current.run(processed.buffer, out)
                UnsafeScores(unsafe = out[0][1].coerceAtLeast(0f))
            }

            5 -> {
                val out = Array(1) { FloatArray(5) }
                current.run(processed.buffer, out)
                UnsafeScores(
                    unsafe = (out[0][1] + out[0][3] + out[0][4]).coerceAtLeast(0f),
                    porn = out[0][3].coerceAtLeast(0f),
                    hentai = out[0][1].coerceAtLeast(0f),
                    sexy = out[0][4].coerceAtLeast(0f)
                )
            }

            else -> {
                val out = Array(1) { FloatArray(outputClasses) }
                current.run(processed.buffer, out)
                UnsafeScores(unsafe = out[0].last().coerceAtLeast(0f))
            }
        }
    }

    private fun isUnsafe(scores: UnsafeScores, threshold: Float): Boolean {
        val strongUnsafeThreshold = threshold
        val combinedThreshold = (threshold * 0.82f).coerceIn(0.35f, 0.9f)
        val sexyThreshold = (threshold * 0.55f).coerceIn(0.24f, 0.48f)

        return scores.porn >= strongUnsafeThreshold ||
            scores.hentai >= strongUnsafeThreshold ||
            scores.sexy >= sexyThreshold ||
            scores.unsafe >= combinedThreshold
    }

    private fun buildBitmapVariants(source: Bitmap): List<Bitmap> {
        val variants = mutableListOf<Bitmap>()
        variants += source

        val width = source.width
        val height = source.height
        if (width < 96 || height < 96) return variants

        val square = min(width, height)
        val squareX = ((width - square) / 2).coerceAtLeast(0)
        val squareY = ((height - square) / 2).coerceAtLeast(0)
        variants += Bitmap.createBitmap(source, squareX, squareY, square, square)

        val topHeight = (height * 0.72f).toInt().coerceIn(96, height)
        variants += Bitmap.createBitmap(source, 0, 0, width, topHeight)

        val startY = (height * 0.18f).toInt().coerceIn(0, height - 96)
        val lowerHeight = (height - startY).coerceAtLeast(96)
        variants += Bitmap.createBitmap(source, 0, startY, width, lowerHeight)

        return variants
    }

    private fun UnsafeScores.merge(other: UnsafeScores): UnsafeScores = UnsafeScores(
        unsafe = maxOf(unsafe, other.unsafe),
        porn = maxOf(porn, other.porn),
        hentai = maxOf(hentai, other.hentai),
        sexy = maxOf(sexy, other.sexy)
    )

    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
    }
}
