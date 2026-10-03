package com.cardpregrade.core.cv.detection

import com.cardpregrade.core.cv.edges.EdgeCandidates
import com.cardpregrade.core.cv.geometry.Line
import com.cardpregrade.core.cv.geometry.LineFitResult
import com.cardpregrade.core.cv.geometry.LineFitter
import com.cardpregrade.core.cv.geometry.LineIntersection
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.cv.geometry.QuadValidation
import com.cardpregrade.core.cv.geometry.Quadrilateral
import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.cv.image.Sobel
import com.cardpregrade.core.model.CardCorner
import kotlin.math.abs
import kotlin.math.ceil

/** Evenly spaced scan lines over the Sobel-valid interior of one image axis. */
internal object ScanPlan {
    /**
     * Up to [count] distinct pixel indices in 1..extent − 2 (the rows or columns where a 3 × 3
     * Sobel is defined): index i is `1 + ⌊(2i + 1)·n / 2k⌋` with n = extent − 2 and
     * k = min(count, n), i.e. the centres of k equal bands. Empty when extent < 3.
     */
    fun lines(extent: Int, count: Int): List<Int> {
        val n = extent - 2
        if (n < 1 || count < 1) return emptyList()
        val k = minOf(count, n)
        return List(k) { i -> 1 + ((2L * i + 1) * n / (2L * k)).toInt() }
    }
}

/**
 * Finds the physical outer boundary of a card in a grey image with scan lines, Sobel profiles and
 * line fitting. Pure Kotlin; knows nothing about where the image came from.
 *
 * 1. **Scans.** [CardDetectionPolicy.scanLinesPerAxis] rows and columns ([ScanPlan]). Each row
 *    gives a [Sobel.rowDx] profile over x ∈ [1, width − 1), each column a [Sobel.columnDy]
 *    profile, and [EdgeCandidates.find] turns each profile into signed sub-pixel candidates.
 * 2. **Pools.** The [CardDetectionPolicy.candidatesPerScanEnd] candidates nearest each end of a
 *    scan become observations for the side at that end (rows → LEFT/RIGHT, columns →
 *    TOP/BOTTOM), ranked from the image border inwards. Several candidates per scan are kept so
 *    a spurious outer response does not hide the card edge. A scan with fewer than twice that
 *    many candidates offers some of them to both ends, so the opposite edge also competes for a
 *    side (it loses on position when both edges are visible; see the limitation below).
 * 3. **Seeds.** Every pair of observations from different scans with the same [EdgePolarity]
 *    defines a seed line, kept only if the side's scans can observe it (strictly less than 45°
 *    from perpendicular to them). Its support is the number of distinct scans with a same-polarity
 *    observation within [CardDetectionPolicy.inlierTolerancePx]. Polarity is only a
 *    consistency constraint inside a hypothesis; both polarities compete.
 * 4. **Selection.** Among seeds with support ≥ max(minInliers, ⌈supportFraction · best⌉), the
 *    outermost wins: its position across the scans, evaluated at the mean scan coordinate of the
 *    best seed's inliers, is nearest the image border on that side. This is what separates the
 *    physical edge from printed frames and art, which are straight and well supported too but
 *    lie inside it. Seeds whose positions differ by at most [CardDetectionPolicy.inlierTolerancePx]
 *    are the same edge to the detector, so among them higher support wins; remaining ties go to
 *    enumeration order, so the result is deterministic.
 * 5. **Refit.** The chosen seed's inliers (at most one per scan, the closest) are fitted with
 *    [LineFitter]; inliers are re-classified against the fitted line and refitted until the set
 *    is stable. A set still changing after [CardDetectionPolicy.maxRefitIterations] refits is
 *    reported as [DetectionFailure.RefitDidNotConverge]. Outlier handling lives here, not in the
 *    generic fitter.
 * 6. **Corners.** TL = top ∩ left, TR = top ∩ right, BR = bottom ∩ right, BL = bottom ∩ left,
 *    validated with [CardDetectionPolicy.validation].
 *
 * Sides are processed in [Side] order and the first failure is returned. Corners extrapolated
 * from visible sides may lie outside the image; rejecting that is the caller's policy.
 *
 * Known limitation: the detector has no notion of a side being absent. When a side's true edge
 * is not observed (out of frame, below [CardDetectionPolicy.minAbsResponse], on the outermost
 * Sobel sample, or occluded on too many scans), the outermost *other* well-supported line takes
 * its place, typically a printed frame or art line inside the card, and the result is still
 * [BoundaryDetection.Detected].
 */
object CardBoundaryDetector {

    fun detect(image: GrayImage, policy: CardDetectionPolicy = CardDetectionPolicy.DEFAULT): BoundaryDetection {
        if (image.width < 3 || image.height < 3) {
            return BoundaryDetection.NotDetected(
                DetectionFailure.ImageTooSmall(image.width, image.height),
                DetectionDiagnostics(emptyList(), emptyList(), emptyMap()),
            )
        }
        val rows = ScanPlan.lines(image.height, policy.scanLinesPerAxis)
        val columns = ScanPlan.lines(image.width, policy.scanLinesPerAxis)
        val pools = collectObservations(image, rows, columns, policy)

        val sideDiagnostics = LinkedHashMap<Side, SideDiagnostics>()
        val fits = LinkedHashMap<Side, SideFit>()
        fun diagnostics() = DetectionDiagnostics(rows, columns, LinkedHashMap(sideDiagnostics))

        for (side in Side.entries) {
            val outcome = fitSide(side, pools.getValue(side), policy)
            sideDiagnostics[side] = outcome.diagnostics
            val fit = outcome.fit ?: return BoundaryDetection.NotDetected(outcome.failure!!, diagnostics())
            fits[side] = fit
        }

        val corners = ArrayList<PixelPoint>(4)
        for ((corner, a, b) in CORNERS) {
            when (val hit = fits.getValue(a).line.intersection(fits.getValue(b).line)) {
                is LineIntersection.Point -> corners += hit.point
                else -> return BoundaryDetection.NotDetected(DetectionFailure.CornerNotFound(corner, hit), diagnostics())
            }
        }
        val quad = Quadrilateral(corners[0], corners[1], corners[2], corners[3])
        val validation = QuadValidation.validate(quad, PixelSize(image.width, image.height), policy.validation)
        if (!validation.isValid) {
            return BoundaryDetection.NotDetected(DetectionFailure.InvalidQuadrilateral(quad, validation.rejections), diagnostics())
        }
        return BoundaryDetection.Detected(quad, LinkedHashMap(fits), diagnostics())
    }

    /** Corner order matches [Quadrilateral]: TL, TR, BR, BL. */
    private val CORNERS = listOf(
        Triple(CardCorner.TOP_LEFT, Side.TOP, Side.LEFT),
        Triple(CardCorner.TOP_RIGHT, Side.TOP, Side.RIGHT),
        Triple(CardCorner.BOTTOM_RIGHT, Side.BOTTOM, Side.RIGHT),
        Triple(CardCorner.BOTTOM_LEFT, Side.BOTTOM, Side.LEFT),
    )

    private fun collectObservations(
        image: GrayImage,
        rows: List<Int>,
        columns: List<Int>,
        policy: CardDetectionPolicy,
    ): Map<Side, List<SideObservation>> {
        val pools = Side.entries.associateWith { ArrayList<SideObservation>() }
        val k = policy.candidatesPerScanEnd
        rows.forEachIndexed { index, y ->
            val found = EdgeCandidates.find(Sobel.rowDx(image, y, 1, image.width - 1), 1, policy.minAbsResponse)
            for (r in 0 until minOf(k, found.size)) {
                val near = found[r]
                pools.getValue(Side.LEFT) += SideObservation(Side.LEFT, index, y, r, PixelPoint(near.position, y + 0.5), near.response)
                val far = found[found.size - 1 - r]
                pools.getValue(Side.RIGHT) += SideObservation(Side.RIGHT, index, y, r, PixelPoint(far.position, y + 0.5), far.response)
            }
        }
        columns.forEachIndexed { index, x ->
            val found = EdgeCandidates.find(Sobel.columnDy(image, x, 1, image.height - 1), 1, policy.minAbsResponse)
            for (r in 0 until minOf(k, found.size)) {
                val near = found[r]
                pools.getValue(Side.TOP) += SideObservation(Side.TOP, index, x, r, PixelPoint(x + 0.5, near.position), near.response)
                val far = found[found.size - 1 - r]
                pools.getValue(Side.BOTTOM) += SideObservation(Side.BOTTOM, index, x, r, PixelPoint(x + 0.5, far.position), far.response)
            }
        }
        return pools
    }

    private class SideOutcome(val fit: SideFit?, val failure: DetectionFailure?, val diagnostics: SideDiagnostics)

    /** A row-scanned side must be steeper than 45° (|a| > |b|), a column-scanned side shallower. */
    private fun observable(side: Side, line: Line): Boolean =
        if (side.scannedByRows) abs(line.a) > abs(line.b) else abs(line.b) > abs(line.a)

    /**
     * Position of [line] across the scans at scan-axis coordinate [at], signed so that smaller
     * means nearer the image border on [side]'s end (more outward).
     */
    private fun depth(side: Side, line: Line, at: Double): Double {
        val across = if (side.scannedByRows) -(line.b * at + line.c) / line.a else -(line.a * at + line.c) / line.b
        return side.inwardSign * across
    }

    /**
     * Observations supporting [line]: per scan, the same-polarity observation nearest the line if
     * it is within tolerance (ties to the lower rank). [pool] is ordered by scan then rank.
     */
    private fun support(line: Line, polarity: EdgePolarity, pool: List<SideObservation>, tolerance: Double): List<SideObservation> {
        val out = ArrayList<SideObservation>()
        var i = 0
        while (i < pool.size) {
            val scan = pool[i].scanIndex
            var best: SideObservation? = null
            var bestDistance = Double.POSITIVE_INFINITY
            while (i < pool.size && pool[i].scanIndex == scan) {
                val o = pool[i]
                if (o.polarity == polarity) {
                    val d = line.distance(o.point)
                    if (d <= tolerance && d < bestDistance) {
                        best = o
                        bestDistance = d
                    }
                }
                i++
            }
            if (best != null) out += best
        }
        return out
    }

    /** Number of distinct scans in [support] without building the list. */
    private fun supportCount(line: Line, polarity: EdgePolarity, pool: List<SideObservation>, tolerance: Double): Int {
        var count = 0
        var lastCounted = -1
        for (o in pool) {
            if (o.scanIndex == lastCounted || o.polarity != polarity) continue
            if (line.distance(o.point) <= tolerance) {
                count++
                lastCounted = o.scanIndex
            }
        }
        return count
    }

    private fun fitSide(side: Side, pool: List<SideObservation>, policy: CardDetectionPolicy): SideOutcome {
        val tol = policy.inlierTolerancePx
        val seedI = IntArrayList()
        val seedJ = IntArrayList()
        val seedSupport = IntArrayList()
        var best = -1
        for (i in pool.indices) {
            val a = pool[i]
            for (j in i + 1 until pool.size) {
                val b = pool[j]
                if (a.scanIndex == b.scanIndex || a.polarity != b.polarity) continue
                val line = Line.through(a.point, b.point) ?: continue
                if (!observable(side, line)) continue
                val s = supportCount(line, a.polarity, pool, tol)
                seedI.add(i)
                seedJ.add(j)
                seedSupport.add(s)
                if (best < 0 || s > seedSupport[best]) best = seedSupport.size - 1
            }
        }
        val bestSupport = if (best < 0) 0 else seedSupport[best]
        fun diag(chosen: Int?, iterations: Int?) =
            SideDiagnostics(side, pool.size, seedSupport.size, bestSupport, chosen, iterations)

        if (bestSupport < policy.minInliers) {
            return SideOutcome(null, DetectionFailure.InsufficientEvidence(side, bestSupport, policy.minInliers), diag(null, null))
        }

        fun seedLine(s: Int) = Line.through(pool[seedI[s]].point, pool[seedJ[s]].point)!!
        val bestLine = seedLine(best)
        val polarityOfBest = pool[seedI[best]].polarity
        val bestInliers = support(bestLine, polarityOfBest, pool, tol)
        val reference = bestInliers.sumOf { it.scanCoordinate + 0.5 } / bestInliers.size

        // The 1e-9 keeps a decimal fraction's representation error (0.55 · 100 = 55.000000000000007)
        // from raising the threshold by one.
        val threshold = maxOf(policy.minInliers, ceil(policy.supportFraction * bestSupport - 1e-9).toInt())
        var chosen = -1
        var chosenDepth = Double.POSITIVE_INFINITY
        for (s in 0 until seedSupport.size) {
            if (seedSupport[s] < threshold) continue
            val d = depth(side, seedLine(s), reference)
            val better = when {
                chosen < 0 -> true
                d < chosenDepth - tol -> true
                d <= chosenDepth + tol -> seedSupport[s] > seedSupport[chosen]
                else -> false
            }
            if (better) {
                chosen = s
                chosenDepth = d
            }
        }
        val polarity = pool[seedI[chosen]].polarity
        var inliers = support(seedLine(chosen), polarity, pool, tol)

        var iterations = 0
        while (true) {
            iterations++
            val fit = when (val r = LineFitter.fit(inliers.map { it.point })) {
                is LineFitResult.Fitted -> r
                is LineFitResult.Rejected -> return SideOutcome(
                    null,
                    DetectionFailure.SideFitRejected(side, r.reason),
                    diag(seedSupport[chosen], iterations),
                )
            }
            val next = support(fit.line, polarity, pool, tol)
            if (next != inliers && iterations >= policy.maxRefitIterations) {
                return SideOutcome(null, DetectionFailure.RefitDidNotConverge(side, iterations), diag(seedSupport[chosen], iterations))
            }
            if (next == inliers) {
                if (!observable(side, fit.line)) {
                    return SideOutcome(null, DetectionFailure.SideOrientationUnobservable(side, fit.line), diag(seedSupport[chosen], iterations))
                }
                val sideFit = SideFit(side, fit.line, polarity, inliers, fit.rmsResidualPx, fit.maxAbsResidualPx)
                return SideOutcome(sideFit, null, diag(seedSupport[chosen], iterations))
            }
            if (next.size < policy.minInliers) {
                return SideOutcome(
                    null,
                    DetectionFailure.InsufficientEvidence(side, next.size, policy.minInliers),
                    diag(seedSupport[chosen], iterations),
                )
            }
            inliers = next
        }
    }

    /** Minimal growable IntArray, so seeds cost no boxing. */
    private class IntArrayList {
        private var data = IntArray(64)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        operator fun get(i: Int): Int = data[i]
    }
}
