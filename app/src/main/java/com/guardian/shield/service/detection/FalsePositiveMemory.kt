package com.guardian.shield.service.detection

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device "learning" memory for false positives.
 *
 * When the user marks a block as a mistake ("this was a false block"), we store
 * a compact COLOR signature of the offending image. From then on, any image that
 * looks like that same pattern (same colour layout) is treated as safe and is no
 * longer blocked — this is how the app gradually "learns" the harmless patterns
 * a given feed/user keeps showing.
 *
 * The signature math lives in [ImageSignature] (shared with
 * [ConfirmedSensitiveMemory], the opposite-direction store, so the two can
 * never drift apart). Storage: `false_positive_signatures.dat` in filesDir,
 * the same format since v2.4.2.
 *
 * v3.6.0 additions (pure API surface, no behaviour change to the existing
 * methods):
 *  - [peekPendingCandidate]: read the pending candidate WITHOUT consuming it —
 *    used by the report handlers to run the ConfirmedSensitiveMemory refusal
 *    check before deciding whether to let a report proceed.
 *  - [removeSignature]: remove every stored entry matching [sig] — used by the
 *    "Protect" flow so a newly confirmed-sensitive pattern can never remain
 *    simultaneously whitelisted here.
 */
@Singleton
class FalsePositiveMemory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val lock = Any()
    private val signatures = ArrayList<IntArray>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The most recent AI-block candidate (the image that actually caused the
    // block). The block overlay reads this when the user taps "this was wrong".
    @Volatile private var pendingCandidate: IntArray? = null

    companion object {
        private const val FILE_NAME = "false_positive_signatures.dat"
        private const val MAX_SIGNATURES = 2000
    }

    init {
        runCatching { load() }
    }

    /** Colour-code signature of [bitmap]: 8x8 grid of average colours. */
    fun computeSignature(bitmap: Bitmap): IntArray = ImageSignature.compute(bitmap)

    /** Remember a false-positive pattern (from a bitmap). */
    fun add(bitmap: Bitmap) {
        val sig = computeSignature(bitmap)
        addSignature(sig)
    }

    /** Remember a false-positive pattern from an already-computed signature. */
    fun addSignature(sig: IntArray) {
        if (sig.size != ImageSignature.CELLS) return
        synchronized(lock) {
            if (signatures.any { ImageSignature.matches(it, sig) }) {
                Timber.d("False-positive pattern already known")
                return
            }
            signatures.add(sig)
            if (signatures.size > MAX_SIGNATURES) signatures.removeAt(0)
            Timber.i("Learned new false-positive pattern (total=${signatures.size})")
            save()
        }
    }

    /** Is [bitmap] a known false-positive pattern that should NOT be blocked? */
    fun isKnown(bitmap: Bitmap): Boolean {
        val sig = computeSignature(bitmap)
        synchronized(lock) {
            return signatures.any { ImageSignature.matches(it, sig) }
        }
    }

    /** Remember the bitmap/signature that caused the most recent AI block. */
    fun rememberCandidate(sig: IntArray?) {
        pendingCandidate = sig
    }

    /** The block overlay calls this when the user marks the block as wrong. */
    fun takePendingCandidate(): IntArray? {
        val s = pendingCandidate
        pendingCandidate = null
        return s
    }

    /**
     * v3.6.0 — read the pending candidate WITHOUT clearing it. The
     * "Not sensitive" / "Mark False" report handlers peek here first so the
     * ConfirmedSensitiveMemory refusal check can run without destroying the
     * candidate the user may still want to act on (e.g. tap "Protect" next).
     */
    fun peekPendingCandidate(): IntArray? = pendingCandidate

    /**
     * v3.6.0 — remove every whitelist entry matching [sig] (near-duplicates
     * included). Used only by the "Protect" flow: a pattern that is now
     * confirmed-sensitive must not stay whitelisted as a false positive at
     * the same time. Returns the number of entries removed (0 = was not here).
     */
    fun removeSignature(sig: IntArray): Int {
        if (sig.size != ImageSignature.CELLS) return 0
        synchronized(lock) {
            val before = signatures.size
            signatures.removeAll { ImageSignature.matches(it, sig) }
            val removed = before - signatures.size
            if (removed > 0) {
                Timber.i("Removed $removed false-positive pattern(s) overridden by confirmed-sensitive protect")
                save()
            }
            return removed
        }
    }

    fun size(): Int = synchronized(lock) { signatures.size }

    private fun save() {
        // Snapshot under the lock, then write off the caller thread —
        // addSignature() is invoked from the block overlay's main thread and a
        // synchronous ~512 KB write there causes jank.
        val snapshot = synchronized(lock) { ArrayList(signatures) }
        ioScope.launch {
            runCatching {
                val f = File(context.filesDir, FILE_NAME)
                DataOutputStream(FileOutputStream(f)).use { out ->
                    out.writeInt(snapshot.size)
                    for (sig in snapshot) for (v in sig) out.writeInt(v)
                }
            }.onFailure { Timber.e(it, "Failed to save false-positive memory") }
        }
    }

    private fun load() {
        runCatching {
            val f = File(context.filesDir, FILE_NAME)
            if (!f.exists()) return
            DataInputStream(FileInputStream(f)).use { input ->
                val n = input.readInt().coerceIn(0, MAX_SIGNATURES)
                signatures.clear()
                repeat(n) {
                    val sig = IntArray(ImageSignature.CELLS)
                    for (i in 0 until ImageSignature.CELLS) sig[i] = input.readInt()
                    signatures.add(sig)
                }
            }
            Timber.i("Loaded ${signatures.size} false-positive patterns")
        }.onFailure { Timber.e(it, "Failed to load false-positive memory") }
    }
}
