// GATE STUB — fragment viewModels().
package androidx.fragment.app

import androidx.lifecycle.ViewModel

inline fun <reified VM : ViewModel> Fragment.viewModels(
    noinline factoryProducer: (() -> Any?)? = null
): Lazy<VM> = lazy { throw RuntimeException("stub") }

inline fun <reified VM : ViewModel> Fragment.activityViewModels(
    noinline factoryProducer: (() -> Any?)? = null
): Lazy<VM> = lazy { throw RuntimeException("stub") }
