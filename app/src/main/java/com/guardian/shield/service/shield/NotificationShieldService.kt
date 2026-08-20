package com.guardian.shield.service.shield

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.TempBlockManager
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * PHASE 4c (v3.5.0) — notification shade shield.
 *
 * THE GAP (traced pre-implementation): nothing in the app intercepted
 * notifications, so while an app was blocked (static list, schedule window or
 * an AI strike-3 temp block) its notifications still posted to the shade with
 * full preview text/images — leaking exactly the content the block exists to
 * hide. This service closes that gap.
 *
 * WHAT IT DOES (opt-in, OFF by default): when enabled in Settings AND the
 * user has granted system "Notification access", a notification posted by an
 * app that is CURRENTLY blocked is cancelled from the shade. Blocked =
 * on the user's block list or inside a schedule window (via the shared
 * [RulesEngine.evaluatePackage] read — no duplicated logic), or under an
 * active AI temp block ([TempBlockManager.isTempBlocked]). Additionally, if
 * the keyword filter is on, the notification's visible title/body text is
 * scanned with the same shared [RulesEngine.evaluateText] so a sensitive
 * preview from an otherwise-allowed app is also caught.
 *
 * HONEST LIMITS (also in the report):
 *  - Requires the user to grant Notification access; we deep-link them there.
 *  - A notification may flash very briefly in the shade before the listener
 *    callback cancels it. Without system/root hooks that flash cannot be
 *    eliminated — we can only make it near-instant.
 *  - MessagingStyle per-line message bundles are not expanded; we scan
 *    title + text + bigText + subText.
 *  - RulesEngine's in-memory snapshot is refreshed when this listener
 *    (re)connects; the blocking path reloads it on rule changes as usual.
 *
 * This service is strictly ADDITIVE and read-only toward the detection /
 * blocking core: it never writes strikes, blocks, events or settings.
 */
@AndroidEntryPoint
class NotificationShieldService : NotificationListenerService() {

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var tempBlockManager: TempBlockManager
    @Inject lateinit var prefs: GuardianPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var featureEnabled = false
    @Volatile private var protectionEnabled = true
    @Volatile private var keywordFilterOn = true

    override fun onCreate() {
        super.onCreate()
        // Mirror BlockingEngine's cached-prefs pattern: no suspend calls on
        // the listener callback path.
        scope.launch { runCatching { prefs.notifShieldEnabled.collect { featureEnabled = it } } }
        scope.launch { runCatching { prefs.protectionEnabled.collect { protectionEnabled = it } } }
        scope.launch { runCatching { prefs.keywordFilter.collect { keywordFilterOn = it } } }
    }

    override fun onListenerConnected() {
        // Fresh rules snapshot for this (re)bind — same data the blocking
        // path maintains; reload() only refills the in-memory snapshot.
        scope.launch { runCatching { rulesEngine.reload() } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == packageName) return              // never touch our own alerts
        if (!featureEnabled || !protectionEnabled) return

        val fromBlockedApp = runCatching {
            tempBlockManager.isTempBlocked(pkg) != null ||
                rulesEngine.evaluatePackage(pkg) is DetectionResult.Block
        }.getOrDefault(false)

        val sensitiveText = !fromBlockedApp && keywordFilterOn && runCatching {
            val text = extractVisibleText(sbn.notification)
            text.length >= 2 && rulesEngine.evaluateText(text) is DetectionResult.Block
        }.getOrDefault(false)

        if (fromBlockedApp || sensitiveText) {
            runCatching { cancelNotification(sbn.key) }
                .onSuccess {
                    Timber.d(
                        "NotificationShield: cancelled shade preview pkg=%s blocked=%s keyword=%s",
                        pkg, fromBlockedApp, sensitiveText
                    )
                }
        }
    }

    /** Visible shade content only: title + body + expanded body + subtext. */
    private fun extractVisibleText(n: Notification?): String {
        if (n == null) return ""
        val e = n.extras ?: return ""
        val sb = StringBuilder()
        fun add(key: String) {
            e.getCharSequence(key)?.let { sb.append(it).append(' ') }
        }
        add(Notification.EXTRA_TITLE)
        add(Notification.EXTRA_TEXT)
        add(Notification.EXTRA_BIG_TEXT)
        add(Notification.EXTRA_SUB_TEXT)
        return sb.toString()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** True when the USER has granted this app Notification access in
         *  system settings (independent of our in-app feature toggle). */
        fun isAccessGranted(context: android.content.Context): Boolean =
            androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)

        /** System settings screen where the user grants/revokes access. */
        fun accessSettingsIntent(): android.content.Intent =
            android.content.Intent(
                android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
    }
}
