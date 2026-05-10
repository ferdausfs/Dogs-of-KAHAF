package com.kahaf.guardianshield.data.classifier

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

/**
 * Imports user-selected TFLite model files from Storage Access Framework
 * (Uri) into the app's private filesDir, where the classifier reads them
 * from. Ported from the legacy v2.x codebase into the v3.0.0 (kahaf)
 * architecture.
 *
 * Supported model file names:
 *   • [NSFW_MODEL_FILE]   — "nsfw_model.tflite"
 *   • [GENDER_MODEL_FILE] — "gender_model.tflite"
 *
 * Design notes:
 *   - Atomic copy via a `.tmp` file: the existing model is only replaced
 *     after a successful & validated copy. A crash mid-copy leaves the
 *     previously-imported model intact.
 *   - File-format sanity check: validates the TFLite FlatBuffer magic
 *     header ("TFL3" at offset 4) so users can't accidentally import a
 *     random binary that happens to be named *.tflite.
 *   - Final validation step opens the model in a throwaway TFLite
 *     interpreter to confirm it's not corrupt / wrong-arch / wrong-shape.
 *   - Size guards prevent zero-byte / runaway-size imports.
 *   - All I/O on Dispatchers.IO. No exceptions propagate — errors come
 *     back inside Result.failure(...).
 */
@Singleton
class ModelImportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NSFW_MODEL_FILE   = "nsfw_model.tflite"
        const val GENDER_MODEL_FILE = "gender_model.tflite"

        /** Min plausible size of a TFLite model — anything below this is junk. */
        private const val MIN_VALID_MODEL_SIZE = 1024L                // 1 KB
        /** Hard cap to refuse absurdly large imports. */
        private const val MAX_VALID_MODEL_SIZE = 500L * 1024 * 1024   // 500 MB
        private const val COPY_BUFFER_SIZE = 8 * 1024                 // 8 KB
        private const val TAG = "ModelImportManager"
    }

    private val allowedNames = setOf(NSFW_MODEL_FILE, GENDER_MODEL_FILE)

    /**
     * Copy the file behind [uri] into filesDir as [modelName].
     *
     * @return Result.success(Unit) on success, or Result.failure(...) with
     *         a human-readable cause if anything went wrong. Never throws.
     */
    suspend fun importModel(uri: Uri, modelName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (modelName !in allowedNames) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unsupported model name: $modelName")
                )
            }

            val targetFile = File(context.filesDir, modelName)
            val tempFile   = File(context.filesDir, "$modelName.tmp")

            if (tempFile.exists()) tempFile.delete()

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        IOException("Could not open the selected file")
                    )

                var copiedBytes = 0L
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (copiedBytes > MAX_VALID_MODEL_SIZE) {
                                throw IOException(
                                    "File is too large (>500MB). Please pick a valid TFLite model."
                                )
                            }
                            read = input.read(buffer)
                        }
                        output.flush()
                        runCatching { output.fd.sync() }
                    }
                }

                if (copiedBytes < MIN_VALID_MODEL_SIZE) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("File is too small to be a valid TFLite model")
                    )
                }
                if (!isValidTfliteFile(tempFile)) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Selected file is not a valid TFLite model (header check failed)")
                    )
                }

                if (targetFile.exists() && !targetFile.delete()) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Could not remove the previously-imported model")
                    )
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Could not finalize the imported model file")
                    )
                }

                // Final validation: ensure the file actually loads in TFLite.
                val validation = runCatching {
                    val testInterp = Interpreter(targetFile)
                    testInterp.close()
                }
                if (validation.isFailure) {
                    val cause = validation.exceptionOrNull()
                    Log.w(TAG, "Imported model failed TFLite validation", cause)
                    runCatching { targetFile.delete() }
                    return@withContext Result.failure(
                        IOException(
                            "Model validation failed: " +
                                (cause?.message ?: "interpreter could not load file")
                        )
                    )
                }

                Log.i(TAG, "Imported model '$modelName' (${formatSize(copiedBytes)})")
                Result.success(Unit)
            } catch (sec: SecurityException) {
                runCatching { tempFile.delete() }
                Log.e(TAG, "Permission denied while importing $modelName", sec)
                Result.failure(IOException("Permission denied for the selected file", sec))
            } catch (oom: OutOfMemoryError) {
                runCatching { tempFile.delete() }
                Log.e(TAG, "OOM while importing $modelName", oom)
                Result.failure(IOException("Not enough memory to import this file"))
            } catch (t: Throwable) {
                runCatching { tempFile.delete() }
                Log.e(TAG, "Failed to import $modelName", t)
                Result.failure(t)
            }
        }

    fun isImported(modelName: String): Boolean {
        if (modelName !in allowedNames) return false
        val file = File(context.filesDir, modelName)
        return file.exists() && file.isFile && file.length() >= MIN_VALID_MODEL_SIZE
    }

    fun deleteModel(modelName: String): Boolean {
        if (modelName !in allowedNames) return false
        val file = File(context.filesDir, modelName)
        return runCatching { file.exists() && file.delete() }.getOrDefault(false)
    }

    fun getModelSize(modelName: String): String? {
        if (modelName !in allowedNames) return null
        val file = File(context.filesDir, modelName)
        if (!file.exists() || !file.isFile) return null
        return formatSize(file.length())
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
            .coerceIn(0, units.size - 1)
        val size = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format("%.1f %s", size, units[digitGroups])
    }

    /**
     * TFLite files are FlatBuffers with the identifier "TFL3" at byte offset 4.
     * This is a cheap, deterministic guard against picking a non-tflite file.
     */
    private fun isValidTfliteFile(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(8)
            val read = input.read(header)
            if (read < 8) return@runCatching false
            header[4] == 'T'.code.toByte() &&
                header[5] == 'F'.code.toByte() &&
                header[6] == 'L'.code.toByte() &&
                header[7] == '3'.code.toByte()
        }
    }.getOrDefault(false)
}
