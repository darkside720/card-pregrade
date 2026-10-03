package com.cardpregrade.core.cv.detection

import com.cardpregrade.core.cv.geometry.Line
import com.cardpregrade.core.cv.geometry.LineFitRejection
import com.cardpregrade.core.cv.geometry.LineIntersection
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.QuadRejection
import com.cardpregrade.core.cv.geometry.QuadValidationPolicy
import com.cardpregrade.core.cv.geometry.Quadrilateral
import com.cardpregrade.core.model.CardCorner

/**
 * One side of the card outline, together with how a scanline detector observes it.
 *
 * @property scannedByRows true if horizontal scan lines (Sobel rowDx) cross this side, false if
 *   vertical scan lines (Sobel columnDy) do. A side is observable this way only while it is
 *   strictly less than 45° from perpendicular to the scan lines; an edge at exactly 45° belongs
 *   to neither axis.
 * @property inwardSign +1 if moving towards the card along the scan increases the coordinate
 *   (LEFT, TOP: scanning from the image's left/top border), −1 otherwise (RIGHT, BOTTOM).
 */
enum class Side(val scannedByRows: Boolean, val inwardSign: Int) {
    LEFT(true, 1),
    TOP(false, 1),
    RIGHT(true, -1),
    BOTTOM(false, -1),
}

/**
 * Whether intensity increases or decreases when crossing an edge from outside the card to
 * inside it. A sign convention, not an assumption: real photos can have either.
 */
enum class EdgePolarity {
    INWARD_BRIGHTER,
    INWARD_DARKER,
    ;

    companion object {
        /** Polarity of a non-zero signed Sobel [response] measured along the scan that observes [side]. */
        fun of(side: Side, response: Int): EdgePolarity {
            require(response != 0) { "A zero response has no polarity" }
            return if ((response > 0) == (side.inwardSign > 0)) INWARD_BRIGHTER else INWARD_DARKER
        }
    }
}

/**
 * An edge candidate offered as evidence for [side].
 *
 * @property scanIndex index of the scan line within its axis (rows or columns) of the scan plan.
 * @property scanCoordinate the scanned pixel row (y) or column (x).
 * @property rank 0 for the candidate nearest the image border on the side's outer end of the
 *   scan, 1 for the next, and so on.
 * @property point continuous image position: (candidate, y + 0.5) for rows, (x + 0.5, candidate)
 *   for columns.
 * @property response signed Sobel response of the candidate; never 0.
 */
data class SideObservation(
    val side: Side,
    val scanIndex: Int,
    val scanCoordinate: Int,
    val rank: Int,
    val point: PixelPoint,
    val response: Int,
) {
    init {
        require(response != 0) { "An edge observation needs a non-zero response" }
        val along = if (side.scannedByRows) point.y else point.x
        require(along == scanCoordinate + 0.5) { "$side observation at $point is not on scan line $scanCoordinate" }
    }

    val polarity: EdgePolarity get() = EdgePolarity.of(side, response)
}

/**
 * The accepted line for one side and the evidence it was refitted from. Every inlier belongs to
 * [side] and has [polarity]; at most one comes from each scan.
 */
data class SideFit(
    val side: Side,
    val line: Line,
    val polarity: EdgePolarity,
    val inliers: List<SideObservation>,
    val rmsResidualPx: Double,
    val maxAbsResidualPx: Double,
) {
    init {
        require(inliers.all { it.side == side && it.polarity == polarity }) { "Inliers must match $side / $polarity" }
        require(inliers.map { it.scanIndex }.toSet().size == inliers.size) { "At most one inlier per scan" }
    }
}

/**
 * Tuning for the scanline boundary detector. Every value here is **provisional**, chosen
 * against the deterministic synthetic fixtures only; none is calibrated on real photographs.
 *
 * @property scanLinesPerAxis rows and columns sampled (evenly spaced over the image interior).
 * @property minAbsResponse EdgeCandidates threshold on |Sobel| (8-bit luma, 3 × 3 Sobel: a step of
 *   s grey levels gives up to 4·s).
 * @property candidatesPerScanEnd how many candidates, counted inwards from each end of a scan,
 *   are offered to the side at that end.
 * @property inlierTolerancePx maximum orthogonal distance from a side hypothesis for an
 *   observation to support it.
 * @property minInliers minimum supporting scans for a side to be accepted.
 * @property supportFraction hypotheses with at least this fraction of the best support for a
 *   side compete on outermost position (the physical card edge is the outermost straight edge).
 * @property maxRefitIterations cap on the TLS refit / re-classification loop.
 * @property validation checks applied to the assembled quadrilateral.
 */
data class CardDetectionPolicy(
    val scanLinesPerAxis: Int,
    val minAbsResponse: Int,
    val candidatesPerScanEnd: Int,
    val inlierTolerancePx: Double,
    val minInliers: Int,
    val supportFraction: Double,
    val maxRefitIterations: Int,
    val validation: QuadValidationPolicy,
) {
    init {
        require(scanLinesPerAxis >= 2) { "scanLinesPerAxis must be ≥ 2: $scanLinesPerAxis" }
        // |3 × 3 Sobel| on 8-bit luma is at most 4 · 255 = 1020; a higher threshold finds nothing.
        require(minAbsResponse in 0..1020) { "minAbsResponse must be in 0..1020: $minAbsResponse" }
        require(candidatesPerScanEnd >= 1) { "candidatesPerScanEnd must be ≥ 1: $candidatesPerScanEnd" }
        require(inlierTolerancePx > 0.0 && inlierTolerancePx.isFinite()) { "inlierTolerancePx must be positive: $inlierTolerancePx" }
        require(minInliers >= 2) { "minInliers must be ≥ 2: $minInliers" }
        // Each scan line supports a side at most once, so more required inliers than scans can never succeed.
        require(minInliers <= scanLinesPerAxis) { "minInliers ($minInliers) exceeds scanLinesPerAxis ($scanLinesPerAxis)" }
        require(supportFraction > 0.0 && supportFraction <= 1.0) { "supportFraction must be in (0, 1]: $supportFraction" }
        require(maxRefitIterations >= 1) { "maxRefitIterations must be ≥ 1: $maxRefitIterations" }
    }

    companion object {
        /** Provisional defaults; see the class documentation. Only structural quad checks. */
        val DEFAULT = CardDetectionPolicy(
            scanLinesPerAxis = 32,
            minAbsResponse = 64,
            candidatesPerScanEnd = 4,
            inlierTolerancePx = 2.0,
            minInliers = 6,
            supportFraction = 0.6,
            maxRefitIterations = 5,
            validation = QuadValidationPolicy.STRUCTURAL_ONLY,
        )
    }
}

/** Why no boundary was reported. Sides are evaluated in [Side] declaration order; the first failure is reported. */
sealed interface DetectionFailure {
    /** Fewer than 3 pixels in a dimension: no Sobel response exists. */
    data class ImageTooSmall(val width: Int, val height: Int) : DetectionFailure

    /**
     * [side] had fewer than [required] supporting scans: either no seed hypothesis reached it
     * (then [support] is the best seed's), or re-classification during the refit dropped the
     * chosen hypothesis below it (then [support] is the remaining inlier count).
     */
    data class InsufficientEvidence(val side: Side, val support: Int, val required: Int) : DetectionFailure

    /** The refit of [side] was rejected by the line fitter. */
    data class SideFitRejected(val side: Side, val reason: LineFitRejection) : DetectionFailure

    /**
     * The refit / re-classification loop for [side] was still changing its inlier set after
     * [iterations] (= [CardDetectionPolicy.maxRefitIterations]) refits, so no line is consistent
     * with its own supporting evidence.
     */
    data class RefitDidNotConverge(val side: Side, val iterations: Int) : DetectionFailure

    /** The refitted [line] for [side] is 45° or more from perpendicular to its scan lines. */
    data class SideOrientationUnobservable(val side: Side, val line: Line) : DetectionFailure

    /** The two sides meeting at [corner] do not intersect in a point. */
    data class CornerNotFound(val corner: CardCorner, val intersection: LineIntersection) : DetectionFailure

    /** The assembled outline failed the policy's quadrilateral validation. */
    data class InvalidQuadrilateral(val quad: Quadrilateral, val rejections: Set<QuadRejection>) : DetectionFailure
}

/** Per-side bookkeeping for diagnostics and tests. */
data class SideDiagnostics(
    val side: Side,
    /** Observations offered to this side by all scans. */
    val poolSize: Int,
    /** Seed hypotheses scored. */
    val seedsEvaluated: Int,
    /** Largest seed support found. */
    val bestSupport: Int,
    /** Support of the chosen seed, if one was chosen. */
    val chosenSupport: Int?,
    /** Refit iterations used, if the side was refitted. */
    val refitIterations: Int?,
)

/** What the detector looked at, for both outcomes. [sides] holds only the sides it evaluated. */
data class DetectionDiagnostics(
    val scanRows: List<Int>,
    val scanColumns: List<Int>,
    val sides: Map<Side, SideDiagnostics>,
) {
    init {
        require(sides.all { (k, v) -> v.side == k }) { "Side diagnostics must be keyed by their own side" }
    }
}

sealed interface BoundaryDetection {
    val diagnostics: DetectionDiagnostics

    /**
     * [quad] is TL = top ∩ left, TR = top ∩ right, BR = bottom ∩ right, BL = bottom ∩ left.
     * [sides] holds exactly one fit per [Side], keyed by its own side.
     */
    data class Detected(
        val quad: Quadrilateral,
        val sides: Map<Side, SideFit>,
        override val diagnostics: DetectionDiagnostics,
    ) : BoundaryDetection {
        init {
            require(sides.keys == Side.entries.toSet()) { "A detection needs all four sides: ${sides.keys}" }
            require(sides.all { (k, v) -> v.side == k }) { "Side fits must be keyed by their own side" }
        }
    }

    data class NotDetected(
        val failure: DetectionFailure,
        override val diagnostics: DetectionDiagnostics,
    ) : BoundaryDetection
}
