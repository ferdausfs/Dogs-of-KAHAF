package com.guardian.shield.service.dns

import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * R7 — ROOT engine for Private DNS.
 *
 * A rooted phone needs no computer and no Shizuku at all: `su -c` can run
 * `settings put/get global` directly. Detection is a one-shot probe and the
 * result is cached for the process lifetime. On non-rooted devices `su` is
 * either missing (exec throws) or refuses instantly, so probing is cheap and
 * safe; a timeout guards against a `su` that blocks on a UI grant prompt.
 */
object DnsRoot {

    @Volatile
    private var probed: Boolean? = null

    /** Cached root-availability probe ("su -c id" exits 0). */
    @Synchronized
    fun hasRoot(): Boolean {
        probed?.let { return it }
        val ok = run("id", timeoutMs = 2000)?.first == 0
        if (!ok) Timber.d("DnsRoot: no root shell")
        probed = ok
        return ok
    }

    /** Read a Settings.Global value, or null when unavailable/failing. */
    fun getGlobal(key: String): String? {
        val (code, out) = run("settings get global $key") ?: return null
        if (code != 0) return null
        val line = out.lineSequence().firstOrNull()?.trim() ?: return null
        return line.takeUnless { it.isEmpty() || it == "null" }
    }

    /** Write a Settings.Global value. Returns true on exit code 0. */
    fun putGlobal(key: String, value: String): Boolean =
        run("settings put global $key $value")?.first == 0

    /**
     * Execute [command] via `su -c`. @return Pair(exitCode, stdout) or null
     * when the shell could not even start (no su / error / timeout).
     */
    private fun run(command: String, timeoutMs: Long = 4000): Pair<Int, String>? {
        return runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                Timber.w("DnsRoot: timeout running: $command")
                return null
            }
            val out = runCatching {
                proc.inputStream.bufferedReader().readText()
            }.getOrDefault("")
            proc.exitValue() to out
        }.onFailure { Timber.d("DnsRoot: exec failed: ${it.message}") }
            .getOrNull()
    }
}
