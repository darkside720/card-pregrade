package com.cardpregrade.app.ui.calibration

import com.cardpregrade.core.cv.detection.BoundaryDetection
import com.cardpregrade.core.cv.detection.CardDetectionPolicy
import com.cardpregrade.core.cv.detection.DetectionFailure
import com.cardpregrade.core.cv.detection.EdgePolarity
import com.cardpregrade.core.cv.detection.Side
import com.cardpregrade.core.cv.geometry.LineIntersection
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.QuadValidationPolicy
import com.cardpregrade.core.cv.geometry.Quadrilateral
import java.util.Locale

/** One label/value line of developer diagnostics. */
data class DiagnosticRow(val label: String, val value: String)

/**
 * What the overlay draws, in working-image pixel coordinates.
 *
 * @property quad the detected outline, or the outline rejected by validation; null otherwise.
 * @property quadRejected true when [quad] failed validation and is shown only as evidence.
 * @property sideSegments each fitted side line clipped to the image.
 * @property inliers each side's accepted observations.
 */
data class OverlayShapes(
    val quad: Quadrilateral?,
    val quadRejected: Boolean,
    val sideSegments: Map<Side, Pair<PixelPoint, PixelPoint>>,
    val inliers: Map<Side, List<PixelPoint>>,
)

data class SidePresentation(val side: Side, val rows: List<DiagnosticRow>)

/** Display model for one capture's boundary detection. Built from typed results only. */
data class DetectionPresentation(
    val detected: Boolean,
    val headline: String,
    val failureRows: List<DiagnosticRow>,
    val sides: List<SidePresentation>,
    val imageRows: List<DiagnosticRow>,
    val policyRows: List<DiagnosticRow>,
    val shapes: OverlayShapes,
)

object DetectionPresenter {

    const val KNOWN_LIMITATIONS =
        "Experimental detector, tuned only on synthetic scenes. Known gaps: low-contrast card edges, " +
            "cards near the image edge or partly out of frame (an inner printed line can stand in for the " +
            "missing side and still be reported as detected), textured backgrounds, rounded corners, " +
            "glare and shadows. The overlay draws exactly what the detector returned."

    fun present(imageWidth: Int, imageHeight: Int, policy: CardDetectionPolicy, detection: BoundaryDetection): DetectionPresentation {
        val diagnostics = detection.diagnostics
        val imageRows = listOf(
            DiagnosticRow("Working image", "$imageWidth × $imageHeight px"),
            DiagnosticRow("Scan lines", "${diagnostics.scanRows.size} rows · ${diagnostics.scanColumns.size} columns"),
        )
        val fits = (detection as? BoundaryDetection.Detected)?.sides.orEmpty()
        val sides = Side.entries.mapNotNull { side ->
            val fit = fits[side]
            val diag = diagnostics.sides[side]
            if (fit == null && diag == null) return@mapNotNull null
            val rows = buildList {
                fit?.let {
                    add(DiagnosticRow("Polarity", polarity(it.polarity)))
                    add(DiagnosticRow("Inliers", it.inliers.size.toString()))
                    add(DiagnosticRow("RMS residual", px(it.rmsResidualPx)))
                    add(DiagnosticRow("Max residual", px(it.maxAbsResidualPx)))
                }
                diag?.let {
                    add(DiagnosticRow("Candidate pool", it.poolSize.toString()))
                    add(DiagnosticRow("Seeds scored", it.seedsEvaluated.toString()))
                    add(DiagnosticRow("Best support", it.bestSupport.toString()))
                    add(DiagnosticRow("Chosen support", it.chosenSupport?.toString() ?: "—"))
                    add(DiagnosticRow("Refit iterations", it.refitIterations?.toString() ?: "—"))
                }
            }
            SidePresentation(side, rows)
        }
        val shapes = when (detection) {
            is BoundaryDetection.Detected -> OverlayShapes(
                quad = detection.quad,
                quadRejected = false,
                sideSegments = detection.sides.mapNotNull { (side, fit) ->
                    clipLineToRect(fit.line, imageWidth.toDouble(), imageHeight.toDouble())?.let { side to it }
                }.toMap(),
                inliers = detection.sides.mapValues { (_, fit) -> fit.inliers.map { it.point } },
            )
            is BoundaryDetection.NotDetected -> OverlayShapes(
                quad = (detection.failure as? DetectionFailure.InvalidQuadrilateral)?.quad,
                quadRejected = true,
                sideSegments = emptyMap(),
                inliers = emptyMap(),
            )
        }
        return when (detection) {
            is BoundaryDetection.Detected -> DetectionPresentation(
                detected = true,
                headline = "Detected (experimental): quad drawn as returned",
                failureRows = emptyList(),
                sides = sides,
                imageRows = imageRows,
                policyRows = policyRows(policy),
                shapes = shapes,
            )
            is BoundaryDetection.NotDetected -> {
                val (name, rows) = describe(detection.failure)
                DetectionPresentation(
                    detected = false,
                    headline = "Not detected: $name",
                    failureRows = rows,
                    sides = sides,
                    imageRows = imageRows,
                    policyRows = policyRows(policy),
                    shapes = shapes,
                )
            }
        }
    }

    /** Failure type name and its structured fields. Exhaustive over [DetectionFailure]. */
    fun describe(failure: DetectionFailure): Pair<String, List<DiagnosticRow>> = when (failure) {
        is DetectionFailure.ImageTooSmall -> "ImageTooSmall" to listOf(
            DiagnosticRow("Width", "${failure.width} px"),
            DiagnosticRow("Height", "${failure.height} px"),
        )
        is DetectionFailure.InsufficientEvidence -> "InsufficientEvidence" to listOf(
            DiagnosticRow("Side", failure.side.name),
            DiagnosticRow("Support", failure.support.toString()),
            DiagnosticRow("Required", failure.required.toString()),
        )
        is DetectionFailure.SideFitRejected -> "SideFitRejected" to listOf(
            DiagnosticRow("Side", failure.side.name),
            DiagnosticRow("Line-fit rejection", failure.reason.name),
        )
        is DetectionFailure.RefitDidNotConverge -> "RefitDidNotConverge" to listOf(
            DiagnosticRow("Side", failure.side.name),
            DiagnosticRow("Iterations", failure.iterations.toString()),
        )
        is DetectionFailure.SideOrientationUnobservable -> "SideOrientationUnobservable" to listOf(
            DiagnosticRow("Side", failure.side.name),
            DiagnosticRow("Line (a, b, c)", String.format(Locale.ROOT, "%.4f, %.4f, %.2f", failure.line.a, failure.line.b, failure.line.c)),
        )
        is DetectionFailure.CornerNotFound -> "CornerNotFound" to listOf(
            DiagnosticRow("Corner", failure.corner.name),
            DiagnosticRow(
                "Intersection",
                when (val i = failure.intersection) {
                    is LineIntersection.Parallel -> "parallel, ${px(i.separation)} apart"
                    LineIntersection.Coincident -> "coincident"
                    is LineIntersection.Point -> "point ${point(i.point)}"
                },
            ),
        )
        is DetectionFailure.InvalidQuadrilateral -> "InvalidQuadrilateral" to listOf(
            DiagnosticRow("Rejections", failure.rejections.joinToString { it.name }),
            DiagnosticRow("Corners (TL, TR, BR, BL)", failure.quad.points.joinToString(" ") { point(it) }),
        )
    }

    fun policyRows(policy: CardDetectionPolicy): List<DiagnosticRow> = listOf(
        DiagnosticRow("Scan lines per axis", policy.scanLinesPerAxis.toString()),
        DiagnosticRow("Min |Sobel| response", policy.minAbsResponse.toString()),
        DiagnosticRow("Candidates per scan end", policy.candidatesPerScanEnd.toString()),
        DiagnosticRow("Inlier tolerance", px(policy.inlierTolerancePx)),
        DiagnosticRow("Min inliers", policy.minInliers.toString()),
        DiagnosticRow("Support fraction", String.format(Locale.ROOT, "%.2f", policy.supportFraction)),
        DiagnosticRow("Max refit iterations", policy.maxRefitIterations.toString()),
        DiagnosticRow("Quad validation", if (policy.validation == QuadValidationPolicy.STRUCTURAL_ONLY) "structural only" else policy.validation.toString()),
    )

    private fun polarity(p: EdgePolarity) = when (p) {
        EdgePolarity.INWARD_BRIGHTER -> "inward brighter"
        EdgePolarity.INWARD_DARKER -> "inward darker"
    }

    private fun px(v: Double) = String.format(Locale.ROOT, "%.3f px", v)

    private fun point(p: PixelPoint) = String.format(Locale.ROOT, "(%.1f, %.1f)", p.x, p.y)
}
