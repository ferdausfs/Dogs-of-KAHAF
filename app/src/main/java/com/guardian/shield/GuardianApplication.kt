package com.guardian.shield

import android.app.Application
import com.guardian.shield.service.accessibility.GuardianAccessibilityService
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
                    // CRITICAL FIX: Even if model already exists, broadcast so the
                    // accessibility service (which may have started BEFORE us and found
                    // no model yet) gets a chance to reload. Without this, AI stays
                    // disabled on every boot even though the model is present.
                    broadcastModelReady()
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

                // CRITICAL FIX: Notify accessibility service that model is now ready.
                // Without this, the service starts, finds no model, disables AI, and
                // never re-checks — even after copy finishes. Now it will reload.
                broadcastModelReady()

            } catch (e: Exception) {
                Timber.e(e, "GuardianApp: model copy from assets FAILED")
            }
        }.apply {
            name = "model-copy"
            isDaemon = true
            start()
        }
    }

    private fun broadcastModelReady() {
        try {
            // Small delay to let the accessibility service finish binding first
            Thread.sleep(2500)
            val intent = android.content.Intent(
                GuardianAccessibilityService.ACTION_RELOAD_MODEL
            ).apply { setPackage(packageName) }
            sendBroadcast(intent)
            Timber.d("GuardianApp: broadcasted ACTION_RELOAD_MODEL → service will load AI model")
        } catch (e: Exception) {
            Timber.e(e, "GuardianApp: broadcastModelReady failed")
        }
    }
}