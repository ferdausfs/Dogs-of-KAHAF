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
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.keyguard"
        // ✅ Settings ইচ্ছাকৃতভাবে বাদ — commitment এ monitor করা হবে
        // ✅ PackageInstaller বাদ — uninstall attempt detect করা হবে
    )

    // ✅ Settings packages — commitment এ monitor করতে হবে
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.oneplus.settings",
        "com.oppo.settings",
        "com.huawei.systemmanager"
    )

    // ✅ Package installer packages — uninstall attempt (সব major OEM covered)
    val INSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        // Samsung
        "com.samsung.android.packageinstaller",
        // Xiaomi / MIUI
        "com.miui.packageinstaller",
        // Huawei / HarmonyOS
        "com.huawei.android.packageinstaller",
        "com.huawei.appmarket",
        // OnePlus / OxygenOS / ColorOS
        "com.oneplus.packageinstaller",
        "com.coloros.packagemanager",
        "com.oppo.packagemanager",
        // Vivo / FuntouchOS
        "com.vivo.packageinstaller",
        // Realme / Asus
        "com.realme.packageinstaller",
        "com.asus.packageinstaller",
        // ADB-triggered (android.content.pm.PackageInstaller)
        "com.android.shell"
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

    fun isSettingsPackage(pkg: String): Boolean =
        SETTINGS_PACKAGES.any { pkg == it || pkg.startsWith(it) }

    fun isInstallerPackage(pkg: String): Boolean =
        INSTALLER_PACKAGES.any { pkg == it || pkg.startsWith(it) }

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