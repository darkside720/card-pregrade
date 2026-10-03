package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.Homography
import com.cardpregrade.core.cv.geometry.HomographyResult
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.cv.geometry.QuadRejection
import com.cardpregrade.core.cv.geometry.QuadValidation
import com.cardpregrade.core.cv.geometry.QuadValidationPolicy
import com.cardpregrade.core.cv.geometry.Quadrilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.floor

class SyntheticSceneRendererTest {

    private val k4 = StandardCardDesign(pxPerMm = 4)
    private val background = SceneRequest.DEFAULT_BACKGROUND_ARGB

    // --- coordinate convention ---

    @Test
    fun `identity samples every pixel at its centre`() {
        val probe = RecordingPattern(PixelSize(8, 6))
        render(SceneRequest(PixelSize(8, 6), rect(0.0, 0.0, 8.0, 6.0), probe))
        assertEquals(48, probe.samples.size)
        assertPoint(PixelPoint(0.5, 0.5), probe.samples.first())
        assertPoint(PixelPoint(7.5, 5.5), probe.samples.last())
        probe.samples.forEachIndexed { i, s -> assertPoint(PixelPoint(i % 8 + 0.5, i / 8 + 0.5), s) }
    }

    @Test
    fun `half pixel translation lands features where pixel centre sampling predicts`() {
        // Card shifted by +0.5 px: scene pixel x samples card u = x (+0.5 − 0.5). A stripe at
        // u ∈ [9.75, 10.25) therefore paints exactly column 10. A sampler that forgot the +0.5
        // would sample u = x − 0.5 and paint no column at all.
        val stripe = 0xFF00FF00.toInt()
        val plain = 0xFF0000FF.toInt()
        val probe = FunctionPattern(PixelSize(40, 10)) { u, v -> if (u >= 9.75 && u < 10.25 || v >= 4.75 && v < 5.25) stripe else plain }
        val scene = render(SceneRequest(PixelSize(60, 12), rect(0.5, 0.5, 40.5, 10.5), probe))
        val image = scene.image
        for (y in 0..9) for (x in 0..39) {
            assertEquals("($x, $y)", if (x == 10 || y == 5) stripe else plain, image[x, y])
        }
        for (y in 0 until 12) for (x in 40 until 60) assertEquals("($x, $y)", background, image[x, y])
        for (x in 0 until 60) assertEquals("($x, 10)", background, image[x, 10])
        for (x in 0 until 60) assertEquals("($x, 11)", background, image[x, 11])
    }

    @Test
    fun `card membership is half open at exact boundary samples`() {
        // With the card shifted by +0.5 px, column 0 / row 0 sample exactly u = 0 / v = 0 and
        // column 40 / row 10 sample exactly u = W = 40 / v = H = 10. The contract 0 ≤ u < W,
        // 0 ≤ v < H keeps the near edges and excludes the far edges.
        val inside = 0xFF00AA00.toInt()
        val probe = FunctionPattern(PixelSize(40, 10)) { _, _ -> inside }
        val scene = render(SceneRequest(PixelSize(60, 12), rect(0.5, 0.5, 40.5, 10.5), probe))
        assertEquals(0.0, scene.sceneToCard.apply(PixelPoint(0.5, 5.5))!!.x, 0.0)
        assertEquals(40.0, scene.sceneToCard.apply(PixelPoint(40.5, 5.5))!!.x, 0.0)
        assertEquals(0.0, scene.sceneToCard.apply(PixelPoint(20.5, 0.5))!!.y, 0.0)
        assertEquals(10.0, scene.sceneToCard.apply(PixelPoint(20.5, 10.5))!!.y, 0.0)

        assertEquals("u = 0 is on the card", inside, scene.image[0, 5])
        assertEquals("v = 0 is on the card", inside, scene.image[20, 0])
        assertEquals("u = 0, v = 0 is on the card", inside, scene.image[0, 0])
        assertEquals("u = W is off the card", background, scene.image[40, 5])
        assertEquals("v = H is off the card", background, scene.image[20, 10])
        assertEquals("u = W, v = H is off the card", background, scene.image[40, 10])
    }

    @Test
    fun `two to one downscale samples odd canonical columns`() {
        // Scene pixel x has centre x + 0.5, which maps to card u = 2x + 1. The pattern is A
        // exactly where floor(u − 0.5) is even, i.e. around odd integers; integer-centre
        // sampling (u = 2x) would see B everywhere.
        val a = 0xFFAA0000.toInt()
        val b = 0xFF0000AA.toInt()
        val probe = FunctionPattern(PixelSize(40, 10)) { u, _ -> if (floor(u - 0.5).toInt() % 2 == 0) a else b }
        val image = render(SceneRequest(PixelSize(20, 5), rect(0.0, 0.0, 20.0, 5.0), probe)).image
        assertTrue(image.pixels.all { it == a })
    }

    @Test
    fun `integer translation reproduces the canonical raster exactly`() {
        val canonical = SyntheticSceneRenderer.renderCanonical(k4)
        val image = render(SceneRequest(PixelSize(300, 400), rect(7.0, 5.0, 259.0, 357.0), k4)).image
        for (y in 0 until 400) for (x in 0 until 300) {
            val inside = x in 7 until 259 && y in 5 until 357
            assertEquals("($x, $y)", if (inside) canonical[x - 7, y - 5] else background, image[x, y])
        }
    }

    @Test
    fun `identity scene equals the canonical raster byte for byte`() {
        val canonical = SyntheticSceneRenderer.renderCanonical(k4)
        val scene = render(SceneRequest(k4.canonicalSize, SyntheticSceneRenderer.canonicalRect(k4.canonicalSize), k4))
        assertTrue(scene.image.pixels.contentEquals(canonical.pixels))
    }

    // --- partial coverage and determinism ---

    @Test
    fun `a card partly outside the scene is rendered with exact ground truth`() {
        val quad = (CardPose(0.0, 510.0, widthPx = 480.0).toQuad() as PoseResult.Projected).quad
        assertTrue(quad.topLeft.x < 0)
        val scene = render(SceneRequest(PixelSize(768, 1020), quad, k4))
        assertNotEquals(background, scene.image[0, 510])
        assertEquals(background, scene.image[767, 510])
        assertEquals(quad, scene.cardQuad)
    }

    @Test
    fun `identical requests render identical pixels`() {
        val request = SyntheticFixtures.request("combined")
        val first = render(request).image.pixels
        val second = render(SyntheticFixtures.request("combined")).image.pixels
        assertTrue(first.contentEquals(second))
    }

    // --- rejections ---

    @Test
    fun `structurally invalid quads are rejected with their quad rejections`() {
        assertQuadRejected(QuadRejection.SELF_INTERSECTING, quad(100.0 to 100.0, 400.0 to 500.0, 400.0 to 100.0, 100.0 to 500.0))
        assertQuadRejected(QuadRejection.WRONG_WINDING, quad(100.0 to 100.0, 100.0 to 500.0, 400.0 to 500.0, 400.0 to 100.0))
        assertQuadRejected(QuadRejection.DEGENERATE, quad(100.0 to 100.0, 100.0 to 100.0, 400.0 to 500.0, 100.0 to 500.0))
        assertQuadRejected(QuadRejection.NON_FINITE, quad(Double.NaN to 100.0, 400.0 to 100.0, 400.0 to 500.0, 100.0 to 500.0))
    }

    @Test
    fun `an unsolvable card to scene homography is rejected`() {
        // A thin rectangle → normal quad map is too ill-conditioned to solve. (The opposite
        // direction, canonical card → structurally valid sliver quad, is an anisotropic scale and
        // solves fine, so the thin side here is the canonical card.)
        val thinCard = FunctionPattern(PixelSize(1_000_000, 1)) { _, _ -> 0xFFFFFFFF.toInt() }
        assertRejected(SceneRejection.HOMOGRAPHY_FAILED, SceneRequest(PixelSize(768, 1020), rect(100.0, 100.0, 600.0, 800.0), thinCard))
    }

    @Test
    fun `a structurally valid near triangle is rejected as an ill conditioned projective map`() {
        // BR sits 1e-4 px beyond the line TR–BL: structurally valid (turn ≫ 1e-9 · extent²) and
        // solvable, and every corner w has the same sign (the card is in front), but the corner
        // w values span > 10⁶, below the 1e-6 numerical safety ratio.
        val tr = PixelPoint(700.0, 120.0)
        val bl = PixelPoint(100.0, 900.0)
        val outward = 1e-4 / kotlin.math.hypot(780.0, 600.0)
        val br = PixelPoint((tr.x + bl.x) / 2 + 780.0 * outward, (tr.y + bl.y) / 2 + 600.0 * outward)
        val nearTriangle = Quadrilateral(PixelPoint(100.0, 100.0), tr, br, bl)
        assertTrue(QuadValidation.validate(nearTriangle, PixelSize(768, 1020), QuadValidationPolicy.STRUCTURAL_ONLY).isValid)
        assertTrue(Homography.fromFourPoints(SyntheticSceneRenderer.canonicalRect(k4.canonicalSize), nearTriangle) is HomographyResult.Solved)
        assertRejected(SceneRejection.ILL_CONDITIONED_PROJECTIVE_MAP, SceneRequest(PixelSize(768, 1020), nearTriangle, k4))
    }

    @Test
    fun `a card with no scene coverage is rejected`() {
        assertRejected(SceneRejection.OUTSIDE_SCENE, SceneRequest(PixelSize(768, 1020), rect(2000.0, 100.0, 2300.0, 500.0), k4))
        // Touching the right edge only.
        assertRejected(SceneRejection.OUTSIDE_SCENE, SceneRequest(PixelSize(768, 1020), rect(768.0, 100.0, 1000.0, 500.0), k4))
        // Bounding boxes overlap, but every corner has x − y ≥ 800 while the scene's maximum is 768
        // at its top-right corner: a quad edge separates them.
        val beyondCorner = quad(750.0 to -50.0, 760.0 to -60.0, 860.0 to 40.0, 850.0 to 50.0)
        assertTrue(beyondCorner.isConvex && beyondCorner.signedArea > 0)
        assertRejected(SceneRejection.OUTSIDE_SCENE, SceneRequest(PixelSize(768, 1020), beyondCorner, k4))
        // Overlaps the scene geometrically but contains no pixel centre.
        assertRejected(SceneRejection.OUTSIDE_SCENE, SceneRequest(PixelSize(768, 1020), rect(10.1, 10.1, 10.4, 10.4), k4))
    }

    @Test
    fun `invalid scene sizes are rejected`() {
        assertRejected(SceneRejection.INVALID_SCENE_SIZE, SceneRequest(PixelSize(5000, 4000), rect(0.0, 0.0, 100.0, 140.0), k4))
        assertThrows(IllegalArgumentException::class.java) { PixelSize(0, 1020) }
    }

    @Test
    fun `crossing or touching the vanishing line is not in front`() {
        val size = k4.canonicalSize // 252 × 352
        assertNull(SyntheticSceneRenderer.checkInFront(Homography.IDENTITY, size))
        // w = 1 − u/126: +1 at the left corners, −1 at the right corners (the horizon crosses the card).
        assertProjectiveRejection(SceneRejection.NOT_IN_FRONT, listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0 / 126, 0.0, 1.0), size)
        // On a 256-wide card, w = 1 − u/256 is exactly 0 at the right corners (dyadic arithmetic):
        // the horizon touches the card's right edge.
        assertProjectiveRejection(SceneRejection.NOT_IN_FRONT, listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0 / 256, 0.0, 1.0), PixelSize(256, 352))
    }

    @Test
    fun `a same sign map below the 1e-6 ratio is ill conditioned not out of view`() {
        val size = k4.canonicalSize
        // w = 1 − (1 − 1e-7)·u/252: positive at every corner, but min/max = 1e-7 < 1e-6.
        assertProjectiveRejection(SceneRejection.ILL_CONDITIONED_PROJECTIVE_MAP, listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -(1 - 1e-7) / 252, 0.0, 1.0), size)
        // min/max = 1e-5 passes.
        val near = solved(Homography.fromRowMajor(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -(1 - 1e-5) / 252, 0.0, 1.0)))
        assertNull(SyntheticSceneRenderer.checkInFront(near, size))

        // The two causes are reported distinctly by the same check.
        val crossing = SyntheticSceneRenderer.checkInFront(solved(Homography.fromRowMajor(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0 / 126, 0.0, 1.0))), size)
        val illConditioned = SyntheticSceneRenderer.checkInFront(solved(Homography.fromRowMajor(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -(1 - 1e-7) / 252, 0.0, 1.0))), size)
        assertEquals(SceneRejection.NOT_IN_FRONT, crossing?.reason)
        assertEquals(SceneRejection.ILL_CONDITIONED_PROJECTIVE_MAP, illConditioned?.reason)
        assertNotEquals(crossing?.reason, illConditioned?.reason)
    }

    // --- helpers ---

    private class FunctionPattern(override val canonicalSize: PixelSize, private val f: (Double, Double) -> Int) : CardPattern {
        override fun argbAt(u: Double, v: Double): Int = f(u, v)
    }

    private class RecordingPattern(override val canonicalSize: PixelSize) : CardPattern {
        val samples = mutableListOf<PixelPoint>()
        override fun argbAt(u: Double, v: Double): Int {
            samples += PixelPoint(u, v)
            return 0xFFFFFFFF.toInt()
        }
    }

    private fun render(request: SceneRequest): SyntheticScene = when (val r = SyntheticSceneRenderer.render(request)) {
        is SceneResult.Rendered -> r.scene
        is SceneResult.Rejected -> fail("Rejected: $r") as Nothing
    }

    private fun assertRejected(reason: SceneRejection, request: SceneRequest): SceneResult.Rejected {
        val result = SyntheticSceneRenderer.render(request)
        if (result !is SceneResult.Rejected) fail("Expected $reason, got $result")
        result as SceneResult.Rejected
        assertEquals(result.detail, reason, result.reason)
        return result
    }

    private fun assertQuadRejected(expected: QuadRejection, quad: Quadrilateral) {
        val result = assertRejected(SceneRejection.INVALID_QUAD, SceneRequest(PixelSize(768, 1020), quad, k4))
        assertTrue("${result.quadRejections}", expected in result.quadRejections)
    }

    private fun assertProjectiveRejection(expected: SceneRejection, elements: List<Double>, size: PixelSize) {
        val result = SyntheticSceneRenderer.checkInFront(solved(Homography.fromRowMajor(elements)), size)
        assertEquals(expected, result?.reason)
    }

    private fun solved(result: HomographyResult): Homography = when (result) {
        is HomographyResult.Solved -> result.homography
        is HomographyResult.Failed -> fail("Expected a solution, got $result") as Nothing
    }

    private fun rect(left: Double, top: Double, right: Double, bottom: Double) =
        Quadrilateral(PixelPoint(left, top), PixelPoint(right, top), PixelPoint(right, bottom), PixelPoint(left, bottom))

    private fun quad(tl: Pair<Double, Double>, tr: Pair<Double, Double>, br: Pair<Double, Double>, bl: Pair<Double, Double>) =
        Quadrilateral(PixelPoint(tl.first, tl.second), PixelPoint(tr.first, tr.second), PixelPoint(br.first, br.second), PixelPoint(bl.first, bl.second))

    private fun assertPoint(expected: PixelPoint, actual: PixelPoint?, tolerance: Double = 1e-9) {
        if (actual == null) fail("Expected $expected, got null")
        assertEquals("x", expected.x, actual!!.x, tolerance)
        assertEquals("y", expected.y, actual.y, tolerance)
    }
}
