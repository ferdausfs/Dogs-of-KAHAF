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
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        return resolveInfos.mapNotNull { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val packageName = appInfo.packageName

            // Skip our own app and system-protected packages
            if (packageName in Constants.SYSTEM_PROTECTED_PACKAGES) return@mapNotNull null

            AppInfo(
                packageName = packageName,
                appName = appInfo.loadLabel(pm).toString(),
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isBlocked = false,
                isWhitelisted = false
            )
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun getAppName(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}