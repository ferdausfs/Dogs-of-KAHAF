package com.guardian.shield.service.detection

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class ImportProgress {
    data object Idle : ImportProgress()
    data class Working(val percent: Int) : ImportProgress()
    data class Success(val modelName: String, val sizeBytes: Long) : ImportProgress()
    data class Error(val modelName: String, val message: String) : ImportProgress()
}

@Singleton
class ModelImportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _progress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    suspend fun importModel(uri: Uri, modelName: String): Result<File> = withContext(Dispatchers.IO) {
        _progress.value = ImportProgress.Working(0)
        try {
            val finalFile = File(context.filesDir, modelName)
            val tmp = File(context.filesDir, "$modelName.tmp")

            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    val msg = "Cannot open file — try a different file manager"
                    _progress.value = ImportProgress.Error(modelName, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    val max = MAX_BYTES
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > max) {
                            tmp.delete()
                            val msg = "File too large (max 500 MB)"
                            _progress.value = ImportProgress.Error(modelName, msg)
                            return@withContext Result.failure(IllegalStateException(msg))
                        }
                        out.write(buf, 0, n)
                        _progress.value = ImportProgress.Working(
                            ((total * 100) / max).toInt().coerceAtMost(99)
                        )
                    }
                }
            }

            if (tmp.length() < 1024) {
                tmp.delete()
                val msg = "File too small — not a valid model"
                _progress.value = ImportProgress.Error(modelName, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tmp.renameTo(finalFile)) {
                tmp.copyTo(finalFile, overwrite = true)
                tmp.delete()
            }

            // R9 (v3.7.9) — DELIVERY FIX part 1: prove the file actually loads
            // as a TFLite model BEFORE calling it a success. Until now any
            // random file was "installed" and AI detection then failed forever
            // at inference time. Validation is CPU-only (no delegates) and
            // one-off; a bad file is rolled back with a clear message.
            val loads = runCatching {
                org.tensorflow.lite.Interpreter(finalFile).close()
            }.isSuccess
            if (!loads) {
                finalFile.delete()
                val msg = "Not a loadable .tflite model — nothing was installed"
                _progress.value = ImportProgress.Error(modelName, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            _progress.value = ImportProgress.Success(modelName, finalFile.length())
            Result.success(finalFile)
        } catch (t: Throwable) {
            Timber.e(t, "Import failed for $modelName")
            _progress.value = ImportProgress.Error(modelName, t.message ?: "error")
            Result.failure(t)
        }
    }

    fun isModelImported(modelName: String): Boolean {
        val f = File(context.filesDir, modelName)
        return f.exists() && f.length() > 0
    }

    fun modelSizeBytes(modelName: String): Long {
        val f = File(context.filesDir, modelName)
        return if (f.exists()) f.length() else 0L
    }

    fun deleteModel(modelName: String): Boolean {
        val f = File(context.filesDir, modelName)
        return if (f.exists()) f.delete() else false
    }

    companion object {
        const val MAX_BYTES: Long = 500L * 1024 * 1024
    }
}
