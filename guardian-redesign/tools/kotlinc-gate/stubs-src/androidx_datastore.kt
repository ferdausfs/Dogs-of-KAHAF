// GATE STUB — androidx.datastore.preferences.core.
package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore

abstract class Preferences {
    open class Key<T>(val name: String)

    abstract operator fun <T> get(key: Key<T>): T?
    abstract fun contains(key: Key<*>): Boolean
    abstract fun asMap(): Map<Key<*>, Any>
}

class MutablePreferences : Preferences() {
    override fun <T> get(key: Key<T>): T? = null
    override fun contains(key: Key<*>): Boolean = false
    override fun asMap(): Map<Key<*>, Any> = emptyMap()

    operator fun <T> set(key: Key<T>, value: T) {}
    fun <T> remove(key: Key<T>): T? = null
    fun clear() {}
}

fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = Preferences.Key(name)
fun floatPreferencesKey(name: String): Preferences.Key<Float> = Preferences.Key(name)
fun intPreferencesKey(name: String): Preferences.Key<Int> = Preferences.Key(name)
fun longPreferencesKey(name: String): Preferences.Key<Long> = Preferences.Key(name)
fun stringPreferencesKey(name: String): Preferences.Key<String> = Preferences.Key(name)
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>> = Preferences.Key(name)

suspend fun DataStore<Preferences>.edit(
    transform: suspend (MutablePreferences) -> Unit
): Preferences {
    val prefs = MutablePreferences()
    transform(prefs)
    return prefs
}

fun emptyPreferences(): Preferences = MutablePreferences()
