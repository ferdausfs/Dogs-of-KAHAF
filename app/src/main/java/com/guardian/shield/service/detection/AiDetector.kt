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
    private val prefs: GuardianPreferences,
    private val falsePositiveMemory: FalsePositiveMemory
) {
    private val inferenceLock = Mutex()
    private var legacyInterpreter: Interpreter? = null
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
    @Volatile var cachedThreshold: Float = 0.72f
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
                try { prefs.aiThreshold.collect { cachedThreshold = it } }
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

    suspend fun ensureLoaded() {
        inferenceLock.withLock {
            if (legacyInterpreter == null) legacyInterpreter = tryLoad(MODEL_LEGACY)
            Timber.d("Models: legacy=${legacyInterpreter != null}")
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
            try { gpuDelegate?.close() } catch (_: Throwable) {}
            legacyInterpreter = null
            gpuDelegate = null

            legacyInterpreter = tryLoad(MODEL_LEGACY, forceCpu = true)
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
                    // Assign to local val first so Kotlin can smart-cast
                    // (gpuDelegate is a class var, so gpuDelegate!! is needed
                    // if passed directly — the local avoids the !!).
                    val newDelegate = GpuDelegate()
                    gpuDelegate = newDelegate
                    opts.addDelegate(newDelegate)
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

                // LEARNING MEMORY — if this exact pattern was already marked as a
                // false block by the user, never block it again (no inference needed).
                if (falsePositiveMemory.isKnown(bitmap)) {
                    Timber.d("Known false-positive pattern — skipping block")
                    return@withLock false
                }

                // Precision-first: honour the user slider down to 0.30, then
                // derive a per-cell floor a notch above it so grid votes stay
                // stricter than the whole-frame decision.
                val threshold = cachedThreshold.coerceIn(0.30f, 0.95f)
                val gridCellFloor = (threshold + 0.10f).coerceIn(0.55f, 0.92f)
                val voteNeeded = cachedGridVoteCount.coerceIn(1, 4)

                val fullOut = runInferenceSafe(interp, bitmap) ?: return@withLock false
                // Diagnostic: always log the raw model output so any false block
                // can be traced to the exact class scores (send us one line).
                Timber.d("Guardian out[${fullOut.size}] = ${fullOut.joinToString(" ")}")
                val fullScore = extractGuardianScore(fullOut)
                Timber.d("Guardian full: $fullScore / $threshold")

                // PRECISION-FIRST (v2.4.2): a frame counts as unsafe only when the
                // unsafe mass clearly beats the safe mass. Anything at/below the
                // hard floor is treated as safe and never reaches the grid — grid
                // scanning of noisy "Sexy" cells is what used to block cats,
                // cartoons and avatars on social feeds.
                if (fullScore < HARD_BLOCK_FLOOR) return@withLock false

                // Decisive whole-frame block only when the score is at/above the
                // user threshold (the hard floor is a lower bound on top of it),
                // so a busy feed can't trip on a single borderline number.
                if (fullScore >= threshold) return@withLock true

                // NO FALSE DETECTION: small cropped regions (avatars, thumbnails,
                // image tiles) lose their context once cropped, so they must NEVER
                // be blocked on a borderline score.
                if (bitmap.width < 500 || bitmap.height < 500) {
                    return@withLock false
                }

                // Whole frame is borderline: confirm with a high-density
                // overlapping grid (4x5, 25% overlap). Each cell must be
                // overwhelmingly unsafe (danger >> safe) to count as a vote, so
                // fur/faces that score "sexy" on tiny crops can't accumulate into
                // a false block.
                val regions = splitIntoOverlappingGrid(bitmap, cols = 4, rows = 5, overlapPercent = 0.25f)
                var triggeredCount = 0

                for ((idx, region) in regions.withIndex()) {
                    if (!isImageComplex(region)) {
                        region.recycle()
                        continue
                    }
                    try {
                        val out = runInferenceSafe(interp, region) ?: continue
                        val score = extractGuardianScore(out)
                        Timber.d("Grid[$idx]: $score")

                        // PRECISION-FIRST: a grid cell must be overwhelmingly
                        // unsafe to count as a vote (danger mass >> safe mass).
                        if (score >= gridCellFloor) triggeredCount++
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
            3 -> {
                // 3-class NSFW models are normally laid out as
                // [safe/neutral, questionable/sexy, explicit/porn], i.e. the
                // safe class comes first. We apply the SAME "safe-first"
                // principle as the 5-class branch below: the unsafe mass
                // (classes 1+2) must clearly beat the safe class (class 0)
                // before the score rises.
                //
                // The old code simply summed classes 1+2 with NO safe
                // subtraction, so swapping a 5-class model for a 3-class one
                // never reduced false positives — benign photos (a fully
                // clothed person, fashion, food) still scored high and fed the
                // grid scanner. This fix restores the intended behavior.
                val safe = scores.getOrElse(0) { 0f }
                val danger = (scores.getOrElse(1) { 0f } + scores.getOrElse(2) { 0f })
                    .coerceIn(0f, 1f)
                // danger dominates -> high; safe dominates -> low. Map to [0,1].
                ((danger - safe + 1.0f) / 2.0f).coerceIn(0f, 1f)
            }
            5 -> {
                // NSFWJS MobileNetV2 class order (softmax, sums to 1):
                //   [0]=Drawing (safe cartoon/art/anime)  [1]=Hentai (adult drawn)
                //   [2]=Neutral                            [3]=Porn   [4]=Sexy
                val drawing = scores.getOrElse(0) { 0f }
                val hentai  = scores.getOrElse(1) { 0f }
                val neutral = scores.getOrElse(2) { 0f }
                val porn    = scores.getOrElse(3) { 0f }
                val sexy    = scores.getOrElse(4) { 0f }

                // The model only "knows" two sides: safe mass (Neutral +
                // Drawing) vs unsafe mass (Porn + Sexy + Hentai). Block only
                // when unsafe CLEARLY dominates safe. This kills the classic
                // NSFWJS false positives — cats/fur/cartoons/food where "Sexy"
                // gets a weak 0.3-0.6 score but Neutral/Drawing are ahead.
                val danger = (porn + sexy + hentai) - (neutral + drawing) // [-1, 1]

                // Map to [0,1] so the existing threshold slider keeps working:
                //   0.50 = tie,  0.65 = unsafe ahead by 0.30,  ...
                ((danger + 1.0f) / 2.0f).coerceIn(0f, 1f)
            }
            else -> scores.drop(1).max()
        }
    }

    /**
     * Complexity check to ignore simple/blank images that often cause AI false positives.
     * Uses color variance and sampling to ensure we only scan photographic content.
     */
    private fun isImageComplex(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 64 || h < 64) return false

        val stepX = (w / 8).coerceAtLeast(1)
        val stepY = (h / 8).coerceAtLeast(1)
        var count = 0
        var sumL = 0.0
        var sumL2 = 0.0

        for (y in stepY until h step stepY) {
            for (x in stepX until w step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                // Rec. 601 luma
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                sumL += luma
                sumL2 += luma * luma
                count++
            }
        }

        if (count == 0) return false
        val avgL = sumL / count
        val variance = (sumL2 / count) - (avgL * avgL)

        // Variance threshold: flat UI elements/text usually have very low (<150)
        // or extreme contrast (e.g. black text on white).
        // Natural images usually sit in the 250-6000 range.
        return variance in 200.0..8500.0
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
        try { gpuDelegate?.close() } catch (_: Throwable) {}
        legacyInterpreter = null
        gpuDelegate = null
        inputBuffer = null
        pixelsArray = null
    }

    companion object {
        const val MODEL_LEGACY = "guardian_model.tflite"

        // PRECISION-FIRST (v2.4.2) — safety floors for blocking decisions.
        // Lowering these increases sensitivity but also raises false positives.
        const val HARD_BLOCK_FLOOR = 0.50f      // never block below this score
    }
}
