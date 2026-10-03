package com.cardpregrade.core.cv.detection

import com.cardpregrade.core.cv.fixtures.CardPose
import com.cardpregrade.core.cv.fixtures.PoseResult
import com.cardpregrade.core.cv.fixtures.SceneRequest
import com.cardpregrade.core.cv.fixtures.SyntheticFixtures
import com.cardpregrade.core.cv.fixtures.SyntheticScene
import com.cardpregrade.core.cv.fixtures.SyntheticSceneRenderer
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.Quadrilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

/**
 * [CardBoundaryDetector] on rendered synthetic scenes. The detector sees only the rendered grey
 * pixels; [SyntheticScene.cardQuad] is used afterwards as ground truth.
 *
 * The scenes are deterministic, point-sampled, crisp and noise-free (flat background, sharp card
 * corners, no blur, glare, texture or JPEG). Passing here shows the pipeline recovers the
 * boundary when the evidence is clean; it does not show robustness on real photographs.
 *
 * Corner tolerance: point sampling moves each straight edge to the nearest pixel boundary, at
 * most 0.5 px along the scan, so an axis-aligned corner can be off by up to √2 · 0.5 ≈ 0.71 px;
 * slanted edges average the quantisation over many scans. 1.0 px bounds that. (Measured worst
 * case on the required fixtures: 0.49 px.)
 */
class SyntheticCardDetectionTest {

    private val required = listOf("centered", "translated", "rot-cw-20", "rot-ccw-25", "persp-mild", "persp-strong", "combined")
    private val cornerTolerancePx = 1.0

    private fun render(request: SceneRequest): SyntheticScene = SyntheticSceneRenderer.render(request).orThrow()

    private fun detect(scene: SyntheticScene) = CardBoundaryDetector.detect(scene.image.toGrayImage())

    private fun detected(result: BoundaryDetection, what: String): BoundaryDetection.Detected =
        result as? BoundaryDetection.Detected ?: fail("$what: $result") as Nothing

    private fun cornerErrors(d: BoundaryDetection.Detected, truth: Quadrilateral) = d.quad.points.zip(truth.points).map { (a, b) -> a.distanceTo(b) }

    private fun assertMatches(truth: Quadrilateral, d: BoundaryDetection.Detected, what: String) {
        val errors = cornerErrors(d, truth)
        assertTrue("$what corner errors $errors", errors.all { it <= cornerTolerancePx })
    }

    /**
     * Signed distance of [p] from side [i] of the clockwise (y-down) [quad] (side i runs from
     * corner i to corner i + 1: 0 top, 1 right, 2 bottom, 3 left), positive towards the inside.
     */
    private fun sideDepth(quad: Quadrilateral, i: Int, p: PixelPoint): Double {
        val a = quad.points[i]
        val b = quad.points[(i + 1) % 4]
        return ((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) / a.distanceTo(b)
    }

    /** Distance of [p] inside the convex [quad]: the smallest [sideDepth]. */
    private fun insideDepth(quad: Quadrilateral, p: PixelPoint): Double = (0 until 4).minOf { sideDepth(quad, it, p) }


    private fun poseScene(pose: CardPose): SyntheticScene {
        val quad = (pose.toQuad() as? PoseResult.Projected)?.quad ?: fail("pose rejected: $pose") as Nothing
        return render(SceneRequest(SyntheticFixtures.SCENE_SIZE, quad, SyntheticFixtures.DESIGN))
    }

    @Test
    fun `required fixtures are detected within a pixel of the true corners`() {
        for (name in required) {
            val scene = SyntheticFixtures.render(name)
            val d = detected(detect(scene), name)
            assertMatches(scene.cardQuad, d, name)
            for (fit in d.sides.values) {
                // Bright card border (luma ≈ 203) on the dark default background (32).
                assertEquals("$name ${fit.side}", EdgePolarity.INWARD_BRIGHTER, fit.polarity)
                // Straight edges quantised to the pixel grid stay within half a pixel of one line.
                assertTrue("$name ${fit.side} rms ${fit.rmsResidualPx}", fit.rmsResidualPx <= 0.5)
            }
        }
    }

    @Test
    fun `small card is detected as well (outside the required gate)`() {
        val scene = SyntheticFixtures.render("scaled-small")
        assertMatches(scene.cardQuad, detected(detect(scene), "scaled-small"), "scaled-small")
    }

    @Test
    fun `reversed polarity background is detected with inward-darker sides`() {
        // White background (luma 255) against the border (203): every edge darkens going inwards.
        for (name in listOf("centered", "rot-cw-20", "persp-strong")) {
            val scene = render(SyntheticFixtures.request(name).copy(backgroundArgb = 0xFFFFFFFF.toInt()))
            val d = detected(detect(scene), "$name/white")
            assertMatches(scene.cardQuad, d, "$name/white")
            for (fit in d.sides.values) assertEquals("$name ${fit.side}", EdgePolarity.INWARD_DARKER, fit.polarity)
        }
    }

    @Test
    fun `outer boundary is chosen over stronger printed edges inside it`() {
        // Background luma 180: the card edge gives |Sobel| ≤ 4·(203 − 180) = 92, while the printed
        // frame gives up to 4·(203 − 102) = 404, the top-left marker 4·(203 − 89) = 456 and the
        // bottom-right marker 4·(203 − 14) = 756.
        for (name in listOf("centered", "rot-cw-20", "persp-strong")) {
            val scene = render(SyntheticFixtures.request(name).copy(backgroundArgb = 0xFFB4B4B4.toInt()))
            assertMatches(scene.cardQuad, detected(detect(scene), "$name/bg180"), "$name/bg180")
        }
    }

    @Test
    fun `boundary below the response threshold is missed and the printed frame is reported (known limitation)`() {
        // Background luma 190: the card edge gives |Sobel| ≤ 4·13 = 52 < minAbsResponse 64, so the
        // outermost supported lines are the printed art box (border 203 → art ≤ 134: darker
        // inwards). The detector reports that box, projected into the scene, and never a quad
        // outside the card.
        val art = SyntheticFixtures.DESIGN.artBox
        for (name in listOf("centered", "rot-cw-20", "persp-strong")) {
            val scene = render(SyntheticFixtures.request(name).copy(backgroundArgb = 0xFFBEBEBE.toInt()))
            val d = detected(detect(scene), "$name/bg190")
            val artInScene = listOf(art.left to art.top, art.right to art.top, art.right to art.bottom, art.left to art.bottom)
                .map { (u, v) -> scene.cardToScene.apply(PixelPoint(u, v)) ?: fail("art corner not projectable") as Nothing }
            val errors = d.quad.points.zip(artInScene).map { (a, b) -> a.distanceTo(b) }
            assertTrue("$name/bg190 art-box corner errors $errors", errors.all { it <= cornerTolerancePx })
            for (fit in d.sides.values) assertEquals("$name ${fit.side}", EdgePolarity.INWARD_DARKER, fit.polarity)
            for (p in d.quad.points) assertTrue("$name/bg190 corner $p", insideDepth(scene.cardQuad, p) > 10.0)
        }
    }

    @Test
    fun `card edge on the outermost Sobel column is not observed (known limitation)`() {
        // near-boundary: the card's left edge is at x ≈ 2, whose Sobel plateau starts on the
        // profile's first sample, an endpoint EdgeCandidates never reports. The left side falls
        // back to the printed frame inside the card; the other three sides are still exact.
        val scene = SyntheticFixtures.render("near-boundary")
        val d = detected(detect(scene), "near-boundary")
        val errors = cornerErrors(d, scene.cardQuad)
        assertTrue("TR, BR $errors", errors[1] <= cornerTolerancePx && errors[2] <= cornerTolerancePx)
        assertLeftReplacedByPrintedLine(scene, d)
    }

    /**
     * The left side of [d] is an inner printed line: TL and BL stay on the true top and bottom
     * lines, and, mapped back into card coordinates, both lie on one vertical art-box or
     * checkerboard boundary u = artBox.left + k · cell (k ≥ 0).
     */
    private fun assertLeftReplacedByPrintedLine(scene: SyntheticScene, d: BoundaryDetection.Detected) {
        val tl = d.quad.topLeft
        val bl = d.quad.bottomLeft
        assertTrue("TL on top line: $tl", abs(sideDepth(scene.cardQuad, 0, tl)) <= cornerTolerancePx)
        assertTrue("BL on bottom line: $bl", abs(sideDepth(scene.cardQuad, 2, bl)) <= cornerTolerancePx)
        val design = SyntheticFixtures.DESIGN
        val cell = 4.0 * design.pxPerMm
        val ks = listOf(tl, bl).map { p ->
            val u = (scene.sceneToCard.apply(p) ?: fail("corner not mappable: $p") as Nothing).x
            val k = (u - design.artBox.left) / cell
            // A scene pixel spans about 252 / 480 ≈ 0.53 canonical pixels for these poses, so a
            // 1 px detection error stays within 1 canonical pixel of the printed line.
            assertTrue("u = $u is not on a printed vertical line", abs(k - round(k)) * cell <= 1.0 && k > -0.5)
            round(k)
        }
        assertEquals("TL and BL on the same printed line", ks[0], ks[1], 0.0)
    }

    @Test
    fun `card partly out of frame reports an inner line for the missing side (known limitation)`() {
        // Left edge at x ≈ −140: no scan can see it, and the detector has no notion of an
        // off-frame side, so an inner printed line stands in for it. Visible sides stay exact.
        val left = poseScene(CardPose(100.0, 510.0, widthPx = 480.0))
        val d = detected(detect(left), "off-frame left")
        val errors = cornerErrors(d, left.cardQuad)
        assertTrue("TR, BR $errors", errors[1] <= cornerTolerancePx && errors[2] <= cornerTolerancePx)
        assertLeftReplacedByPrintedLine(left, d)
        for (p in d.quad.points) {
            assertTrue("in image: $p", p.x >= 0.0 && p.x <= left.image.width && p.y >= 0.0 && p.y <= left.image.height)
        }
    }

    @Test
    fun `re-rendering and re-detecting in one run gives identical results`() {
        for (name in listOf("combined", "persp-strong")) {
            val first = detect(SyntheticFixtures.render(name))
            val second = detect(SyntheticFixtures.render(name))
            assertEquals(name, first, second)
        }
    }
}
