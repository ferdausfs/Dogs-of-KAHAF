// GATE STUB — kotlinx-coroutines flow surface.
@file:Suppress("UNCHECKED_CAST")
package kotlinx.coroutines.flow

import kotlinx.coroutines.CoroutineScope

interface Flow<out T> {
    suspend fun collect(collector: suspend (T) -> Unit) {}
}

interface SharedFlow<out T> : Flow<T>

interface StateFlow<out T> : SharedFlow<T> {
    val value: T
}

interface MutableSharedFlow<T> : SharedFlow<T> {
    fun tryEmit(value: T): Boolean
    suspend fun emit(value: T)
}

interface MutableStateFlow<T> : StateFlow<T>, MutableSharedFlow<T> {
    override var value: T
}

private class MutableSharedFlowImpl<T>(
    @Suppress("unused") replay: Int,
    @Suppress("unused") extraBufferCapacity: Int
) : MutableSharedFlow<T> {
    override fun tryEmit(value: T): Boolean = true
    override suspend fun emit(value: T) {}
}

private class MutableStateFlowImpl<T>(initial: T) : MutableStateFlow<T> {
    override var value: T = initial
    override fun tryEmit(value: T): Boolean = true
    override suspend fun emit(value: T) {}
}

fun <T> MutableSharedFlow(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: Any? = null
): MutableSharedFlow<T> = MutableSharedFlowImpl(replay, extraBufferCapacity)

fun <T> MutableStateFlow(value: T): MutableStateFlow<T> = MutableStateFlowImpl(value)

fun <T> MutableSharedFlow<T>.asSharedFlow(): SharedFlow<T> = this
fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this

fun <T, R> Flow<T>.map(transform: suspend (T) -> R): Flow<R> =
    object : Flow<R> {}

suspend fun <T> Flow<T>.first(): T = throw RuntimeException("stub")
suspend fun <T> Flow<T>.firstOrNull(): T? = null

interface SharingStarted {
    companion object {
        fun WhileSubscribed(stopTimeoutMillis: Long = 0): SharingStarted = object : SharingStarted {}
        val Eagerly: SharingStarted = object : SharingStarted {}
        val Lazily: SharingStarted = object : SharingStarted {}
    }
}

fun <T> Flow<T>.stateIn(scope: CoroutineScope, started: SharingStarted, initialValue: T): StateFlow<T> =
    MutableStateFlow(initialValue)

fun <T1, T2, R> combine(
    f1: Flow<T1>, f2: Flow<T2>,
    transform: suspend (T1, T2) -> R
): Flow<R> = object : Flow<R> {}

fun <T1, T2, T3, R> combine(
    f1: Flow<T1>, f2: Flow<T2>, f3: Flow<T3>,
    transform: suspend (T1, T2, T3) -> R
): Flow<R> = object : Flow<R> {}

fun <T1, T2, T3, T4, R> combine(
    f1: Flow<T1>, f2: Flow<T2>, f3: Flow<T3>, f4: Flow<T4>,
    transform: suspend (T1, T2, T3, T4) -> R
): Flow<R> = object : Flow<R> {}

fun <T, R> combine(
    vararg flows: Flow<T>,
    transform: suspend (Array<T>) -> R
): Flow<R> = object : Flow<R> {}

fun <T> flowOf(vararg values: T): Flow<T> = object : Flow<T> {}
