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
 * The base signature + [matches] are the original v2.4.2 tolerance, extracted
 * verbatim from [FalsePositiveMemory] (same GRID, same tolerance, same ratio
 * — behaviour is byte-equivalent to the pre-extraction code) so the whitelist
 * store can never drift from its historical notion of "the same pattern".
 *
 * The signature is a colour code: the image is downscaled to an 8x8 grid and
 * for each cell we keep the average colour. Two images are considered "the
 * same pattern" when most cells agree within a small per-channel tolerance,
 * so it is robust to minor rendering / compression differences but still
 * strict enough that a genuinely different image is not matched.
 *
 * v3.6.2 — [matchesStrict] is a DELIBERATELY STRICTER match used ONLY by
 * [ConfirmedSensitiveMemory]. The cost of a false match is asymmetric:
 *  - a false match in the WHITELIST merely skips re-flagging something the
 *    user already called harmless (low cost),
 *  - a false match in the confirmed-sensitive BLACKLIST force-blocks
 *    completely innocent content at confidence 1.00 AND permanently refuses
 *    every future "Not sensitive" / "Mark False" report on it (very high
 *    cost). So the blacklist must require far more evidence before declaring
 *    two images "the same pattern".
 *
 * Chosen values (measured on the real compiled code — see
 * COMPILE_REVIEW_REPORT.md v3.6.2 session):
 *  - CHANNEL_TOLERANCE_STRICT = 24 (was 48): a cell only agrees when all
 *    three channels are within ~9.4% of the 0..255 range.
 *  - MATCH_RATIO_STRICT = 0.85 (was 0.62): at least 55 of the 64 cells must
 *    agree — a genuine duplicate rendered with realistic ≤8/255 per-channel
 *    noise scores ratio ≈ 1.000, while pairs of *different* images that only
 *    share a colour/brightness distribution (warm skin tones, dark UI chrome)
 *    peak at ≈ 0.594 under the old tolerance and ≈ 0.56-0.60 at tolerance 24.
 *    The 0.85 bar therefore sits ~0.25 above the worst false pair while
 *    staying ~0.15 below the easiest true duplicate — a deliberate bias
 *    toward "no match unless confident" for a permanent blacklist (a missed
 *    re-rendered variant can simply be Protected again; a false block cannot
 *    be undone).
 */
object ImageSignature {

    const val GRID = 8
    const val CELLS = GRID * GRID

    // A cell matches when every channel differs by at most this (0..255).
    const val CHANNEL_TOLERANCE = 48

    // How many cells must agree for two images to count as the same pattern.
    const val MATCH_RATIO = 0.62f

    // v3.6.2 — STRICT thresholds for the permanent blacklist only. See the
    // class KDoc for the measured separation behind these numbers.
    const val CHANNEL_TOLERANCE_STRICT = 24
    const val MATCH_RATIO_STRICT = 0.85f

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

    /** Persist a signature as comma-separated ints (Room TEXT column). */
    fun toCsv(sig: IntArray): String =
        if (sig.size != CELLS) "" else sig.joinToString(",")

    /** Inverse of [toCsv]; returns null when the payload is empty or malformed. */
    fun fromCsv(csv: String): IntArray? {
        if (csv.isBlank()) return null
        val parts = csv.split(',')
        if (parts.size != CELLS) return null
        val out = IntArray(CELLS)
        for (i in 0 until CELLS) {
            out[i] = parts[i].toIntOrNull() ?: return null
        }
        return out
    }

    /**
     * Fraction of the 64 cells that agree within [tolerance] per channel.
     * 0f for mismatched sizes. Exposed so callers can log *how close* a match
     * was (used by the confirmed-sensitive memory's Timber.w diagnostics).
     */
    fun matchRatio(a: IntArray, b: IntArray, tolerance: Int = CHANNEL_TOLERANCE): Float {
        if (a.size != CELLS || b.size != CELLS) return 0f
        var match = 0
        for (i in 0 until CELLS) {
            val ar = (a[i] shr 16) and 0xFF
            val ag = (a[i] shr 8) and 0xFF
            val ab = a[i] and 0xFF
            val br = (b[i] shr 16) and 0xFF
            val bg = (b[i] shr 8) and 0xFF
            val bb = b[i] and 0xFF
            if (Math.abs(ar - br) <= tolerance &&
                Math.abs(ag - bg) <= tolerance &&
                Math.abs(ab - bb) <= tolerance
            ) match++
        }
        return match.toFloat() / CELLS
    }

    /** True when [a] and [b] describe the same colour pattern (WHITELIST tolerance). */
    fun matches(a: IntArray, b: IntArray): Boolean =
        matchRatio(a, b, CHANNEL_TOLERANCE) >= MATCH_RATIO

    /**
     * True when [a] and [b] describe the same colour pattern under the STRICT
     * tolerance. Used ONLY by [ConfirmedSensitiveMemory] — see the class KDoc
     * for why the permanent blacklist demands a higher bar than the whitelist.
     */
    fun matchesStrict(a: IntArray, b: IntArray): Boolean =
        matchRatio(a, b, CHANNEL_TOLERANCE_STRICT) >= MATCH_RATIO_STRICT
}
