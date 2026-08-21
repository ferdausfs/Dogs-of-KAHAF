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
 * v3.6.0 — PERMANENT confirmed-sensitive memory (the opposite of
 * [FalsePositiveMemory]).
 *
 * When the user taps "সংরক্ষণ করো" (Protect) on a strike warning, they are
 * saying: "the AI under-scored this — this specific content is genuinely bad,
 * remember that forever". We store the same compact 8x8 colour signature
 * [ImageSignature] computes (shared with [FalsePositiveMemory], so the two
 * stores agree on what "the same pattern" means) as a PERMANENT blacklist:
 *
 *  - [AiDetector.isUnsafe] checks this store FIRST — a match is treated as
 *    guaranteed-sensitive and short-circuits to `true`, before the
 *    false-positive whitelist check and before any model inference, so no
 *    live confidence score can ever under-score it again.
 *  - The "Not sensitive" (strike 1/2) and "Mark False" (strike 3) report
 *    handlers REFUSE to run when the current candidate matches this store,
 *    before the confidence-based cooling-off logic — a confirmed-sensitive
 *    pattern can never be whitelisted by a later report.
 *
 * PERMANENCE CONTRACT (mirrors the user's stated intent that this is hard to
 * undo):
 *  - No timestamps are stored and no time-based expiry or reset exists — the
 *    store survives app restarts, STRIKE_RESET_MS, the cooling-off window and
 *    every other time-based system. Nothing else in the codebase writes or
 *    clears this file (the only writes are [addConfirmedSignature] here).
 *  - Capacity trimming is the ONLY removal path: when the store exceeds
 *    [MAX_SIGNATURES] the OLDEST entry is dropped (same file-size discipline
 *    as [FalsePositiveMemory]) — it is capacity-based, never time-based.
 *  - Deliberately one-way: no UI to browse or remove individual entries
 *    (Settings shows only the count). This matches the commitment-device
 *    design of the cooling-off system.
 *
 * Storage mirrors [FalsePositiveMemory] exactly: a binary file
 * (`confirmed_sensitive_signatures.dat`) in filesDir — count (Int) followed by
 * 64 Ints per signature — saved asynchronously off the caller thread and
 * loaded in `init` with `runCatching { load() }`.
 */
@Singleton
class ConfirmedSensitiveMemory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val lock = Any()
    private val signatures = ArrayList<IntArray>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var persistEpoch = 0

    companion object {
        private const val FILE_NAME = "confirmed_sensitive_signatures.dat"
        private const val MAX_SIGNATURES = 2000
    }

    init {
        runCatching { load() }
    }

    /**
     * Persist a confirmed-sensitive pattern permanently. Near-duplicates
     * (same [ImageSignature] STRICT match) are ignored, so repeated protects
     * of the same content do not grow the store — while two visually distinct
     * patterns that merely share a colour palette are stored separately
     * (strict matching, v3.6.2).
     */
    fun addConfirmedSignature(sig: IntArray) {
        if (sig.size != ImageSignature.CELLS) return
        synchronized(lock) {
            if (signatures.any { ImageSignature.matchesStrict(it, sig) }) {
                Timber.d("Confirmed-sensitive pattern already stored (strict match)")
                return
            }
            signatures.add(sig)
            if (signatures.size > MAX_SIGNATURES) signatures.removeAt(0)
            Timber.i("Stored confirmed-sensitive pattern (total=${signatures.size})")
            save()
        }
    }

    /**
     * Is [bitmap] a stored confirmed-bad pattern? Checked FIRST by
     * [AiDetector.classify] — a match forces `unsafe = true` regardless of the
     * live model score, and is evaluated BEFORE the [FalsePositiveMemory]
     * whitelist so a false-positive entry can never suppress it.
     *
     * v3.6.2 — matching uses the STRICT [ImageSignature.matchesStrict]
     * threshold so innocent content that merely shares a colour distribution
     * with a protected pattern is not force-blocked at 1.00. On a match, a
     * Timber.w is emitted with the actual agreeing-cell ratio so future
     * false-match reports can be diagnosed from the log.
     */
    fun isConfirmedSensitive(bitmap: Bitmap): Boolean {
        val sig = ImageSignature.compute(bitmap)
        return isConfirmedSignature(sig)
    }

    /**
     * Is an already-computed signature a stored confirmed-bad pattern? Used by
     * the report handlers, which receive the candidate signature directly
     * (via [FalsePositiveMemory.peekPendingCandidate]) without a bitmap.
     */
    fun isConfirmedSignature(sig: IntArray): Boolean {
        if (sig.size != ImageSignature.CELLS) return false
        synchronized(lock) {
            for (stored in signatures) {
                if (ImageSignature.matchesStrict(stored, sig)) {
                    val ratio = ImageSignature.matchRatio(
                        stored, sig, ImageSignature.CHANNEL_TOLERANCE_STRICT
                    )
                    Timber.w(
                        "Confirmed-sensitive STRICT match — ratio=%.2f (need %.2f, tolerance=%d) — " +
                            "forced sensitive / report refused",
                        ratio,
                        ImageSignature.MATCH_RATIO_STRICT,
                        ImageSignature.CHANNEL_TOLERANCE_STRICT
                    )
                    return true
                }
            }
            return false
        }
    }

    /** Number of stored confirmed-sensitive patterns (Settings visibility). */
    fun size(): Int = synchronized(lock) { signatures.size }

    private fun save() {
        // Same discipline as FalsePositiveMemory: snapshot at write time
        // under an epoch so a later add cannot be overwritten by an earlier
        // in-flight write.
        val epoch = synchronized(lock) { ++persistEpoch; persistEpoch }
        ioScope.launch {
            val snapshot = synchronized(lock) {
                if (epoch != persistEpoch) return@launch
                ArrayList(signatures)
            }
            runCatching {
                val f = File(context.filesDir, FILE_NAME)
                DataOutputStream(FileOutputStream(f)).use { out ->
                    out.writeInt(snapshot.size)
                    for (sig in snapshot) for (v in sig) out.writeInt(v)
                }
            }.onFailure { Timber.e(it, "Failed to save confirmed-sensitive memory") }
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
            Timber.i("Loaded ${signatures.size} confirmed-sensitive patterns")
        }.onFailure { Timber.e(it, "Failed to load confirmed-sensitive memory") }
    }
}
