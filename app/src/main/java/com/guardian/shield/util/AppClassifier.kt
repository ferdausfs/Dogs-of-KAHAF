package com.guardian.shield.util

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import timber.log.Timber

object AppClassifier {

    private val SYSTEM_ALWAYS_ALLOW = setOf(
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.samsung.android.app.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oneplus.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.huawei.android.launcher",
        "com.realme.launcher",
        "com.asus.launcher",
        "com.teslacoilsw.launcher",
        "com.actionlauncher.playstore",
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.keyguard",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        // ===== TASK 4: Extended default allowlist =====
        // Messaging apps — always allowed
        "com.imo.android.imoim",          // IMO Video Call
        "com.imo.android.imoimbeta",      // IMO Beta
        "com.imo.android.imoimhd",        // IMO HD
        "com.imo.android.imoimlite",      // IMO Lite (newer)
        "org.thoughtcrime.securesms",     // Signal
        "com.facebook.orca",              // Facebook Messenger
        "com.facebook.mlite",             // Messenger Lite
        "com.whatsapp",                   // WhatsApp
        "com.whatsapp.w4b",               // WhatsApp Business
        // Additional popular keyboards
        "com.touchtype.swiftkey",         // SwiftKey
        "com.ridmik.keyboard",            // Ridmik Keyboard
        "net.mayabi.keyboard",            // Mayabi Keyboard
        "sutonnyMJ.BijoyBanglaSoft",      // Bijoy Bangla
        "com.menny.android.anysoftkeyboard" // AnySoftKeyboard
    )

    @Volatile private var cachedHomePkg: String? = null

    fun getHomePkg(context: Context): String? {
        cachedHomePkg?.let { return it }
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pkg = context.packageManager.resolveActivity(intent, 0)
                ?.activityInfo?.packageName
            cachedHomePkg = pkg
            pkg
        } catch (t: Throwable) {
            Timber.w(t, "Failed to get home package")
            null
        }
    }

    fun isAlwaysAllowedPackage(
        ownPkg: String,
        targetPkg: String,
        inputMethods: Set<String>,
        homePkg: String? = null
    ): Boolean {
        if (targetPkg.isBlank()) return true
        if (targetPkg == ownPkg) return true
        if (SYSTEM_ALWAYS_ALLOW.any { targetPkg == it || targetPkg.startsWith("$it.") }) return true
        if (homePkg != null && targetPkg == homePkg) return true
        if (inputMethods.contains(targetPkg)) return true
        return false
    }

    fun loadInputMethodPackages(context: Context): Set<String> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? InputMethodManager ?: return emptySet()
            imm.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to load IME packages")
            emptySet()
        }
    }
}