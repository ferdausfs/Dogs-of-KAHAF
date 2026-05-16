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
    @Volatile var cachedThreshold: Float = 0.5f
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

    // =========================================
    // GUARDIAN MODEL [1,3] — Grid Scan
    // =========================================
    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        val interp = legacyInterpreter ?: return false
        return inferenceLock.withLock {
            try {
                // guardian_model threshold: user setting - 0.2, min 0.3
                val threshold = (cachedThreshold - 0.2f).coerceAtLeast(0.3f)

                // Step 1: Full image — fast check
                val fullScore = extractGuardianScore(runInference(interp, bitmap))
                Timber.d("Guardian full: $fullScore threshold: $threshold")
                if (fullScore >= threshold) return@withLock true

                // Step 2: Very safe → skip grid (performance)
                if (fullScore < threshold * GuardianConstants.EARLY_EXIT_RATIO) {
                    return@withLock false
                }

                // Step 3: Grid scan — 2 cols × 3 rows = 6 regions
                // YouTube feed এ top content + bottom UI mix থাকে
                // Grid দিয়ে প্রতিটা region আলাদা check করো
                val regions = splitIntoGrid(bitmap, cols = 2, rows = 3)
                var blocked = false
                for ((idx, region) in regions.withIndex()) {
                    try {
                        val score = extractGuardianScore(runInference(interp, region))
                        Timber.d("Grid[$idx]: $score")
                        if (score >= threshold) {
                            blocked = true
                            region.recycle()
                            break
                        }
                    } catch (t: Throwable) {
                        Timber.e(t, "Grid[$idx] error")
                    } finally {
                        if (!region.isRecycled) region.recycle()
                    }
                }

                blocked
            } catch (t: Throwable) {
                Timber.e(t, "isUnsafe failed")
                false
            }
        }
    }

    // =========================================
    // NSFW MODEL [1,5] gate — Grid Scan
    // =========================================
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

                // Step 1: NSFW gate — full image first
                val fullNsfwScore = extractNsfwGateScore(runInference(nsfw, bitmap))
                Timber.d("NSFW gate full: $fullNsfwScore")

                // Step 2: Grid scan for NSFW gate
                var maxNsfwScore = fullNsfwScore
                if (fullNsfwScore < GuardianConstants.NSFW_GATE_THRESHOLD) {
                    val regions = splitIntoGrid(bitmap, cols = 2, rows = 3)
                    for ((idx, region) in regions.withIndex()) {
                        try {
                            val score = extractNsfwGateScore(runInference(nsfw, region))
                            Timber.d("NSFW Grid[$idx]: $score")
                            if (score > maxNsfwScore) maxNsfwScore = score
                        } catch (t: Throwable) {
                            Timber.e(t, "NSFW Grid[$idx] error")
                        } finally {
                            if (!region.isRecycled) region.recycle()
                        }
                    }
                }

                Timber.d("NSFW max score: $maxNsfwScore threshold: ${GuardianConstants.NSFW_GATE_THRESHOLD}")
                if (maxNsfwScore < GuardianConstants.NSFW_GATE_THRESHOLD) return@withLock false

                // Step 3: Gender check — full image only (faster)
                val genderScores = runInference(gender, bitmap)
                val half = genderScores.size / 2
                val firstHalfSum = genderScores.take(half).sum()
                val secondHalfSum = genderScores.drop(half).sum()
                val total = (firstHalfSum + secondHalfSum).coerceAtLeast(0.001f)
                val femaleProb = firstHalfSum / total
                val maleProb = secondHalfSum / total

                Timber.d("Gender: male=$maleProb female=$femaleProb user=$currentGender")

                when (currentGender) {
                    "MALE" -> femaleProb >= GuardianConstants.GENDER_CONFIDENCE_THRESHOLD
                    "FEMALE" -> maleProb >= GuardianConstants.GENDER_CONFIDENCE_THRESHOLD
                    else -> false
                }
            } catch (t: Throwable) {
                Timber.e(t, "Gender NSFW failed")
                false
            }
        }
    }

    // =========================================
    // Grid split — bitmap কে regions এ ভাগ করো
    // =========================================
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
                    }.onFailure { Timber.e(it, "Grid crop failed [$row,$col]") }
                }
            }
        }
        return regions
    }

    // =========================================
    // Score extractors
    // =========================================

    /**
     * guardian_model [1,3]:
     * index 0 = safe
     * index 1 = nsfw/porn
     * index 2 = explicit/sexy
     * → sum of index 1+2 = total harmful score
     */
    private fun extractGuardianScore(scores: FloatArray): Float {
        return when (scores.size) {
            1 -> scores[0]
            2 -> scores[1]
            3 -> (scores.getOrElse(1){0f} + scores.getOrElse(2){0f}).coerceAtMost(1.0f)
            5 -> maxOf(
                scores.getOrElse(1){0f},  // hentai
                scores.getOrElse(3){0f},  // porn
                scores.getOrElse(4){0f}   // sexy
            )
            else -> scores.drop(1).max()
        }
    }

    /**
     * nsfw_model [1,5]:
     * Yahoo Open NSFW: [drawings, hentai, neutral, porn, sexy]
     * → MAX of harmful (hentai, porn, sexy)
     * NOT sum — sum can exceed 1.0
     */
    private fun extractNsfwGateScore(scores: FloatArray): Float {
        return when (scores.size) {
            1 -> scores[0]
            2 -> scores[1]
            5 -> maxOf(
                scores.getOrElse(1){0f},  // hentai
                scores.getOrElse(3){0f},  // porn
                scores.getOrElse(4){0f}   // sexy
            )
            else -> scores.drop(1).max()
        }
    }

    // =========================================
    // Inference runner
    // =========================================
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