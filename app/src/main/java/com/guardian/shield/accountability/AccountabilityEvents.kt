package com.guardian.shield.accountability

import timber.log.Timber

/**
 * PHASE 2 (v3.5.0) — process-wide accountability event hub.
 *
 * OBSERVE-ONLY. Producers (TamperLogger, PendingReportManager) emit events
 * AFTER their existing work is done; this hub cannot change any detection,
 * blocking, strike, or cooling-off decision. The single consumer is
 * [AccountabilityNotifier], registered at app start.
 *
 * All emission is wrapped in runCatching: a listener failure must never break
 * the producer (tamper logging / cooling-off queue) that emitted the event.
 */
object AccountabilityEvents {

    enum class Kind {
        /** Protection was paused/disabled by the user from the dashboard. */
        PROTECTION_PAUSED,
        /** Anti-tamper event (same attemptType strings TamperLogger persists). */
        TAMPER_DETECTED,
        /**
         * A HIGH-confidence "Not sensitive"/"Mark False" report was queued by
         * the confidence-based cooling-off system (observational hook in
         * PendingReportManager.enqueue — the queue decision does not change).
         */
        HIGH_CONFIDENCE_REPORT
    }

    data class Event(
        val kind: Kind,
        val detail: String,
        val at: Long = System.currentTimeMillis()
    )

    @Volatile
    private var listener: ((Event) -> Unit)? = null

    fun setListener(l: ((Event) -> Unit)?) {
        listener = l
    }

    fun emit(kind: Kind, detail: String) {
        val l = listener ?: return
        runCatching { l.invoke(Event(kind, detail)) }
            .onFailure { Timber.w(it, "Accountability listener failed for $kind") }
    }
}
