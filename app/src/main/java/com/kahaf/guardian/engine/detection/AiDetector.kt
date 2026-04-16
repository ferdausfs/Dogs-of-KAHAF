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

class AiDetector constructor(private val ctx: Context, private val settings: SettingsRepository) {
    private var interpreter: Interpreter? = null
    private var loaded = false
    @Volatile private var lastScan = 0L

    fun initialize() {
        if (loaded) return
        try { loadModel()?.let { interpreter = Interpreter(it); loaded = true } } catch (_: Exception) {}
    }

    suspend fun detect(bitmap: Bitmap, pkg: String): DetectionResult {
        if (!settings.isAiDetectionEnabled().first()) return DetectionResult(false, packageName = pkg)
        if (!loaded) { initialize(); if (!loaded) return DetectionResult(false, packageName = pkg) }
        val now = System.currentTimeMillis()
        if (now - lastScan < Constants.AI_SCAN_INTERVAL_MS) return DetectionResult(false, packageName = pkg)
        lastScan = now
        return withContext(Dispatchers.Default) {
            try {
                val s = Constants.AI_IMAGE_SIZE
                val resized = Bitmap.createScaledBitmap(bitmap, s, s, true)
                val buf = ByteBuffer.allocateDirect(4 * s * s * 3).order(ByteOrder.nativeOrder())
                val px = IntArray(s * s); resized.getPixels(px, 0, s, 0, 0, s, s)
                for (p in px) { buf.putFloat(((p shr 16) and 0xFF) / 255f); buf.putFloat(((p shr 8) and 0xFF) / 255f); buf.putFloat((p and 0xFF) / 255f) }
                buf.rewind(); val out = Array(1) { FloatArray(2) }
                interpreter?.run(buf, out)
                val score = out[0][1]; val thresh = if (settings.isStrictModeEnabled().first()) 0.5f else 0.7f
                if (score >= thresh) DetectionResult(true, BlockReason.AI_DETECTED, "AI: ${"%.1f".format(score * 100)}%", pkg, score)
                else DetectionResult(false, packageName = pkg)
            } catch (_: Exception) { DetectionResult(false, packageName = pkg) }
        }
    }

    private fun loadModel(): MappedByteBuffer? = try {
        val afd = ctx.assets.openFd("model.tflite")
        FileInputStream(afd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    } catch (_: Exception) { null }

    fun release() { interpreter?.close(); interpreter = null; loaded = false }
}
