// GATE STUB — rikka.shizuku (Shizuku API surface used by the app).
package rikka.shizuku

import android.content.ComponentName
import android.content.ServiceConnection

class Shizuku private constructor() {

    class UserServiceArgs(componentName: ComponentName) {
        fun daemon(value: Boolean): UserServiceArgs = this
        fun processNameSuffix(suffix: String): UserServiceArgs = this
        fun debuggable(value: Boolean): UserServiceArgs = this
        fun version(code: Int): UserServiceArgs = this
    }

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
        fun bindUserService(args: UserServiceArgs, connection: ServiceConnection) {}
        fun unbindUserService(
            args: UserServiceArgs,
            connection: ServiceConnection,
            remove: Boolean
        ) {
        }
    }
}
