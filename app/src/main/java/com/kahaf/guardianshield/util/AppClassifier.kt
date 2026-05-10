package com.kahaf.guardianshield.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.InputMethod

/**
 * Ported from the legacy v2.x codebase into the v3.0.0 (kahaf) architecture.
 *
 * Centralised "app classification" helpers used across the detection /
 * blocking pipeline:
 *
 *  • [isAlwaysAllowedPackage]   – packages we must never block (system UI,
 *                                  launcher, IME) so we never lock the user
 *                                  out of their own phone.
 *  • [isContentSourceApp]       – social / messaging / browser apps whose
 *                                  EXPLICIT detections trigger the 15-min
 *                                  source-based auto-lock.
 *  • [isSafeHeavyImageApp]      – Photos / Gallery / Camera / Maps that
 *                                  legitimately show large amounts of
 *                                  skin / portrait imagery and should
 *                                  receive a +0.10 effective-threshold
 *                                  boost so they stop spuriously
 *                                  triggering AI block.
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
     * Apps that legitimately deliver large amounts of user-controlled media.
     * When AI confirms EXPLICIT material from one of these, the engine may
     * apply the source-based 15-min auto-lock.
     */
    val KNOWN_CONTENT_SOURCE_APPS: Set<String> = setOf(
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
     * Apps that legitimately show lots of skin / portrait / family photos.
     * The effective AI threshold is boosted by [GuardianConstants.HEAVY_IMAGE_APP_THRESHOLD_BOOST]
     * for these so casual content stops being misclassified as EXPLICIT.
     */
    val KNOWN_SAFE_HEAVY_IMAGE_APPS: Set<String> = setOf(
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

    fun loadInputMethodPackages(context: Context): Set<String> = runCatching {
        val pm = context.packageManager
        val intent = Intent(InputMethod.SERVICE_INTERFACE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        services.mapNotNull { it.serviceInfo?.packageName }.toSet()
    }.getOrDefault(emptySet())

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

    fun isContentSourceApp(pkg: String): Boolean {
        if (pkg in KNOWN_CONTENT_SOURCE_APPS) return true
        return KNOWN_CONTENT_SOURCE_PREFIXES.any { pkg.startsWith(it) }
    }

    fun isSafeHeavyImageApp(pkg: String): Boolean = pkg in KNOWN_SAFE_HEAVY_IMAGE_APPS
}
