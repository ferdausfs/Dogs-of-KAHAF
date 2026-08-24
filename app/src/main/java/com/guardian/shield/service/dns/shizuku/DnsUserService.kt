package com.guardian.shield.service.dns.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import timber.log.Timber

/**
 * R6 — hand-rolled Binder equivalent of:
 *
 *   interface IDnsUserService { String exec(String command); }
 *
 * AIDL output is exactly this same boilerplate; writing it in pure Kotlin
 * keeps the offline kotlinc gate able to compile the WHOLE app with zero
 * code generation. Wire format (must never change):
 *   descriptor  = com.guardian.shield.service.dns.shizuku.IDnsUserService
 *   exec (code=1): in  (interfaceToken, String cmd)
 *                  out (noException, String "<exitCode>\n<combined output>")
 */
interface IDnsUserService : android.os.IInterface {
    fun exec(command: String): String?

    companion object {
        const val DESCRIPTOR = "com.guardian.shield.service.dns.shizuku.IDnsUserService"
        const val TRANSACTION_EXEC = IBinder.FIRST_CALL_TRANSACTION

        fun asInterface(binder: IBinder?): IDnsUserService? {
            if (binder == null) return null
            val iin = binder.queryLocalInterface(DESCRIPTOR)
            if (iin is IDnsUserService) return iin
            return Proxy(binder)
        }
    }

    class Proxy(private val remote: IBinder) : IDnsUserService {
        override fun asBinder(): IBinder = remote

        override fun exec(command: String): String? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(command)
                remote.transact(TRANSACTION_EXEC, data, reply, 0)
                reply.readException()
                reply.readString()
            } catch (t: Throwable) {
                Timber.e(t, "ShizukuDns proxy exec failed")
                null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}

abstract class DnsUserServiceStub : Binder(), IDnsUserService {
    init {
        attachInterface(this, IDnsUserService.DESCRIPTOR)
    }

    override fun asBinder(): IBinder = this

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            IBinder.INTERFACE_TRANSACTION -> {
                reply?.writeString(IDnsUserService.DESCRIPTOR)
                true
            }
            IDnsUserService.TRANSACTION_EXEC -> {
                // Manual interface-token check (wire-compatible with
                // writeInterfaceToken == writeString; the SDK-level
                // Parcel.enforceInterfaceToken helper is not public API).
                if (data.readString() != IDnsUserService.DESCRIPTOR) {
                    return super.onTransact(code, data, reply, flags)
                }
                val result = exec(data.readString().orEmpty())
                reply?.writeNoException()
                reply?.writeString(result)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }
}

/**
 * Launched by the SHIZUKU server in a shell-uid (2000) process — so
 * ProcessBuilder("sh", "-c", ...) here carries shell privileges:
 * `settings put global ...`, `pm grant ...` etc. That's the entire
 * mechanism of the no-computer, one-tap DNS grant (R6).
 *
 * Note: declared exported=true in the manifest (Shizuku starts it across
 * uid). A THIRD-PARTY app starting this service would only get OUR uid's
 * privileges, never shell — it is inert as an attack surface.
 */
class DnsUserService : DnsUserServiceStub() {

    override fun exec(command: String): String {
        return try {
            val proc = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            "$code\n$output"
        } catch (t: Throwable) {
            Timber.e(t, "DnsUserService exec failed: $command")
            "-1\n${t.message.orEmpty()}"
        }
    }
}
