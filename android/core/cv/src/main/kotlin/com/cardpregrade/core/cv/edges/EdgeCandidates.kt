package com.cardpregrade.core.cv.edges

import kotlin.math.abs

/**
 * A significant local extremum of a 1-D derivative profile.
 *
 * @property position continuous image coordinate along the scan line (the
 *   [com.cardpregrade.core.cv.geometry.PixelPoint] convention: pixel p has centre p + 0.5).
 * @property response the signed profile value at the extremum: the peak sample, or the shared
 *   value of a plateau. Its sign is the edge polarity (positive = intensity rising along the line).
 */
data class EdgeCandidate(val position: Double, val response: Int)

/**
 * Finds edge candidates in a derivative profile such as [com.cardpregrade.core.cv.image.Sobel.rowDx]
 * or [com.cardpregrade.core.cv.image.Sobel.columnDy]. A generic signal primitive: it applies no
 * selection policy beyond the threshold and knows nothing about what produced the profile.
 *
 * **Coordinates.** `profile[i]` is the response centred on source pixel `firstPixel + i`, i.e. at
 * the continuous coordinate `firstPixel + i + 0.5`. Positions are reported in that system, never
 * as array indices.
 *
 * **Runs and polarity.** The profile is split into maximal runs of *identical signed* values.
 * A run of value v ≠ 0 has polarity s = sign(v); a neighbour n counts with strength s·n, so a
 * neighbour of the opposite sign is weaker than any same-polarity value. Runs of opposite sign
 * are never merged, and zero is never a candidate.
 *
 * **Local extremum.** A run [lo, hi] is a candidate when
 * - it has a neighbour on both sides inside the profile (lo ≥ 1 and hi ≤ size − 2), so runs
 *   touching either endpoint are never candidates and profiles shorter than 3 yield none;
 * - both neighbours are strictly weaker: s·profile[lo − 1] < |v| and s·profile[hi + 1] < |v|;
 * - |v| ≥ [find]'s `minAbsResponse` (inclusive), computed in Long so Int.MIN_VALUE is safe.
 *
 * **Position.**
 * - Plateau (hi > lo): the run's centre, `firstPixel + (lo + hi) / 2 + 0.5`, with no
 *   interpolation. An ideal intensity step between source pixels k − 1 and k gives a 3 × 3 Sobel
 *   plateau on pixels k − 1 and k, so it is reported at exactly k.
 * - Single sample (hi == lo == i): a parabola through the strengths a = s·profile[i − 1],
 *   b = |v|, c = s·profile[i + 1] at offsets −1, 0, +1 peaks at
 *   `δ = (a − c) / (2 · (a − 2b + c))`, giving `firstPixel + i + 0.5 + δ`. Because a < b and c < b
 *   the denominator is strictly negative and |δ| < 0.5, so the position stays strictly between
 *   the neighbouring sample centres.
 *
 * Candidates are returned in increasing position order. Allocation is per candidate, not per
 * sample.
 */
object EdgeCandidates {

    fun find(profile: IntArray, firstPixel: Int, minAbsResponse: Int): List<EdgeCandidate> {
        require(minAbsResponse >= 0) { "minAbsResponse must be ≥ 0: $minAbsResponse" }
        val n = profile.size
        val out = ArrayList<EdgeCandidate>()
        var lo = 0
        while (lo < n) {
            val v = profile[lo]
            var hi = lo
            while (hi + 1 < n && profile[hi + 1] == v) hi++
            if (lo > 0 && hi < n - 1 && v != 0) {
                val b = abs(v.toLong())
                val s = if (v > 0) 1L else -1L
                val a = s * profile[lo - 1]
                val c = s * profile[hi + 1]
                if (b >= minAbsResponse && a < b && c < b) {
                    val centre = if (lo == hi) lo + parabolicOffset(a, b, c) else (lo + hi) / 2.0
                    out += EdgeCandidate(firstPixel + centre + 0.5, v)
                }
            }
            lo = hi + 1
        }
        return out
    }

    /** Vertex of the parabola through (−1, a), (0, b), (+1, c); requires a < b and c < b. */
    private fun parabolicOffset(a: Long, b: Long, c: Long): Double =
        (a - c).toDouble() / (2.0 * (a - 2 * b + c).toDouble())
}
