package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.cv.geometry.Quadrilateral
import com.cardpregrade.core.model.CardCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Checks every named fixture against invariants computed WITHOUT the renderer's homographies:
 * pixel membership from half-planes of the pose quad, marker positions from the pinhole pose,
 * and the projective centre from the pose centre.
 */
class SyntheticFixturesTest {

    private companion object {
        val scenes: Map<String, SyntheticScene> by lazy { SyntheticFixtures.POSES.keys.associateWith { SyntheticFixtures.render(it) } }

        /** Pixels whose centre is within this distance of an edge line are not classified. */
        const val EDGE_TOLERANCE_PX = 1e-6
    }

    @Test
    fun `catalog uses the quarter resolution Pixel scene and the expected names`() {
        assertEquals(PixelSize(768, 1020), SyntheticFixtures.SCENE_SIZE)
        assertEquals(
            listOf("centered", "translated", "scaled-small", "rot-cw-20", "rot-ccw-25", "persp-mild", "persp-strong", "combined", "near-boundary"),
            SyntheticFixtures.POSES.keys.toList(),
        )
        for ((name, scene) in scenes) {
            assertEquals(name, 768, scene.image.width)
            assertEquals(name, 1020, scene.image.height)
        }
    }

    @Test
    fun `every fixture lies inside the scene with clockwise TL TR BR BL corners`() {
        for ((name, scene) in scenes) {
            val q = scene.cardQuad
            assertTrue("$name winding", q.signedArea > 0 && q.isConvex)
            q.points.forEach { assertTrue("$name corner $it", it.x in 0.0..768.0 && it.y in 0.0..1020.0) }
        }
        assertEquals(2.0, scenes.getValue("near-boundary").cardQuad.topLeft.x, 1e-9)
    }

    @Test
    fun `top left corners match the independently computed design review values`() {
        // From the design review's separate (Python) pinhole calculation, rounded to 0.1 px.
        val reviewed = mapOf(
            "centered" to PixelPoint(98.0, 110.5),
            "translated" to PixelPoint(90.0, 224.8),
            "scaled-small" to PixelPoint(234.0, 300.5),
            "rot-cw-20" to PixelPoint(273.1, 112.9),
            "rot-ccw-25" to PixelPoint(24.8, 307.6),
            "persp-mild" to PixelPoint(93.5, 110.3),
            "persp-strong" to PixelPoint(113.7, 125.2),
            "combined" to PixelPoint(3.9, 185.2),
            "near-boundary" to PixelPoint(2.0, 174.8),
        )
        for ((name, expected) in reviewed) assertPoint(name, expected, scenes.getValue(name).cardQuad.topLeft, 0.051)
    }

    @Test
    fun `card pixels are exactly the pixel centres inside the expected quad`() {
        for ((name, scene) in scenes) {
            var mismatches = 0
            var skipped = 0
            for (y in 0 until 1020) for (x in 0 until 768) {
                val distance = minEdgeDistance(scene.cardQuad, PixelPoint(x + 0.5, y + 0.5))
                if (abs(distance) <= EDGE_TOLERANCE_PX) { skipped++; continue }
                val isCard = scene.image[x, y] != scene.backgroundArgb
                if (isCard != (distance > 0)) mismatches++
            }
            assertEquals("$name mismatches", 0, mismatches)
            assertTrue("$name skipped $skipped", skipped < 10)
        }
    }

    @Test
    fun `markers appear at their pinhole projected centres`() {
        val design = SyntheticFixtures.DESIGN
        for ((name, pose) in SyntheticFixtures.POSES) {
            val image = scenes.getValue(name).image
            for ((corner, region) in design.markers) {
                val p = pose.projectCardPointMm(region.centreU / design.pxPerMm, region.centreV / design.pxPerMm)!!
                assertEquals("$name $corner", StandardCardDesign.MARKER_ARGB.getValue(corner), image[floor(p.x).toInt(), floor(p.y).toInt()])
            }
        }
    }

    @Test
    fun `orientation is preserved and not mirrored`() {
        for ((name, scene) in scenes) {
            val centroids = CardCorner.entries.associateWith { markerCentroid(scene.image, StandardCardDesign.MARKER_ARGB.getValue(it)) }
            val corners = mapOf(
                CardCorner.TOP_LEFT to scene.cardQuad.topLeft,
                CardCorner.TOP_RIGHT to scene.cardQuad.topRight,
                CardCorner.BOTTOM_RIGHT to scene.cardQuad.bottomRight,
                CardCorner.BOTTOM_LEFT to scene.cardQuad.bottomLeft,
            )
            for ((corner, centroid) in centroids) {
                val nearest = corners.minBy { it.value.distanceTo(centroid) }.key
                assertEquals("$name marker $corner", corner, nearest)
            }
            // Marker centroids in TL → TR → BR → BL order wind clockwise on the y-down image, like the card.
            val ordered = Quadrilateral(
                centroids.getValue(CardCorner.TOP_LEFT), centroids.getValue(CardCorner.TOP_RIGHT),
                centroids.getValue(CardCorner.BOTTOM_RIGHT), centroids.getValue(CardCorner.BOTTOM_LEFT),
            )
            assertTrue("$name marker winding", ordered.signedArea > 0)
        }
    }

    @Test
    fun `homographies reproduce the independent ground truth`() {
        val canonical = SyntheticSceneRenderer.canonicalRect(SyntheticFixtures.DESIGN.canonicalSize)
        for ((name, scene) in scenes) {
            val q = scene.cardQuad
            canonical.points.zip(q.points).forEach { (c, s) ->
                assertPoint("$name card→scene", s, scene.cardToScene.apply(c), 1e-9 * 1020)
                assertPoint("$name scene→card", c, scene.sceneToCard.apply(s), 1e-9 * 352)
            }
            // The canonical centre (126, 176) must land on the pose centre, which the pinhole model fixes analytically.
            val pose = SyntheticFixtures.POSES.getValue(name)
            assertPoint("$name centre", PixelPoint(pose.centreX, pose.centreY), scene.cardToScene.apply(PixelPoint(126.0, 176.0)), 1e-6)
        }
    }

    @Test
    fun `fixtures are deterministic`() {
        for (name in SyntheticFixtures.POSES.keys) {
            assertEquals(name, SyntheticFixtures.request(name), SyntheticFixtures.request(name))
            assertTrue(name, SyntheticFixtures.render(name).image.pixels.contentEquals(scenes.getValue(name).image.pixels))
        }
    }

    @Test
    fun `a 16 px per mm card renders with the same ground truth`() {
        val design = StandardCardDesign(pxPerMm = 16)
        val pose = SyntheticFixtures.POSES.getValue("persp-strong")
        val request = SyntheticFixtures.request("persp-strong").copy(design = design)
        val scene = SyntheticSceneRenderer.render(request).orThrow()
        assertPoint("centre", PixelPoint(pose.centreX, pose.centreY), scene.cardToScene.apply(PixelPoint(504.0, 704.0)), 1e-6)
        for ((corner, region) in design.markers) {
            val p = pose.projectCardPointMm(region.centreU / 16, region.centreV / 16)!!
            assertEquals("$corner", StandardCardDesign.MARKER_ARGB.getValue(corner), scene.image[floor(p.x).toInt(), floor(p.y).toInt()])
        }
    }

    // --- independent geometry helpers ---

    /** Smallest signed distance from [p] to the quad's edge lines; positive inside a clockwise (y-down) quad. */
    private fun minEdgeDistance(q: Quadrilateral, p: PixelPoint): Double {
        val pts = q.points
        return (0 until 4).minOf { i ->
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            ((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) / hypot(b.x - a.x, b.y - a.y)
        }
    }

    private fun markerCentroid(image: ArgbRaster, argb: Int): PixelPoint {
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (image[x, y] == argb) { sx += x + 0.5; sy += y + 0.5; n++ }
        }
        if (n == 0) fail("Marker colour ${Integer.toHexString(argb)} not found")
        return PixelPoint(sx / n, sy / n)
    }

    private fun assertPoint(message: String, expected: PixelPoint, actual: PixelPoint?, tolerance: Double) {
        if (actual == null) fail("$message: expected $expected, got null")
        assertEquals("$message x", expected.x, actual!!.x, tolerance)
        assertEquals("$message y", expected.y, actual.y, tolerance)
    }
}
