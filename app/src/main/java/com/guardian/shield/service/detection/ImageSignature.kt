package com.guardian.shield.service.detection

import android.graphics.Bitmap

/**
 * Shared 8x8 average-colour image-signature logic used by BOTH on-device
 * learning memories:
 *
 *  - [FalsePositiveMemory] — whitelist of patterns the user marked as
 *    harmless ("Not sensitive" / "Mark False"). Matches are skipped.
 *  - [ConfirmedSensitiveMemory] — permanent blacklist of patterns the user
 *    protected ("সংরক্ষণ করো"). Matches are forced-sensitive.
 *
 * Extracted verbatim from [FalsePositiveMemory] (same GRID, same tolerance,
 * same ratio — behaviour is byte-equivalent to the pre-extraction code) so
 * the two stores can never drift apart in their notion of "the same pattern".
 *
 * The signature is a colour code: the image is downscaled to an 8x8 grid and
 * for each cell we keep the average colour. Two images are considered "the
 * same pattern" when most cells agree within a small per-channel tolerance,
 * so it is robust to minor rendering / compression differences but still
 * strict enough that a genuinely different image is not matched.
 */
object ImageSignature {

    const val GRID = 8
    const val CELLS = GRID * GRID

    // A cell matches when every channel differs by at most this (0..255).
    const val CHANNEL_TOLERANCE = 48

    // How many cells must agree for two images to count as the same pattern.
    const val MATCH_RATIO = 0.62f

    /** Colour-code signature of [bitmap]: 8x8 grid of average colours. */
    fun compute(bitmap: Bitmap): IntArray {
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

    /** True when [a] and [b] describe the same colour pattern. */
    fun matches(a: IntArray, b: IntArray): Boolean {
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
}
