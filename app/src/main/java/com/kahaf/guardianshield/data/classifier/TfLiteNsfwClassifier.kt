package com.kahaf.guardianshield.data.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kahaf.guardianshield.domain.model.NsfwLabel
import com.kahaf.guardianshield.domain.model.NsfwResult
import com.kahaf.guardianshield.domain.repository.NsfwClassifier
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device TFLite classifier.
 *
 *  - Loads `assets/nsfw_v1.tflite` (lazy).
 *  - Delegate selection (best-effort): GPU → NNAPI → CPU.
 *  - One dummy warm-up inference after load to pre-JIT kernels.
 *  - Skips inference for bitmaps below `AiSettings.minImageSize`.
 *  - Supports both 4-output (SAFE/NATURAL/SUGGESTIVE/EXPLICIT) and 2-output
 *    (SFW/NSFW) softmax layouts. The output dimension is read from the model
 *    at load-time. For 2-class models, the NSFW score is mapped to severity
 *    tiers as documented in `assets/nsfw_v1.tflite.README`.
 *  - When `AiSettings.modelInputNormalized` is true, inputs are scaled to the
 *    [0,1] float range; otherwise they are passed as raw [0,255] floats.
 *  - Inference runs on Dispatchers.Default; serialized via a Mutex because
 *    Interpreter is NOT thread-safe.
 *
 * If the model asset is missing the constructor does NOT crash — `classify`
 * returns a deterministic SAFE result so the rest of the app still works.
 *
 * v3.0.0: now the default classifier (see RepositoryModule).
 */
@Singleton
class TfLiteNsfwClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : NsfwClassifier {

    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var gpuDelegate: GpuDelegate? = null
    private val initMutex = Mutex()
    private val inferMutex = Mutex()
    private var modelMissing = false
    private var inputW = INPUT_SIZE
    private var inputH = INPUT_SIZE
    private var numClasses = DEFAULT_NUM_CLASSES

    private val _isModelLoaded = MutableStateFlow(false)
    /** Emits `true` once the TFLite model has been mapped & warmed up successfully. */
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private suspend fun ensureLoaded() = initMutex.withLock {
        if (interpreter != null || modelMissing) return@withLock
        try {
            val mb = FileUtil.loadMappedFile(context, MODEL_ASSET)
            val opts = Interpreter.Options().apply {
                setNumThreads(2)
                // Best-effort delegate chain: GPU → NNAPI → CPU.
                val gpuOk = runCatching {
                    val d = GpuDelegate()
                    addDelegate(d)
                    gpuDelegate = d
                    Log.i(TAG, "Using GPU delegate")
                }.isSuccess
                if (!gpuOk) {
                    runCatching {
                        nnApiDelegate = NnApiDelegate()
                        addDelegate(nnApiDelegate!!)
                        Log.i(TAG, "Using NNAPI delegate")
                    }.onFailure { Log.w(TAG, "NNAPI unavailable, CPU fallback: ${it.message}") }
                }
            }
            interpreter = Interpreter(mb, opts)
            interpreter?.getInputTensor(0)?.shape()?.let { shape ->
                if (shape.size == 4) {
                    inputH = shape[1].coerceAtLeast(1)
                    inputW = shape[2].coerceAtLeast(1)
                }
            }
            interpreter?.getOutputTensor(0)?.shape()?.let { shape ->
                // [1, N] — N classes.
                if (shape.size >= 2) numClasses = shape[1].coerceIn(2, 4)
            }
            // Warm-up: single dummy inference to pre-JIT kernels and trigger any
            // delegate compilation costs *before* the first real frame.
            runCatching {
                val dummy = Array(1) { Array(inputH) { Array(inputW) { FloatArray(3) } } }
                val dummyOut = Array(1) { FloatArray(numClasses) }
                interpreter?.run(dummy, dummyOut)
            }.onFailure { Log.w(TAG, "Warm-up inference skipped: ${it.message}") }
            Log.i(TAG, "TFLite model loaded: ${inputW}x${inputH} → $numClasses classes")
            _isModelLoaded.value = true
        } catch (t: Throwable) {
            Log.w(TAG, "Model not present, using SAFE fallback: ${t.message}")
            modelMissing = true
            _isModelLoaded.value = false
        }
    }

    override suspend fun classify(bitmap: Bitmap): NsfwResult = withContext(Dispatchers.Default) {
        ensureLoaded()
        val itp = interpreter
        if (itp == null || modelMissing || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext safeResult()
        }
        // Skip frames smaller than the user-configured minimum dimension.
        val ai = runCatching { settingsRepository.aiSettings.first() }.getOrNull()
        val minSize = ai?.minImageSize ?: 120
        if (bitmap.width < minSize || bitmap.height < minSize) {
            return@withContext safeResult()
        }
        try {
            val img = TensorImage.fromBitmap(
                if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
                else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            )
            val builder = ImageProcessor.Builder()
                .add(ResizeOp(inputH, inputW, ResizeOp.ResizeMethod.BILINEAR))
            if (ai?.modelInputNormalized == true) {
                // Scale [0,255] → [0,1] for models trained on normalized inputs.
                builder.add(NormalizeOp(0f, 255f))
            }
            val processor = builder.build()
            val processed = processor.process(img)
            val output = Array(1) { FloatArray(numClasses) }
            inferMutex.withLock { itp.run(processed.buffer, output) }
            val scores = output[0]
            return@withContext when (numClasses) {
                2 -> map2ClassOutput(scores)
                else -> map4ClassOutput(scores)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Inference failed; returning SAFE", t)
            safeResult()
        }
    }

    /** 4-output softmax: index 0..3 → SAFE, NATURAL, SUGGESTIVE, EXPLICIT. */
    private fun map4ClassOutput(scores: FloatArray): NsfwResult {
        val (idx, conf) = scores.withIndex().maxByOrNull { it.value }
            ?.let { it.index to it.value } ?: (0 to 1f)
        val label = NsfwLabel.values().getOrElse(idx) { NsfwLabel.SAFE }
        return NsfwResult(
            label = label,
            confidence = conf,
            scores = mapOf(
                NsfwLabel.SAFE to (scores.getOrNull(0) ?: 0f),
                NsfwLabel.NATURAL to (scores.getOrNull(1) ?: 0f),
                NsfwLabel.SUGGESTIVE to (scores.getOrNull(2) ?: 0f),
                NsfwLabel.EXPLICIT to (scores.getOrNull(3) ?: 0f)
            )
        )
    }

    /**
     * 2-output softmax: index 0 = SFW, index 1 = NSFW.
     * Severity mapping documented in nsfw_v1.tflite.README:
     *   nsfw < 0.40                 → SAFE
     *   0.40 ≤ nsfw < 0.60          → NATURAL
     *   0.60 ≤ nsfw < 0.80          → SUGGESTIVE
     *   nsfw ≥ 0.80                 → EXPLICIT
     */
    private fun map2ClassOutput(scores: FloatArray): NsfwResult {
        val sfw = scores.getOrNull(0) ?: 0f
        val nsfw = scores.getOrNull(1) ?: 0f
        val label = when {
            nsfw < 0.40f -> NsfwLabel.SAFE
            nsfw < 0.60f -> NsfwLabel.NATURAL
            nsfw < 0.80f -> NsfwLabel.SUGGESTIVE
            else -> NsfwLabel.EXPLICIT
        }
        return NsfwResult(
            label = label,
            confidence = if (label == NsfwLabel.SAFE) sfw else nsfw,
            scores = mapOf(
                NsfwLabel.SAFE to sfw,
                NsfwLabel.NATURAL to 0f,
                NsfwLabel.SUGGESTIVE to nsfw * 0.3f,
                NsfwLabel.EXPLICIT to nsfw
            )
        )
    }

    override fun close() {
        runCatching { interpreter?.close() }
        runCatching { nnApiDelegate?.close() }
        runCatching { gpuDelegate?.close() }
        interpreter = null
        nnApiDelegate = null
        gpuDelegate = null
        _isModelLoaded.value = false
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
        private const val DEFAULT_NUM_CLASSES = 4
    }
}
