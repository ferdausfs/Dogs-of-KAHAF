package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
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

    // STABILITY FIX — count inference failures so we can refuse to keep using
    // a clearly broken delegate.
    @Volatile private var consecutiveInferenceFails = 0
    private val INFERENCE_FAIL_THRESHOLD = 3

    @Volatile var cachedAiEnabled: Boolean = false
        private set
    @Volatile var cachedUserGender: String = "NONE"
        private set
    @Volatile var cachedThreshold: Float = 0.65f
        private set
    @Volatile var cachedNsfwGateThreshold: Float = 0.60f
        private set
    @Volatile var cachedGenderThreshold: Float = 0.70f
        private set
    @Volatile var cachedGridVoteCount: Int = 2
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
        scope.launch {
            while (isActive) {
                try { prefs.nsfwGateThreshold.collect { cachedNsfwGateThreshold = it } }
                catch (t: Throwable) { Timber.e(t); delay(1_000) }
            }
        }
        scope.launch {
            while (isActive) {
                try { prefs.genderThreshold.collect { cachedGenderThreshold = it } }
                catch (t: Throwable) { Timber.e(t); delay(1_000) }
            }
        }
        scope.launch {
            while (isActive) {
                try { prefs.gridVoteCount.collect { cachedGridVoteCount = it } }
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

    /**
     * STABILITY FIX — if the GPU delegate is producing bad inferences, drop all
     * interpreters and rebuild them on CPU. Called automatically after a few
     * consecutive failures.
     */
    private fun rebuildAllOnCpu() {
        try {
            Timber.w("Rebuilding AI interpreters on CPU after $consecutiveInferenceFails failures")
            try { legacyInterpreter?.close() } catch (_: Throwable) {}
            try { nsfwInterpreter?.close() } catch (_: Throwable) {}
            try { genderInterpreter?.close() } catch (_: Throwable) {}
            try { gpuDelegate?.close() } catch (_: Throwable) {}
            legacyInterpreter = null
            nsfwInterpreter = null
            genderInterpreter = null
            gpuDelegate = null

            legacyInterpreter = tryLoad(MODEL_LEGACY, forceCpu = true)
            nsfwInterpreter = tryLoad(MODEL_NSFW, forceCpu = true)
            genderInterpreter = tryLoad(MODEL_GENDER, forceCpu = true)
            consecutiveInferenceFails = 0
        } catch (t: Throwable) {
            Timber.e(t, "rebuildAllOnCpu failed")
        }
    }

    private fun tryLoad(name: String, forceCpu: Boolean = false): Interpreter? {
        return try {
            val buffer = loadModelBuffer(name) ?: return null
            buildInterpreter(buffer, forceCpu).also {
                Timber.i("Loaded: $name (cpu=$forceCpu)")
            }
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

    private fun buildInterpreter(buffer: ByteBuffer, forceCpu: Boolean = false): Interpreter {
        val opts = Interpreter.Options()
        if (!forceCpu) {
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
        } else {
            opts.setNumThreads(2)
        }
        return try {
            Interpreter(buffer, opts)
        } catch (t: Throwable) {
            Timber.w(t, "Interp build failed; CPU retry")
            try { gpuDelegate?.close() } catch (_: Throwable) {}
            gpuDelegate = null
            Interpreter(buffer, Interpreter.Options().setNumThreads(2))
        }
    }

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        val interp = legacyInterpreter ?: return false
        return inferenceLock.withLock {
            try {
                val threshold = cachedThreshold
                val voteNeeded = cachedGridVoteCount

                val fullScore = extractGuardianScore(runInferenceSafe(interp, bitmap)
                    ?: return@withLock false)
                Timber.d("Guardian full: $fullScore / $threshold")

                if (fullScore < threshold * 0.3f) return@withLock false
                if (fullScore >= threshold) return@withLock true

                // Use a smarter grid: check middle and bottom first where content (body/legs) usually is
                val regions = splitIntoGrid(bitmap, cols = 2, rows = 3)
                val prioritizedIndices = listOf(2, 3, 4, 5, 0, 1).filter { it < regions.size }
                var triggeredCount = 0

                for (idx in prioritizedIndices) {
                    val region = regions[idx]
                    try {
                        val out = runInferenceSafe(interp, region)
                        if (out == null) continue
                        val score = extractGuardianScore(out)
                        Timber.d("Grid[$idx]: $score")
                        if (score >= threshold) triggeredCount++
                        if (triggeredCount >= voteNeeded) {
                            region.recycle()
                            break
                        }
                    } catch (t: Throwable) {
                        Timber.e(t, "Grid[$idx] error")
                    } finally {
                        if (!region.isRecycled) region.recycle()
                    }
                }

                Timber.d("Grid vote: $triggeredCount/$voteNeeded needed")
                triggeredCount >= voteNeeded

            } catch (t: Throwable) {
                Timber.e(t, "isUnsafe failed")
                false
            }
        }
    }

    suspend fun isOppositeGenderNsfw(bitmap: Bitmap, userGender: String): Boolean {
        val nsfw = nsfwInterpreter ?: return false
        val gender = genderInterpreter ?: return false
        if (userGender != "MALE" && userGender != "FEMALE") return false

        return inferenceLock.withLock {
            try {
                val currentGender = runCatching {
                    prefs.userGender.first()
                }.getOrElse { userGender }
                if (currentGender != "MALE" && currentGender != "FEMALE") return@withLock false

                val nsfwGate = cachedNsfwGateThreshold
                val genderConf = cachedGenderThreshold
                val voteNeeded = cachedGridVoteCount

                val initial = runInferenceSafe(nsfw, bitmap) ?: return@withLock false
                var maxNsfwScore = extractNsfwGateScore(initial)
                Timber.d("NSFW gate full: $maxNsfwScore / $nsfwGate")

                if (maxNsfwScore < nsfwGate) {
                    val regions = splitIntoGrid(bitmap, cols = 2, rows = 3)
                    var nsfwVotes = 0
                    val prioritizedIndices = listOf(2, 3, 4, 5, 0, 1).filter { it < regions.size }
                    for (idx in prioritizedIndices) {
                        val region = regions[idx]
                        try {
                            val out = runInferenceSafe(nsfw, region)
                            if (out == null) continue
                            val score = extractNsfwGateScore(out)
                            Timber.d("NSFW Grid[$idx]: $score")
                            if (score > maxNsfwScore) maxNsfwScore = score
                            if (score >= nsfwGate) nsfwVotes++
                            if (nsfwVotes >= voteNeeded) {
                                region.recycle()
                                break
                            }
                        } catch (t: Throwable) {
                            Timber.e(t, "NSFW Grid[$idx] error")
                        } finally {
                            if (!region.isRecycled) region.recycle()
                        }
                    }
                    if (maxNsfwScore < nsfwGate && nsfwVotes < voteNeeded) {
                        return@withLock false
                    }
                }

                val genderScores = runInferenceSafe(gender, bitmap) ?: return@withLock false
                val half = genderScores.size / 2
                val firstSum = genderScores.take(half).sum()
                val secondSum = genderScores.drop(half).sum()
                val total = (firstSum + secondSum).coerceAtLeast(0.001f)
                val femaleProb = firstSum / total
                val maleProb = secondSum / total

                Timber.d("Gender: male=$maleProb female=$femaleProb conf=$genderConf user=$currentGender")

                val genderMatch = when (currentGender) {
                    "MALE" -> femaleProb >= genderConf
                    "FEMALE" -> maleProb >= genderConf
                    else -> false
                }

                // HYBRID DETECTION:
                // 1. If it's a full NSFW match (based on genderConf), block.
                // 2. If it's a "Soft" NSFW match, we allow a lower gender confidence to catch semi-nudes.
        val isSoftNsfw = maxNsfwScore >= com.guardian.shield.util.GuardianConstants.SOFT_NSFW_THRESHOLD

                if (isSoftNsfw) {
                    val softGenderConf = genderConf * 0.85f // Lower requirement for semi-nudes
                    val softGenderMatch = when (currentGender) {
                        "MALE" -> femaleProb >= softGenderConf
                        "FEMALE" -> maleProb >= softGenderConf
                        else -> false
                    }
                    if (softGenderMatch) {
                        Timber.i("Hybrid block: softGenderMatch=$softGenderMatch score=$maxNsfwScore")
                        return@withLock true
                    }
                }

                genderMatch
            } catch (t: Throwable) {
                Timber.e(t, "Gender NSFW failed")
                false
            }
        }
    }

    private fun splitIntoGrid(bitmap: Bitmap, cols: Int, rows: Int): List<Bitmap> {
        val regions = mutableListOf<Bitmap>()
        val cellW = bitmap.width / cols
        val cellH = bitmap.height / rows
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * cellW
                val y = row * cellH
                val w = if (col == cols - 1) bitmap.width - x else cellW
                val h = if (row == rows - 1) bitmap.height - y else cellH
                if (w > 32 && h > 32) {
                    runCatching {
                        regions.add(Bitmap.createBitmap(bitmap, x, y, w, h))
                    }.onFailure { Timber.e(it, "Grid crop [$row,$col]") }
                }
            }
        }
        return regions
    }

    private fun extractGuardianScore(scores: FloatArray): Float {
        return when (scores.size) {
            1 -> scores[0]
            2 -> scores[1]
            3 -> (scores.getOrElse(1){0f} + scores.getOrElse(2){0f}).coerceAtMost(1.0f)
            5 -> maxOf(
                scores.getOrElse(1){0f},
                scores.getOrElse(3){0f},
                scores.getOrElse(4){0f}
            )
            else -> scores.drop(1).max()
        }
    }

    private fun extractNsfwGateScore(scores: FloatArray): Float {
        return when (scores.size) {
            1 -> scores[0]
            2 -> scores[1]
            5 -> maxOf(
                scores.getOrElse(1){0f},
                scores.getOrElse(3){0f},
                scores.getOrElse(4){0f}
            )
            else -> scores.drop(1).max()
        }
    }

    /**
     * STABILITY FIX — single inference call that:
     *   1) catches all exceptions
     *   2) increments a failure counter
     *   3) rebuilds interpreters on CPU once the counter crosses threshold
     *
     * Returns null on failure so callers can skip cleanly instead of crashing
     * or returning a junk false-positive.
     */
    private fun runInferenceSafe(interp: Interpreter, bitmap: Bitmap): FloatArray? {
        return try {
            val result = runInference(interp, bitmap)
            consecutiveInferenceFails = 0
            result
        } catch (t: Throwable) {
            consecutiveInferenceFails++
            Timber.w(t, "Inference failed (streak=$consecutiveInferenceFails)")
            if (consecutiveInferenceFails >= INFERENCE_FAIL_THRESHOLD) {
                rebuildAllOnCpu()
            }
            null
        }
    }

    private fun runInference(interp: Interpreter, bitmap: Bitmap): FloatArray {
        val inputShape = interp.getInputTensor(0).shape()
        val h = inputShape.getOrNull(1) ?: 224
        val w = inputShape.getOrNull(2) ?: 224

        // Use a more memory-efficient scaling if needed
        val resized = if (bitmap.width != w || bitmap.height != h) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val input = ByteBuffer.allocateDirect(4 * w * h * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)

        // Fast buffer filling
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        input.rewind()

        if (resized !== bitmap) resized.recycle()

        val outShape = interp.getOutputTensor(0).shape()
        val outSize = outShape.last()
        val output = Array(1) { FloatArray(outSize) }
        interp.run(input, output)
        return output[0]
    }

    fun close() {
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            withTimeoutOrNull(2_000L) {
                inferenceLock.withLock { tearDown() }
            } ?: tearDown()
        }
    }

    private fun tearDown() {
        try { legacyInterpreter?.close() } catch (_: Throwable) {}
        try { nsfwInterpreter?.close() } catch (_: Throwable) {}
        try { genderInterpreter?.close() } catch (_: Throwable) {}
        try { gpuDelegate?.close() } catch (_: Throwable) {}
        legacyInterpreter = null
        nsfwInterpreter = null
        genderInterpreter = null
        gpuDelegate = null
    }

    companion object {
        const val MODEL_LEGACY = "guardian_model.tflite"
        const val MODEL_NSFW = "nsfw_model.tflite"
        const val MODEL_GENDER = "gender_model.tflite"
    }
}
