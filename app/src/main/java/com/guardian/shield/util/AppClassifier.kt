package com.guardian.shield.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.InputMethod

/**
 * v10 (2.1.0) FIX-LOG:
 *  • Added KNOWN_CONTENT_SOURCE_APPS — Facebook, Instagram, Twitter,
 *    TikTok, Snapchat, YouTube, Telegram, WhatsApp, Reddit, Pinterest,
 *    Chrome / Brave / Firefox / Edge / Opera / Samsung Internet.
 *    Used by the source-based 15-min auto-lock.
 *  • Added KNOWN_SAFE_HEAVY_IMAGE_APPS — Photos, Gallery, Camera, Maps —
 *    apps that show lots of legitimate skin / portrait imagery and
 *    therefore receive a +0.10 effective threshold boost (less aggressive
 *    judgement) so they stop spuriously triggering AI block.
 */
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

    /**
     * v10: apps that legitimately deliver large amounts of user-controlled
     * media. When AI confirms EXPLICIT material from one of these, we
     * apply the source-based 15-min auto-lock — straight to HOME, no
     * arguments, no second chances.
     */
    private val KNOWN_CONTENT_SOURCE_APPS = setOf(
        // Social
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",                    // Messenger
        "com.instagram.android",
        "com.instagram.lite",
        "com.twitter.android",
        "com.x.android",                        // X (Twitter rename)
        "com.zhiliaoapp.musically",             // TikTok
        "com.ss.android.ugc.trill",             // TikTok (older / region)
        "com.snapchat.android",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.tumblr",
        "com.linkedin.android",
        // Messaging that often hosts links / media
        "org.telegram.messenger",
        "org.thunderdog.challegram",            // Telegram X
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.discord",
        "com.viber.voip",
        "com.skype.raider",
        // Browsers
        "com.android.chrome",
        "com.chrome.beta",
        "com.brave.browser",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.UCMobile.intl",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser"
    )

    private val KNOWN_CONTENT_SOURCE_PREFIXES = listOf(
        "com.facebook.",
        "com.instagram.",
        "com.twitter.",
        "com.snapchat.",
        "com.reddit.",
        "com.pinterest.",
        "org.telegram.",
        "com.android.browser.",
        "com.chrome.",
        "org.mozilla."
    )

    /**
     * v10: apps that legitimately show lots of skin / portrait / family
     * photos. Effective AI threshold is boosted by
     * [GuardianConstants.HEAVY_IMAGE_APP_THRESHOLD_BOOST] (+0.10) so
     * casual content stops being misclassified as EXPLICIT.
     */
    private val KNOWN_SAFE_HEAVY_IMAGE_APPS = setOf(
        // Google Photos / Gallery / Camera
        "com.google.android.apps.photos",
        "com.google.android.GoogleCamera",
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.apps.camera",
        "com.sec.android.gallery3d",
        "com.miui.gallery",
        "com.huawei.photos",
        "com.oneplus.gallery",
        "com.coloros.gallery3d",
        // Maps / Earth (skin tones in street view)
        "com.google.android.apps.maps",
        "com.google.earth",
        // Contacts (avatars)
        "com.google.android.contacts",
        "com.android.contacts",
        "com.samsung.android.contacts",
        // File / Doc viewers
        "com.google.android.apps.docs",
        "com.adobe.reader"
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

    /**
     * v10: true if [pkg] is known to be a content-source app (social /
     * messaging / browser). These apps qualify for the 15-min source-based
     * auto-lock when AI confirms EXPLICIT material.
     */
    fun isContentSourceApp(pkg: String): Boolean {
        if (pkg in KNOWN_CONTENT_SOURCE_APPS) return true
        return KNOWN_CONTENT_SOURCE_PREFIXES.any { pkg.startsWith(it) }
    }

    /**
     * v10: true if [pkg] is a "heavy image" app that legitimately shows
     * lots of skin / portrait imagery (Photos, Gallery, Camera, Maps).
     * Caller should add HEAVY_IMAGE_APP_THRESHOLD_BOOST to the effective
     * threshold for this app.
     */
    fun isSafeHeavyImageApp(pkg: String): Boolean = pkg in KNOWN_SAFE_HEAVY_IMAGE_APPS
}
