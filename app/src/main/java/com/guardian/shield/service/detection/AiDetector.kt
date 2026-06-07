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

    // REUSABLE BUFFERS to reduce GC pressure
    private var inputBuffer: ByteBuffer? = null
    private var pixelsArray: IntArray? = null

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
                if (!isImageComplex(bitmap)) return@withLock false

                // Increased local threshold for "Safe First"
                val threshold = cachedThreshold.coerceAtLeast(0.80f)
                // Ultimate Level: Require sufficient votes but sensitive to small fragments
                val voteNeeded = (cachedGridVoteCount + 1).coerceAtMost(4)

                val fullScore = extractGuardianScore(runInferenceSafe(interp, bitmap)
                    ?: return@withLock false)
                Timber.d("Guardian full: $fullScore / $threshold")

                if (fullScore < threshold * 0.3f) return@withLock false
                if (fullScore >= threshold) return@withLock true

                // ULTIMATE LEVEL: Use a ultra-high-density overlapping grid (5x6) to catch even tiny social media feed images.
                // 35% overlap ensures maximum coverage of content boundaries.
                val regions = splitIntoOverlappingGrid(bitmap, cols = 5, rows = 6, overlapPercent = 0.35f)
                // Scan the entire grid for absolute coverage in "Ultimate Level" analysis
                var triggeredCount = 0

                for ((idx, region) in regions.withIndex()) {
                    try {
                        val out = runInferenceSafe(interp, region) ?: continue
                        val score = extractGuardianScore(out)
                        Timber.d("Grid[$idx]: $score")

                        // Ultimate Level: Lower threshold for grid cells to catch partial exposure
                        val effectiveThreshold = threshold * 0.90f

                        if (score >= effectiveThreshold) triggeredCount++
                        if (triggeredCount >= voteNeeded) break
                    } catch (t: Throwable) {
                        Timber.e(t, "Grid[$idx] error")
                    } finally {
                        region.recycle()
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
                if (!isImageComplex(bitmap)) return@withLock false

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
                    val regions = splitIntoOverlappingGrid(bitmap, cols = 5, rows = 6, overlapPercent = 0.35f)
                    var nsfwVotes = 0
                    for ((idx, region) in regions.withIndex()) {
                        try {
                            val out = runInferenceSafe(nsfw, region) ?: continue
                            val score = extractNsfwGateScore(out)
                            Timber.d("NSFW Grid[$idx]: $score")
                            if (score > maxNsfwScore) maxNsfwScore = score

                            // Ultimate Level: Higher sensitivity for small image fragments in grid
                            if (score >= nsfwGate * 0.85f) nsfwVotes++

                            if (nsfwVotes >= voteNeeded) break
                        } catch (t: Throwable) {
                            Timber.e(t, "NSFW Grid[$idx] error")
                        } finally {
                            region.recycle()
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

                // HYBRID ULTIMATE DETECTION:
                // If it's a "Soft" NSFW match (e.g. lingerie shown in screenshot),
                // we significantly lower the gender confidence requirement to catch semi-nudes.
                val isSoftNsfw = maxNsfwScore >= com.guardian.shield.util.GuardianConstants.SOFT_NSFW_THRESHOLD

                if (isSoftNsfw) {
                    // ULTIMATE LEVEL: Reduce gender confidence requirement by 20% for soft-NSFW hits (Increased sensitivity)
                    val softGenderConf = (genderConf * 0.80f).coerceAtLeast(0.62f)
                    val softGenderMatch = when (currentGender) {
                        "MALE" -> femaleProb >= softGenderConf
                        "FEMALE" -> maleProb >= softGenderConf
                        else -> false
                    }
                    if (softGenderMatch) {
                        Timber.i("Hybrid block (Ultimate): softGenderMatch=$softGenderMatch score=$maxNsfwScore")
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

    private fun splitIntoOverlappingGrid(
        bitmap: Bitmap,
        cols: Int,
        rows: Int,
        overlapPercent: Float = 0f
    ): List<Bitmap> {
        val regions = mutableListOf<Bitmap>()
        val w = bitmap.width
        val h = bitmap.height

        // Calculate cell sizes
        val cellW = w / cols
        val cellH = h / rows

        // Calculate step sizes (smaller than cell size if overlap > 0)
        val stepX = (cellW * (1f - overlapPercent)).toInt().coerceAtLeast(cellW / 2)
        val stepY = (cellH * (1f - overlapPercent)).toInt().coerceAtLeast(cellH / 2)

        var y = 0
        while (y + cellH <= h || (y < h && y + cellH > h)) {
            val currentH = if (y + cellH > h) h - y else cellH
            if (currentH < 64) break

            var x = 0
            while (x + cellW <= w || (x < w && x + cellW > w)) {
                val currentW = if (x + cellW > w) w - x else cellW
                if (currentW < 64) break

                runCatching {
                    regions.add(Bitmap.createBitmap(bitmap, x, y, currentW, currentH))
                }.onFailure { Timber.e(it, "Grid crop at $x,$y") }

                if (x + cellW >= w) break
                x += stepX
                if (x + cellW > w) x = w - cellW
            }

            if (y + cellH >= h) break
            y += stepY
            if (y + cellH > h) y = h - cellH
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
     * Complexity check to ignore simple/blank images that often cause AI false positives.
     */
    private fun isImageComplex(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return false

        // Sample a few pixels to check for variance
        val samples = intArrayOf(
            bitmap.getPixel(w / 4, h / 4),
            bitmap.getPixel(3 * w / 4, h / 4),
            bitmap.getPixel(w / 4, 3 * h / 4),
            bitmap.getPixel(3 * w / 4, 3 * h / 4),
            bitmap.getPixel(w / 2, h / 2)
        )
        val first = samples[0]
        return samples.any { it != first }
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

        // Ensure buffers are allocated and large enough
        val bufferSize = 4 * w * h * 3
        val currentInput = inputBuffer.takeIf { it?.capacity() == bufferSize }
            ?: ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder()).also { inputBuffer = it }

        val pixelCount = w * h
        val currentPixels = pixelsArray.takeIf { it?.size == pixelCount }
            ?: IntArray(pixelCount).also { pixelsArray = it }

        currentInput.rewind()

        // Use a more memory-efficient scaling if needed
        val resized = if (bitmap.width != w || bitmap.height != h) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        resized.getPixels(currentPixels, 0, w, 0, 0, w, h)

        // Fast buffer filling with zero-allocation loop
        val inv255 = 1.0f / 255.0f
        for (p in currentPixels) {
            currentInput.putFloat(((p shr 16) and 0xFF) * inv255)
            currentInput.putFloat(((p shr 8) and 0xFF) * inv255)
            currentInput.putFloat((p and 0xFF) * inv255)
        }
        currentInput.rewind()

        if (resized !== bitmap) resized.recycle()

        val outShape = interp.getOutputTensor(0).shape()
        val outSize = outShape.last()
        val output = Array(1) { FloatArray(outSize) }
        interp.run(currentInput, output)
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
        inputBuffer = null
        pixelsArray = null
    }

    companion object {
        const val MODEL_LEGACY = "guardian_model.tflite"
        const val MODEL_NSFW = "nsfw_model.tflite"
        const val MODEL_GENDER = "gender_model.tflite"
    }
}
