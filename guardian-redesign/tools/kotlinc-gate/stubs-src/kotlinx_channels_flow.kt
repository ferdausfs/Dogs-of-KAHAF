// GATE STUB — channel/flow bridges.
package kotlinx.coroutines.flow

import kotlinx.coroutines.channels.ReceiveChannel

fun <T> ReceiveChannel<T>.receiveAsFlow(): Flow<T> = object : Flow<T> {}
