package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import com.guardian.shield.data.local.datastore.GuardianPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences
) {
    companion object {
        const val MODEL_FILE = "guardian_model.tflite"
        const val INPUT_SIZE = 224
    }

    private var interpreter: Interpreter? = null
    private val processor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun isModelAvailable(): Boolean = File(context.filesDir, MODEL_FILE).exists()

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (interpreter != null) return true
        val file = File(context.filesDir, MODEL_FILE)
        if (!file.exists()) return false
        return runCatching {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes); buffer.rewind()
            interpreter = Interpreter(buffer)
            true
        }.onFailure { Timber.e(it, "Failed to load TFLite model") }.getOrDefault(false)
    }

    suspend fun isUnsafe(bitmap: Bitmap): Boolean {
        if (!ensureLoaded()) return false
        val threshold = prefs.aiThreshold.first()
        val tensor = TensorImage(org.tensorflow.lite.DataType.FLOAT32).apply { load(bitmap) }
        val processed = processor.process(tensor)
        val out2 = Array(1) { FloatArray(2) }
        return runCatching {
            interpreter!!.run(processed.buffer, out2)
            out2[0][1] >= threshold
        }.recoverCatching {
            val out5 = Array(1) { FloatArray(5) }
            interpreter!!.run(processed.buffer, out5)
            (out5[0][1] + out5[0][3] + out5[0][4]) >= threshold
        }.getOrDefault(false)
    }

    fun close() { interpreter?.close(); interpreter = null }
}
