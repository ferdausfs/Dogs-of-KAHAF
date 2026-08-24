// GATE STUB — androidx.lifecycle.
package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope

class Lifecycle {
    enum class State { DESTROYED, INITIALIZED, CREATED, STARTED, RESUMED }
}

interface LifecycleOwner {
    val lifecycle: Lifecycle
}

abstract class ViewModel

val ViewModel.viewModelScope: CoroutineScope
    get() = throw RuntimeException("stub")

val LifecycleOwner.lifecycleScope: CoroutineScope
    get() = throw RuntimeException("stub")

suspend fun LifecycleOwner.repeatOnLifecycle(
    state: Lifecycle.State,
    block: suspend CoroutineScope.() -> Unit
) {
}

abstract class LiveData<T> {
    var value: T?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") v) {}
}

class MutableLiveData<T> : LiveData<T>()
