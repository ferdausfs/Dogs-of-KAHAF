package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_FEMALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_MALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * v9 (2.0.0) FIX-LOG (performance pass):
 *  • P1-A → GPU delegate is tried first; CPU fallback on unsupported devices.
 *  • P1-B → memory-mapped model loading via FileChannel.map (zero-copy).
 *           Fallback to byte-array copy if mmap fails.
 *  • P1-C → cached preference values (cachedAiEnabled, cachedUserGender).
 *           startPrefsCache() is called once from the AccessibilityService
 *           and replaces the per-tick DataStore reads.
 *  • P1-D → adaptive bitmap variant scanning with early exit.
 *  • P2-A → close() now uses withTimeoutOrNull(2 s) to prevent ANR if the
 *           inference lock is held by a long-running run.
 *
 * Earlier v8 stability guarantees (preserved):
 *   - Loads are wrapped in try/catch — missing/corrupt model never crashes.
 *   - Inference is mutex-guarded; coarse [inferenceInFlight] short-circuits
 *     re-entrant calls.
 *   - close() is idempotent.
 *   - Bitmaps created internally are recycled in `finally`.
 *   - All inference runs on Dispatchers.Default; never on Main.
 *   - OutOfMemoryError is caught explicitly so we degrade instead of crashing.
 */
@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences
) {
    companion object {
        // Legacy / combined model (existing flow)
        const val MODEL_FILE = "guardian_model.tflite"

        // New dedicated models
        const val NSFW_MODEL_FILE   = "nsfw_model.tflite"
        const val GENDER_MODEL_FILE = "gender_model.tflite"

        const val INPUT_SIZE = 224

        // ── Tunable thresholds (kept as constants for easy tweaking) ──────
        // P5-B: re-exported via util/Constants.kt; these aliases preserve
        // the existing public surface in case other callers reach in.
        const val NSFW_GATE_THRESHOLD: Float = GuardianConstants.NSFW_GATE_THRESHOLD
        const val GENDER_CONFIDENCE_THRESHOLD: Float = GuardianConstants.GENDER_CONFIDENCE_THRESHOLD
    }

    private data class UnsafeScores(
        val unsafe: Float = 0f,
        val porn: Float = 0f,
        val hentai: Float = 0f,
        val sexy: Float = 0f
    )

    /** Result of the opposite-gender pipeline. */
    data class GenderNsfwResult(
        val isNsfw: Boolean,
        val maleProb: Float,
        val femaleProb: Float,
        val nsfwProb: Float
    )

    // Legacy interpreter — keeps existing isUnsafe() working unchanged.
    @Volatile private var interpreter: Interpreter? = null
    private var outputClasses: Int = 2

    // New pipeline interpreters.
    @Volatile private var nsfwInterpreter: Interpreter? = null
    @Volatile private var genderInterpreter: Interpreter? = null

    /** True only after we've actively attempted to load — prevents re-trying on every call. */
    @Volatile private var nsfwLoadAttempted   = false
    @Volatile private var genderLoadAttempted = false

    /** Set to true when the file is missing OR load failed — used to short-circuit. */
    @Volatile private var nsfwLoadFailed   = false
    @Volatile private var genderLoadFailed = false

    // P1-A: track the GPU delegates so we can release them on close().
    @Volatile private var legacyGpuDelegate: GpuDelegate? = null
    @Volatile private var nsfwGpuDelegate:   GpuDelegate? = null
    @Volatile private var genderGpuDelegate: GpuDelegate? = null

    private val inferenceLock = Mutex()
    private val inferenceInFlight = AtomicBoolean(false)

    private val resizer = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    // ── P1-C: preference cache ────────────────────────────────────────
    /** Hot-cached value of prefs.aiDetectionEnabled. Default = false until first emit. */
    @Volatile var cachedAiEnabled: Boolean = false
        private set
    /** Hot-cached value of prefs.userGender. Default = NONE until first emit. */
    @Volatile var cachedUserGender: String = GENDER_NONE
        private set
    @Volatile private var prefsCacheStarted = false

    /**
     * P1-C: collect both preference flows once and stay hot. Replaces the
     * `prefs.aiDetectionEnabled.first()` and `prefs.userGender.first()` calls
     * that previously happened on EVERY scan tick (every 850 ms).
     *
     * Must be called once from GuardianAccessibilityService.onServiceConnected.
     * Safe to call multiple times — only the first call attaches collectors.
     */
    @Synchronized
    fun startPrefsCache(scope: CoroutineScope) {
        if (prefsCacheStarted) return
        prefsCacheStarted = true
        scope.launch {
            runCatching {
                prefs.aiDetectionEnabled.collect { cachedAiEnabled = it }
            }.onFailure { Timber.w(it, "aiDetectionEnabled collector failed") }
        }
        scope.launch {
            runCatching {
                prefs.userGender.collect { cachedUserGender = it }
            }.onFailure { Timber.w(it, "userGender collector failed") }
        }
    }

    // ── Public surface ────────────────────────────────────────────────────

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

    /**
     * P1-A: build interpreter options with GPU delegate first, CPU fallback.
     * We hand back the GpuDelegate (if any) so the caller can null it on close().
     */
    private data class BuiltOptions(val options: Interpreter.Options, val gpu: GpuDelegate?)

    private fun buildInterpreterOptions(label: String): BuiltOptions {
        val opts = Interpreter.Options()
        // Try GPU delegate first.
        val gpu: GpuDelegate? = runCatching {
            val compat = CompatibilityList()
            if (compat.isDelegateSupportedOnThisDevice) {
                val gpuOpts = GpuDelegate.Options().apply { setPrecisionLossAllowed(true) }
                val delegate = GpuDelegate(gpuOpts)
                opts.addDelegate(delegate)
                Timber.i("TFLite[$label]: GPU delegate enabled")
                delegate
            } else {
                Timber.i("TFLite[$label]: GPU not supported — using CPU")
                null
            }
        }.onFailure {
            Timber.w(it, "TFLite[$label]: GPU delegate unavailable, falling back to CPU")
        }.getOrNull()

        if (gpu == null) {
            // CPU fallback — same threading policy as before.
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            opts.setNumThreads(threads)
        }
        return BuiltOptions(opts, gpu)
    }

    /**
     * Lazily load the legacy combined model (existing behavior — DO NOT change).
     * Returns false if loading failed; callers should silently skip in that case.
     */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (interpreter != null) return true

        val buffer = readModelBuffer(MODEL_FILE) ?: return false
        return runCatching {
            val (options, gpu) = buildInterpreterOptions("legacy")
            val created = try {
                Interpreter(buffer, options)
            } catch (t: Throwable) {
                // GPU failed at runtime — release delegate & retry on CPU.
                runCatching { gpu?.close() }
                Timber.w(t, "GPU interpreter failed for legacy — retrying on CPU")
                val cpuOpts = Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                }
                Interpreter(buffer, cpuOpts)
            }.also {
                if (gpu != null) legacyGpuDelegate = gpu
            }
            outputClasses = created.getOutputTensor(0).shape().last().coerceAtLeast(2)
            interpreter = created
            Timber.i("TFLite (legacy) model loaded — output classes=$outputClasses")
            true
        }.onFailure { Timber.e(it, "Failed to create legacy TFLite interpreter") }
            .getOrDefault(false)
    }

    /**
     * Lazily load both nsfw + gender models. Each one is independent — if gender
     * fails we still return true as long as nsfw is up. If nsfw also fails, returns false.
     */
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
            // Graceful degradation — NOT a crash.
            Timber.e(it, "Failed to create gender_model interpreter — falling back to NSFW-only")
        }
    }

    /**
     * P1-B: memory-mapped model loading.
     * Previously: file.readBytes() into a byte array then copied to
     * ByteBuffer.allocateDirect() — 2× memory usage and slow start.
     * Now: FileChannel.map() returns a zero-copy MappedByteBuffer.
     * Fallback to the byte-array path if mmap throws (some FS, sealed assets).
     */
    private fun readModelBuffer(name: String): ByteBuffer? {
        // 1. Imported file in filesDir — try mmap first.
        val externalFile = File(context.filesDir, name)
        if (externalFile.exists()) {
            // Attempt mmap.
            val mapped = runCatching {
                FileInputStream(externalFile).use { fis ->
                    fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, externalFile.length())
                        .order(ByteOrder.nativeOrder())
                }
            }.onFailure { Timber.w(it, "MappedByteBuffer failed for $name, falling back to readBytes") }
                .getOrNull()
            if (mapped != null) return mapped

            // Fallback: byte-array copy (original v8 path).
            return runCatching {
                val bytes = externalFile.readBytes()
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    .also { it.put(bytes); it.rewind() }
            }.onFailure { Timber.e(it, "Failed to read $name from filesDir") }.getOrNull()
        }

        // 2. Asset fallback — assets cannot be mmap'd through the standard API,
        // so we keep the byte-array copy here.
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

    // ── Existing isUnsafe() flow (P1-D adaptive variants with early exit) ──

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        if (!ensureLoaded()) return false
        val threshold = prefs.aiThreshold.first().coerceIn(0.2f, 0.95f)

        return inferenceLock.withLock {
            withContext(Dispatchers.Default) {
                runCatching {
                    val variants = buildBitmapVariants(bitmap)
                    try {
                        // P1-D: fast path — run full image first.
                        val primaryScores = runLegacyInference(variants[0])
                        // If primary score is very low (< 20% of threshold), skip crops.
                        val earlyExitThreshold = threshold * GuardianConstants.EARLY_EXIT_RATIO
                        if (primaryScores.unsafe < earlyExitThreshold &&
                            primaryScores.porn   < earlyExitThreshold &&
                            primaryScores.hentai < earlyExitThreshold &&
                            primaryScores.sexy   < earlyExitThreshold * 0.5f
                        ) {
                            return@runCatching false
                        }
                        if (isUnsafe(primaryScores, threshold)) return@runCatching true

                        // Full scan for remaining variants.
                        var strongest = primaryScores
                        for (i in 1 until variants.size) {
                            val scores = runLegacyInference(variants[i])
                            strongest = strongest.merge(scores)
                            if (isUnsafe(scores, threshold)) return@runCatching true
                        }
                        isUnsafe(strongest, threshold)
                    } finally {
                        variants.forEach { if (it !== bitmap) safeRecycle(it) }
                    }
                }.onFailure { Timber.e(it, "TFLite inference failed") }
                    .getOrDefault(false)
            }
        }
    }

    // ── New: opposite-gender NSFW pipeline ────────────────────────────────

    /**
     * Two-stage check:
     *  1. nsfw_model → if NSFW prob ≤ [NSFW_GATE_THRESHOLD], allow.
     *  2. gender_model → if dominant gender prob ≥ [GENDER_CONFIDENCE_THRESHOLD]
     *     AND it matches the user's *opposite* gender → block.
     *
     * Returns false on any failure / missing model / bitmap == null / userGender == NONE.
     * NEVER throws — every path is wrapped.
     */
    suspend fun isOppositeGenderNsfw(bitmap: Bitmap?, userGender: String): Boolean {
        // Fast guards — no model work at all.
        if (bitmap == null || bitmap.isRecycled) return false
        if (userGender == GENDER_NONE) return false
        if (userGender != GENDER_MALE && userGender != GENDER_FEMALE) return false

        // Load on demand.
        if (!ensureGenderPipelineLoaded()) return false
        val nsfw = nsfwInterpreter ?: return false

        // Concurrent inference guard.
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
            // Production-safe: never log the bitmap / pixel data, only the error class.
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
        // Stage 1 — NSFW probability.
        val nsfwProb = runCatching { runNsfwInference(nsfwInterp, bitmap) }
            .onFailure { Timber.e(it, "nsfw_model inference failed") }
            .getOrDefault(0f)

        if (nsfwProb < NSFW_GATE_THRESHOLD) return false

        // Stage 2 — gender. If the gender model isn't there, we deliberately
        // do NOT block on NSFW alone (that would be a false positive for the
        // opposite-gender feature — the legacy isUnsafe() pipeline is what
        // handles "NSFW for everyone" detection).
        val gender = genderInterpreter ?: return false
        val (maleProb, femaleProb) = runCatching { runGenderInference(gender, bitmap) }
            .onFailure { Timber.e(it, "gender_model inference failed") }
            .getOrDefault(0f to 0f)

        val femaleDetected = femaleProb >= GENDER_CONFIDENCE_THRESHOLD &&
            femaleProb >= maleProb
        val maleDetected   = maleProb   >= GENDER_CONFIDENCE_THRESHOLD &&
            maleProb >= femaleProb

        return when (userGender) {
            GENDER_MALE   -> femaleDetected   // user is male  → block female NSFW
            GENDER_FEMALE -> maleDetected     // user is female → block male NSFW
            else          -> false
        }
    }

    /** Returns NSFW probability ∈ [0,1]. Handles single-float OR 2-class outputs. */
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
                // Convention: index 1 = NSFW.
                out[0][1].coerceIn(0f, 1f)
            }
            else -> {
                val out = Array(1) { FloatArray(outSize) }
                interp.run(processed.buffer, out)
                out[0].last().coerceIn(0f, 1f)
            }
        }
    }

    /** Returns (maleProb, femaleProb). */
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

    // ── Legacy inference helpers (unchanged behavior) ─────────────────────

    private fun runLegacyInference(bitmap: Bitmap): UnsafeScores {
        val current = interpreter ?: return UnsafeScores()
        val processed = preprocess(bitmap) ?: return UnsafeScores()

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
                    porn   = out[0][3].coerceAtLeast(0f),
                    hentai = out[0][1].coerceAtLeast(0f),
                    sexy   = out[0][4].coerceAtLeast(0f)
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

        return scores.porn   >= strongUnsafeThreshold ||
            scores.hentai >= strongUnsafeThreshold ||
            scores.sexy   >= sexyThreshold ||
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
     * P2-A: ANR-safe close.
     *
     * v8 used a plain `runBlocking { inferenceLock.withLock { ... } }` which
     * could block the main thread for the full duration of an in-flight
     * inference (potentially 1–3 s on slow devices) → ANR risk on Android
     * 12+ where onDestroy must return promptly.
     *
     * We now run on Dispatchers.IO and bound the wait to AI_DETECTOR_CLOSE_TIMEOUT_MS.
     * If the lock can't be acquired in time we tear down anyway — the next
     * Interpreter.run() will fail gracefully (caller already wraps it in
     * runCatching).
     *
     * Safe to call multiple times. Never throws.
     */
    fun close() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                val acquired = withTimeoutOrNull(GuardianConstants.AI_DETECTOR_CLOSE_TIMEOUT_MS) {
                    inferenceLock.withLock { tearDownInterpreters() }
                    true
                }
                if (acquired == null) {
                    Timber.w("AiDetector.close() timed out waiting for inference lock — tearing down anyway")
                    tearDownInterpreters()
                }
            }
        }.onFailure { Timber.w(it, "AiDetector.close() failed (suppressed)") }
    }

    private fun tearDownInterpreters() {
        runCatching { interpreter?.close() }
        runCatching { nsfwInterpreter?.close() }
        runCatching { genderInterpreter?.close() }
        // Release GPU delegates explicitly — TFLite docs say leaking these
        // can pin GPU memory until process death.
        runCatching { legacyGpuDelegate?.close() }
        runCatching { nsfwGpuDelegate?.close() }
        runCatching { genderGpuDelegate?.close() }
        interpreter = null
        nsfwInterpreter = null
        genderInterpreter = null
        legacyGpuDelegate = null
        nsfwGpuDelegate = null
        genderGpuDelegate = null
        // Allow the next ensureLoaded() / ensureGenderPipelineLoaded()
        // to actually re-create the interpreters from the new file.
        nsfwLoadAttempted = false
        genderLoadAttempted = false
        nsfwLoadFailed = false
        genderLoadFailed = false
    }
}
