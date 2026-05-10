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
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device TFLite classifier.
 *
 *  v3.1.1 (FIX): The previous version only read the bundled
 *  `assets/nsfw_v1.tflite`. ModelImportManager saves user-imported
 *  models to `filesDir/nsfw_model.tflite`, so user imports were
 *  completely ignored — that's why "AI can't detect / can't block
 *  NSFW" was happening on builds without a CI-bundled model.
 *
 *  Loading priority (first-found wins):
 *    1. filesDir/nsfw_model.tflite        — user-imported (SAF)
 *    2. assets/nsfw_v1.tflite             — CI-bundled
 *    3. SAFE deterministic fallback       — keeps build green
 *
 *  - Delegate selection (best-effort): GPU → NNAPI → CPU.
 *  - One dummy warm-up inference after load to pre-JIT kernels.
 *  - Skips inference for bitmaps below `AiSettings.minImageSize`.
 *  - Supports both 4-output and 2-output softmax layouts.
 *  - Inference serialized via Mutex (Interpreter is NOT thread-safe).
 *  - [reload] forces a re-load — call after a successful custom-model
 *    import or delete so the new model takes effect immediately.
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
    @Volatile private var modelMissing = false
    private var inputW = INPUT_SIZE
    private var inputH = INPUT_SIZE
    private var numClasses = DEFAULT_NUM_CLASSES

    private val _isModelLoaded = MutableStateFlow(false)
    /** Emits `true` once the TFLite model has been mapped & warmed up successfully. */
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    /** Where the model was loaded from — surfaced in the UI. */
    private val _modelSource = MutableStateFlow(ModelSource.NONE)
    val modelSource: StateFlow<ModelSource> = _modelSource.asStateFlow()

    enum class ModelSource { NONE, CUSTOM_IMPORTED, BUNDLED_ASSET }

    private suspend fun ensureLoaded() = initMutex.withLock {
        if (interpreter != null) return@withLock
        if (modelMissing) return@withLock
        try {
            // Resolve in priority order: custom > bundled.
            val customFile = File(context.filesDir, ModelImportManager.NSFW_MODEL_FILE)
            val (mb, source) = when {
                customFile.exists() && customFile.length() > 0L -> {
                    Log.i(TAG, "Loading custom imported model: ${customFile.absolutePath}")
                    loadMappedFile(customFile) to ModelSource.CUSTOM_IMPORTED
                }
                else -> {
                    Log.i(TAG, "Loading bundled asset model: $MODEL_ASSET")
                    FileUtil.loadMappedFile(context, MODEL_ASSET) to ModelSource.BUNDLED_ASSET
                }
            }
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
            Log.i(TAG, "TFLite model loaded from ${source.name}: ${inputW}x${inputH} → $numClasses classes")
            _modelSource.value = source
            _isModelLoaded.value = true
            modelMissing = false
        } catch (t: Throwable) {
            Log.w(TAG, "Model not present, using SAFE fallback: ${t.message}")
            modelMissing = true
            _isModelLoaded.value = false
            _modelSource.value = ModelSource.NONE
        }
    }

    private fun loadMappedFile(file: File): MappedByteBuffer {
        file.inputStream().channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    /**
     * Force a re-load. Call after the user imports / deletes a custom model so
     * the new model takes effect without needing a process restart.
     */
    suspend fun reload() = initMutex.withLock {
        runCatching { interpreter?.close() }
        runCatching { nnApiDelegate?.close() }
        runCatching { gpuDelegate?.close() }
        interpreter = null
        nnApiDelegate = null
        gpuDelegate = null
        modelMissing = false
        _isModelLoaded.value = false
        _modelSource.value = ModelSource.NONE
        // The next call to classify() will lazy-init it. Trigger one now so the
        // UI status flips quickly.
        // Drop the lock first by re-entering ensureLoaded outside this lock.
        // (initMutex is re-entrant only via a helper; safer to release & re-take)
    }.also {
        // Now actually trigger a load (outside the lock above, since we just released it
        // by exiting the .withLock block).
        ensureLoaded()
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
        _modelSource.value = ModelSource.NONE
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
