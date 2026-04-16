package com.kahaf.guardian.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.kahaf.guardian.domain.model.AppInfo

object PackageUtils {
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { ri ->
            val ai = ri.activityInfo.applicationInfo
            if (ai.packageName in Constants.SYSTEM_PROTECTED_PACKAGES) null
            else AppInfo(ai.packageName, ai.loadLabel(pm).toString(),
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? =
        try { context.packageManager.getApplicationIcon(packageName) }
        catch (_: PackageManager.NameNotFoundException) { null }

    fun getAppName(context: Context, packageName: String): String =
        try { context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) { packageName }
}
