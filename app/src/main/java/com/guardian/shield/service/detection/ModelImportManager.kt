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
                    val msg = "Cannot open uri"
                    _progress.value = ImportProgress.Error(modelName, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val header = ByteArray(8)
                val read = input.read(header)
                if (read < 8 || !(header[4] == 'T'.code.toByte()
                            && header[5] == 'F'.code.toByte()
                            && header[6] == 'L'.code.toByte()
                            && header[7] == '3'.code.toByte())
                ) {
                    val msg = "Invalid TFLite header"
                    _progress.value = ImportProgress.Error(modelName, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                tmp.outputStream().use { out ->
                    out.write(header, 0, read)
                    val buf = ByteArray(64 * 1024)
                    var total = read.toLong()
                    val max = MAX_BYTES
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > max) {
                            tmp.delete()
                            val msg = "Exceeds size limit"
                            _progress.value = ImportProgress.Error(modelName, msg)
                            return@withContext Result.failure(IllegalStateException(msg))
                        }
                        out.write(buf, 0, n)
                        _progress.value = ImportProgress.Working(((total * 100) / max).toInt().coerceAtMost(99))
                    }
                }
            }
            if (finalFile.exists()) finalFile.delete()
            if (!tmp.renameTo(finalFile)) {
                tmp.copyTo(finalFile, overwrite = true)
                tmp.delete()
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
