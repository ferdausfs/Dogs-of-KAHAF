// GATE STUB — viewModels() delegates.
package androidx.activity

import androidx.lifecycle.ViewModel

class ViewModelProvider {
    open class Factory
}

inline fun <reified VM : ViewModel> ComponentActivity.viewModels(
    noinline factoryProducer: (() -> Any?)? = null
): Lazy<VM> = lazy { throw RuntimeException("stub") }
