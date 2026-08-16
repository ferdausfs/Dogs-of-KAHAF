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
 * The signature is a colour code: the image is downscaled to an 8x8 grid and for
 * each cell we keep the average colour. Two images are considered "the same
 * pattern" when most cells agree within a small per-channel tolerance, so it is
 * robust to minor rendering / compression differences but still strict enough
 * that a genuinely different image is not skipped.
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
        private const val GRID = 8
        private const val CELLS = GRID * GRID

        // A cell matches when every channel differs by at most this (0..255).
        private const val CHANNEL_TOLERANCE = 48
        // How many cells must agree for two images to count as the same pattern.
        private const val MATCH_RATIO = 0.62f
        private const val MAX_SIGNATURES = 2000
    }

    init {
        runCatching { load() }
    }

    /** Colour-code signature of [bitmap]: 8x8 grid of average colours. */
    fun computeSignature(bitmap: Bitmap): IntArray {
        val scaled = if (bitmap.width != GRID || bitmap.height != GRID) {
            Bitmap.createScaledBitmap(bitmap, GRID, GRID, true)
        } else bitmap
        val pixels = IntArray(CELLS)
        scaled.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
        if (scaled !== bitmap) scaled.recycle()

        val sig = IntArray(CELLS)
        for (i in 0 until CELLS) {
            val c = pixels[i]
            sig[i] = ((c shr 16) and 0xFF shl 16) or ((c shr 8) and 0xFF shl 8) or (c and 0xFF)
        }
        return sig
    }

    /** Remember a false-positive pattern (from a bitmap). */
    fun add(bitmap: Bitmap) {
        val sig = computeSignature(bitmap)
        addSignature(sig)
    }

    /** Remember a false-positive pattern from an already-computed signature. */
    fun addSignature(sig: IntArray) {
        if (sig.size != CELLS) return
        synchronized(lock) {
            if (signatures.any { isMatch(it, sig) }) {
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
            return signatures.any { isMatch(it, sig) }
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

    fun size(): Int = synchronized(lock) { signatures.size }

    private fun isMatch(a: IntArray, b: IntArray): Boolean {
        if (a.size != CELLS || b.size != CELLS) return false
        var match = 0
        for (i in 0 until CELLS) {
            val ar = (a[i] shr 16) and 0xFF
            val ag = (a[i] shr 8) and 0xFF
            val ab = a[i] and 0xFF
            val br = (b[i] shr 16) and 0xFF
            val bg = (b[i] shr 8) and 0xFF
            val bb = b[i] and 0xFF
            if (Math.abs(ar - br) <= CHANNEL_TOLERANCE &&
                Math.abs(ag - bg) <= CHANNEL_TOLERANCE &&
                Math.abs(ab - bb) <= CHANNEL_TOLERANCE
            ) match++
        }
        return (match.toFloat() / CELLS) >= MATCH_RATIO
    }

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
                    val sig = IntArray(CELLS)
                    for (i in 0 until CELLS) sig[i] = input.readInt()
                    signatures.add(sig)
                }
            }
            Timber.i("Loaded ${signatures.size} false-positive patterns")
        }.onFailure { Timber.e(it, "Failed to load false-positive memory") }
    }
}
