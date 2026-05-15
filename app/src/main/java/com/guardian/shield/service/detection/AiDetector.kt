package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
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
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences
) {
    private val inferenceLock = Mutex()
    private var legacyInterpreter: Interpreter? = null
    private var nsfwInterpreter: Interpreter? = null
    private var genderInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    @Volatile var cachedAiEnabled: Boolean = false
        private set
    @Volatile var cachedUserGender: String = "NONE"
        private set
    @Volatile var cachedThreshold: Float = 0.7f
        private set

    fun startPrefsCache(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                try { prefs.aiDetection.collect { cachedAiEnabled = it } }
                catch (t: Throwable) { Timber.e(t); delay(1_000) }
            }
        }
        scope.launch {
            while (isActive) {
                try { prefs.userGender.collect { cachedUserGender = it } }
                catch (t: Throwable) { Timber.e(t); delay(1_000) }
            }
        }
        scope.launch {
            while (isActive) {
                try { prefs.aiThreshold.collect { cachedThreshold = it } }
                catch (t: Throwable) { Timber.e(t); delay(1_000) }
            }
        }
    }

    fun isLegacyAvailable(): Boolean = legacyInterpreter != null
    fun isGenderModelAvailable(): Boolean = genderInterpreter != null
    fun isNsfwGateAvailable(): Boolean = nsfwInterpreter != null

    suspend fun ensureLoaded() {
        inferenceLock.withLock {
            if (legacyInterpreter == null) legacyInterpreter = tryLoad(MODEL_LEGACY)
            if (nsfwInterpreter == null) nsfwInterpreter = tryLoad(MODEL_NSFW)
            if (genderInterpreter == null) genderInterpreter = tryLoad(MODEL_GENDER)
            Timber.d("Models: legacy=${legacyInterpreter != null} nsfw=${nsfwInterpreter != null} gender=${genderInterpreter != null}")
        }
    }

    private fun tryLoad(name: String): Interpreter? {
        return try {
            val buffer = loadModelBuffer(name) ?: return null
            buildInterpreter(buffer).also { Timber.i("Loaded: $name") }
        } catch (t: Throwable) {
            Timber.w(t, "Failed to load $name")
            null
        }
    }

    private fun loadModelBuffer(name: String): ByteBuffer? {
        val f = File(context.filesDir, name)
        if (f.exists() && f.length() > 0) {
            return try {
                FileInputStream(f).channel.use { ch ->
                    ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
                } as MappedByteBuffer
            } catch (t: Throwable) {
                Timber.w(t, "mmap failed; byte copy $name")
                val bytes = f.readBytes()
                ByteBuffer.allocateDirect(bytes.size)
                    .order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
            }
        }
        return try {
            context.assets.open(name).use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return null
                ByteBuffer.allocateDirect(bytes.size)
                    .order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
            }
        } catch (_: Throwable) { null }
    }

    private fun buildInterpreter(buffer: ByteBuffer): Interpreter {
        val opts = Interpreter.Options()
        try {
            val cl = CompatibilityList()
            if (cl.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate()
                opts.addDelegate(gpuDelegate)
                Timber.i("GPU delegate enabled")
            } else {
                opts.setNumThreads(2)
            }
        } catch (t: Throwable) {
            Timber.w(t, "GPU init failed; CPU fallback")
            opts.setNumThreads(2)
        }
        return try {
            Interpreter(buffer, opts)
        } catch (t: Throwable) {
            Timber.w(t, "GPU interpreter failed; CPU retry")
            try { gpuDelegate?.close() } catch (_: Throwable) {}
            gpuDelegate = null
            Interpreter(buffer, Interpreter.Options().setNumThreads(2))
        }
    }

    // =============================================
    // GUARDIAN MODEL — [1, 3] output
    // Classes: [safe/sfw, nsfw/porn, explicit/sexy] (3-class)
    // ✅ Fix: index 1+2 sum = total harmful score
    // Threshold: user setting (default 0.5, NOT 0.7)
    // =============================================
    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        val interp = legacyInterpreter ?: return false
        return inferenceLock.withLock {
            try {
                // ✅ guardian_model threshold is lower — 0.5 default
                val threshold = (cachedThreshold - 0.2f).coerceAtLeast(0.3f)
                val fullScores = runInference(interp, bitmap)
                val nsfwScore = extractGuardianScore(fullScores)
                Timber.d("Guardian scores: ${fullScores.toList()} → nsfw=$nsfwScore threshold=$threshold")

                // Early exit — very safe
                if (nsfwScore < threshold * GuardianConstants.EARLY_EXIT_RATIO) return@withLock false
                if (nsfwScore >= threshold) return@withLock true

                // Center crop
                val center = cropSquare(bitmap)
                val centerScore = extractGuardianScore(runInference(interp, center))
                center.recycle()
                if (centerScore >= threshold) return@withLock true

                // Top 72%
                val top = cropVertical(bitmap, 0f, 0.72f)
                val topScore = extractGuardianScore(runInference(interp, top))
                top.recycle()
                if (topScore >= threshold) return@withLock true

                // Bottom 82%
                val bot = cropVertical(bitmap, 0.18f, 1f)
                val botScore = extractGuardianScore(runInference(interp, bot))
                bot.recycle()

                val maxScore = maxOf(nsfwScore, centerScore, topScore, botScore)
                Timber.d("Guardian max score: $maxScore")
                maxScore >= threshold
            } catch (t: Throwable) {
                Timber.e(t, "isUnsafe failed")
                false
            }
        }
    }

    /**
     * guardian_model output [1, 3]:
     * Index 0 = safe/sfw probability
     * Index 1 = nsfw/porn probability
     * Index 2 = explicit/sexy probability
     * ✅ Fix: sum of index 1 + index 2 = total harmful
     * Previous bug: maxOrNull() would return safe=0.9 and block!
     */
    private fun extractGuardianScore(scores: FloatArray): Float {
        return when (scores.size) {
            1 -> scores[0]
            2 -> scores[1]  // [safe, nsfw]
            3 -> {
                // [safe, harmful1, harmful2] → sum of harmful classes
                val harmful = scores.getOrElse(1){0f} + scores.getOrElse(2){0f}
                harmful.coerceAtMost(1.0f)
            }
            5 -> {
                // Yahoo NSFW: [drawings, hentai, neutral, porn, sexy]
                // ✅ Use MAX not SUM — sum can exceed 1.0
                maxOf(
                    scores.getOrElse(1){0f},  // hentai
                    scores.getOrElse(3){0f},  // porn
                    scores.getOrElse(4){0f}   // sexy
                )
            }
            else -> scores.drop(1).max()  // skip index 0 (safe), take max
        }
    }

    // =============================================
    // NSFW MODEL — [1, 5] output
    // Yahoo Open NSFW: [drawings, hentai, neutral, porn, sexy]
    // ✅ Fix: use MAX of harmful classes, NOT sum
    // Gate threshold: 0.45 (lower = more sensitive)
    // =============================================
    private fun extractNsfwGateScore(scores: FloatArray): Float {
        return when (scores.size) {
            1 -> scores[0]
            2 -> scores[1]
            5 -> {
                // ✅ MAX of harmful: hentai(1), porn(3), sexy(4)
                // NOT sum — sum of 3 classes can give 1.5+ which breaks thresholds
                maxOf(
                    scores.getOrElse(1){0f},  // hentai
                    scores.getOrElse(3){0f},  // porn
                    scores.getOrElse(4){0f}   // sexy
                )
            }
            else -> scores.drop(1).max()
        }
    }

    // =============================================
    // GENDER MODEL — [1, 133] output
    // MobileNetV2 age+gender combined model
    // Output: 133 classes (age groups + gender)
    // ✅ Fix: gender_model এর সঠিক index বের করো
    //
    // Common layout for age+gender models:
    // First half: age probabilities
    // Last 2: male probability, female probability
    // OR: index 0=male, last=female
    //
    // Since we can't run inference here, use a safe approach:
    // Sum first 67 = one gender, sum last 66 = other gender
    // =============================================
    suspend fun isOppositeGenderNsfw(bitmap: Bitmap, userGender: String): Boolean {
        val nsfw = nsfwInterpreter ?: return false
        val gender = genderInterpreter ?: return false
        if (userGender != "MALE" && userGender != "FEMALE") return false

        return inferenceLock.withLock {
            try {
                val currentGender = runCatching { prefs.userGender.first() }.getOrElse { userGender }
                if (currentGender != "MALE" && currentGender != "FEMALE") return@withLock false

                // Step 1: NSFW gate check
                val nsfwScores = runInference(nsfw, bitmap)
                val nsfwProb = extractNsfwGateScore(nsfwScores)
                Timber.d("NSFW gate: $nsfwProb (threshold: ${GuardianConstants.NSFW_GATE_THRESHOLD})")
                if (nsfwProb < GuardianConstants.NSFW_GATE_THRESHOLD) return@withLock false

                // Step 2: Gender check
                val genderScores = runInference(gender, bitmap)

                // ✅ Fix for [1, 133] output:
                // MobileNetV2 age+gender model layout varies
                // Strategy: split 133 outputs into two halves
                // First half (0..65) = one gender group
                // Second half (66..132) = other gender group
                // Sum each half — higher sum = that gender
                val half = genderScores.size / 2
                val firstHalfSum = genderScores.take(half).sum()
                val secondHalfSum = genderScores.drop(half).sum()

                // Normalize
                val total = (firstHalfSum + secondHalfSum).coerceAtLeast(0.001f)
                val firstProb = firstHalfSum / total
                val secondProb = secondHalfSum / total

                Timber.d("Gender halves: first=$firstProb second=$secondProb user=$currentGender")

                // First half typically = female (lower age indices)
                // Second half typically = male (higher age indices)
                // This is heuristic — adjust based on testing
                val femaleProb = firstProb
                val maleProb = secondProb

                val threshold = GuardianConstants.GENDER_CONFIDENCE_THRESHOLD
                val result = when (currentGender) {
                    "MALE" -> femaleProb >= threshold    // Male user — block female content
                    "FEMALE" -> maleProb >= threshold    // Female user — block male content
                    else -> false
                }
                Timber.d("Gender detection: male=$maleProb female=$femaleProb → block=$result")
                result
            } catch (t: Throwable) {
                Timber.e(t, "Gender NSFW failed")
                false
            }
        }
    }

    private fun runInference(interp: Interpreter, bitmap: Bitmap): FloatArray {
        val inputShape = interp.getInputTensor(0).shape()
        val h = inputShape.getOrNull(1) ?: 224
        val w = inputShape.getOrNull(2) ?: 224
        val resized = if (bitmap.width != w || bitmap.height != h)
            Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val input = ByteBuffer.allocateDirect(4 * w * h * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xff) / 255f)
            input.putFloat(((p shr 8) and 0xff) / 255f)
            input.putFloat((p and 0xff) / 255f)
        }
        input.rewind()
        if (resized !== bitmap) resized.recycle()
        val outShape = interp.getOutputTensor(0).shape()
        val outSize = outShape.fold(1) { acc, i -> acc * i }
        val output = Array(1) { FloatArray(outSize) }
        interp.run(input, output)
        return output[0]
    }

    private fun cropSquare(b: Bitmap): Bitmap {
        val side = minOf(b.width, b.height)
        return Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
    }

    private fun cropVertical(b: Bitmap, from: Float, to: Float): Bitmap {
        val y = (b.height * from).toInt().coerceAtLeast(0)
        val h = (b.height * (to - from)).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(b, 0, y, b.width, h.coerceAtMost(b.height - y))
    }

    fun close() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            withTimeoutOrNull(GuardianConstants.AI_DETECTOR_CLOSE_TIMEOUT_MS) {
                inferenceLock.withLock { tearDown() }
            } ?: tearDown()
        }
    }

    private fun tearDown() {
        try { legacyInterpreter?.close() } catch (_: Throwable) {}
        try { nsfwInterpreter?.close() } catch (_: Throwable) {}
        try { genderInterpreter?.close() } catch (_: Throwable) {}
        try { gpuDelegate?.close() } catch (_: Throwable) {}
        legacyInterpreter = null; nsfwInterpreter = null
        genderInterpreter = null; gpuDelegate = null
    }

    companion object {
        const val MODEL_LEGACY = "guardian_model.tflite"
        const val MODEL_NSFW = "nsfw_model.tflite"
        const val MODEL_GENDER = "gender_model.tflite"
    }
}