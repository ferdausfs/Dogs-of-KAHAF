package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            try {
                prefs.aiDetection.collect { cachedAiEnabled = it }
            } catch (t: Throwable) { Timber.e(t) }
        }
        scope.launch {
            try {
                prefs.userGender.collect { cachedUserGender = it }
            } catch (t: Throwable) { Timber.e(t) }
        }
        scope.launch {
            try {
                prefs.aiThreshold.collect { cachedThreshold = it }
            } catch (t: Throwable) { Timber.e(t) }
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
        // 1) filesDir → mmap
        val f = File(context.filesDir, name)
        if (f.exists() && f.length() > 0) {
            return try {
                FileInputStream(f).channel.use { ch ->
                    ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
                } as MappedByteBuffer
            } catch (t: Throwable) {
                Timber.w(t, "mmap failed; copying $name to buffer")
                f.readBytes().let { bytes ->
                    ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
                }
            }
        }
        // 2) assets
        return try {
            context.assets.open(name).use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return null
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
            }
        } catch (_: Throwable) { null }
    }

    private fun buildInterpreter(buffer: ByteBuffer): Interpreter {
        val opts = Interpreter.Options()
        try {
            val cl = CompatibilityList()
            if (cl.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(cl.bestOptionsForThisDevice)
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
            val cpuOpts = Interpreter.Options().setNumThreads(2)
            Interpreter(buffer, cpuOpts)
        }
    }

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        val interp = legacyInterpreter ?: return false
        return inferenceLock.withLock {
            try {
                val full = runInference(interp, bitmap)
                val threshold = cachedThreshold
                if (full.maxOrNull() ?: 0f < threshold * GuardianConstants.EARLY_EXIT_RATIO) {
                    return@withLock false
                }
                val centerCrop = cropSquare(bitmap)
                val centerScore = runInference(interp, centerCrop)
                centerCrop.recycle()
                if (centerScore.maxOrNull() ?: 0f >= threshold) return@withLock true

                val topCrop = cropVertical(bitmap, 0f, 0.72f)
                val topScore = runInference(interp, topCrop)
                topCrop.recycle()
                if (topScore.maxOrNull() ?: 0f >= threshold) return@withLock true

                val lowerCrop = cropVertical(bitmap, 0.18f, 1f)
                val lowerScore = runInference(interp, lowerCrop)
                lowerCrop.recycle()

                val max = maxOf(
                    full.maxOrNull() ?: 0f,
                    centerScore.maxOrNull() ?: 0f,
                    topScore.maxOrNull() ?: 0f,
                    lowerScore.maxOrNull() ?: 0f
                )
                max >= threshold
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
                val nsfwScores = runInference(nsfw, bitmap)
                val nsfwProb = nsfwScores.maxOrNull() ?: 0f
                if (nsfwProb < GuardianConstants.NSFW_GATE_THRESHOLD) return@withLock false
                val genderScores = runInference(gender, bitmap)
                val maleProb = genderScores.getOrNull(0) ?: 0f
                val femaleProb = genderScores.getOrNull(1) ?: 0f
                when (userGender) {
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

    private fun runInference(interp: Interpreter, bitmap: Bitmap): FloatArray {
        val inputShape = interp.getInputTensor(0).shape() // [1,H,W,3]
        val h = inputShape.getOrNull(1) ?: 224
        val w = inputShape.getOrNull(2) ?: 224
        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val input = ByteBuffer.allocateDirect(4 * 1 * w * h * 3).order(ByteOrder.nativeOrder())
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
        val x = (b.width - side) / 2
        val y = (b.height - side) / 2
        return Bitmap.createBitmap(b, x, y, side, side)
    }

    private fun cropVertical(b: Bitmap, fromRatio: Float, toRatio: Float): Bitmap {
        val y = (b.height * fromRatio).toInt().coerceAtLeast(0)
        val h = ((b.height * (toRatio - fromRatio)).toInt()).coerceAtLeast(1)
        return Bitmap.createBitmap(b, 0, y, b.width, h.coerceAtMost(b.height - y))
    }

    fun close() {
        runBlocking(Dispatchers.IO) {
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
