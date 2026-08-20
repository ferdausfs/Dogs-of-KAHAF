package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.annotation.RequiresApi
import com.guardian.shield.R
import com.guardian.shield.admin.TamperLogger
import com.guardian.shield.admin.UninstallProtection
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.db.BlockEventDao
import com.guardian.shield.data.local.db.BlockEventEntity
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.AiStrikeResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.blocker.PendingReportManager
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.ConfirmedSensitiveMemory
import com.guardian.shield.service.detection.FalsePositiveMemory
import com.guardian.shield.service.detection.ReelScrollDetector
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.overlay.ReelReminderActivity
import com.guardian.shield.util.AccessibilityHeartbeat
import com.guardian.shield.util.AppClassifier
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.ReligiousReminders
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.ArrayDeque
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences
    @Inject lateinit var reelScrollDetector: ReelScrollDetector
    @Inject lateinit var timeLockManager: TimeLockManager
    @Inject lateinit var falsePositiveMemory: FalsePositiveMemory
    @Inject lateinit var confirmedSensitiveMemory: ConfirmedSensitiveMemory
    @Inject lateinit var blockEventDao: BlockEventDao
    @Inject lateinit var pendingReportManager: PendingReportManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isScreenOn = true
    @Volatile private var protectionEnabled = true
    @Volatile private var currentPackage: String? = null
    @Volatile private var lastTextScan = 0L
    @Volatile private var lastContentHash: Int = 0
    @Volatile private var isBlockingInProgress = false

    // PRECISION-FIRST (v2.4.2) — only treat a node as scannable *content* when it
    // is large enough to be real content. Feed thumbnails must still be scanned,
    // so this only filters out truly tiny icons / buttons / emoji (<=96px). Profile
    // avatars are handled separately below (excluded from scanning).
    private val MIN_CONTENT_IMAGE_PX = 96

    // STABILITY FIX — auto-reset guard so a stuck flag never freezes detection
    @Volatile private var blockingFlagSetAt = 0L
    private val BLOCKING_FLAG_MAX_HOLD_MS = 6_000L

    // STABILITY FIX — track consecutive screenshot failures so we can back off
    @Volatile private var screenshotFailStreak = 0
    private val SCREENSHOT_FAIL_BACKOFF_THRESHOLD = 5

    private val aiScanMap = LinkedHashMap<String, Long>()
    private var periodicJob: Job? = null
    private var stuckFlagJob: Job? = null
    private var heartbeatJob: Job? = null
    private var homePkg: String? = null
    private var keyguardManager: KeyguardManager? = null

    // LIFECYCLE GUARD — onServiceConnected() can be invoked again after a
    // disconnect/onInterrupt without onDestroy(). Guard the one-time setup so
    // we never double-register the screen receiver or stack coroutines.
    @Volatile private var connected = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentPackage = null
                    // ✅ Screen off → reset everything
                    clearBlockingFlag("screen-off")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) {
            Timber.w("Accessibility re-connected — skipping duplicate init")
            return
        }
        connected = true
        Timber.i("Accessibility connected")
        homePkg = AppClassifier.getHomePkg(this)
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
        }
        isScreenOn =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

        ioScope.launch {
            try {
                prefs.rulesVersion.collect {
                    try { rulesEngine.reload(); aiDetector.ensureLoaded() }
                    catch (t: Throwable) { Timber.e(t) }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }

        serviceScope.launch {
            try { prefs.protectionEnabled.collect { protectionEnabled = it } }
            catch (t: Throwable) { Timber.e(t) }
        }

        aiDetector.startPrefsCache(serviceScope)
        startPeriodicScanner()
        startStuckFlagWatchdog()
        startHeartbeat()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (!protectionEnabled || isDeviceLocked()) return
        try {
            val pkg = ev.packageName?.toString().orEmpty()

            // TASK 2 — Reel/Short scroll addiction detection
            if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && pkg.isNotBlank()) {
                checkReelScroll(pkg)
            }

            when (ev.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleContentChange(pkg)
                else -> Unit
            }
        } catch (t: Throwable) { Timber.e(t) }
    }

    /**
     * STABILITY FIX — central place to clear the in-progress flag so we never
     * leak it. Also stamps the clear time for the watchdog.
     */
    private fun setBlockingFlag(): Boolean {
        synchronized(this) {
            if (isBlockingInProgress) return false
            isBlockingInProgress = true
            blockingFlagSetAt = System.currentTimeMillis()
            return true
        }
    }

    private fun clearBlockingFlag(reason: String) {
        synchronized(this) {
            if (isBlockingInProgress) {
                Timber.d("Clearing blocking flag: $reason")
            }
            isBlockingInProgress = false
            blockingFlagSetAt = 0L
        }
    }

    /**
     * STABILITY FIX — surfaces and clears the reel reminder regardless of
     * whether the AI sub-system is busy.
     */
    private fun checkReelScroll(pkg: String) {
        try {
            if (isSafePackage(pkg)) return

            // Simple heuristic for Reels/Shorts vs General Feed
            val isShortForm = isShortFormView(pkg)

            val shouldRemind = reelScrollDetector.recordScroll(pkg, isShortForm)
            if (shouldRemind) {
                reelScrollDetector.markReminderShown(pkg)
                mainHandler.post {
                    try {
                        startActivity(ReelReminderActivity.createIntent(this, pkg))
                    } catch (t: Throwable) { Timber.e(t, "Failed to show reel reminder") }
                }
            }
        } catch (t: Throwable) { Timber.e(t, "checkReelScroll failed") }
    }

    private fun isShortFormView(pkg: String): Boolean {
        if (pkg == "com.zhiliaoapp.musically" || pkg == "com.ss.android.ugc.trill") return true

        // Check all windows for "Reels" or "Shorts" text to handle PIP/Split-screen
        // Optimization: For Facebook/Instagram/YouTube, explicitly look for Short-form indicators
        val indicators = when {
            pkg.contains("facebook") || pkg.contains("instagram") -> listOf("Reels")
            pkg.contains("youtube") -> listOf("Shorts")
            else -> listOf("Reels", "Shorts")
        }

        val nodes = findNodesByTextAcrossWindows(indicators)
        // Verify node is actually a visible tab or header, not just random text in a post
        val found = nodes.any { node ->
            val parent = node.parent
            val parentClickable = parent?.isClickable == true
            parent?.recycle()
            node.isVisibleToUser && (node.isClickable || node.isFocused || parentClickable)
        }
        nodes.forEach { it.recycle() }
        return found
    }

    private fun findNodesByTextAcrossWindows(texts: List<String>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val currentWindows = windows ?: return emptyList()
        for (window in currentWindows) {
            val root = window.root ?: continue
            for (text in texts) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                if (!nodes.isNullOrEmpty()) {
                    result.addAll(nodes)
                }
            }
            root.recycle()
        }
        return result
    }

    private fun isDeviceLocked() = try {
        keyguardManager?.isKeyguardLocked == true
    } catch (_: Throwable) { false }

    private fun isSafePackage(pkg: String) = AppClassifier.isAlwaysAllowedPackage(
        packageName, pkg, rulesEngine.current().inputMethods, homePkg
    )

    private fun goHomeAndBlock(pkg: String, reason: BlockReason, detail: String, confidence: Float = -1f) {
        // POST-BLOCK GRACE — right after an AI temp block expires we let the user
        // back in instead of instantly re-blocking on the next scan. This is what
        // makes a "15 min block → then unlocked" actually hold in practice.
        if (reason == BlockReason.AI_DETECTION && blockingEngine.isGracePeriodActive(pkg)) {
            Timber.d("AI grace period active for $pkg — not re-blocking")
            clearBlockingFlag("ai-grace")
            return
        }

        // Count AI strikes BEFORE kicking the user home. Strikes 1..(N-1) stay in
        // the app (no HOME, no overlay) but show a visible styled warning card
        // (v2.5.2) so the eventual 3rd-strike block never feels unannounced.
        val overlayDetail = if (reason == BlockReason.AI_DETECTION) {
            when (val result = blockingEngine.evaluateAiStrike(pkg, confidence)) {
                is AiStrikeResult.Blocked -> result.detail
                is AiStrikeResult.StrikeCounted -> {
                    showAiStrikeWarning(pkg, result.strikeCount, confidence)
                    Timber.d(
                        "AI strike ${result.strikeCount}/${GuardianConstants.STRIKE_THRESHOLD} " +
                            "for $pkg — warning shown, staying in app (conf=$confidence)"
                    )
                    return
                }
                AiStrikeResult.GracePeriod -> {
                    Timber.d("AI grace period active for $pkg — not re-blocking")
                    return
                }
                AiStrikeResult.Duplicate -> {
                    Timber.d("AI strike duplicate for $pkg (burst dedup) — ignored")
                    return
                }
                AiStrikeResult.WarningCardVisible -> {
                    Timber.d("AI strike deferred for $pkg — warning card still showing")
                    return
                }
            }
        } else detail

        if (!setBlockingFlag()) {
            // 3rd AI strike already applied a temp-block — still show the overlay
            // even if another block is mid-flight, otherwise the user stays in
            // the app with a silent 15-minute lock.
            if (reason == BlockReason.AI_DETECTION) {
                Timber.d("Block flag busy; delivering AI overlay anyway for $pkg")
                try { blockingEngine.block(pkg, reason, overlayDetail, confidence) }
                catch (t: Throwable) { Timber.e(t, "blockingEngine.block failed") }
            } else {
                Timber.d("Block in progress, skip: $pkg")
            }
            return
        }
        // ✅ Immediately clear current package — periodic scanner shouldn't see a stale pkg
        currentPackage = null
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            try { blockingEngine.block(pkg, reason, overlayDetail, confidence) }
            catch (t: Throwable) { Timber.e(t, "blockingEngine.block failed") }
        }, 120)
    }

    /**
     * Visible warning for an AI strike that did NOT reach the block threshold.
     * Shown for strikes 1 and 2 only; never for strike 3 (that path returns a
     * [AiStrikeResult.Blocked] and gets the full overlay instead).
     */
    private fun showAiStrikeWarning(pkg: String, strikeCount: Int, confidence: Float = -1f) {
        // Strikes 1 & 2 only — styled mid-screen overlay card (v2.5.2 redesign of
        // the old plain Toast). Strike counting, the STRIKE_THRESHOLD gate and the
        // strike-3 BlockOverlayActivity path are untouched.
        // goHomeAndBlock already runs on the main thread, but post through the
        // main handler so the overlay is guaranteed on the UI looper regardless of
        // which coroutine context reached us.
        mainHandler.post {
            try {
                if (Settings.canDrawOverlays(this)) {
                    showStrikeWarningOverlay(pkg, strikeCount, confidence)
                } else {
                    // Overlay permission not granted yet — keep the pre-2.5.2 Toast
                    // as a graceful fallback so the warning is never silently lost.
                    // There is no card to acknowledge, so reopen the strike gate
                    // (the 1s burst dedup still covers same-tick double counts).
                    blockingEngine.setWarningCardShowing(false)
                    val message = getString(
                        R.string.ai_strike_warning_fmt,
                        strikeCount,
                        GuardianConstants.STRIKE_THRESHOLD
                    )
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                Timber.w(t, "Strike warning overlay failed — falling back to Toast")
                blockingEngine.setWarningCardShowing(false)
                runCatching {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.ai_strike_warning_fmt,
                            strikeCount,
                            GuardianConstants.STRIKE_THRESHOLD
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ---- v2.5.2 / v3.3.0 strike-warning overlay card ----
    // WindowManager TYPE_APPLICATION_OVERLAY card shown mid-upper screen.
    // Reuses the SYSTEM_ALERT_WINDOW overlay permission the app already declares
    // (AndroidManifest) and manages (PermissionManager / PermissionsActivity).
    // FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL: non-blocking — touches outside
    // the card pass through to the app underneath; only the card area intercepts.
    //
    // v3.3.0 dismiss contract:
    //   • Primary: user taps "আমি বুঝেছি" (btnAcknowledge).
    //   • Also: tap elsewhere on the card, or "Not sensitive" (which also
    //     cancels the strike + learns the pattern — Bug B/E, unchanged).
    //   • NO silent 3.5s auto-dismiss. A 40s SAFETY FALLBACK is the only
    //     timer, and it logs Timber.w so testers can see if it fires.
    //   • While the card is up, TempBlockManager.isWarningCardShowing gates
    //     the next strike (Bug A redesigned off the old fixed-duration constant).

    private var strikeWarningView: View? = null
    private val strikeWarningSafetyFallbackRunnable = Runnable {
        Timber.w(
            "Strike warning card SAFETY FALLBACK fired after %dms — auto-dismissing and reopening strike gate",
            GuardianConstants.STRIKE_WARNING_SAFETY_FALLBACK_MS
        )
        dismissAiStrikeWarning("safety-fallback")
    }

    private fun showStrikeWarningOverlay(pkg: String, strikeCount: Int, confidence: Float = -1f) {
        dismissAiStrikeWarning("replace", reopenGate = false)

        val card = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_GuardianShield))
            .inflate(R.layout.view_strike_warning, null)
        val titleView = card.findViewById<TextView>(R.id.txtStrikeTitle)
        titleView.text = getString(
            R.string.ai_strike_warning_title_fmt,
            strikeCount,
            GuardianConstants.STRIKE_THRESHOLD
        )
        // kicker + body are static copy from the approved mockup (no strike number).

        // TASK A — display the real AI confidence score as a small badge on the
        // strike-1/2 warning card. Only shown when a real score is available.
        if (confidence >= 0f) {
            val badgeView = card.findViewById<TextView>(R.id.txtConfidenceBadge)
            badgeView?.let {
                it.text = getString(R.string.ai_confidence_badge_fmt, confidence)
                it.visibility = View.VISIBLE
            }
        }

        ReligiousReminders.bind(
            card.findViewById(R.id.txtAyatArabic),
            card.findViewById(R.id.txtAyatBengali),
            card.findViewById(R.id.txtAyatCitation)
        )

        // v2.5.4 — "Not sensitive" audit report (Bug B + Bug E). Own click target:
        // does NOT trigger the card's tap-to-dismiss listener below. Writes one
        // block_events row (reason=NOT_SENSITIVE), cancels the current strike, and
        // (Bug E, v3.1.1) learns the pending AI-detection candidate via
        // FalsePositiveMemory.addSignature so the same pattern is skipped in the
        // future, then dismisses the card.
        // v3.6.0 — the report is REFUSED inside reportNotSensitive() when the
        // pending candidate is confirmed-sensitive (protected) — no strike
        // cancel, no learning, no cooling-off enqueue.
        val notSensitiveBtn = card.findViewById<TextView>(R.id.btnNotSensitive)
        notSensitiveBtn.setOnClickListener {
            reportNotSensitive(pkg, strikeCount, confidence)
            dismissAiStrikeWarning("not-sensitive")
        }

        // v3.6.0 — "সংরক্ষণ করো" (Protect): the user confirms THIS content is
        // genuinely sensitive even though the AI under-scored it. The pending
        // candidate signature is taken (same capture as "Not sensitive",
        // Bug E) and stored PERMANENTLY in ConfirmedSensitiveMemory. Any
        // matching false-positive whitelist entry is removed (defensive
        // override — Protect always wins). Per the user's explicit
        // confirmation, the current warning/strike is NOT escalated: this only
        // affects FUTURE detections of the same/similar pattern.
        val protectBtn = card.findViewById<TextView>(R.id.btnProtect)
        protectBtn.setOnClickListener {
            val sig = falsePositiveMemory.takePendingCandidate()
            if (sig != null) {
                confirmedSensitiveMemory.addConfirmedSignature(sig)
                // Defensive override: if this exact pattern had somehow been
                // whitelisted before, "Protect" removes it so the blacklist and
                // the whitelist can never both contain the same pattern.
                falsePositiveMemory.removeSignature(sig)
                Timber.i("Protected confirmed-sensitive pattern for $pkg (strike=$strikeCount)")
                runCatching {
                    Toast.makeText(this, R.string.ai_strike_protected_confirmed, Toast.LENGTH_SHORT).show()
                }.onFailure { Timber.w(it, "Protect confirmation Toast failed") }
            } else {
                Timber.w("Protect: no pending candidate signature to store for $pkg")
                runCatching {
                    Toast.makeText(this, R.string.ai_strike_protect_unavailable, Toast.LENGTH_SHORT).show()
                }.onFailure { Timber.w(it, "Protect unavailable Toast failed") }
            }
            dismissAiStrikeWarning("protect")
        }

        val acknowledgeBtn = card.findViewById<MaterialButton>(R.id.btnAcknowledge)
        acknowledgeBtn.setOnClickListener {
            Timber.d("Strike warning acknowledged (আমি বুঝেছি) for $pkg strike=$strikeCount")
            dismissAiStrikeWarning("acknowledge")
        }

        val metrics = resources.displayMetrics
        val marginPx = (20 * metrics.density).toInt() // v2.5.0 screen padding token
        val params = WindowManager.LayoutParams(
            metrics.widthPixels - 2 * marginPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = (metrics.heightPixels * 0.18f).toInt() // mid-upper screen (mockup)

        card.setOnClickListener { dismissAiStrikeWarning("tap-card") }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(card, params)
        strikeWarningView = card

        // Gate the next strike until this card is acknowledged / dismissed.
        blockingEngine.setWarningCardShowing(true)

        mainHandler.removeCallbacks(strikeWarningSafetyFallbackRunnable)
        mainHandler.postDelayed(
            strikeWarningSafetyFallbackRunnable,
            GuardianConstants.STRIKE_WARNING_SAFETY_FALLBACK_MS
        )
        Timber.d(
            "Strike %d/%d warning card shown (user-dismiss; safety fallback %dms)",
            strikeCount,
            GuardianConstants.STRIKE_THRESHOLD,
            GuardianConstants.STRIKE_WARNING_SAFETY_FALLBACK_MS
        )
    }

    private fun dismissAiStrikeWarning(reason: String = "dismiss", reopenGate: Boolean = true) {
        mainHandler.removeCallbacks(strikeWarningSafetyFallbackRunnable)
        val view = strikeWarningView
        strikeWarningView = null
        // reopenGate=false is used when replacing a card so the gate raised
        // at StrikeCounted time is not briefly dropped before the new view
        // is attached.
        if (reopenGate) {
            blockingEngine.setWarningCardShowing(false)
        }
        if (view != null) {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            } catch (_: Throwable) {
                // already removed — nothing to do
            }
            Timber.d("Strike warning dismissed ($reason); strike gate reopened")
        } else if (reason != "replace") {
            Timber.d("Strike warning dismiss ($reason) with no view; strike gate reopened")
        }
    }

    /**
     * "Not sensitive" report for a strike-1/2 warning (Bug B + Bug E).
     *
     * Three effects, all triggered when the user taps the card's "Not sensitive":
     *   1. Audit log (unchanged): writes a [BlockEventEntity] row (reason =
     *      [BlockReason.NOT_SENSITIVE], strike count recorded in `matchedTerm`)
     *      for later human review.
     *   2. Live strike undo (Bug B fix): calls
     *      [com.guardian.shield.service.blocker.TempBlockManager.cancelLastStrike]
     *      (via [BlockingEngine.cancelLastStrike]) so the current strike counter
     *      for [pkg] drops by one (floor 0) and the inter-strike timestamp is
     *      cleared — the user's next legitimate scan is not penalised by the
     *      burst-dedup window.
     *   3. Pattern learning (Bug E, user-confirmed reversal of the earlier
     *      audit-only scope): takes the pending AI-detection candidate
     *      ([FalsePositiveMemory.takePendingCandidate]) and, if one is available,
     *      learns it via [FalsePositiveMemory.addSignature] — the same mechanism
     *      the strike-3 "Mark False" button uses. From then on, the same visual
     *      pattern is skipped by [AiDetector.isKnown] before inference, so the
     *      same content no longer re-triggers strikes 1/2/3 (the user found the
     *      old per-event-undo-only behaviour worse: the same content kept
     *      re-triggering strikes repeatedly). Learning is best-effort: a missing
     *      candidate (e.g. process restart) only logs a warning — the strike
     *      undo and the audit log still happen.
     *
     * The DB write runs on [ioScope] (off the main thread); the confirmation Toast
     * is shown immediately from the click handler's (main) thread.
     */
    /**
     * "Not sensitive" report for a strike-1/2 warning (Bug B + Bug E + Task B + v3.6.0).
     *
     * v3.6.0 refusal override (checked FIRST, before everything below): if the
     * pending candidate matches ConfirmedSensitiveMemory, the report is
     * REFUSED — no strike cancel, no learning, no cooling-off enqueue — with
     * honest feedback, regardless of the live confidence score.
     *
     * Task B branching (unchanged, reached only when NOT refused):
     *   LOW confidence (< 0.82) → apply immediately (existing instant behavior):
     *     cancelLastStrike() + addSignature() + dismiss card.
     *   HIGH confidence (>= 0.82) → defer via cooling-off queue:
     *     Insert a PENDING row, schedule WorkManager, show delay messaging.
     *     The strike is NOT cancelled yet — it applies when the delay expires.
     */
    private fun reportNotSensitive(pkg: String, strikeCount: Int, confidence: Float = -1f) {
        // v3.6.0 — CONFIRMED-SENSITIVE REFUSAL OVERRIDE. Runs BEFORE the audit
        // log and BEFORE the confidence-based cooling-off branching, and takes
        // priority over EVERYTHING else in this flow (including the 0.82
        // threshold and the escalating-delay queue): if the current candidate
        // pattern is protected, the report is refused outright — no strike
        // cancel (Bug B), no addSignature learning (Bug E), no cooling-off
        // enqueue (Task B), regardless of the live confidence score. The
        // candidate is PEEKED (not consumed) so the user can still tap
        // "Protect" afterwards; a null candidate (e.g. process restart) falls
        // through to the normal flow, which remains protected by the
        // confidence gate.
        val candidate = falsePositiveMemory.peekPendingCandidate()
        if (candidate != null && confirmedSensitiveMemory.isConfirmedSignature(candidate)) {
            Timber.w("Not-sensitive report REFUSED for $pkg — pattern is confirmed-sensitive (protected)")
            // Audit row for the refusal (reason=NOT_SENSITIVE, distinct
            // matchedTerm) so the attempt stays visible in the evidence trail.
            val matched = "refused:confirmed-sensitive strike=$strikeCount conf=${"%.2f".format(confidence.coerceAtLeast(0f))}"
            ioScope.launch {
                runCatching {
                    blockEventDao.insert(
                        BlockEventEntity(
                            packageName = pkg,
                            reason = BlockReason.NOT_SENSITIVE.name,
                            matchedTerm = matched,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }.onFailure { Timber.e(it, "Failed to log refused not-sensitive report for $pkg") }
            }
            runCatching {
                Toast.makeText(this, R.string.ai_strike_report_refused_confirmed, Toast.LENGTH_LONG).show()
            }.onFailure { Timber.w(it, "Refused-report Toast failed") }
            return
        }

        // Audit log always fires regardless of confidence.
        val matched = "strike=$strikeCount conf=${"%.2f".format(confidence.coerceAtLeast(0f))}"
        ioScope.launch {
            runCatching {
                blockEventDao.insert(
                    BlockEventEntity(
                        packageName = pkg,
                        reason = BlockReason.NOT_SENSITIVE.name,
                        matchedTerm = matched,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onFailure { Timber.e(it, "Failed to log not-sensitive report for $pkg ($matched)") }
        }

        // Task B — confidence-based branching.
        if (pendingReportManager.isHighConfidence(confidence)) {
            // HIGH confidence → defer the action.
            ioScope.launch {
                try {
                    val result = pendingReportManager.enqueue(
                        pkg = pkg,
                        confidence = confidence,
                        source = PendingReportManager.Source.WARNING_CARD,
                        strikeCount = strikeCount
                    )
                    val delayMin = result.delayMs / 60_000
                    val applyTime = java.text.SimpleDateFormat(
                        "HH:mm", java.util.Locale.getDefault()
                    ).format(java.util.Date(result.scheduledApplyAt))
                    val msg = getString(R.string.cooling_queued_fmt, applyTime)
                    mainHandler.post {
                        runCatching {
                            Toast.makeText(this@GuardianAccessibilityService, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    Timber.i("Not-sensitive HIGH-conf queued: pkg=$pkg delay=${delayMin}min applyAt=$applyTime")
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to enqueue pending report for $pkg")
                }
            }
        } else {
            // LOW confidence → apply immediately (existing behavior).
            // Bug B — undo this specific strike.
            blockingEngine.cancelLastStrike(pkg)

            // Bug E — also learn the offending pattern.
            val sig = falsePositiveMemory.takePendingCandidate()
            if (sig != null) {
                falsePositiveMemory.addSignature(sig)
            } else {
                Timber.w("Not-sensitive report: no pending candidate signature to learn from for $pkg")
            }

            runCatching {
                Toast.makeText(this, R.string.ai_strike_report_confirmed, Toast.LENGTH_SHORT).show()
            }.onFailure { Timber.w(it, "Not-sensitive report Toast failed") }
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg.isBlank()) return

        // PHASE 5 — Uninstall / Force-stop / Disable protection.
        // If the user opened a package-manager page that targets us, kick them out.
        if (UninstallProtection.isPackageManager(pkg)) {
            try {
                if (UninstallProtection.isManagingOurApp(this)) {
                    Timber.w("Uninstall/Tamper attempt blocked from $pkg")
                    TamperLogger.log(this, "uninstall-attempt")

                    if (timeLockManager.isLocked() || timeLockManager.isInCooldown()) {
                        // Committed Lock active -> Strict block
                        goHomeAndBlock(pkg, BlockReason.TAMPER_ATTEMPT, "committed_lock_active")
                    } else {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    return
                }
            } catch (t: Throwable) { Timber.e(t, "Uninstall protection check failed") }
        }

        // STABILITY FIX — keyboard / system-UI / home are transient. We must
        // NOT wipe currentPackage when an IME pops over a target app, or the
        // periodic scanner stops scanning.
        if (isSafePackage(pkg)) {
            // Only fully reset state when the user reaches the launcher; for
            // other safe packages (keyboards, system dialogs) we keep the
            // previous currentPackage so AI scanning continues.
            if (pkg == homePkg) {
                currentPackage = null
                clearBlockingFlag("home")
            }
            return
        }

        // ✅ Whitelisted → track but don't block
        if (!rulesEngine.canBlock(pkg)) {
            currentPackage = pkg
            // Optimization: if blocking is in progress (e.g. overlay is launching),
            // don't clear it yet to prevent AI from re-triggering during transition.
            if (!isBlockingInProgress) {
                clearBlockingFlag("whitelist")
            }
            return
        }

        currentPackage = pkg

        // Temp block check
        val tempBlock = blockingEngine.isTempBlocked(pkg)
        if (tempBlock != null) {
            goHomeAndBlock(pkg, BlockReason.APP_BLOCKED, "temp_block:${tempBlock.remainingMinutes}min")
            return
        }

        // Rules check
        val result = rulesEngine.evaluatePackage(pkg)
        if (result is DetectionResult.Block) {
            goHomeAndBlock(pkg, result.reason, result.detail)
            return
        }

        // ✅ Block-eligible but not blocked → reset flag so subsequent AI scan can act
        clearBlockingFlag("window-change-evaluated")

        if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) triggerAiCheckThrottled(pkg)
    }

    private fun handleContentChange(pkg: String) {
        if (pkg.isBlank() || isSafePackage(pkg)) return
        if (!rulesEngine.canBlock(pkg)) return
        if (isBlockingInProgress) return

        currentPackage = pkg

        val now = System.currentTimeMillis()
        if (now - lastTextScan < GuardianConstants.TEXT_THROTTLE_MS) return
        lastTextScan = now

        serviceScope.launch {
            try {
                // AccessibilityNodeInfo trees are NOT thread-safe and can be
                // recycled by the system mid-walk. Traverse on the main thread
                // (the BFS is bounded to a few hundred cheap text reads).
                val text = withContext(Dispatchers.Main) { collectVisibleText() }
                val contentHash = text?.hashCode() ?: 0
                if (contentHash == lastContentHash && !isBlockingInProgress) return@launch
                lastContentHash = contentHash

                if (!text.isNullOrBlank()) {
                    val r = rulesEngine.evaluateText(text)
                    if (r is DetectionResult.Block) {
                        withContext(Dispatchers.Main) { goHomeAndBlock(pkg, r.reason, r.detail) }
                        return@launch
                    }
                }

                // If text scan didn't block, maybe trigger a faster AI check if it's a major change
                if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    withContext(Dispatchers.Main) { triggerAiCheckThrottled(pkg) }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }
    }

    private fun collectVisibleText(): String? {
        val builder = StringBuilder()
        val currentWindows = windows ?: return null

        for (window in currentWindows) {
            val root = window.root ?: continue
            val visited = HashSet<AccessibilityNodeInfo>()
            val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < GuardianConstants.MAX_NODES_BFS) {
                val node = queue.poll() ?: continue
                if (!visited.add(node)) {
                    node.recycle()
                    continue
                }
                count++
                node.text?.toString()?.let { if (it.isNotBlank()) builder.append(it).append(' ') }
                node.contentDescription?.toString()
                    ?.let { if (it.isNotBlank()) builder.append(it).append(' ') }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) queue.add(child)
                }
            }
            // Cleanup: recycle both visited and queued nodes to prevent leaks
            visited.forEach { it.recycle() }
            while (queue.isNotEmpty()) {
                queue.poll()?.recycle()
            }
        }
        return builder.toString().trim().ifEmpty { null }
    }

    private fun triggerAiCheckThrottled(pkg: String) {
        if (isBlockingInProgress) return

        // STABILITY FIX — back off briefly after a screenshot failure storm
        if (screenshotFailStreak >= SCREENSHOT_FAIL_BACKOFF_THRESHOLD) {
            Timber.w("Screenshot back-off active ($screenshotFailStreak fails)")
            return
        }

        val now = System.currentTimeMillis()

        // Use a more aggressive throttle if the user is scrolling to catch fleeting images
        val throttleMs = if (isScrollingActive()) {
            GuardianConstants.AI_THROTTLE_MS / 2
        } else {
            GuardianConstants.AI_THROTTLE_MS
        }

        synchronized(aiScanMap) {
            val last = aiScanMap[pkg] ?: 0L
            if (now - last < throttleMs) return
            aiScanMap[pkg] = now
            if (aiScanMap.size > GuardianConstants.MAX_AI_SCAN_MAP) {
                val it = aiScanMap.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) triggerAiCheck(pkg)
    }

    private fun isScrollingActive(): Boolean {
        // ReelScrollDetector tracks scroll events
        return reelScrollDetector.isCurrentlyScrolling()
    }

    private fun collectImageRegions(): List<android.graphics.Rect> {
        val regions = mutableListOf<android.graphics.Rect>()
        val currentWindows = try { windows } catch (t: Throwable) { null } ?: return emptyList()

        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels

        for (window in currentWindows) {
            val root = try { window.root } catch (t: Throwable) { null } ?: continue
            val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 150) {
                val node = queue.poll() ?: continue
                count++

                val className = node.className?.toString() ?: ""
                val viewId = node.viewIdResourceName?.toString() ?: ""
                val isImage = className.contains("ImageView") || className.contains("Image") ||
                        viewId.contains("image", ignoreCase = true) ||
                        viewId.contains("photo", ignoreCase = true) ||
                        viewId.contains("video", ignoreCase = true) ||
                        viewId.contains("story", ignoreCase = true)

                // PROFILE FIX — profile pictures / avatars are user *identity*, not
                // content, and their tiny crops are a huge source of false AI blocks
                // (they contain a person's face/hair and get cropped without any
                // context). Exclude them explicitly so feed thumbnails still scan.
                val isProfileImage =
                    viewId.contains("avatar", ignoreCase = true) ||
                    viewId.contains("profile", ignoreCase = true) ||
                    viewId.contains("profile_pic", ignoreCase = true) ||
                    viewId.contains("profilepic", ignoreCase = true) ||
                    viewId.contains("user_image", ignoreCase = true) ||
                    viewId.contains("dp_", ignoreCase = true)

                if (isImage && !isProfileImage) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    // Ignore truly tiny icons/buttons/emoji; scan everything else
                    // (including feed thumbnails). Small crops are still protected
                    // by AiDetector so they can't block on a borderline score.
                    if (rect.width() >= MIN_CONTENT_IMAGE_PX && rect.height() >= MIN_CONTENT_IMAGE_PX) {
                        // Ensure rect is within screen bounds to avoid bad crops
                        rect.left = rect.left.coerceIn(0, displayWidth)
                        rect.top = rect.top.coerceIn(0, displayHeight)
                        rect.right = rect.right.coerceIn(0, displayWidth)
                        rect.bottom = rect.bottom.coerceIn(0, displayHeight)
                        if (rect.width() > 64 && rect.height() > 64) {
                            regions.add(rect)
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                node.recycle()
            }
            while (queue.isNotEmpty()) { queue.poll()?.recycle() }
        }

        // Prioritize: Center-Bottom > Center > Size
        return regions.distinct()
            .sortedByDescending { rect ->
                val area = rect.width() * rect.height()
                val centerY = rect.centerY()
                val centerX = rect.centerX()

                var weight = 1.0f
                // Center-weighted
                if (centerX > displayWidth / 4 && centerX < 3 * displayWidth / 4) weight += 0.2f
                // Bottom-weighted (scrolling content)
                if (centerY > displayHeight / 3) weight += 0.3f

                area * weight
            }
            .take(10)
    }

    private suspend fun runContentAwareScan(
        fullBitmap: Bitmap,
        regions: List<android.graphics.Rect>,
        pkg: String
    ): Boolean {
        // High density scanning for detected image regions
        for (rect in regions) {
            if (isBlockingInProgress) return true

            // Validate rect within bitmap bounds
            val left = rect.left.coerceAtLeast(0)
            val top = rect.top.coerceAtLeast(0)
            val width = rect.width().coerceAtMost(fullBitmap.width - left)
            val height = rect.height().coerceAtMost(fullBitmap.height - top)

            // Skip only truly tiny regions (icons/buttons). Thumbnails and avatars
            // that slipped through are still scanned here, but the small-crop
            // protection in AiDetector prevents a borderline score from blocking.
            if (width < MIN_CONTENT_IMAGE_PX || height < MIN_CONTENT_IMAGE_PX) continue

            var regionBmp: Bitmap? = null
            try {
                regionBmp = Bitmap.createBitmap(fullBitmap, left, top, width, height)
                if (aiDetector.isLegacyAvailable()) {
                    val classified = aiDetector.classify(regionBmp)
                    if (classified.unsafe && currentPackage == pkg) {
                        // Confidence is returned atomically with the decision —
                        // do NOT re-read lastDetectionScore (a concurrent scan
                        // can overwrite that volatile between return and read).
                        val conf = classified.confidence
                        // LEARNING MEMORY — keep the offending region so the overlay
                        // can offer "this was a false block" and never block it again.
                        falsePositiveMemory.rememberCandidate(
                            falsePositiveMemory.computeSignature(regionBmp)
                        )
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "content-aware-legacy", conf)
                        }
                        return true
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Region scan failed")
            } finally {
                regionBmp?.recycle()
            }
        }
        return false
    }

    /**
     * STABILITY FIX — on screenshot failure, remove the throttle entry so the
     * next scan isn't suppressed for AI_THROTTLE_MS.
     */
    private fun clearAiThrottleEntry(pkg: String) {
        synchronized(aiScanMap) { aiScanMap.remove(pkg) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String) {
        try {
            // Optimization: If scrolling, prioritize content-aware scanning of ImageViews
            val targetRegions = if (isScrollingActive()) collectImageRegions() else emptyList()

            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        screenshotFailStreak = 0
                        var bmp: Bitmap? = null
                        serviceScope.launch {
                            try {
                                if (isBlockingInProgress) return@launch
                                val hw = screenshot.hardwareBuffer
                                val cs = screenshot.colorSpace
                                bmp = try {
                                    Bitmap.wrapHardwareBuffer(hw, cs)
                                        ?.copy(Bitmap.Config.ARGB_8888, false)
                                } catch (t: Throwable) {
                                    Timber.w(t, "Bitmap.wrapHardwareBuffer failed")
                                    null
                                }
                                try { hw.close() } catch (_: Throwable) {}
                                val b = bmp ?: return@launch
                                if (!rulesEngine.canBlock(pkg)) return@launch

                                // ✅ Ensure the app we are scanning is STILL the one in the foreground
                                if (currentPackage != pkg) {
                                    Timber.d("Package changed during AI scan (from $pkg to $currentPackage). Skipping block.")
                                    return@launch
                                }

                                // Content-Aware Pre-scan: Check detected image regions first
                                var blocked = false
                                if (targetRegions.isNotEmpty()) {
                                    blocked = runContentAwareScan(b, targetRegions, pkg)
                                }

                                if (!blocked) {
                                    if (aiDetector.isLegacyAvailable()) {
                                        val classified = aiDetector.classify(b)
                                        if (classified.unsafe) {
                                            val conf = classified.confidence
                                            // ✅ Final sanity check before blocking
                                            if (currentPackage == pkg) {
                                                // LEARNING MEMORY — keep the frame so the
                                                // overlay can offer "this was a false block".
                                                falsePositiveMemory.rememberCandidate(
                                                    falsePositiveMemory.computeSignature(b)
                                                )
                                                withContext(Dispatchers.Main) {
                                                    goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "legacy", conf)
                                                }
                                                blocked = true
                                            }
                                        }
                                    }
                                }

                                if (!blocked) {
                                    clearBlockingFlag("ai-check-safe")
                                }
                            } catch (t: Throwable) { Timber.e(t, "AI check failed") }
                            finally { try { bmp?.recycle() } catch (_: Throwable) {} }
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        screenshotFailStreak++
                        Timber.w("Screenshot fail: $errorCode (streak=$screenshotFailStreak)")
                        // STABILITY FIX — let the next scan retry immediately
                        clearAiThrottleEntry(pkg)
                    }
                })
        } catch (t: Throwable) {
            screenshotFailStreak++
            Timber.e(t, "takeScreenshot threw (streak=$screenshotFailStreak)")
            clearAiThrottleEntry(pkg)
        }
    }

    /**
     * LIFECYCLE FIX — emit a liveness heartbeat so the foreground-service
     * watchdog can tell "enabled but dead" from "enabled and alive".
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                AccessibilityHeartbeat.beat()
                delay(GuardianConstants.ACCESSIBILITY_HEARTBEAT_MS)
            }
        }
    }

    private fun startPeriodicScanner() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(
                        if (!isScreenOn) GuardianConstants.SCREEN_OFF_PERIODIC_MS
                        else GuardianConstants.AI_PERIODIC_MS
                    )

                    if (!isScreenOn || !protectionEnabled || isDeviceLocked()) continue
                    if (isBlockingInProgress) continue

                    val pkg = currentPackage ?: continue
                    if (isSafePackage(pkg)) continue
                    if (!rulesEngine.canBlock(pkg)) continue

                    val tempBlock = blockingEngine.isTempBlocked(pkg)
                    if (tempBlock != null) {
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(
                                pkg, BlockReason.APP_BLOCKED,
                                "temp_block:${tempBlock.remainingMinutes}min"
                            )
                        }
                        continue
                    }

                    val r = rulesEngine.evaluatePackage(pkg)
                    if (r is DetectionResult.Block) {
                        withContext(Dispatchers.Main) { goHomeAndBlock(pkg, r.reason, r.detail) }
                        continue
                    }

                    if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ) withContext(Dispatchers.Main) { triggerAiCheckThrottled(pkg) }

                } catch (t: Throwable) { Timber.e(t) }
            }
        }
    }

    /**
     * STABILITY FIX — every few seconds, if [isBlockingInProgress] has been
     * stuck for longer than [BLOCKING_FLAG_MAX_HOLD_MS], force-clear it. This
     * is the single biggest cause of "AI detection becomes silent" complaints.
     */
    private fun startStuckFlagWatchdog() {
        stuckFlagJob?.cancel()
        stuckFlagJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(3_000L)
                    if (isBlockingInProgress && blockingFlagSetAt > 0) {
                        val held = System.currentTimeMillis() - blockingFlagSetAt
                        if (held > BLOCKING_FLAG_MAX_HOLD_MS) {
                            Timber.w("isBlockingInProgress stuck for ${held}ms — forcing reset")
                            clearBlockingFlag("watchdog-timeout")
                        }
                    }
                    // STABILITY FIX — periodic screenshot streak decay so
                    // back-off doesn't last forever once the device recovers.
                    if (screenshotFailStreak > 0
                        && screenshotFailStreak >= SCREENSHOT_FAIL_BACKOFF_THRESHOLD) {
                        // Try one slow recovery scan
                        screenshotFailStreak = SCREENSHOT_FAIL_BACKOFF_THRESHOLD - 1
                    }
                } catch (t: Throwable) { Timber.e(t, "watchdog tick failed") }
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("Accessibility service interrupted")
        clearBlockingFlag("interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissAiStrikeWarning()
        runCatching { unregisterReceiver(screenReceiver) }
        periodicJob?.cancel()
        stuckFlagJob?.cancel()
        heartbeatJob?.cancel()
        serviceScope.cancel()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        try { aiDetector.close() } catch (_: Throwable) {}
    }
}
