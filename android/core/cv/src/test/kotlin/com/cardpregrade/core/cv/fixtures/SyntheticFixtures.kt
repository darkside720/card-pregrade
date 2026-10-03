package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.PixelSize

/**
 * Named, deterministic synthetic scenes for geometry tests.
 *
 * The scene is 768 × 1020, exactly a quarter of the upright 3072 × 4080 Pixel capture, with a
 * 600 px focal length and a 4 px/mm card. The poses roughly cover guided capture (card filling a
 * meaningful part of the frame, ±20–25° rotation, mild to strong tilt). They are **test
 * fixtures**, not CardDetector acceptance thresholds or capture policy.
 */
object SyntheticFixtures {
    val SCENE_SIZE = PixelSize(768, 1020)
    val DESIGN = StandardCardDesign(pxPerMm = 4)

    /** Fixture poses by name, in a stable order. */
    val POSES: Map<String, CardPose> = linkedMapOf(
        "centered" to CardPose(384.0, 510.0, widthPx = 572.0),
        "translated" to CardPose(330.0, 560.0, widthPx = 480.0),
        "scaled-small" to CardPose(384.0, 510.0, widthPx = 300.0),
        "rot-cw-20" to CardPose(384.0, 510.0, widthPx = 480.0, rotationDeg = 20.0),
        "rot-ccw-25" to CardPose(384.0, 510.0, widthPx = 480.0, rotationDeg = -25.0),
        // Tilt signs follow CardPose (positive = top/right edge recedes) and reproduce the design-review geometry.
        "persp-mild" to CardPose(384.0, 510.0, widthPx = 520.0, tiltXDeg = -10.0),
        "persp-strong" to CardPose(384.0, 510.0, widthPx = 480.0, tiltXDeg = -25.0, tiltYDeg = -15.0),
        "combined" to CardPose(400.0, 500.0, widthPx = 500.0, rotationDeg = -15.0, tiltXDeg = -12.0, tiltYDeg = 8.0),
        "near-boundary" to CardPose(242.0, 510.0, widthPx = 480.0),
    )

    fun request(name: String): SceneRequest {
        val pose = POSES[name] ?: throw IllegalArgumentException("Unknown fixture: $name")
        val quad = when (val r = pose.toQuad()) {
            is PoseResult.Projected -> r.quad
            is PoseResult.Rejected -> throw IllegalStateException("Fixture $name pose rejected: ${r.reason}")
        }
        return SceneRequest(SCENE_SIZE, quad, DESIGN)
    }

    fun render(name: String): SyntheticScene = SyntheticSceneRenderer.render(request(name)).orThrow()
}
