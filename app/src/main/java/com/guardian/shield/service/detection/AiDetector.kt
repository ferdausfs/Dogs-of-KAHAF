package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_FEMALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_MALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.domain.model.ClassificationResult
import com.guardian.shield.domain.model.ContentTier
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * v14 (2.1.4) STABILITY PATCH 4:
 *  • CRITICAL FIX: startPrefsCache() now uses the process-lifetime
 *    Scopes.appDefault scope instead of the caller's scope. Previously
 *    the accessibility service passed its own scope — when the service
 *    died, the scope cancelled and the preference collectors stopped
 *    forever. The `prefsCacheStarted` flag then blocked any new service
 *    from re-registering, so cachedAiEnabled / cachedUserGender /
 *    cachedSensitivity / cachedAiThreshold became permanently stale
 *    (manifested as "I toggled AI off/on but nothing changed").
 *  • DEFENSIVE: the scope parameter is now ignored — kept only for
 *    source compatibility with existing callers (no API break).
 *
 * v13 (2.1.3):
 *  • outputClasses no longer `coerceAtLeast(2)`. 1-output sigmoid models
 *    now have their own branch in runLegacyInference().
 *  • outputClasses bound to a sane range [1, 32].
 *
 * v11 (2.1.1):
 *  • close() split into closeAsync()/closeSuspend()/close() (no main-thread runBlocking).
 *  • outputClasses @Volatile.
 *  • GPU delegate construction protected with try/catch (Adreno UnsatisfiedLinkError).
 */
@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences
) {
    companion object {
        const val MODEL_FILE = "guardian_model.tflite"
        const val NSFW_MODEL_FILE   = "nsfw_model.tflite"
        const val GENDER_MODEL_FILE = "gender_model.tflite"
        const val INPUT_SIZE = 224

        const val NSFW_GATE_THRESHOLD: Float = GuardianConstants.NSFW_GATE_THRESHOLD
        const val GENDER_CONFIDENCE_THRESHOLD: Float = GuardianConstants.GENDER_CONFIDENCE_THRESHOLD
    }

    private data class UnsafeScores(
        val unsafe: Float = 0f,
        val porn: Float = 0f,
        val hentai: Float = 0f,
        val sexy: Float = 0f
    )

    data class GenderNsfwResult(
        val isNsfw: Boolean,
        val maleProb: Float,
        val femaleProb: Float,
        val nsfwProb: Float
    )

    @Volatile private var interpreter: Interpreter? = null
    /** v13: real output dim from the model — no longer coerced upward. */
    @Volatile private var outputClasses: Int = 2

    @Volatile private var nsfwInterpreter: Interpreter? = null
    @Volatile private var genderInterpreter: Interpreter? = null

    @Volatile private var nsfwLoadAttempted   = false
    @Volatile private var genderLoadAttempted = false
    @Volatile private var nsfwLoadFailed   = false
    @Volatile private var genderLoadFailed = false

    @Volatile private var legacyGpuDelegate: GpuDelegate? = null
    @Volatile private var nsfwGpuDelegate:   GpuDelegate? = null
    @Volatile private var genderGpuDelegate: GpuDelegate? = null

    private val inferenceLock = Mutex()
    private val inferenceInFlight = AtomicBoolean(false)

    private val resizer = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    @Volatile var cachedAiEnabled: Boolean = false
        private set
    @Volatile var cachedUserGender: String = GENDER_NONE
        private set
    @Volatile var cachedSensitivity: String = GuardianConstants.SENSITIVITY_BALANCED
        private set
    @Volatile var cachedAiThreshold: Float = GuardianConstants.DEFAULT_AI_THRESHOLD
        private set
    @Volatile private var prefsCacheStarted = false

    /**
     * v14 (2.1.4): scope parameter is now ignored — kept for binary/source
     * compatibility. Collectors are launched on the process-lifetime
     * [com.guardian.shield.util.Scopes.appDefault] scope so they survive
     * accessibility-service teardown and restart.
     */
    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun startPrefsCache(scope: CoroutineScope) {
        if (prefsCacheStarted) return
        prefsCacheStarted = true
        val singleton = com.guardian.shield.util.Scopes.appDefault
        singleton.launch {
            runCatching {
                prefs.aiDetectionEnabled.collect { cachedAiEnabled = it }
            }.onFailure { Timber.w(it, "aiDetectionEnabled collector failed") }
        }
        singleton.launch {
            runCatching {
                prefs.userGender.collect { cachedUserGender = it }
            }.onFailure { Timber.w(it, "userGender collector failed") }
        }
        singleton.launch {
            runCatching {
                prefs.sensitivity.collect { cachedSensitivity = it }
            }.onFailure { Timber.w(it, "sensitivity collector failed") }
        }
        singleton.launch {
            runCatching {
                prefs.aiThreshold.collect { cachedAiThreshold = it }
            }.onFailure { Timber.w(it, "aiThreshold collector failed") }
        }
    }

    fun isModelAvailable(): Boolean =
        File(context.filesDir, MODEL_FILE).exists() ||
            modelExistsInAssets(MODEL_FILE) ||
            isNsfwModelAvailable()

    fun isNsfwModelAvailable(): Boolean =
        File(context.filesDir, NSFW_MODEL_FILE).exists() || modelExistsInAssets(NSFW_MODEL_FILE)

    fun isGenderModelAvailable(): Boolean =
        File(context.filesDir, GENDER_MODEL_FILE).exists() || modelExistsInAssets(GENDER_MODEL_FILE)

    private fun modelExistsInAssets(name: String): Boolean = runCatching {
        context.assets.open(name).use { true }
    }.getOrDefault(false)

    private data class BuiltOptions(val options: Interpreter.Options, val gpu: GpuDelegate?)

    private fun buildInterpreterOptions(label: String): BuiltOptions {
        val opts = Interpreter.Options()
        val gpu: GpuDelegate? = try {
            val compat = CompatibilityList()
            if (compat.isDelegateSupportedOnThisDevice) {
                val delegate = GpuDelegate()
                opts.addDelegate(delegate)
                Timber.i("TFLite[$label]: GPU delegate enabled")
                delegate
            } else {
                Timber.i("TFLite[$label]: GPU not supported — using CPU")
                null
            }
        } catch (t: Throwable) {
            Timber.w(t, "TFLite[$label]: GPU delegate unavailable, falling back to CPU")
            null
        }

        if (gpu == null) {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            opts.setNumThreads(threads)
        }
        return BuiltOptions(opts, gpu)
    }

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (interpreter != null) return true

        val buffer = readModelBuffer(MODEL_FILE) ?: return false
        return runCatching {
            val (options, gpu) = buildInterpreterOptions("legacy")
            val created = try {
                Interpreter(buffer, options)
            } catch (t: Throwable) {
                runCatching { gpu?.close() }
                Timber.w(t, "GPU interpreter failed for legacy — retrying on CPU")
                val cpuOpts = Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                }
                Interpreter(buffer, cpuOpts)
            }.also {
                if (gpu != null) legacyGpuDelegate = gpu
            }
            // v13: honour the model's actual output shape. Bound to [1, 32]
            // so a corrupt model can't OOM us.
            val rawOut = runCatching { created.getOutputTensor(0).shape().last() }
                .getOrDefault(2)
            outputClasses = rawOut.coerceIn(1, 32)
            interpreter = created
            Timber.i("TFLite (legacy) model loaded — output classes=$outputClasses (raw=$rawOut)")
            true
        }.onFailure { Timber.e(it, "Failed to create legacy TFLite interpreter") }
            .getOrDefault(false)
    }

    @Synchronized
    fun ensureGenderPipelineLoaded(): Boolean {
        loadNsfwIfNeeded()
        loadGenderIfNeeded()
        return nsfwInterpreter != null
    }

    private fun loadNsfwIfNeeded() {
        if (nsfwInterpreter != null || nsfwLoadAttempted) return
        nsfwLoadAttempted = true
        val buffer = readModelBuffer(NSFW_MODEL_FILE)
        if (buffer == null) {
            nsfwLoadFailed = true
            Timber.i("nsfw_model.tflite not found — opposite-gender NSFW filter disabled")
            return
        }
        runCatching {
            val (opts, gpu) = buildInterpreterOptions("nsfw")
            val created = try {
                Interpreter(buffer, opts)
            } catch (t: Throwable) {
                runCatching { gpu?.close() }
                Timber.w(t, "GPU interpreter failed for nsfw — retrying on CPU")
                val cpuOpts = Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                }
                Interpreter(buffer, cpuOpts)
            }.also {
                if (gpu != null) nsfwGpuDelegate = gpu
            }
            nsfwInterpreter = created
            Timber.i("nsfw_model.tflite loaded successfully")
        }.onFailure {
            nsfwLoadFailed = true
            nsfwInterpreter = null
            Timber.e(it, "Failed to create nsfw_model interpreter — feature disabled")
        }
    }

    private fun loadGenderIfNeeded() {
        if (genderInterpreter != null || genderLoadAttempted) return
        genderLoadAttempted = true
        val buffer = readModelBuffer(GENDER_MODEL_FILE)
        if (buffer == null) {
            genderLoadFailed = true
            Timber.i("gender_model.tflite not found — falling back to NSFW-only flow")
            return
        }
        runCatching {
            val (opts, gpu) = buildInterpreterOptions("gender")
            val created = try {
                Interpreter(buffer, opts)
            } catch (t: Throwable) {
                runCatching { gpu?.close() }
                Timber.w(t, "GPU interpreter failed for gender — retrying on CPU")
                val cpuOpts = Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                }
                Interpreter(buffer, cpuOpts)
            }.also {
                if (gpu != null) genderGpuDelegate = gpu
            }
            genderInterpreter = created
            Timber.i("gender_model.tflite loaded successfully")
        }.onFailure {
            genderLoadFailed = true
            genderInterpreter = null
            Timber.e(it, "Failed to create gender_model interpreter — falling back to NSFW-only")
        }
    }

    private fun readModelBuffer(name: String): ByteBuffer? {
        val externalFile = File(context.filesDir, name)
        if (externalFile.exists()) {
            val mapped = runCatching {
                FileInputStream(externalFile).use { fis ->
                    fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, externalFile.length())
                        .order(ByteOrder.nativeOrder())
                }
            }.onFailure { Timber.w(it, "MappedByteBuffer failed for $name, falling back to readBytes") }
                .getOrNull()
            if (mapped != null) return mapped

            return runCatching {
                val bytes = externalFile.readBytes()
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    .also { it.put(bytes); it.rewind() }
            }.onFailure { Timber.e(it, "Failed to read $name from filesDir") }.getOrNull()
        }

        if (modelExistsInAssets(name)) {
            return runCatching {
                context.assets.open(name).use { stream ->
                    val bytes = stream.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                        .also { it.put(bytes); it.rewind() }
                }
            }.onFailure { Timber.e(it, "Failed to read $name from assets") }.getOrNull()
        }
        return null
    }

    fun effectiveThresholdFor(packageName: String?): Float {
        val presetBase = GuardianConstants.thresholdForSensitivity(cachedSensitivity)
        val sliderDiverged =
            kotlin.math.abs(cachedAiThreshold - GuardianConstants.DEFAULT_AI_THRESHOLD) > 0.02f
        val base = if (sliderDiverged) cachedAiThreshold else presetBase

        val boosted = if (packageName != null &&
            com.guardian.shield.util.AppClassifier.isSafeHeavyImageApp(packageName)
        ) {
            base + GuardianConstants.HEAVY_IMAGE_APP_THRESHOLD_BOOST
        } else base

        return boosted.coerceIn(0.30f, 0.95f)
    }

    suspend fun classify(bitmap: Bitmap, packageName: String? = null): ClassificationResult {
        if (!ensureLoaded()) return ClassificationResult.SAFE
        if (bitmap.isRecycled) return ClassificationResult.SAFE
        val threshold = effectiveThresholdFor(packageName)

        return inferenceLock.withLock {
            withContext(Dispatchers.Default) {
                runCatching {
                    val variants = buildBitmapVariants(bitmap)
                    try {
                        val primary = runLegacyInference(variants[0])
                        val earlyExit = threshold * GuardianConstants.EARLY_EXIT_RATIO
                        if (primary.unsafe < earlyExit &&
                            primary.porn   < earlyExit &&
                            primary.hentai < earlyExit &&
                            primary.sexy   < earlyExit * 0.5f
                        ) {
                            return@runCatching ClassificationResult(
                                tier = ContentTier.SAFE,
                                pornScore = primary.porn,
                                hentaiScore = primary.hentai,
                                sexyScore = primary.sexy,
                                combinedUnsafeScore = primary.unsafe
                            )
                        }
                        var strongest = primary
                        if (classifyTier(primary, threshold) == ContentTier.EXPLICIT) {
                            return@runCatching toResult(primary, ContentTier.EXPLICIT)
                        }
                        for (i in 1 until variants.size) {
                            val s = runLegacyInference(variants[i])
                            strongest = strongest.merge(s)
                            if (classifyTier(s, threshold) == ContentTier.EXPLICIT) {
                                return@runCatching toResult(strongest, ContentTier.EXPLICIT)
                            }
                        }
                        toResult(strongest, classifyTier(strongest, threshold))
                    } finally {
                        variants.forEach { if (it !== bitmap) safeRecycle(it) }
                    }
                }.onFailure { Timber.e(it, "TFLite classify() failed") }
                    .getOrDefault(ClassificationResult.SAFE)
            }
        }
    }

    private fun classifyTier(s: UnsafeScores, threshold: Float): ContentTier {
        val porn   = s.porn
        val hentai = s.hentai
        val sexy   = s.sexy
        val combined = s.unsafe

        val explicitCombined = maxOf(threshold, GuardianConstants.COMBINED_EXPLICIT_THRESHOLD)
        if (porn   >= GuardianConstants.PORN_BLOCK_THRESHOLD)   return ContentTier.EXPLICIT
        if (hentai >= GuardianConstants.HENTAI_BLOCK_THRESHOLD) return ContentTier.EXPLICIT
        if (combined >= explicitCombined)                       return ContentTier.EXPLICIT

        if (sexy >= GuardianConstants.SEXY_LOG_THRESHOLD)       return ContentTier.SUGGESTIVE
        if (combined >= GuardianConstants.SUGGESTIVE_THRESHOLD) return ContentTier.SUGGESTIVE

        if (combined >= GuardianConstants.NATURAL_THRESHOLD)    return ContentTier.NATURAL

        return ContentTier.SAFE
    }

    private fun toResult(s: UnsafeScores, tier: ContentTier) = ClassificationResult(
        tier = tier,
        pornScore = s.porn,
        hentaiScore = s.hentai,
        sexyScore = s.sexy,
        combinedUnsafeScore = s.unsafe
    )

    suspend fun isUnsafe(bitmap: Bitmap): Boolean =
        classify(bitmap, packageName = null).tier.shouldBlock()

    suspend fun isOppositeGenderNsfw(bitmap: Bitmap?, userGender: String): Boolean {
        if (bitmap == null || bitmap.isRecycled) return false
        if (userGender == GENDER_NONE) return false
        if (userGender != GENDER_MALE && userGender != GENDER_FEMALE) return false

        if (!ensureGenderPipelineLoaded()) return false
        val nsfw = nsfwInterpreter ?: return false

        if (!inferenceInFlight.compareAndSet(false, true)) return false

        return try {
            inferenceLock.withLock {
                withContext(Dispatchers.Default) {
                    runOppositeGenderInference(bitmap, nsfw, userGender)
                }
            }
        } catch (oom: OutOfMemoryError) {
            Timber.e(oom, "OOM during opposite-gender inference — degrading")
            false
        } catch (t: Throwable) {
            Timber.e(t, "Opposite-gender inference unexpectedly failed")
            false
        } finally {
            inferenceInFlight.set(false)
        }
    }

    private fun runOppositeGenderInference(
        bitmap: Bitmap,
        nsfwInterp: Interpreter,
        userGender: String
    ): Boolean {
        val nsfwProb = runCatching { runNsfwInference(nsfwInterp, bitmap) }
            .onFailure { Timber.e(it, "nsfw_model inference failed") }
            .getOrDefault(0f)

        if (nsfwProb < NSFW_GATE_THRESHOLD) return false

        val gender = genderInterpreter ?: return false
        val (maleProb, femaleProb) = runCatching { runGenderInference(gender, bitmap) }
            .onFailure { Timber.e(it, "gender_model inference failed") }
            .getOrDefault(0f to 0f)

        val femaleDetected = femaleProb >= GENDER_CONFIDENCE_THRESHOLD &&
            femaleProb >= maleProb
        val maleDetected   = maleProb   >= GENDER_CONFIDENCE_THRESHOLD &&
            maleProb >= femaleProb

        return when (userGender) {
            GENDER_MALE   -> femaleDetected
            GENDER_FEMALE -> maleDetected
            else          -> false
        }
    }

    private fun runNsfwInference(interp: Interpreter, bitmap: Bitmap): Float {
        val processed = preprocess(bitmap) ?: return 0f
        val outShape = interp.getOutputTensor(0).shape()
        val outSize  = outShape.last().coerceAtLeast(1)

        return when (outSize) {
            1 -> {
                val out = Array(1) { FloatArray(1) }
                interp.run(processed.buffer, out)
                out[0][0].coerceIn(0f, 1f)
            }
            2 -> {
                val out = Array(1) { FloatArray(2) }
                interp.run(processed.buffer, out)
                out[0][1].coerceIn(0f, 1f)
            }
            else -> {
                val out = Array(1) { FloatArray(outSize) }
                interp.run(processed.buffer, out)
                out[0].last().coerceIn(0f, 1f)
            }
        }
    }

    private fun runGenderInference(interp: Interpreter, bitmap: Bitmap): Pair<Float, Float> {
        val processed = preprocess(bitmap) ?: return 0f to 0f
        val out = Array(1) { FloatArray(2) }
        interp.run(processed.buffer, out)
        val male   = out[0][0].coerceIn(0f, 1f)
        val female = out[0][1].coerceIn(0f, 1f)
        return male to female
    }

    private fun preprocess(bitmap: Bitmap): TensorImage? = try {
        if (bitmap.isRecycled) null
        else {
            val tensor = TensorImage(org.tensorflow.lite.DataType.FLOAT32).apply { load(bitmap) }
            resizer.process(tensor)
        }
    } catch (t: Throwable) {
        Timber.e(t, "preprocess failed")
        null
    }

    /**
     * v13 (2.1.3): added explicit 1-output sigmoid branch to match models
     * whose final layer is a single scalar. Previously the branch fell into
     * `else` with `outputClasses` still claimed to be 2 → TFLite size
     * mismatch crash.
     */
    private fun runLegacyInference(bitmap: Bitmap): UnsafeScores {
        val current = interpreter ?: return UnsafeScores()
        val processed = preprocess(bitmap) ?: return UnsafeScores()
        val classes = outputClasses

        return try {
            when (classes) {
                1 -> {
                    // Single-scalar sigmoid: probability of UNSAFE.
                    val out = Array(1) { FloatArray(1) }
                    current.run(processed.buffer, out)
                    UnsafeScores(unsafe = out[0][0].coerceIn(0f, 1f))
                }
                2 -> {
                    val out = Array(1) { FloatArray(2) }
                    current.run(processed.buffer, out)
                    UnsafeScores(unsafe = out[0][1].coerceIn(0f, 1f))
                }
                5 -> {
                    val out = Array(1) { FloatArray(5) }
                    current.run(processed.buffer, out)
                    UnsafeScores(
                        unsafe = (out[0][1] + out[0][3] + out[0][4]).coerceIn(0f, 1f),
                        porn   = out[0][3].coerceAtLeast(0f),
                        hentai = out[0][1].coerceAtLeast(0f),
                        sexy   = out[0][4].coerceAtLeast(0f)
                    )
                }
                else -> {
                    val out = Array(1) { FloatArray(classes) }
                    current.run(processed.buffer, out)
                    UnsafeScores(unsafe = out[0].last().coerceIn(0f, 1f))
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "runLegacyInference threw — returning zeros")
            UnsafeScores()
        }
    }

    private fun buildBitmapVariants(source: Bitmap): List<Bitmap> {
        val variants = mutableListOf<Bitmap>()
        if (source.isRecycled) return variants
        variants += source

        val width = source.width
        val height = source.height
        if (width < 96 || height < 96) return variants

        val square = min(width, height)
        val squareX = ((width - square) / 2).coerceAtLeast(0)
        val squareY = ((height - square) / 2).coerceAtLeast(0)
        runCatching {
            variants += Bitmap.createBitmap(source, squareX, squareY, square, square)
        }

        val topHeight = (height * 0.72f).toInt().coerceIn(96, height)
        runCatching {
            variants += Bitmap.createBitmap(source, 0, 0, width, topHeight)
        }

        val startY = (height * 0.18f).toInt().coerceIn(0, height - 96)
        val lowerHeight = (height - startY).coerceAtLeast(96)
        runCatching {
            variants += Bitmap.createBitmap(source, 0, startY, width, lowerHeight)
        }

        return variants
    }

    private fun UnsafeScores.merge(other: UnsafeScores): UnsafeScores = UnsafeScores(
        unsafe = maxOf(unsafe, other.unsafe),
        porn   = maxOf(porn,   other.porn),
        hentai = maxOf(hentai, other.hentai),
        sexy   = maxOf(sexy,   other.sexy)
    )

    private fun safeRecycle(b: Bitmap) {
        runCatching { if (!b.isRecycled) b.recycle() }
    }

    /**
     * v11: SAFE non-blocking close for UI / ViewModel callers.
     */
    fun closeAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching { closeSuspend() }
                .onFailure { Timber.w(it, "closeAsync teardown failed") }
        }
    }

    suspend fun closeSuspend() {
        runCatching {
            withTimeoutOrNull(GuardianConstants.AI_DETECTOR_CLOSE_TIMEOUT_MS) {
                inferenceLock.withLock { tearDownInterpreters() }
            } ?: run {
                Timber.w("closeSuspend timed out — tearing down anyway")
                tearDownInterpreters()
            }
        }.onFailure { Timber.w(it, "closeSuspend failed (suppressed)") }
    }

    fun close() {
        runCatching { tearDownInterpreters() }
            .onFailure { Timber.w(it, "close() teardown failed") }
    }

    private fun tearDownInterpreters() {
        runCatching { interpreter?.close() }
        runCatching { nsfwInterpreter?.close() }
        runCatching { genderInterpreter?.close() }
        runCatching { legacyGpuDelegate?.close() }
        runCatching { nsfwGpuDelegate?.close() }
        runCatching { genderGpuDelegate?.close() }
        interpreter = null
        nsfwInterpreter = null
        genderInterpreter = null
        legacyGpuDelegate = null
        nsfwGpuDelegate = null
        genderGpuDelegate = null
        nsfwLoadAttempted = false
        genderLoadAttempted = false
        nsfwLoadFailed = false
        genderLoadFailed = false
    }
}
