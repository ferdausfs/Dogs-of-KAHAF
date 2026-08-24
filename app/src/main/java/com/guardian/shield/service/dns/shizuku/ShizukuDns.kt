package com.guardian.shield.service.dns.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * R6 — one-tap, computer-free bridge to the Shizuku shell.
 *
 * The single most important trick: `pm grant` runs fine from the shell uid,
 * so once Shizuku is running and the user taps "Enable", the app grants
 * ITSELF WRITE_SECURE_SETTINGS — permanently, surviving Shizuku stops,
 * reboots and app restarts (only lost on uninstall). After that legitimate
 * grant, every DNS write uses the plain Settings.Global path and Shizuku is
 * out of the loop; the shell stays only as an interim fallback.
 */
object ShizukuDns {

    @Volatile
    private var service: IDnsUserService? = null

    @Volatile
    private var bindLatch: CountDownLatch? = null

    @Volatile
    private var pendingOnBound: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IDnsUserService.asInterface(binder)
            Timber.i("ShizukuDns: user service connected")
            pendingOnBound?.invoke()
            pendingOnBound = null
            bindLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            Timber.w("ShizukuDns: user service disconnected")
        }
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                com.guardian.shield.BuildConfig.APPLICATION_ID,
                DnsUserService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("shizuku_dns")
            .debuggable(com.guardian.shield.BuildConfig.DEBUG)
            .version(1)
    }

    /** Shizuku app installed AND its server running (started by the user). */
    fun isShizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** Our permission to talk to Shizuku (its own runtime permission). */
    fun hasShizukuPermission(): Boolean =
        isShizukuRunning() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun requestPermission(requestCode: Int = 9441) {
        runCatching { Shizuku.requestPermission(requestCode) }
            .onFailure { Timber.e(it, "ShizukuDns: requestPermission failed") }
    }

    fun bindService(onBound: (() -> Unit)? = null) {
        pendingOnBound = onBound
        runCatching { Shizuku.bindUserService(userServiceArgs, serviceConnection) }
            .onFailure { Timber.e(it, "ShizukuDns: bindUserService failed") }
    }

    /**
     * Blocking bind with a small timeout — safe from worker/IO threads (the
     * engine paths call us off the main thread; UI callers use [bindService]
     * with the callback instead).
     */
    fun ensureBound(timeoutMs: Long = 3000): Boolean {
        if (service != null) return true
        if (!hasShizukuPermission()) return false
        val latch = CountDownLatch(1)
        bindLatch = latch
        bindService()
        runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
        bindLatch = null
        return service != null
    }

    /** Execute a shell command via Shizuku. Returns "code\noutput" or null. */
    fun run(command: String): String? {
        val svc = service ?: return null
        return runCatching { svc.exec(command) }.getOrNull()
    }

    /** One-tap: shell-side `pm grant` gives US the permanent permission. */
    fun grantSelfSecureSettings(context: Context): Boolean {
        val out = run(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        ) ?: return false
        Timber.i("ShizukuDns: pm grant -> $out")
        return out.startsWith("0\n")
    }
}
