// GATE STUB — rikka.shizuku (Shizuku API surface used by the app).
// NOTE: newProcess is intentionally NOT here — it is private in the real
// API and reached via reflection in the app, so the gate must NOT allow
// direct calls to it (that's how this bug reached CI in v3.7.2-pre).
package rikka.shizuku

class Shizuku private constructor() {

    fun interface OnRequestPermissionResultListener {
        fun onRequestPermissionResult(requestCode: Int, grantResult: Int)
    }

    companion object {
        fun pingBinder(): Boolean = false
        fun checkSelfPermission(): Int = -1
        fun requestPermission(requestCode: Int) {}
        fun addRequestPermissionResultListener(
            listener: OnRequestPermissionResultListener
        ) {
        }
        fun removeRequestPermissionResultListener(
            listener: OnRequestPermissionResultListener
        ) {
        }
    }
}
