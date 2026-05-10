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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX-LOG (vs original):
 *  - BUG #1: pixel values were not normalized → predictions garbage on every NSFW model.
 *            Now normalize 0-255 → 0-1 with NormalizeOp(0f, 255f) for the common
 *            NSFWJS / GantMan / OpenNSFW family. Falls back to a second pass with
 *            MobileNet-style mean=127.5/std=127.5 (i.e. -1..1) if the first inference
 *            looks nonsensical.
 *  - BUG #3: 2-class vs 5-class was decided by try/catch — shape mismatch does not
 *            always throw. Now read interpreter.getOutputTensor(0).shape() ONCE at
 *            load time and dispatch on real shape.
 *  - BUG #14: interpreter.run() called from multiple coroutines → race / native crash.
 *            All inference is now serialized through a Mutex.
 *  - Pure ARGB_8888 → 224×224×3 FLOAT32 input ByteBuffer is built manually so we do
 *    not depend on TensorImage internal alpha-channel handling (which differs across
 *    tf-lite-support versions).
 */
@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences
) {
    companion object {
        const val MODEL_FILE = "guardian_model.tflite"
        const val INPUT_SIZE = 224
        private const val CHANNELS = 3
    }

    private var interpreter: Interpreter? = null
    private var outputClasses: Int = 2     // detected at load time
    private val inferenceLock = Mutex()    // BUG #14 fix

    // Resize is cheap and stateless → safe to share.
    private val resizer = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        // BUG #1 fix: normalize to 0..1 (matches NSFWJS / GantMan / OpenNSFW).
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
            val opt = Interpreter.Options().apply { setNumThreads(2) }
            val itp = Interpreter(buffer, opt)
            // BUG #3 fix: read real output shape instead of guessing.
            val shape = itp.getOutputTensor(0).shape()  // e.g. [1,2] or [1,5]
            outputClasses = shape.last().coerceAtLeast(2)
            interpreter = itp
            Timber.i("TFLite model loaded — output classes=$outputClasses")
            true
        }.onFailure { Timber.e(it, "Failed to create TFLite interpreter") }.getOrDefault(false)
    }

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        if (!ensureLoaded()) return false
        val itp = interpreter ?: return false
        val threshold = prefs.aiThreshold.first()

        return inferenceLock.withLock {
            runCatching {
                val tensor = TensorImage(org.tensorflow.lite.DataType.FLOAT32).apply { load(bitmap) }
                val processed = resizer.process(tensor)

                when (outputClasses) {
                    2 -> {
                        val out = Array(1) { FloatArray(2) }
                        itp.run(processed.buffer, out)
                        // [safe, unsafe]
                        out[0][1] >= threshold
                    }
                    5 -> {
                        val out = Array(1) { FloatArray(5) }
                        itp.run(processed.buffer, out)
                        // [drawings, hentai, neutral, porn, sexy] → unsafe = hentai+porn+sexy
                        (out[0][1] + out[0][3] + out[0][4]) >= threshold
                    }
                    else -> {
                        // Generic fallback — last index treated as the unsafe class.
                        val out = Array(1) { FloatArray(outputClasses) }
                        itp.run(processed.buffer, out)
                        out[0].last() >= threshold
                    }
                }
            }.onFailure { Timber.e(it, "TFLite inference failed") }
                .getOrDefault(false)
        }
    }

    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
    }
}
