package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_FEMALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_MALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-04 → close() now serialises with active inference by acquiring
 *    inferenceLock via runBlocking before nulling the interpreters. This
 *    prevents the JNI native crash where a model re-import (or service
 *    teardown) raced with an in-flight Interpreter.run() and called close()
 *    on a live native handle.
 *
 * Loads & runs three TFLite models:
 *   • [MODEL_FILE]            — legacy combined "guardian" NSFW model (back-compat)
 *   • [NSFW_MODEL_FILE]       — dedicated NSFW probability head (single float / 2-class output)
 *   • [GENDER_MODEL_FILE]     — gender classifier → [male_prob, female_prob]
 *
 * Stability guarantees:
 *   - All loads are wrapped in try/catch — a missing/corrupt model NEVER crashes the app.
 *   - Inference is mutex-guarded (no concurrent runs), and a coarse [inferenceInFlight] flag
 *     short-circuits re-entrant calls coming through different paths.
 *   - close() is serialised against active inference (BUG-04).
 *   - Bitmaps created internally are recycled in `finally`; the caller's bitmap is never
 *     recycled by us.
 *   - All inference runs on Dispatchers.Default; never on Main.
 *   - OutOfMemoryError is caught explicitly so we degrade instead of crashing.
 *   - close() is idempotent and try/catch-guarded.
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
        /** Minimum NSFW score (from nsfw_model) before we even bother running gender. */
        const val NSFW_GATE_THRESHOLD: Float = 0.6f

        /** Minimum class probability from gender_model to trust the classification. */
        const val GENDER_CONFIDENCE_THRESHOLD: Float = 0.65f
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

    private val inferenceLock = Mutex()
    private val inferenceInFlight = AtomicBoolean(false)

    private val resizer = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

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
     * Lazily load the legacy combined model (existing behavior — DO NOT change).
     * Returns false if loading failed; callers should silently skip in that case.
     */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (interpreter != null) return true

        val buffer = readModelBuffer(MODEL_FILE) ?: return false
        return runCatching {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val options = Interpreter.Options().apply { setNumThreads(threads) }
            val created = Interpreter(buffer, options)
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
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val opts = Interpreter.Options().apply { setNumThreads(threads) }
            nsfwInterpreter = Interpreter(buffer, opts)
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
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val opts = Interpreter.Options().apply { setNumThreads(threads) }
            genderInterpreter = Interpreter(buffer, opts)
            Timber.i("gender_model.tflite loaded successfully")
        }.onFailure {
            genderLoadFailed = true
            genderInterpreter = null
            // Graceful degradation — NOT a crash.
            Timber.e(it, "Failed to create gender_model interpreter — falling back to NSFW-only")
        }
    }

    private fun readModelBuffer(name: String): ByteBuffer? {
        val externalFile = File(context.filesDir, name)
        return when {
            externalFile.exists() -> runCatching {
                val bytes = externalFile.readBytes()
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    .also { it.put(bytes); it.rewind() }
            }.onFailure { Timber.e(it, "Failed to read $name from filesDir") }.getOrNull()

            modelExistsInAssets(name) -> runCatching {
                context.assets.open(name).use { stream ->
                    val bytes = stream.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                        .also { it.put(bytes); it.rewind() }
                }
            }.onFailure { Timber.e(it, "Failed to read $name from assets") }.getOrNull()

            else -> null
        }
    }

    // ── Existing isUnsafe() flow (kept verbatim for back-compat) ──────────

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        if (!ensureLoaded()) return false
        val threshold = prefs.aiThreshold.first().coerceIn(0.2f, 0.95f)

        return inferenceLock.withLock {
            withContext(Dispatchers.Default) {
                runCatching {
                    val variants = buildBitmapVariants(bitmap)
                    try {
                        var strongest = UnsafeScores()
                        for (candidate in variants) {
                            val scores = runLegacyInference(candidate)
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
     * BUG-04: Idempotent and now SAFE w.r.t. concurrent inference.
     *
     * Without serialisation, `close()` could call `interpreter?.close()` while
     * another thread was mid-`Interpreter.run()` → JNI native crash that
     * silently terminates the AccessibilityService. We acquire `inferenceLock`
     * via runBlocking (this method is NOT a suspend function) so any active
     * inference completes before we tear down native handles.
     *
     * Safe to call multiple times. Never throws.
     */
    fun close() {
        runCatching {
            runBlocking {
                inferenceLock.withLock {
                    runCatching { interpreter?.close() }
                    runCatching { nsfwInterpreter?.close() }
                    runCatching { genderInterpreter?.close() }
                    interpreter = null
                    nsfwInterpreter = null
                    genderInterpreter = null
                    // Allow the next ensureLoaded() / ensureGenderPipelineLoaded()
                    // to actually re-create the interpreters from the new file.
                    nsfwLoadAttempted = false
                    genderLoadAttempted = false
                    nsfwLoadFailed = false
                    genderLoadFailed = false
                }
            }
        }.onFailure { Timber.w(it, "AiDetector.close() failed (suppressed)") }
    }
}
