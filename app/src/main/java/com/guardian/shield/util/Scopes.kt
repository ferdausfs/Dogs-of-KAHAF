package com.guardian.shield.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object Scopes {
    fun io() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun default() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun main() = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
