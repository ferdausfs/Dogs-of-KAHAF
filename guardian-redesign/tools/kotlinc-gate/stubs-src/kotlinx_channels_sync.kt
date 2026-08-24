// GATE STUB — kotlinx-coroutines channels + sync.
package kotlinx.coroutines.channels

import kotlinx.coroutines.flow.Flow

interface ReceiveChannel<out E> {
    fun close(cause: Throwable? = null): Boolean
}

interface SendChannel<in E> {
    suspend fun send(element: E)
    fun trySend(element: E): Unit
}

open class Channel<E> : SendChannel<E>, ReceiveChannel<E> {
    override suspend fun send(element: E) {}
    override fun trySend(element: E) {}
    override fun close(cause: Throwable?): Boolean = true

    companion object {
        const val UNLIMITED: Int = Int.MAX_VALUE
        const val BUFFERED: Int = -2
        const val RENDEZVOUS: Int = 0
        const val CONFLATED: Int = -1
    }
}

fun <E> Channel(capacity: Int = Channel.RENDEZVOUS): Channel<E> = Channel()
