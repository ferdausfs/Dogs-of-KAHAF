// GATE STUB — androidx.datastore.preferences (preferencesDataStore delegate).
package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun preferencesDataStore(
    name: String
): ReadOnlyProperty<Context, DataStore<Preferences>> =
    object : ReadOnlyProperty<Context, DataStore<Preferences>> {
        override fun getValue(thisRef: Context, property: KProperty<*>): DataStore<Preferences> =
            throw RuntimeException("stub")
    }
