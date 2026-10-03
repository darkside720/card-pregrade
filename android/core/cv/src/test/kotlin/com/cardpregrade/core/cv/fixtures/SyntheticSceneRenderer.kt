package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.Homography
import com.cardpregrade.core.cv.geometry.HomographyResult
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.cv.geometry.QuadRejection
import com.cardpregrade.core.cv.geometry.QuadValidation
import com.cardpregrade.core.cv.geometry.QuadValidationPolicy
import com.cardpregrade.core.cv.geometry.Quadrilateral
import kotlin.math.abs

/**
 * What to render: a card [design] whose outline in a [sceneSize] scene is exactly [cardQuad]
 * (TL/TR/BR/BL, continuous pixel coordinates). The card may extend past the scene edges.
 */
data class SceneRequest(
    val sceneSize: PixelSize,
    val cardQuad: Quadrilateral,
    val design: CardPattern,
    val backgroundArgb: Int = DEFAULT_BACKGROUND_ARGB,
) {
    companion object {
        /** Dark grey; not in [StandardCardDesign.PALETTE]. */
        const val DEFAULT_BACKGROUND_ARGB = 0xFF202020.toInt()
    }
}

/**
 * A rendered scene with exact ground truth. Only [SyntheticSceneRenderer] creates it, from one
 * quad in one place, so the fields cannot disagree.
 *
 * - [cardQuad] is the independent input outline (never derived from a homography).
 * - [cardToScene] maps canonical card pixels ([CardPattern.canonicalSize]) to scene pixels:
 *   the card's corners (0, 0), (W, 0), (W, H), (0, H) go to TL, TR, BR, BL of [cardQuad]. This is
 *   what an ideal perspective corrector would use to sample the scene for each output pixel.
 * - [sceneToCard] is its inverse, the exact matrix used for rasterization.
 * - The scene size is [image]'s size.
 */
class SyntheticScene internal constructor(
    val image: ArgbRaster,
    val design: CardPattern,
    val cardQuad: Quadrilateral,
    val cardToScene: Homography,
    val sceneToCard: Homography,
    val backgroundArgb: Int,
)

sealed interface SceneResult {
    data class Rendered(val scene: SyntheticScene) : SceneResult

    data class Rejected(
        val reason: SceneRejection,
        val detail: String,
        val quadRejections: Set<QuadRejection> = emptySet(),
    ) : SceneResult

    fun orThrow(): SyntheticScene = when (this) {
        is Rendered -> scene
        is Rejected -> throw IllegalStateException("Scene rejected: $reason ($detail)")
    }
}

enum class SceneRejection {
    /** Scene larger than [ArgbRaster.MAX_PIXELS]. */
    INVALID_SCENE_SIZE,

    /** [SceneRequest.cardQuad] fails structural validation; see [SceneResult.Rejected.quadRejections]. */
    INVALID_QUAD,

    /** No homography from the canonical card to the quad (structurally valid but ill-conditioned). */
    HOMOGRAPHY_FAILED,

    /**
     * The card touches or crosses the projective vanishing line: a corner w of the card-to-scene
     * transform is zero, non-finite, or of the opposite sign to the others.
     */
    NOT_IN_FRONT,

    /**
     * All corner w values share a sign (the card is in front), but min|w| < 1e-6 · max|w|: the
     * map is too ill-conditioned to rasterize reliably. A numerical safety limit only, not a
     * detector, capture-quality or user-facing perspective threshold.
     */
    ILL_CONDITIONED_PROJECTIVE_MAP,

    /** The card covers no scene pixel centre. */
    OUTSIDE_SCENE,
}

/**
 * Deterministic point-sampling renderer for synthetic card scenes (test infrastructure only).
 *
 * Each scene pixel (x, y) is sampled once at its centre (x + 0.5, y + 0.5), mapped to canonical
 * card coordinates with `sceneToCard`, and coloured by [CardPattern.argbAt] when it lands in
 * the half-open card [0, W) × [0, H); otherwise it gets the background. No splatting, filtering
 * or anti-aliasing.
 */
object SyntheticSceneRenderer {

    /**
     * Minimum |w| at a card corner relative to the largest corner |w|. Numerical safety only: for
     * a pinhole pose the ratio is the nearest/farthest corner depth (guided fixtures ≈ 0.5–1.0,
     * even edge-on poses ≈ 0.1), so only pathological projective maps reach it.
     */
    private const val IN_FRONT_RATIO = 1e-6

    fun render(request: SceneRequest): SceneResult {
        val size = request.sceneSize
        if (size.width.toLong() * size.height > ArgbRaster.MAX_PIXELS) {
            return SceneResult.Rejected(SceneRejection.INVALID_SCENE_SIZE, "${size.width}x${size.height}")
        }
        val quad = request.cardQuad
        val validation = QuadValidation.validate(quad, size, QuadValidationPolicy.STRUCTURAL_ONLY)
        if (!validation.isValid) {
            return SceneResult.Rejected(SceneRejection.INVALID_QUAD, validation.rejections.toString(), validation.rejections)
        }
        if (!overlapsScene(quad, size)) return SceneResult.Rejected(SceneRejection.OUTSIDE_SCENE, "no overlap with the scene")

        val canonical = request.design.canonicalSize
        val cardToScene = when (val r = Homography.fromFourPoints(canonicalRect(canonical), quad)) {
            is HomographyResult.Solved -> r.homography
            is HomographyResult.Failed -> return SceneResult.Rejected(SceneRejection.HOMOGRAPHY_FAILED, "${r.reason} ${r.detail}")
        }
        val sceneToCard = when (val r = cardToScene.inverse()) {
            is HomographyResult.Solved -> r.homography
            is HomographyResult.Failed -> return SceneResult.Rejected(SceneRejection.HOMOGRAPHY_FAILED, "inverse: ${r.reason}")
        }
        checkInFront(cardToScene, canonical)?.let { return it }

        val image = ArgbRaster(size.width, size.height)
        val cardW = canonical.width.toDouble()
        val cardH = canonical.height.toDouble()
        var cardPixels = 0
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                val card = sceneToCard.apply(PixelPoint(x + 0.5, y + 0.5))
                image[x, y] = if (card != null && card.x >= 0.0 && card.x < cardW && card.y >= 0.0 && card.y < cardH) {
                    cardPixels++
                    request.design.argbAt(card.x, card.y)
                } else {
                    request.backgroundArgb
                }
            }
        }
        if (cardPixels == 0) return SceneResult.Rejected(SceneRejection.OUTSIDE_SCENE, "no pixel centre inside the card")
        return SceneResult.Rendered(SyntheticScene(image, request.design, quad, cardToScene, sceneToCard, request.backgroundArgb))
    }

    /** The upright card image itself: each pixel sampled at its centre, no transform involved. */
    fun renderCanonical(design: CardPattern): ArgbRaster {
        val size = design.canonicalSize
        val image = ArgbRaster(size.width, size.height)
        for (y in 0 until size.height) for (x in 0 until size.width) image[x, y] = design.argbAt(x + 0.5, y + 0.5)
        return image
    }

    /** Outline of a whole card of [size] in canonical coordinates. */
    fun canonicalRect(size: PixelSize): Quadrilateral {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        return Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(w, 0.0), PixelPoint(w, h), PixelPoint(0.0, h))
    }

    /**
     * Vanishing-line guard. With w(u, v) = h20·u + h21·v + h22 of [cardToScene] at the four
     * canonical corners:
     * - any non-finite or zero w, or mixed signs → [SceneRejection.NOT_IN_FRONT] (since w is
     *   affine, a shared strict sign puts the whole card on one side of the vanishing line);
     * - a shared sign but min|w| < 1e-6 · max|w| → [SceneRejection.ILL_CONDITIONED_PROJECTIVE_MAP].
     * Null when it passes.
     */
    internal fun checkInFront(cardToScene: Homography, canonical: PixelSize): SceneResult.Rejected? {
        val w = canonicalRect(canonical).points.map { cardToScene[2, 0] * it.x + cardToScene[2, 1] * it.y + cardToScene[2, 2] }
        val sameSign = w.all { it > 0.0 } || w.all { it < 0.0 }
        val magnitudes = w.map { abs(it) }
        if (!sameSign || !magnitudes.all { it.isFinite() }) {
            return SceneResult.Rejected(SceneRejection.NOT_IN_FRONT, "corner w = $w")
        }
        if (magnitudes.min() < IN_FRONT_RATIO * magnitudes.max()) {
            return SceneResult.Rejected(SceneRejection.ILL_CONDITIONED_PROJECTIVE_MAP, "corner w = $w")
        }
        return null
    }

    /**
     * True if the convex, clockwise (y-down) quad and the scene rectangle [0, W] × [0, H] share
     * interior area (separating-axis test; touching counts as no overlap).
     */
    private fun overlapsScene(quad: Quadrilateral, size: PixelSize): Boolean {
        val p = quad.points
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        if (p.maxOf { it.x } <= 0.0 || p.minOf { it.x } >= w || p.maxOf { it.y } <= 0.0 || p.minOf { it.y } >= h) return false
        val rect = listOf(PixelPoint(0.0, 0.0), PixelPoint(w, 0.0), PixelPoint(w, h), PixelPoint(0.0, h))
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            // Interior of a clockwise (positive signed area, y-down) outline has a positive cross product.
            if (rect.all { (b.x - a.x) * (it.y - a.y) - (b.y - a.y) * (it.x - a.x) <= 0.0 }) return false
        }
        return true
    }
}
