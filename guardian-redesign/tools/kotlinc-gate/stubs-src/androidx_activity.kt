// GATE STUB — androidx.activity.
package androidx.activity

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

abstract class OnBackPressedCallback(private var enabled: Boolean) {
    abstract fun handleOnBackPressed()
    var isEnabled: Boolean
        get() = enabled
        set(value) { enabled = value }
}

class OnBackPressedDispatcher {
    fun addCallback(owner: LifecycleOwner, onBackPressedCallback: OnBackPressedCallback) {}
    fun addCallback(onBackPressedCallback: OnBackPressedCallback) {}
    fun onBackPressed() {}
}

abstract class ComponentActivity : Activity(), LifecycleOwner {
    override val lifecycle: Lifecycle get() = throw RuntimeException("stub")
    val onBackPressedDispatcher: OnBackPressedDispatcher get() = throw RuntimeException("stub")
    fun <I, O> registerForActivityResult(
        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
        callback: (O) -> Unit
    ): androidx.activity.result.ActivityResultLauncher<I> = throw RuntimeException("stub")
}
