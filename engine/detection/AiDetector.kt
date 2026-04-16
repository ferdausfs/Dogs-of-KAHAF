package com.kahaf.guardian.engine.detection

import android.content.Context
import android.graphics.Bitmap
import com.kahaf.guardian.domain.model.BlockReason
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDetector @Inject constructor(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    @Volatile
    private var lastScanTime = 0L

    private val nsfwThreshold: Float
        get() = 0.7f // Default threshold

    private val strictThreshold: Float
        get() = 0.5f

    fun initialize() {
        if (isModelLoaded) return
        try {
            val model = loadModelFile()
            if (model != null) {
                interpreter = Interpreter(model)
                isModelLoaded = true
            }
        } catch (e: Exception) {
            // Model not available - AI detection will be disabled
            isModelLoaded = false
        }
    }

    suspend fun detect(bitmap: Bitmap, packageName: String): DetectionResult {
        if (!settingsRepository.isAiDetectionEnabled().first()) {
            return DetectionResult(shouldBlock = false, packageName = packageName)
        }

        if (!isModelLoaded) {
            initialize()
            if (!isModelLoaded) {
                return DetectionResult(shouldBlock = false, packageName = packageName)
            }
        }

        // Rate limit: max once every AI_SCAN_INTERVAL_MS
        val now = System.currentTimeMillis()
        if (now - lastScanTime < Constants.AI_SCAN_INTERVAL_MS) {
            return DetectionResult(shouldBlock = false, packageName = packageName)
        }
        lastScanTime = now

        return withContext(Dispatchers.Default) {
            try {
                val resized = Bitmap.createScaledBitmap(
                    bitmap,
                    Constants.AI_IMAGE_SIZE,
                    Constants.AI_IMAGE_SIZE,
                    true
                )
                val inputBuffer = bitmapToByteBuffer(resized)
                val output = Array(1) { FloatArray(2) } // [safe, nsfw]

                interpreter?.run(inputBuffer, output)

                val nsfwScore = output[0][1]
                val isStrict = settingsRepository.isStrictModeEnabled().first()
                val threshold = if (isStrict) strictThreshold else nsfwThreshold

                if (nsfwScore >= threshold) {
                    DetectionResult(
                        shouldBlock = true,
                        reason = BlockReason.AI_DETECTED,
                        details = "AI confidence: ${"%.1f".format(nsfwScore * 100)}%",
                        packageName = packageName,
                        confidence = nsfwScore
                    )
                } else {
                    DetectionResult(shouldBlock = false, packageName = packageName)
                }
            } catch (e: Exception) {
                DetectionResult(shouldBlock = false, packageName = packageName)
            }
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val size = Constants.AI_IMAGE_SIZE
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
            buffer.putFloat((pixel and 0xFF) / 255.0f)           // B
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            val assetFileDescriptor = context.assets.openFd("model.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            null
        }
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
}