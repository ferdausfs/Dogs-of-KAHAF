package com.guardian.shield

import android.app.Application
import com.guardian.shield.service.detection.AiDetector
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File

@HiltAndroidApp
class GuardianApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        copyModelFromAssetsIfNeeded()
    }

    private fun copyModelFromAssetsIfNeeded() {
        Thread {
            try {
                val dest = AiDetector.modelFile(this)
                if (dest.exists() && dest.length() > 1024) {
                    Timber.d("GuardianApp: model already exists (${dest.length() / 1024}KB), skipping copy")
                    return@Thread
                }

                val assetList = assets.list("") ?: emptyArray()
                if (!assetList.contains(AiDetector.MODEL_FILENAME)) {
                    Timber.w("GuardianApp: ${AiDetector.MODEL_FILENAME} not found in assets — user must import manually via Settings")
                    return@Thread
                }

                val temp = File(dest.parent, "model_temp.tflite")
                assets.open(AiDetector.MODEL_FILENAME).use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }

                if (temp.length() < 1024) {
                    temp.delete()
                    Timber.e("GuardianApp: copied model is too small, aborting")
                    return@Thread
                }

                temp.renameTo(dest)
                Timber.d("GuardianApp: model copied from assets → ${dest.absolutePath} (${dest.length() / 1024}KB)")

            } catch (e: Exception) {
                Timber.e(e, "GuardianApp: model copy from assets FAILED")
            }
        }.apply {
            name = "model-copy"
            isDaemon = true
            start()
        }
    }
}