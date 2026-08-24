// GATE STUB — kotlinx-coroutines-core API surface (compile-time faithful).
package kotlinx.coroutines

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface Job : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    fun cancel(cause: java.util.concurrent.CancellationException? = null) {}
    fun start(): Boolean = true
    val isActive: Boolean get() = true
    val isCompleted: Boolean get() = false
    companion object Key : CoroutineContext.Key<Job>
}

abstract class CoroutineDispatcher : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: kotlin.coroutines.Continuation<T>): kotlin.coroutines.Continuation<T> = continuation
}

object Dispatchers {
    val Default: CoroutineDispatcher = object : CoroutineDispatcher() {}
    val Main: CoroutineDispatcher = object : CoroutineDispatcher() {}
    val IO: CoroutineDispatcher = object : CoroutineDispatcher() {}
    val Unconfined: CoroutineDispatcher = object : CoroutineDispatcher() {}
}

interface CoroutineScope {
    val coroutineContext: CoroutineContext
}

fun CoroutineScope(context: CoroutineContext): CoroutineScope =
    object : CoroutineScope { override val coroutineContext: CoroutineContext = context }

fun SupervisorJob(parent: Job? = null): Job = object : Job {}

val CoroutineScope.isActive: Boolean get() = true

fun CoroutineScope.cancel(cause: java.util.concurrent.CancellationException? = null) {}

fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): Deferred<T> = throw RuntimeException("stub")

interface Deferred<out T> : Job {
    suspend fun await(): T
}

fun CoroutineScope.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
): Job = object : Job {}

suspend fun delay(timeMillis: Long) {}

suspend fun <T> withContext(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = throw RuntimeException("stub")

suspend fun <T> withTimeoutOrNull(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> T
): T? = throw RuntimeException("stub")

object NonCancellable : AbstractCoroutineContextElement(Job), Job {
    override val key: CoroutineContext.Key<*> get() = Job.Key
}

interface CompletableJob : Job

fun Job(parent: Job? = null): CompletableJob = object : CompletableJob {}

fun runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {}
