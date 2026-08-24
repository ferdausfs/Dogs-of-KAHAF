package com.guardian.shield.util

import timber.log.Timber
import java.util.Collections

/**
 * DEFAULT SYSTEM/OEM WHITELIST
 * ============================
 *
 * A static, app-shipped safety layer that keeps Guardian Shield from ever
 * scanning or blocking system-critical / OEM-bundled packages (Settings,
 * SystemUI, dialers, vendor services, chipset daemons, ...). These packages
 * are basic phone functionality: blocking them — or even repeatedly
 * screenshot-scanning them — could break the device experience and can never
 * be a meaningful content-filtering win anyway.
 *
 * This is a DISTINCT concept from the user-configured rules in
 * [com.guardian.shield.service.detection.RulesEngine]'s Room-backed snapshot:
 *
 *   LAYER 0  [AppClassifier.isAlwaysAllowedPackage] — hard absolute net
 *            (self package, SystemUI/keyguard, home launcher, active IMEs).
 *            Never overridable by anyone. UNCHANGED by this layer.
 *   LAYER 1  [DefaultSystemWhitelist] (this file) — static patterns below.
 *            Skips broad OEM/system namespaces, UNLESS the user has an
 *            EXPLICIT rule (manual block or enabled schedule) for that exact
 *            package — the user's own explicit choice always beats this
 *            default (it is their device; a deliberate block of e.g. a Samsung/
 *            Xiaomi system app must be respected). See RulesEngine.canBlock /
 *            evaluatePackage for the exact priority wiring.
 *   LAYER 2  User-configured Room rules — explicit block, whitelist, keyword,
 *            schedule (whitelist-beats-blocklist, unchanged since before).
 *
 * Priority (evaluated in this exact order in RulesEngine):
 *   1. always-allowed (layer 0)            -> never block/scannable
 *   2. user whitelist                      -> Allow
 *   3. user explicit block                 -> Block   (BEATS layer 1)
 *   4. user enabled schedule (in window)   -> Block   (BEATS layer 1)
 *   5. this default system/OEM whitelist   -> Allow / not scannable
 *   6. otherwise                           -> normal evaluation (AI etc.)
 *
 * RATIONALE FOR #3/#4-BEATS-#5: the default whitelist exists to prevent
 * ACCIDENTAL harm from treating everything equally. A rule the user typed in
 * themselves is, by definition, not accidental — e.g. a parent knowingly
 * blocking a social app, or a user scheduling a settings/store app. So an
 * explicit user rule overrides the safety default; the default only fills the
 * gap where the user has expressed no opinion. The always-allow layer (0)
 * remains the single non-overridable net because blocking own-package /
 * SystemUI / the active launcher / the keyboard can deadlock the device
 * itself, and that list contains no content surface.
 *
 * DESIGN RULES:
 * - Patterns are package-name PREFIXES (NOT a giant per-package list) so new
 *   OEM packages in the same namespace are covered for free.
 *   `com.android.*`-style = match on the trailing-dot prefix, so
 *   `com.androidx.core` is NOT matched by `com.android.` (boundary-safe).
 * - [NEVER_DEFAULT_WHITELIST] is checked FIRST and always wins over any
 *   pattern above: listed consumer apps (YouTube/Instagram/Chrome/browsers/
 *   Telegram...) stay fully subject to normal blocking/AI-detection even if
 *   they share a namespace with system components (e.g.
 *   `com.google.android.youtube` vs a Google system prefix).
 * - This list is NOT user-editable (deliberately, for now — diagnosability
 *   only via the Timber skip-log below). Every first skip of a package is
 *   logged with the exact rule that matched, worded distinctly from any
 *   user-whitelist path so logcat can tell the two apart.
 *
 * PACKAGE-ID EVIDENCE (session 2026-08-23; see COMPILE_REVIEW_REPORT.md):
 * - YouTube com.google.android.youtube — Play Store listing id
 *   (stackoverflow.com/q/14578373); YouTube for Android TV
 *   com.google.android.youtube.tv / YouTube Music
 *   com.google.android.apps.youtube.music / YouTube Kids
 *   com.google.android.apps.youtube.kids — uptodown/apkpure technical pages.
 * - Facebook com.facebook.katana, FB Lite com.facebook.lite, Messenger
 *   com.facebook.orca, Messenger Lite com.facebook.mlite —
 *   stackoverflow.com/q/53433969 (5-upvote answer) + Play Store ids.
 * - Instagram com.instagram.android, IG Lite com.instagram.lite
 *   (androidapks.com technical file info), Threads com.instagram.barcelona
 *   (threads.androidapks.com + threads.com post documenting the id).
 * - X/Twitter com.twitter.android — 9to5google.com/2023/07/26/x-twitter-android
 *   ("the app's package name remains com.twitter.android"); Twitter Lite
 *   com.twitter.android.lite (twitter-lite.androidapks.com).
 * - Telegram org.telegram.messenger (Play APK) and
 *   org.telegram.messenger.web (direct-APK build) — BOTH from Telegram's own
 *   core.telegram.org/reproducible-builds doc; Telegram X
 *   org.thunderdog.challegram — github.com/TGX-Android/Telegram-X.
 * - Chrome com.android.chrome (apkpure listing); legacy AOSP browser
 *   com.android.browser; Samsung Internet com.sec.android.app.sbrowser
 *   (mobile.softpedia.com + androidapks.com + android.stackexchange.com);
 *   Mi Video com.miui.video / com.miui.videoplayer (apkmirror/apkcombo —
 *   "online short video ... downloader", 1B+ installs).
 * - Google's OWN "Critical Android system apps" MDM list
 *   (knowledge.workspace.google.com/admin/devices/manage-system-apps...) names
 *   com.android.{settings,systemui,phone,vending,bluetooth,keyguard,...},
 *   com.google.android.{gms,gsf,gsf.login,webview,...},
 *   com.samsung.android.{contacts,phone} — confirming com.android. /
 *   com.samsung. are the platform namespaces and that com.android.vending
 *   (Play Store) is treated as device-management infrastructure by Google
 *   itself (it also matches the v2.4.2 report finding [C]); hence the store
 *   is intentionally NOT excluded here (store listings are Play-moderated;
 *   users can still explicitly block it).
 * - Google binary-transparency docs (developers.google.com/android/
 *   binary_transparency/google1p/overview) confirm the core-Google system
 *   components com.google.android.as.oss (Private Compute Services),
 *   com.google.android.safetycore (Android System SafetyCore) and
 *   com.google.android.contactkeys (Android System Key Verifier).
 * - Android System WebView channel packages com.google.android.webview +
 *   .beta/.dev/.canary — separate Google listings (Wikipedia app catalog).
 */
object DefaultSystemWhitelist {

    // ---------------------------------------------------------------------
    // 1) BROAD SYSTEM/OEM NAMESPACE PREFIXES (all end with '.' => boundary-
    //    safe prefix match; e.g. "com.android." never matches "com.androidx").
    // ---------------------------------------------------------------------
    private val SYSTEM_OEM_PREFIXES = listOf(
        // AOSP framework namespace (the literal package "android" is handled
        // by SYSTEM_OEM_EXACT below; this covers android.* tooling packages).
        "android.",

        // Core AOSP platform namespace: Settings, SystemUI, Phone, providers,
        // Bluetooth/NFC stacks, cert installer, carrier config, overlays...
        // (Google MDM "critical system apps" list lives almost entirely here.)
        // Colliding CONSUMER packages excluded in NEVER_DEFAULT_WHITELIST:
        // com.android.chrome, com.android.browser. NOT excluded:
        // com.android.vending (Play Store) — Google's own MDM docs classify it
        // as a critical system app; content is Play-moderated; prior report
        // finding [C] (v2.4.2) already recommended never blocking it.
        "com.android.",

        // Samsung One UI namespace — the app's primary target devices
        // (see One UI 8 sessions). Samsung system UI/dialer/contacts/DeX/
        // Knox/etc. Samsung consumer stores (Galaxy Store = moderated, same
        // treatment as Play Store) and Samsung Internet browser: the browser
        // is com.sec.android.app.sbrowser -> in the NEVER list below.
        "com.samsung.",

        // Legacy Samsung Electronics namespace (older/preinstalled SEC apps,
        // incallui, camera, gallery). Colliding consumer apps excluded below:
        // Samsung Internet (+ Beta), i.e. the only serious content surface.
        "com.sec.",

        // Xiaomi MIUI system namespace (security center, daemon, telemetry
        // daemons, themes host...). Colliding content surface excluded below:
        // Mi Video (com.miui.video) and Play-listed Mi Video
        // (com.miui.videoplayer — "online short video", 1B+ installs).
        // NOT colliding: Xiaomi's global browser is com.mi.globalbrowser —
        // outside this prefix, so it stays scannable without an exclusion.
        "com.miui.",

        // Xiaomi services namespace (account, finddevice, Joyose perf daemon...).
        // GetApps (com.xiaomi.market) is a moderated store — same treatment
        // as Play Store, deliberately kept.
        "com.xiaomi.",

        // Chipset-vendor platform namespaces (radio/baseband/vendor daemons).
        // Blocking these can break connectivity; zero consumer conflict known
        // (no com.mediatek.*/com.qualcomm.qti.* consumer app exists).
        "com.mediatek.",
        "com.qualcomm.qti."
    )

    // The framework package itself ("android") — also in AppClassifier's
    // always-allow list; kept here as belt-and-braces for layer separation.
    private val SYSTEM_OEM_EXACT = setOf("android")

    // ---------------------------------------------------------------------
    // 2) CURATED CORE-GOOGLE PLATFORM COMPONENTS
    //
    // REJECTED DESIGN: a blanket "com.google.android." prefix.
    // That namespace holds BOTH Google's platform components (Play services,
    // WebView, permission controller — listed here) AND nearly ALL of
    // Google's consumer apps: YouTube (com.google.android.youtube), Photos/
    // Maps/Gmail/Drive/Google-Search (com.google.android.apps.* and
    // com.google.android.googlequicksearchbox). Whitelisting the whole prefix
    // and excluding consumer apps one-by-one would be unmaintainable
    // whack-a-mole where every FUTURE Google consumer app would silently
    // escape content filtering until manually noticed — exactly the failure
    // this task warns about. So the whitelist direction is inverted: only
    // the enumerated platform components below are auto-whitelisted, and
    // every consumer app under com.google.android.* stays scannable BY
    // DEFAULT. Sources: Google MDM critical-apps list + Google binary
    // transparency docs (evidence above). Deliberately NOT included (stay
    // scannable): googlequicksearchbox (Google Search/Discover — content
    // surface), Gboard (already layer-0 always-allowed as an IME), dialer/
    // deskclock (already layer-0 always-allowed), everything under
    // com.google.android.apps.*.
    // ---------------------------------------------------------------------
    private val CORE_GOOGLE_EXACT = setOf(
        "com.google.android.gms",           // Google Play services (Google MDM-critical)
        "com.google.android.gsf",           // Google Services Framework (MDM-critical)
        "com.google.android.gsf.login",     // account sign-in framework (MDM-critical)
        "com.google.android.tts",           // Speech services by Google (system TTS)
        "com.google.android.as",            // Android System Intelligence
        "com.google.android.as.oss",        // Private Compute Services (Google docs)
        "com.google.android.safetycore",    // Android System SafetyCore (Google docs)
        "com.google.android.contactkeys",   // Android System Key Verifier (Google docs)
        "com.google.android.ext.services",  // ExtServices Mainline module
        "com.google.android.ext.shared",    // ExtShared Mainline module
        "com.google.android.modulemetadata",// ModuleMetadata Mainline module
        "com.google.android.permissioncontroller", // PermissionController Mainline
        "com.google.android.networkstack",  // NetworkStack Mainline module
        "com.google.android.documentsui"    // Files/SAF picker system UI
    )

    // Google component FAMILIES matched by boundary-safe prefix (exact or
    // sub-package): Android System WebView stable+beta/dev/canary channels
    // (com.google.android.webview + .beta/.dev/.canary) and Google's RRO
    // runtime overlays (com.google.android.overlay.*) — pure resource packs,
    // breaking them breaks theming/9patch of system UI.
    private val CORE_GOOGLE_PREFIXES = listOf(
        "com.google.android.webview",
        "com.google.android.overlay"
    )

    // ---------------------------------------------------------------------
    // 3) NEVER DEFAULT-WHITELISTED — explicit exclusion list. EXACT package
    //    ids, checked before every pattern above; ALWAYS wins over them.
    //    These are consumer apps where sensitive-content exposure is real.
    //    Being here does NOT block them by itself — it only means the DEFAULT
    //    whitelist never silently protects them; they remain fully subject to
    //    the same user rules / AI detection as any ordinary app, exactly as
    //    today. (User whitelist rules still work on them normally, too.)
    // ---------------------------------------------------------------------
    private val NEVER_DEFAULT_WHITELIST = setOf(
        // --- Task-named high-risk social/media apps (+ researched variants) ---
        "com.google.android.youtube",              // YouTube (Play id; SO q/14578373)
        "com.google.android.youtube.tv",           // YouTube for Android TV (uptodown)
        "com.google.android.apps.youtube.music",   // YouTube Music (uptodown)
        "com.google.android.apps.youtube.kids",    // YouTube Kids (uptodown/apkpure)
        "com.google.android.apps.photos",          // Google Photos (task-named example;
                                                   //  apps.* is never whitelisted anyway —
                                                   //  listed for explicit defense-in-depth)
        "com.facebook.katana",                     // Facebook (SO q/53433969 + Play)
        "com.facebook.lite",                       // Facebook Lite (SO q/53433969)
        "com.facebook.orca",                       // Messenger (SO) — NOTE: Messenger is
                                                   //  layer-0 always-allowed as a messaging
                                                   //  app; this entry only documents that the
                                                   //  DEFAULT layer never claims it either.
        "com.facebook.mlite",                      // Messenger Lite (SO; same note as orca)
        "com.instagram.android",                   // Instagram (androidapks)
        "com.instagram.lite",                      // Instagram Lite (androidapks)
        "com.instagram.barcelona",                 // Threads (androidapks/threads.com)
        "com.twitter.android",                     // X / Twitter (9to5google + Play id)
        "com.twitter.android.lite",                // Twitter Lite (androidapksfree)
        "org.telegram.messenger",                  // Telegram, Play build (core.telegram.org)
        "org.telegram.messenger.web",              // Telegram, telegram.org build (same source)
        "org.thunderdog.challegram",               // Telegram X (github.com/TGX-Android)

        // --- Browsers that COLLIDE with a whitelisted namespace above ---
        // These are the apps the exclusion list functionally protects today:
        // without them, the broad com.android. / com.sec. / com.miui. prefixes
        // would silently turn off content protection inside a WEB BROWSER.
        "com.android.chrome",                      // Google Chrome (apkpure) — vs com.android.
        "com.android.browser",                     // legacy AOSP/MIUI-China browser — vs com.android.
        "com.sec.android.app.sbrowser",            // Samsung Internet (softpedia/androidapks)
        "com.sec.android.app.sbrowser.beta",       // Samsung Internet Beta — vs com.sec.
        "com.miui.video",                          // Mi Video online hub (apkmirror) — vs com.miui.
        "com.miui.videoplayer"                     // Mi Video, Play listing, 1B+ installs
                                                   //  (apkcombo: "online short video") — vs com.miui.
    )

    // ---------------------------------------------------------------------
    // Matching
    // ---------------------------------------------------------------------

    /**
     * Exact rule that default-whitelists [pkg], or null if it is NOT on the
     * default list. Exclusions are checked FIRST so [NEVER_DEFAULT_WHITELIST]
     * always beats a broad namespace pattern. Callers should use the returned
     * reason (also) for logging; never log from inside a hot loop without the
     * [logSkipOnce] dedup.
     */
    fun matchReason(pkg: String): String? {
        if (pkg.isBlank()) return null
        if (NEVER_DEFAULT_WHITELIST.contains(pkg)) return null
        if (SYSTEM_OEM_EXACT.contains(pkg)) return "exact system package \"$pkg\""
        for (prefix in SYSTEM_OEM_PREFIXES) {
            if (pkg.startsWith(prefix)) return "OEM/system namespace \"$prefix*\""
        }
        if (CORE_GOOGLE_EXACT.contains(pkg)) return "core Google platform component"
        for (prefix in CORE_GOOGLE_PREFIXES) {
            if (pkg == prefix || pkg.startsWith("$prefix.")) {
                return "core Google component family \"$prefix(.beta/.dev/...)\""
            }
        }
        return null
    }

    /** True when [pkg] is skipped by the DEFAULT system/OEM whitelist. */
    fun isSystemOrOemPackage(pkg: String): Boolean = matchReason(pkg) != null

    // ---------------------------------------------------------------------
    // Diagnosable logging (per-process dedup; logcat must stay usable)
    // ---------------------------------------------------------------------

    private val loggedSkips = Collections.synchronizedSet(LinkedHashSet<String>())
    private const val MAX_LOGGED_PKGS = 256

    /**
     * Emits a Timber log when [pkg] is skipped BECAUSE of this default
     * whitelist — worded distinctly from any user-whitelist handling (which
     * intentionally stays silent today), so a "why isn't X blocked" report is
     * answerable from logcat alone. Deduped per package per process: the
     * first skip logs, repeats don't. Bounded to [MAX_LOGGED_PKGS] entries;
     * when full, the dedup memory resets (re-logging old packages then is
     * acceptable — the alternative is a silent log).
     */
    fun logSkipOnce(pkg: String, reason: String) {
        val first = synchronized(loggedSkips) {
            if (loggedSkips.size >= MAX_LOGGED_PKGS) loggedSkips.clear()
            loggedSkips.add(pkg)
        }
        if (first) {
            // Explicitly NOT the user whitelist wording: user-rule skips log
            // nothing today, so any whitelist-skip line in logcat is this layer.
            Timber.i(
                "DefaultSystemWhitelist: SKIPPED pkg=%s (%s) — auto system/OEM " +
                    "skip, NOT a user whitelist rule; a user-explicit block or " +
                    "enabled schedule for this package would still have been honored",
                pkg, reason
            )
        }
    }
}
