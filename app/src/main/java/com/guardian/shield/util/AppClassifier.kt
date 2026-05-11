package com.guardian.shield.util

import android.content.Context
import android.view.inputmethod.InputMethodManager
import timber.log.Timber

object AppClassifier {
    private val SYSTEM_ALWAYS_ALLOW = setOf(
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.settings"
    )

    fun isAlwaysAllowedPackage(
        ownPkg: String,
        targetPkg: String,
        inputMethods: Set<String>
    ): Boolean {
        if (targetPkg.isBlank()) return true
        if (targetPkg == ownPkg) return true
        if (SYSTEM_ALWAYS_ALLOW.any { targetPkg == it || targetPkg.startsWith("$it.") }) return true
        if (inputMethods.contains(targetPkg)) return true
        return false
    }

    fun loadInputMethodPackages(context: Context): Set<String> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return emptySet()
            imm.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to load IME packages")
            emptySet()
        }
    }
}
