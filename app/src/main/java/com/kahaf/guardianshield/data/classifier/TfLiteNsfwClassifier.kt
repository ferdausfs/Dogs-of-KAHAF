package com.kahaf.guardianshield.data.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kahaf.guardianshield.domain.model.NsfwLabel
import com.kahaf.guardianshield.domain.model.NsfwResult
import com.kahaf.guardianshield.domain.repository.NsfwClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device TFLite classifier.
 *  - Loads `assets/nsfw_v1.tflite` (lazy).
 *  - Uses NNAPI when the device claims support; falls back to CPU on any error.
 *  - 4-output softmax: [SAFE, NATURAL, SUGGESTIVE, EXPLICIT].
 *  - Inference runs on Dispatchers.Default; serialized via a Mutex because
 *    Interpreter is NOT thread-safe.
 *
 * If the model asset is missing the constructor does NOT crash — `classify`
 * will return a deterministic SAFE result so the rest of the app still works.
 */
@Singleton
class TfLiteNsfwClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) : NsfwClassifier {

    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private val initMutex = Mutex()
    private val inferMutex = Mutex()
    private var modelMissing = false
    private var inputW = INPUT_SIZE
    private var inputH = INPUT_SIZE

    private suspend fun ensureLoaded() = initMutex.withLock {
        if (interpreter != null || modelMissing) return@withLock
        try {
            val mb = FileUtil.loadMappedFile(context, MODEL_ASSET)
            val opts = Interpreter.Options().apply {
                setNumThreads(2)
                runCatching {
                    nnApiDelegate = NnApiDelegate()
                    addDelegate(nnApiDelegate!!)
                }.onFailure { Log.w(TAG, "NNAPI unavailable, CPU fallback: ${it.message}") }
            }
            interpreter = Interpreter(mb, opts)
            interpreter?.getInputTensor(0)?.shape()?.let { shape ->
                if (shape.size == 4) {
                    inputH = shape[1].coerceAtLeast(1)
                    inputW = shape[2].coerceAtLeast(1)
                }
            }
            Log.i(TAG, "TFLite model loaded: ${inputW}x${inputH}")
        } catch (t: Throwable) {
            Log.w(TAG, "Model not present, using SAFE fallback: ${t.message}")
            modelMissing = true
        }
    }

    override suspend fun classify(bitmap: Bitmap): NsfwResult = withContext(Dispatchers.Default) {
        ensureLoaded()
        val itp = interpreter
        if (itp == null || modelMissing || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext safeResult()
        }
        try {
            val img = TensorImage.fromBitmap(
                if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
                else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            )
            val processor = ImageProcessor.Builder()
                .add(ResizeOp(inputH, inputW, ResizeOp.ResizeMethod.BILINEAR))
                .build()
            val processed = processor.process(img)
            val output = Array(1) { FloatArray(NUM_CLASSES) }
            inferMutex.withLock { itp.run(processed.buffer, output) }
            val scores = output[0]
            val (idx, conf) = scores.withIndex().maxByOrNull { it.value }
                ?.let { it.index to it.value } ?: (0 to 1f)
            val label = NsfwLabel.values().getOrElse(idx) { NsfwLabel.SAFE }
            NsfwResult(
                label = label,
                confidence = conf,
                scores = mapOf(
                    NsfwLabel.SAFE to (scores.getOrNull(0) ?: 0f),
                    NsfwLabel.NATURAL to (scores.getOrNull(1) ?: 0f),
                    NsfwLabel.SUGGESTIVE to (scores.getOrNull(2) ?: 0f),
                    NsfwLabel.EXPLICIT to (scores.getOrNull(3) ?: 0f)
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Inference failed; returning SAFE", t)
            safeResult()
        }
    }

    override fun close() {
        runCatching { interpreter?.close() }
        runCatching { nnApiDelegate?.close() }
        interpreter = null
        nnApiDelegate = null
    }

    private fun safeResult() = NsfwResult(
        label = NsfwLabel.SAFE,
        confidence = 1f,
        scores = mapOf(
            NsfwLabel.SAFE to 1f,
            NsfwLabel.NATURAL to 0f,
            NsfwLabel.SUGGESTIVE to 0f,
            NsfwLabel.EXPLICIT to 0f
        )
    )

    companion object {
        private const val TAG = "TfLiteNsfwClassifier"
        private const val MODEL_ASSET = "nsfw_v1.tflite"
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 4
    }
}
