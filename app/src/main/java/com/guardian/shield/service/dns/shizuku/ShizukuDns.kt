package com.guardian.shield.service.dns.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * R7.2 — one-tap, computer-free bridge to the Shizuku shell.
 *
 * Rewritten on top of Shizuku's OFFICIAL [Shizuku.newProcess]: no UserService,
 * no hand-rolled binder, no bind dance — just a shell process like `adb shell`.
 * This removes the whole class of bugs where an in-process binder was never
 * delivered (v3.7.0's DnsUserService, first device test failed).
 *
 * The single most important trick stands: `pm grant` runs fine from the shell
 * uid, so once Shizuku is running and the user taps "Enable", the app grants
 * ITSELF WRITE_SECURE_SETTINGS — permanently, surviving Shizuku stops,
 * reboots and app restarts (only lost on uninstall). After that legitimate
 * grant, every DNS write uses the plain Settings.Global path (engine
 * PERMANENT) and Shizuku is out of the loop; the shell stays as fallback.
 *
 * MUST be called off the main thread (process exec + waitFor). All UI
 * callers dispatch to Dispatchers.IO.
 */
object ShizukuDns {

    /**
     * `Shizuku.newProcess` is a PRIVATE static in shizuku-api 13.1.5 — the
     * documented avenue (Shizuku's own samples) is reflection with a cached
     * Method handle. R8-safe: `rikka.shizuku.**` is fully kept in proguard.
     */
    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }.onFailure {
            Timber.e(it, "ShizukuDns: newProcess missing (Shizuku too old?)")
        }.getOrNull()
    }

    private fun newShizukuProcess(cmd: Array<String>): Process? {
        val method = newProcessMethod ?: return null
        return runCatching { method.invoke(null, cmd, null, null) as Process }
            .onFailure { Timber.e(it, "ShizukuDns: newProcess invoke failed") }
            .getOrNull()
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

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /**
     * Execute [command] in the Shizuku shell. Returns exit code + combined
     * stdout/stderr, or null when the process could not even start
     * (Shizuku gone mid-flight, binder failure, exec error).
     */
    fun run(command: String): Result? {
        if (!hasShizukuPermission()) return null
        val proc = newShizukuProcess(arrayOf("sh", "-c", command)) ?: return null
        // Read BEFORE waitFor: if the child filled the 64KB pipe buffer it
        // blocks on write, and waitFor would never return (classic deadlock).
        val out = runCatching {
            proc.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")
        val err = runCatching {
            proc.errorStream.bufferedReader().readText().trim()
        }.getOrDefault("")
        val code = runCatching { proc.waitFor() }
            .onFailure { Timber.e(it, "ShizukuDns: waitFor failed") }
            .getOrNull() ?: return null
        val combined = listOf(out, err).filter { it.isNotEmpty() }.joinToString("\n")
        if (code != 0) Timber.w("ShizukuDns: `$command` exit=$code: $combined")
        return Result(code, combined)
    }

    fun getGlobal(key: String): String? {
        val r = run("settings get global $key") ?: return null
        if (!r.ok) return null
        val line = r.output.lineSequence().firstOrNull()?.trim() ?: return null
        return line.takeUnless { it.isEmpty() || it == "null" }
    }

    fun putGlobal(key: String, value: String): Boolean =
        run("settings put global $key $value")?.ok == true

    /** One-tap: shell-side `pm grant` gives US the permanent permission. */
    fun grantSelfSecureSettings(context: Context): Boolean {
        val r = run(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        ) ?: return false
        Timber.i("ShizukuDns: pm grant -> exit=${r.exitCode} ${r.output}")
        return r.ok
    }
}
