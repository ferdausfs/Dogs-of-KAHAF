// GATE STUB — kotlinx-coroutines sync.
package kotlinx.coroutines.sync

class Mutex(locked: Boolean = false) {
    suspend fun lock(owner: Any? = null) {}
    fun unlock(owner: Any? = null) {}
    val isLocked: Boolean get() = false
}

suspend inline fun <T> Mutex.withLock(owner: Any? = null, action: () -> T): T = action()
