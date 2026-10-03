package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.CardGeometry
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.Quadrilateral

/**
 * A standard-size card placed in front of an ideal pinhole camera, producing a ground-truth
 * outline analytically (no [com.cardpregrade.core.cv.geometry.Homography] involved).
 *
 * Frames: the camera has +x to the right, +y down and +z forward (depth), right-handed. Card
 * points (x, y) are in millimetres from the card's top-left corner; the centred card point
 * p_card = (x − 31.5, y − 44, 0) has +X towards the right edge and +Y towards the bottom edge.
 *
 * With standard right-handed rotation matrices acting on column vectors:
 *
 * ```
 * p_cam = Rz(rotation) · Rx(−tiltX) · Ry(−tiltY) · p_card + (0, 0, Z0),   Z0 = focalPx · 63 / widthPx
 * u = centreX + focalPx · p_cam.x / p_cam.z,   v = centreY + focalPx · p_cam.y / p_cam.z
 * ```
 *
 * The product applies right to left: Ry first, then Rx, then Rz, about the fixed camera axes.
 * Read as rotations about the card's own axes, the same product is: (1) in-plane rotation,
 * (2) tilt about the card's horizontal axis, (3) tilt about the card's vertical axis.
 *
 * - [widthPx]: projected card width when facing the camera squarely (scale); tilt then changes
 *   the apparent width naturally, with no correction.
 * - [rotationDeg]: in-plane rotation; **positive appears clockwise** because image y increases
 *   downward.
 * - [tiltXDeg]: positive makes the card's TOP edge recede from the camera (top edge appears shorter).
 * - [tiltYDeg]: positive makes the card's RIGHT edge recede (right edge appears shorter).
 * - [centreX], [centreY] are also the pinhole camera's principal point in this synthetic model,
 *   so the card centre always projects there and changing them is intentionally a pure
 *   image-plane translation. The model does not attempt to represent a real camera whose
 *   principal point sits elsewhere relative to an off-axis card.
 *
 * Trigonometry uses [StrictMath] so corners are bit-reproducible across JVMs. These are fixture
 * parameters, not detector acceptance thresholds.
 */
data class CardPose(
    val centreX: Double,
    val centreY: Double,
    val widthPx: Double,
    val rotationDeg: Double = 0.0,
    val tiltXDeg: Double = 0.0,
    val tiltYDeg: Double = 0.0,
    val focalPx: Double = DEFAULT_FOCAL_PX,
) {
    /** Projects the card's four corners as TL, TR, BR, BL, or explains why it cannot. */
    fun toQuad(): PoseResult {
        invalidReason()?.let { return PoseResult.Rejected(it) }
        val w = CardGeometry.STANDARD_WIDTH_MM
        val h = CardGeometry.STANDARD_HEIGHT_MM
        val corners = listOf(0.0 to 0.0, w to 0.0, w to h, 0.0 to h).map { (x, y) -> project(x, y) }
        if (corners.any { it == null }) return PoseResult.Rejected(PoseRejection.BEHIND_CAMERA)
        val p = corners.map { it!! }
        return PoseResult.Projected(Quadrilateral(p[0], p[1], p[2], p[3]))
    }

    /**
     * Projects a card point given in millimetres from the card's top-left corner. Null when the
     * pose is invalid or the point is at or behind the near plane ([NEAR_PLANE_MM]).
     */
    fun projectCardPointMm(xMm: Double, yMm: Double): PixelPoint? {
        if (invalidReason() != null) return null
        return project(xMm, yMm)
    }

    private fun invalidReason(): PoseRejection? = when {
        listOf(centreX, centreY, widthPx, rotationDeg, tiltXDeg, tiltYDeg, focalPx).any { !it.isFinite() } -> PoseRejection.NON_FINITE
        widthPx <= 0.0 || focalPx <= 0.0 -> PoseRejection.INVALID_PARAMETER
        else -> null
    }

    private fun project(xMm: Double, yMm: Double): PixelPoint? {
        val bigX = xMm - CardGeometry.STANDARD_WIDTH_MM / 2
        val bigY = yMm - CardGeometry.STANDARD_HEIGHT_MM / 2
        // Ry(−tiltY): right edge (X > 0) recedes for positive tilt.
        val sinB = StrictMath.sin(StrictMath.toRadians(tiltYDeg))
        val cosB = StrictMath.cos(StrictMath.toRadians(tiltYDeg))
        val x1 = bigX * cosB
        val y1 = bigY
        val z1 = bigX * sinB
        // Rx(−tiltX): top edge (Y < 0) recedes for positive tilt.
        val sinA = StrictMath.sin(StrictMath.toRadians(tiltXDeg))
        val cosA = StrictMath.cos(StrictMath.toRadians(tiltXDeg))
        val y2 = y1 * cosA + z1 * sinA
        val z2 = -y1 * sinA + z1 * cosA
        // Rz(rotation): positive is clockwise on the y-down image plane.
        val sinT = StrictMath.sin(StrictMath.toRadians(rotationDeg))
        val cosT = StrictMath.cos(StrictMath.toRadians(rotationDeg))
        val x3 = x1 * cosT - y2 * sinT
        val y3 = x1 * sinT + y2 * cosT
        val z = z2 + focalPx * CardGeometry.STANDARD_WIDTH_MM / widthPx
        if (!(z > NEAR_PLANE_MM)) return null
        val u = centreX + focalPx * x3 / z
        val v = centreY + focalPx * y3 / z
        return if (u.isFinite() && v.isFinite()) PixelPoint(u, v) else null
    }

    companion object {
        /** Focal length for the 768 × 1020 fixture scene. */
        const val DEFAULT_FOCAL_PX = 600.0

        /** Camera-space depths at or below this (mm) are treated as at/behind the camera. */
        const val NEAR_PLANE_MM = 1.0
    }
}

sealed interface PoseResult {
    data class Projected(val quad: Quadrilateral) : PoseResult
    data class Rejected(val reason: PoseRejection) : PoseResult
}

enum class PoseRejection {
    /** A pose parameter is NaN or infinite. */
    NON_FINITE,

    /** Non-positive width or focal length. */
    INVALID_PARAMETER,

    /** A card corner is at or behind the camera's near plane. */
    BEHIND_CAMERA,
}
