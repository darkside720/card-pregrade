package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.Quadrilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Expected corners come from closed-form rectangle + rotation formulas and line intersections,
 * never from Homography or from CardPose's own projection.
 */
class CardPoseTest {

    @Test
    fun `untilted pose is the centred rectangle`() {
        val q = quad(CardPose(384.0, 510.0, widthPx = 630.0))
        val halfW = 315.0
        val halfH = 315.0 * 88.0 / 63.0 // 440
        assertPoint(PixelPoint(384.0 - halfW, 510.0 - halfH), q.topLeft)
        assertPoint(PixelPoint(384.0 + halfW, 510.0 - halfH), q.topRight)
        assertPoint(PixelPoint(384.0 + halfW, 510.0 + halfH), q.bottomRight)
        assertPoint(PixelPoint(384.0 - halfW, 510.0 + halfH), q.bottomLeft)
    }

    @Test
    fun `translation moves every corner by the same offset`() {
        val base = quad(CardPose(384.0, 510.0, widthPx = 480.0))
        val moved = quad(CardPose(330.0, 560.0, widthPx = 480.0))
        base.points.zip(moved.points).forEach { (a, b) -> assertPoint(PixelPoint(a.x - 54.0, a.y + 50.0), b) }
    }

    @Test
    fun `scale shrinks the outline about the centre`() {
        val big = quad(CardPose(384.0, 510.0, widthPx = 600.0))
        val small = quad(CardPose(384.0, 510.0, widthPx = 300.0))
        big.points.zip(small.points).forEach { (a, b) -> assertPoint(PixelPoint(384.0 + (a.x - 384.0) / 2, 510.0 + (a.y - 510.0) / 2), b) }
    }

    @Test
    fun `positive rotation is clockwise on the y down image`() {
        for (deg in listOf(20.0, -25.0)) {
            val q = quad(CardPose(384.0, 510.0, widthPx = 480.0, rotationDeg = deg))
            val t = Math.toRadians(deg)
            val halfW = 240.0
            val halfH = 240.0 * 88.0 / 63.0
            val expected = listOf(-halfW to -halfH, halfW to -halfH, halfW to halfH, -halfW to halfH).map { (x, y) ->
                PixelPoint(384.0 + x * cos(t) - y * sin(t), 510.0 + x * sin(t) + y * cos(t))
            }
            expected.zip(q.points).forEach { (e, a) -> assertPoint(e, a) }
        }
        val cw = quad(CardPose(384.0, 510.0, widthPx = 480.0, rotationDeg = 20.0))
        assertTrue("clockwise: the top edge descends to the right", cw.topRight.y > cw.topLeft.y)
        val ccw = quad(CardPose(384.0, 510.0, widthPx = 480.0, rotationDeg = -25.0))
        assertTrue("counter-clockwise: the top edge rises to the right", ccw.topRight.y < ccw.topLeft.y)
    }

    @Test
    fun `positive tilts move the top and right edges away from the camera`() {
        val tiltX = quad(CardPose(384.0, 510.0, widthPx = 520.0, tiltXDeg = 10.0))
        val top = tiltX.topLeft.distanceTo(tiltX.topRight)
        val bottom = tiltX.bottomLeft.distanceTo(tiltX.bottomRight)
        assertTrue("receding top edge is shorter", top < bottom)
        // Mirror symmetric about the vertical line through the centre.
        assertEquals(384.0 - tiltX.topLeft.x, tiltX.topRight.x - 384.0, 1e-9)
        assertEquals(tiltX.topLeft.y, tiltX.topRight.y, 1e-9)

        val tiltY = quad(CardPose(384.0, 510.0, widthPx = 520.0, tiltYDeg = 10.0))
        val left = tiltY.topLeft.distanceTo(tiltY.bottomLeft)
        val right = tiltY.topRight.distanceTo(tiltY.bottomRight)
        assertTrue("receding right edge is shorter", right < left)
        assertEquals(510.0 - tiltY.topRight.y, tiltY.bottomRight.y - 510.0, 1e-9)
    }

    @Test
    fun `card centre is the diagonal intersection and projects to the pose centre`() {
        // Projective invariant: the image of the card centre is where the image diagonals cross.
        for (pose in SyntheticFixtures.POSES.values + CardPose(400.0, 500.0, 500.0, -15.0, 12.0, -8.0, focalPx = 450.0)) {
            val q = quad(pose)
            assertPoint(PixelPoint(pose.centreX, pose.centreY), diagonalIntersection(q), 1e-9)
            assertPoint(PixelPoint(pose.centreX, pose.centreY), pose.projectCardPointMm(31.5, 44.0), 1e-12)
        }
    }

    @Test
    fun `corners keep TL TR BR BL order with clockwise winding`() {
        for ((name, pose) in SyntheticFixtures.POSES) {
            val q = quad(pose)
            assertTrue("$name winding", q.signedArea > 0)
            assertTrue("$name convex", q.isConvex)
            assertTrue("$name TL above BL", q.topLeft.y < q.bottomLeft.y)
            assertTrue("$name TR above BR", q.topRight.y < q.bottomRight.y)
        }
    }

    @Test
    fun `invalid poses are rejected with a reason`() {
        assertRejected(PoseRejection.NON_FINITE, CardPose(Double.NaN, 510.0, 480.0))
        assertRejected(PoseRejection.NON_FINITE, CardPose(384.0, 510.0, 480.0, focalPx = Double.POSITIVE_INFINITY))
        assertRejected(PoseRejection.NON_FINITE, CardPose(384.0, 510.0, 480.0, tiltXDeg = Double.NaN))
        assertRejected(PoseRejection.INVALID_PARAMETER, CardPose(384.0, 510.0, 0.0))
        assertRejected(PoseRejection.INVALID_PARAMETER, CardPose(384.0, 510.0, 480.0, focalPx = -600.0))
        assertNull(CardPose(Double.NaN, 0.0, 1.0).projectCardPointMm(0.0, 0.0))
    }

    @Test
    fun `a corner at or behind the near plane is rejected`() {
        // Depth 600·63/3000 = 12.6 mm; tilting 80° brings the bottom edge to 12.6 − 44·sin 80° < 0.
        assertRejected(PoseRejection.BEHIND_CAMERA, CardPose(384.0, 510.0, widthPx = 3000.0, tiltXDeg = 80.0))
        // The same tilt at normal distance stays in front.
        quad(CardPose(384.0, 510.0, widthPx = 480.0, tiltXDeg = 80.0))
    }

    private fun quad(pose: CardPose): Quadrilateral = when (val r = pose.toQuad()) {
        is PoseResult.Projected -> r.quad
        is PoseResult.Rejected -> fail("Pose rejected: ${r.reason}") as Nothing
    }

    private fun assertRejected(reason: PoseRejection, pose: CardPose) {
        assertEquals(PoseResult.Rejected(reason), pose.toQuad())
    }

    private fun diagonalIntersection(q: Quadrilateral): PixelPoint {
        val (a, b) = q.topLeft to q.bottomRight
        val (c, d) = q.topRight to q.bottomLeft
        val denom = (a.x - b.x) * (c.y - d.y) - (a.y - b.y) * (c.x - d.x)
        val t = ((a.x - c.x) * (c.y - d.y) - (a.y - c.y) * (c.x - d.x)) / denom
        return PixelPoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    }

    private fun assertPoint(expected: PixelPoint, actual: PixelPoint?, tolerance: Double = 1e-9) {
        if (actual == null) fail("Expected $expected, got null")
        assertEquals("x", expected.x, actual!!.x, tolerance)
        assertEquals("y", expected.y, actual.y, tolerance)
    }
}
