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

    // ✅ সব threshold cached — DataStore থেকে live update
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
        // ✅ নতুন threshold collectors
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
            Timber.w(t, "GPU failed; CPU retry")
            try { gpuDelegate?.close() } catch (_: Throwable) {}
            gpuDelegate = null
            Interpreter(buffer, Interpreter.Options().setNumThreads(2))
        }
    }

    // ✅ Guardian model [1,3] — Grid scan with voting
    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        val interp = legacyInterpreter ?: return false
        return inferenceLock.withLock {
            try {
                val threshold = cachedThreshold
                val voteNeeded = cachedGridVoteCount

                // Step 1: Full image
                val fullScore = extractGuardianScore(runInference(interp, bitmap))
                Timber.d("Guardian full: $fullScore / $threshold")

                // Clearly safe → skip grid
                if (fullScore < threshold * 0.3f) return@withLock false

                // Clearly unsafe → block immediately
                if (fullScore >= threshold) return@withLock true

                // Borderline → Grid scan with voting
                val regions = splitIntoGrid(bitmap, cols = 2, rows = 3)
                var triggeredCount = 0

                for ((idx, region) in regions.withIndex()) {
                    try {
                        val score = extractGuardianScore(runInference(interp, region))
                        Timber.d("Grid[$idx]: $score")
                        if (score >= threshold) triggeredCount++
                        // Early exit — enough votes collected
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

    // ✅ Gender+NSFW — DataStore threshold use করো
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

                // NSFW gate — full image first
                var maxNsfwScore = extractNsfwGateScore(runInference(nsfw, bitmap))
                Timber.d("NSFW gate full: $maxNsfwScore / $nsfwGate")

                // Grid scan for NSFW gate if borderline
                if (maxNsfwScore < nsfwGate) {
                    val regions = splitIntoGrid(bitmap, cols = 2, rows = 3)
                    var nsfwVotes = 0
                    for ((idx, region) in regions.withIndex()) {
                        try {
                            val score = extractNsfwGateScore(runInference(nsfw, region))
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

                // Gender check
                val genderScores = runInference(gender, bitmap)
                val half = genderScores.size / 2
                val firstSum = genderScores.take(half).sum()
                val secondSum = genderScores.drop(half).sum()
                val total = (firstSum + secondSum).coerceAtLeast(0.001f)
                val femaleProb = firstSum / total
                val maleProb = secondSum / total

                Timber.d("Gender: male=$maleProb female=$femaleProb conf=$genderConf user=$currentGender")

                when (currentGender) {
                    "MALE" -> femaleProb >= genderConf
                    "FEMALE" -> maleProb >= genderConf
                    else -> false
                }
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