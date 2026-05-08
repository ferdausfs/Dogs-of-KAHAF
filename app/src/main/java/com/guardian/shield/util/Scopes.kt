package com.guardian.shield.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * v9 (2.0.0) — P5-A: shared coroutine-scope factory.
 *
 * BlockingEngine, GuardianForegroundService, GuardianAccessibilityService
 * all manually built `CoroutineScope(SupervisorJob() + Dispatchers.X)`. We
 * centralise that boilerplate here. Each call returns a NEW scope — callers
 * are responsible for cancelling them in their own teardown paths.
 */
object Scopes {
    fun io(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun default(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
