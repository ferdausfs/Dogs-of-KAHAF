// GATE STUB — androidx.datastore.core.
package androidx.datastore.core

import kotlinx.coroutines.flow.Flow

interface DataStore<T> {
    val data: Flow<T>
    suspend fun updateData(transform: suspend (T) -> T): T
}
