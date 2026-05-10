package com.guardian.shield.util

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * v12 (2.1.2): added a process-wide singleton [appIo] scope for fire-and-forget
 *  background work (e.g. AiDetector teardown from a service that's about to die).
 *  Previously each caller created a fresh `Scopes.io()` and immediately leaked
 *  it (no cancel ever) — Singleton scope solves that without leaking.
 *
 * Existing fun io() / default() kept for back-compat.
 */
object Scopes {
    private val handler = CoroutineExceptionHandler { _, t ->
        runCatching { Timber.e(t, "Uncaught coroutine exception") }
    }

    /** New, owned scope. Caller MUST cancel it in their own teardown. */
    fun io(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    fun default(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

    /** v12: shared, app-lifetime IO scope — for one-shot fire-and-forget jobs that
     *  outlive the caller (typical example: tearing down TFLite from an
     *  AccessibilityService.onDestroy that itself is being killed). */
    val appIo: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    val appDefault: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
}
