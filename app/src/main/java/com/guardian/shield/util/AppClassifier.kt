package com.guardian.shield.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.InputMethod

object AppClassifier {

    private val ALWAYS_ALLOW_EXACT = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.googlequicksearchbox",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod"
    )

    private val ALWAYS_ALLOW_PREFIXES = listOf(
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.realme.launcher",
        "com.vivo.launcher",
        "com.google.android.inputmethod",
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod",
        "com.touchtype.swiftkey",
        "com.microsoft.swiftkey"
    )

    fun loadInputMethodPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val intent = Intent(InputMethod.SERVICE_INTERFACE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        return services.mapNotNull { it.serviceInfo?.packageName }.toSet()
    }

    fun isAlwaysAllowedPackage(
        ownPackage: String,
        pkg: String,
        inputMethodPackages: Set<String>
    ): Boolean {
        if (pkg == ownPackage) return true
        if (pkg in ALWAYS_ALLOW_EXACT) return true
        if (pkg in inputMethodPackages) return true
        return ALWAYS_ALLOW_PREFIXES.any { pkg.startsWith(it) }
    }

    fun isSystemApp(info: ApplicationInfo): Boolean {
        val mask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (info.flags and mask) != 0
    }
}
