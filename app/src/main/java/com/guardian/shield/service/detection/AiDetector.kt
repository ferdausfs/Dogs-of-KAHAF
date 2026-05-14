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
                catch (t: Throwable) { Timber.e(t, "aiDetection collector crashed"); delay(1_000) }
            }
        }
        scope.launch {
            while (isActive) {
                try { prefs.userGender.collect { cachedUserGender = it; Timber.d("Gender cache: $it") } }
                catch (t: Throwable) { Timber.e(t, "userGender collector crashed"); delay(1_000) }
            }
        }
        scope.launch {
            while (isActive) {
                try { prefs.aiThreshold.collect { cachedThreshold = it } }
                catch (t: Throwable) { Timber.e(t, "aiThreshold collector crashed"); delay(1_000) }
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
        }
    }

    private fun tryLoad(name: String): Interpreter? {
        return try {
            val buffer = loadModelBuffer(name) ?: return null
            buildInterpreter(buffer)
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

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        val interp = legacyInterpreter ?: return false
        return inferenceLock.withLock {
            try {
                val threshold = cachedThreshold
                val w = bitmap.width
                val h = bitmap.height

                val crops = mutableListOf<Bitmap>()
                try {
                    crops.add(bitmap)
                    val sq = minOf(w, h)
                    crops.add(Bitmap.createBitmap(bitmap, (w - sq) / 2, (h - sq) / 2, sq, sq))
                    crops.add(Bitmap.createBitmap(bitmap, 0, 0, w, h / 2))
                    crops.add(Bitmap.createBitmap(bitmap, 0, h / 2, w, h / 2))
                    val stripW = (w * 0.45f).toInt()
                    crops.add(Bitmap.createBitmap(bitmap, 0, 0, stripW, h))
                    crops.add(Bitmap.createBitmap(bitmap, w - stripW, 0, stripW, h))
                    val bandX = (w * 0.25f).toInt()
                    val bandW = (w * 0.50f).toInt()
                    crops.add(Bitmap.createBitmap(bitmap, bandX, 0, bandW, h))

                    val scores = mutableListOf<Float>()
                    for ((i, bmp) in crops.withIndex()) {
                        val s = extractNsfwScore(runInference(interp, bmp))
                        Timber.d("AI crop[$i] score=$s threshold=$threshold")
                        scores.add(s)
                    }

                    val highVotes = scores.count { it >= threshold }
                    val medVotes  = scores.count { it >= threshold * 0.80f }
                    Timber.d("AI votes: high=$highVotes med=$medVotes max=${scores.maxOrNull() ?: 0f}")

                    when {
                        highVotes >= 1 -> true
                        medVotes  >= 3 -> true
                        else           -> false
                    }
                } finally {
                    crops.drop(1).forEach { if (it != bitmap) runCatching { it.recycle() } }
                }
            } catch (t: Throwable) {
                Timber.e(t, "isUnsafe failed")
                false
            }
        }
    }

    private fun extractNsfwScore(scores: FloatArray): Float {
        return when (scores.size) {
            1    -> scores[0]
            2    -> scores[1]
            5    -> scores.getOrElse(1){0f} + scores.getOrElse(3){0f} + scores.getOrElse(4){0f}
            else -> scores.last()
        }
    }

    suspend fun isOppositeGenderNsfw(bitmap: Bitmap, userGender: String): Boolean {
        val nsfw = nsfwInterpreter ?: return false
        val gender = genderInterpreter ?: return false
        if (userGender != "MALE" && userGender != "FEMALE") return false
        return inferenceLock.withLock {
            try {
                val currentGender = runCatching { prefs.userGender.first() }.getOrElse { userGender }
                if (currentGender != "MALE" && currentGender != "FEMALE") return@withLock false
                val w = bitmap.width
                val h = bitmap.height

                val crops = mutableListOf<Bitmap>()
                try {
                    crops.add(bitmap)
                    val sq = minOf(w, h)
                    crops.add(Bitmap.createBitmap(bitmap, (w - sq) / 2, (h - sq) / 2, sq, sq))
                    crops.add(Bitmap.createBitmap(bitmap, 0, 0, w, (h * 0.6f).toInt()))
                    val sw = (w * 0.45f).toInt()
                    crops.add(Bitmap.createBitmap(bitmap, 0, 0, sw, h))
                    crops.add(Bitmap.createBitmap(bitmap, w - sw, 0, sw, h))

                    var genderHits = 0
                    var nsfwHits   = 0
                    val targetGender = if (currentGender == "MALE") "FEMALE" else "MALE"

                    for (crop in crops) {
                        val gScores = runInference(gender, crop)
                        val maleP   = gScores.getOrNull(0) ?: 0f
                        val femaleP = gScores.getOrNull(1) ?: 0f
                        val genderScore = if (targetGender == "FEMALE") femaleP else maleP
                        val nsfwScore = extractNsfwScore(runInference(nsfw, crop))
                        Timber.d("GenderNsfw: gender=$genderScore nsfw=$nsfwScore target=$targetGender")
                        if (genderScore >= GuardianConstants.GENDER_CONFIDENCE_THRESHOLD) genderHits++
                        if (nsfwScore  >= GuardianConstants.NSFW_GATE_THRESHOLD)          nsfwHits++
                    }

                    genderHits >= 1 && nsfwHits >= 1
                } finally {
                    crops.drop(1).forEach { runCatching { it.recycle() } }
                }
            } catch (t: Throwable) {
                Timber.e(t, "isOppositeGenderNsfw failed")
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
            input.putFloat(((p shr 8)  and 0xff) / 255f)
            input.putFloat((p and 0xff)           / 255f)
        }
        input.rewind()
        if (resized !== bitmap) resized.recycle()
        val outShape = interp.getOutputTensor(0).shape()
        val outSize  = outShape.fold(1) { acc, i -> acc * i }
        val output   = Array(1) { FloatArray(outSize) }
        interp.run(input, output)
        return output[0]
    }

    private fun cropSquare(b: Bitmap): Bitmap {
        val side = minOf(b.width, b.height)
        return Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
    }

    private fun cropVertical(b: Bitmap, fromRatio: Float, toRatio: Float): Bitmap {
        val y = (b.height * fromRatio).toInt().coerceAtLeast(0)
        val h = (b.height * (toRatio - fromRatio)).toInt().coerceAtLeast(1)
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
        try { nsfwInterpreter?.close()  } catch (_: Throwable) {}
        try { genderInterpreter?.close()} catch (_: Throwable) {}
        try { gpuDelegate?.close()      } catch (_: Throwable) {}
        legacyInterpreter = null; nsfwInterpreter = null
        genderInterpreter = null; gpuDelegate = null
    }

    companion object {
        const val MODEL_LEGACY = "guardian_model.tflite"
        const val MODEL_NSFW   = "nsfw_model.tflite"
        const val MODEL_GENDER = "gender_model.tflite"
    }
}