// GATE STUB — androidx.fragment.app.
package androidx.fragment.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

open class FragmentActivity : androidx.activity.ComponentActivity {
    constructor() : super()

    val supportFragmentManager: FragmentManager
        get() = throw RuntimeException("stub")
}

abstract class FragmentTransaction {
    open fun replace(containerViewId: Int, fragment: Fragment): FragmentTransaction = this
    open fun add(containerViewId: Int, fragment: Fragment): FragmentTransaction = this
    open fun remove(fragment: Fragment): FragmentTransaction = this
    open fun addToBackStack(name: String?): FragmentTransaction = this
    open fun commit(): Int = 0
}

abstract class FragmentManager {
    abstract fun beginTransaction(): FragmentTransaction
}

abstract class DialogFragment : Fragment() {
    open fun show(manager: FragmentManager, tag: String?) {}
    open fun dismiss() {}
    open fun dismissAllowingStateLoss() {}
}

@Suppress("DEPRECATION")
open class Fragment : LifecycleOwner {
    override val lifecycle: Lifecycle get() = throw RuntimeException("stub")
    val viewLifecycleOwner: LifecycleOwner get() = throw RuntimeException("stub")
    val parentFragmentManager: FragmentManager get() = throw RuntimeException("stub")
    val childFragmentManager: FragmentManager get() = throw RuntimeException("stub")
    val context: Context? get() = null
    val activity: FragmentActivity? get() = null
    val view: View? get() = null
    val isAdded: Boolean get() = true
    var arguments: Bundle? = null
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = null
    open fun onViewCreated(view: View, savedInstanceState: Bundle?) {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onDestroyView() {}
    open fun onDestroy() {}
    fun requireContext(): Context = context ?: throw IllegalStateException("stub")
    fun requireActivity(): FragmentActivity = activity ?: throw IllegalStateException("stub")
    fun requireView(): View = view ?: throw IllegalStateException("stub")
    fun getString(resId: Int): String = ""
    fun getString(resId: Int, vararg formatArgs: Any?): String = ""
    fun startActivity(intent: Intent) {}
    fun startActivityForResult(intent: Intent, requestCode: Int) {}
}

class FragmentContainerView : android.widget.FrameLayout {
    constructor(context: Context) : super(context)
}
