// GATE STUB — rikka.shizuku (Shizuku API surface used by the app).
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
        fun newProcess(
            cmd: Array<String>,
            env: Array<String>?,
            dir: String?
        ): Process = throw UnsupportedOperationException("stub")
    }
}
