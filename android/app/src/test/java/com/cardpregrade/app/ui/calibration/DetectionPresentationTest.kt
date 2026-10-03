package com.cardpregrade.app.ui.calibration

import com.cardpregrade.core.cv.detection.BoundaryDetection
import com.cardpregrade.core.cv.detection.CardBoundaryDetector
import com.cardpregrade.core.cv.detection.CardDetectionPolicy
import com.cardpregrade.core.cv.detection.DetectionDiagnostics
import com.cardpregrade.core.cv.detection.DetectionFailure
import com.cardpregrade.core.cv.detection.Side
import com.cardpregrade.core.cv.detection.SideDiagnostics
import com.cardpregrade.core.cv.geometry.Line
import com.cardpregrade.core.cv.geometry.LineFitRejection
import com.cardpregrade.core.cv.geometry.LineIntersection
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.QuadRejection
import com.cardpregrade.core.cv.geometry.Quadrilateral
import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.model.CardCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionPresentationTest {

    private val policy = CardDetectionPolicy.DEFAULT
    private val emptyDiagnostics = DetectionDiagnostics(listOf(1, 2), listOf(3), emptyMap())

    /** 200 × 300, card [40, 160) × [50, 250) at luma 200 on 30: detected exactly (see core tests). */
    private fun cardImage() = GrayImage(200, 300, IntArray(200 * 300) {
        val x = it % 200
        val y = it / 200
        if (x in 40 until 160 && y in 50 until 250) 200 else 30
    })

    private fun rows(vararg pairs: Pair<String, String>) = pairs.map { DiagnosticRow(it.first, it.second) }

    @Test
    fun `successful detection lists every side and draws the returned geometry`() {
        val d = CardBoundaryDetector.detect(cardImage()) as BoundaryDetection.Detected
        val p = DetectionPresenter.present(200, 300, policy, d)
        assertTrue(p.detected)
        assertEquals("Detected (experimental): quad drawn as returned", p.headline)
        assertEquals(emptyList<DiagnosticRow>(), p.failureRows)
        assertEquals(rows("Working image" to "200 × 300 px", "Scan lines" to "32 rows · 32 columns"), p.imageRows)
        assertEquals(listOf(Side.LEFT, Side.TOP, Side.RIGHT, Side.BOTTOM), p.sides.map { it.side })
        for (side in p.sides) {
            val fit = d.sides.getValue(side.side)
            val diag = d.diagnostics.sides.getValue(side.side)
            assertEquals(
                rows(
                    "Polarity" to "inward brighter",
                    "Inliers" to fit.inliers.size.toString(),
                    "RMS residual" to "0.000 px",
                    "Max residual" to "0.000 px",
                    "Candidate pool" to diag.poolSize.toString(),
                    "Seeds scored" to diag.seedsEvaluated.toString(),
                    "Best support" to diag.bestSupport.toString(),
                    "Chosen support" to diag.chosenSupport.toString(),
                    "Refit iterations" to diag.refitIterations.toString(),
                ),
                side.rows,
            )
        }
        // The quad, clipped side lines and inliers are exactly the detector's output.
        assertEquals(d.quad, p.shapes.quad)
        assertFalse(p.shapes.quadRejected)
        assertEquals(PixelPoint(40.0, 50.0), p.shapes.quad!!.topLeft)
        val left = p.shapes.sideSegments.getValue(Side.LEFT)
        assertEquals(setOf(PixelPoint(40.0, 0.0), PixelPoint(40.0, 300.0)), setOf(left.first, left.second))
        val top = p.shapes.sideSegments.getValue(Side.TOP)
        assertEquals(setOf(PixelPoint(0.0, 50.0), PixelPoint(200.0, 50.0)), setOf(top.first, top.second))
        for (side in Side.entries) assertEquals(d.sides.getValue(side).inliers.map { it.point }, p.shapes.inliers.getValue(side))
    }

    @Test
    fun `failure keeps partial per-side diagnostics and draws nothing invented`() {
        // Blank image: LEFT is evaluated and fails first; no other side has diagnostics.
        val r = CardBoundaryDetector.detect(GrayImage(100, 100, IntArray(100 * 100) { 128 })) as BoundaryDetection.NotDetected
        val p = DetectionPresenter.present(100, 100, policy, r)
        assertFalse(p.detected)
        assertEquals("Not detected: InsufficientEvidence", p.headline)
        assertEquals(rows("Side" to "LEFT", "Support" to "0", "Required" to "6"), p.failureRows)
        assertEquals(listOf(Side.LEFT), p.sides.map { it.side })
        assertEquals(
            rows(
                "Candidate pool" to "0",
                "Seeds scored" to "0",
                "Best support" to "0",
                "Chosen support" to "—",
                "Refit iterations" to "—",
            ),
            p.sides.single().rows,
        )
        assertNull(p.shapes.quad)
        assertTrue(p.shapes.sideSegments.isEmpty() && p.shapes.inliers.isEmpty())
    }

    @Test
    fun `an invalid quadrilateral is drawn as rejected evidence`() {
        val quad = Quadrilateral(PixelPoint(100.0, 100.0), PixelPoint(100.0, 100.0), PixelPoint(100.0, 100.0), PixelPoint(100.0, 100.0))
        val failure = DetectionFailure.InvalidQuadrilateral(quad, setOf(QuadRejection.DEGENERATE))
        val sideDiag = mapOf(Side.LEFT to SideDiagnostics(Side.LEFT, 12, 30, 10, 10, 1))
        val p = DetectionPresenter.present(200, 200, policy, BoundaryDetection.NotDetected(failure, DetectionDiagnostics(listOf(1), listOf(1), sideDiag)))
        assertEquals(quad, p.shapes.quad)
        assertTrue(p.shapes.quadRejected)
        assertEquals("Not detected: InvalidQuadrilateral", p.headline)
        assertEquals(
            rows("Rejections" to "DEGENERATE", "Corners (TL, TR, BR, BL)" to "(100.0, 100.0) (100.0, 100.0) (100.0, 100.0) (100.0, 100.0)"),
            p.failureRows,
        )
        assertEquals(listOf(Side.LEFT), p.sides.map { it.side })
    }

    @Test
    fun `every failure type is named and keeps its structured fields`() {
        val line = Line.of(1.0, 1.0, -100.0)!!
        val cases = listOf(
            DetectionFailure.ImageTooSmall(2, 50) to ("ImageTooSmall" to rows("Width" to "2 px", "Height" to "50 px")),
            DetectionFailure.InsufficientEvidence(Side.TOP, 3, 6) to ("InsufficientEvidence" to rows("Side" to "TOP", "Support" to "3", "Required" to "6")),
            DetectionFailure.SideFitRejected(Side.RIGHT, LineFitRejection.NO_DOMINANT_DIRECTION) to
                ("SideFitRejected" to rows("Side" to "RIGHT", "Line-fit rejection" to "NO_DOMINANT_DIRECTION")),
            DetectionFailure.RefitDidNotConverge(Side.BOTTOM, 5) to ("RefitDidNotConverge" to rows("Side" to "BOTTOM", "Iterations" to "5")),
            // (1, 1, −100) normalised: a = b = 1/√2 ≈ 0.7071, c = −100/√2 ≈ −70.71.
            DetectionFailure.SideOrientationUnobservable(Side.LEFT, line) to
                ("SideOrientationUnobservable" to rows("Side" to "LEFT", "Line (a, b, c)" to "0.7071, 0.7071, -70.71")),
            DetectionFailure.CornerNotFound(CardCorner.TOP_LEFT, LineIntersection.Parallel(3.5)) to
                ("CornerNotFound" to rows("Corner" to "TOP_LEFT", "Intersection" to "parallel, 3.500 px apart")),
            DetectionFailure.CornerNotFound(CardCorner.BOTTOM_RIGHT, LineIntersection.Coincident) to
                ("CornerNotFound" to rows("Corner" to "BOTTOM_RIGHT", "Intersection" to "coincident")),
            DetectionFailure.InvalidQuadrilateral(
                Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(10.0, 0.0), PixelPoint(0.0, 10.0), PixelPoint(10.0, 10.0)),
                setOf(QuadRejection.SELF_INTERSECTING, QuadRejection.NOT_CONVEX),
            ) to ("InvalidQuadrilateral" to rows(
                "Rejections" to "SELF_INTERSECTING, NOT_CONVEX",
                "Corners (TL, TR, BR, BL)" to "(0.0, 0.0) (10.0, 0.0) (0.0, 10.0) (10.0, 10.0)",
            )),
        )
        for ((failure, expected) in cases) {
            assertEquals(failure.toString(), expected, DetectionPresenter.describe(failure))
            val p = DetectionPresenter.present(100, 100, policy, BoundaryDetection.NotDetected(failure, emptyDiagnostics))
            assertEquals("Not detected: ${expected.first}", p.headline)
            assertEquals(expected.second, p.failureRows)
            assertEquals(rows("Working image" to "100 × 100 px", "Scan lines" to "2 rows · 1 columns"), p.imageRows)
        }
        // Every subtype appears above: kind() is exhaustive, so a new subtype fails to compile here.
        assertEquals(7, cases.map { kind(it.first) }.toSet().size)
    }

    private fun kind(f: DetectionFailure): String = when (f) {
        is DetectionFailure.ImageTooSmall -> "ImageTooSmall"
        is DetectionFailure.InsufficientEvidence -> "InsufficientEvidence"
        is DetectionFailure.SideFitRejected -> "SideFitRejected"
        is DetectionFailure.RefitDidNotConverge -> "RefitDidNotConverge"
        is DetectionFailure.SideOrientationUnobservable -> "SideOrientationUnobservable"
        is DetectionFailure.CornerNotFound -> "CornerNotFound"
        is DetectionFailure.InvalidQuadrilateral -> "InvalidQuadrilateral"
    }

    @Test
    fun `policy rows show the provisional defaults`() {
        assertEquals(
            rows(
                "Scan lines per axis" to "32",
                "Min |Sobel| response" to "64",
                "Candidates per scan end" to "4",
                "Inlier tolerance" to "2.000 px",
                "Min inliers" to "6",
                "Support fraction" to "0.60",
                "Max refit iterations" to "5",
                "Quad validation" to "structural only",
            ),
            DetectionPresenter.policyRows(policy),
        )
    }

    @Test
    fun `known limitations name every documented gap and no confidence is shown`() {
        val note = DetectionPresenter.KNOWN_LIMITATIONS
        for (gap in listOf("low-contrast", "near the image edge", "partly out of frame", "textured backgrounds", "rounded corners", "glare", "shadows")) {
            assertTrue(gap, note.contains(gap))
        }
        val d = CardBoundaryDetector.detect(cardImage())
        val p = DetectionPresenter.present(200, 300, policy, d)
        val text = (p.imageRows + p.policyRows + p.sides.flatMap { it.rows }).joinToString { "${it.label} ${it.value}" } + p.headline
        assertFalse(text, text.contains("confidence", ignoreCase = true) || text.contains("%"))
    }
}
