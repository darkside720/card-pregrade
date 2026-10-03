package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.CardGeometry
import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.model.CardCorner
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A procedural card face, evaluated at continuous canonical-card coordinates (u, v) in pixels of
 * an upright card image of [canonicalSize]: the card spans [0, width) × [0, height), with the
 * [com.cardpregrade.core.cv.geometry.PixelPoint] convention (pixel (i, j) has centre (i+0.5, j+0.5)).
 *
 * The size is part of the pattern (not a fun interface) because the renderer needs the card's
 * canonical bounds to build its ground-truth homography.
 */
interface CardPattern {
    val canonicalSize: PixelSize

    /** Opaque ARGB at (u, v); callers only ask for points inside [0, width) × [0, height). */
    fun argbAt(u: Double, v: Double): Int
}

/** Half-open rectangle [left, right) × [top, bottom) in canonical card pixels. */
data class CardRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val centreU: Double get() = (left + right) / 2
    val centreV: Double get() = (top + bottom) / 2

    fun contains(u: Double, v: Double): Boolean = u >= left && u < right && v >= top && v < bottom
}

/** Printed-frame margins between the card edge and the art box, in millimetres. */
data class MarginsMm(val left: Double, val right: Double, val top: Double, val bottom: Double)

/**
 * Entirely synthetic, copyright-free standard-size card (63 × 88 mm from [CardGeometry]) drawn at
 * an integer [pxPerMm], so the canonical image is exactly 63k × 88k pixels.
 *
 * Layers, back to front:
 * - card base in [BORDER_ARGB] over the whole card;
 * - an art box inset by deliberately unequal margins (L 3.50, R 2.75, T 4.00, B 3.25 mm), so any
 *   rotation or mirror changes the measured margins;
 * - a two-tone 4 mm checkerboard filling the art box, anchored at its top-left;
 * - four 6 × 6 mm orientation markers in the art box's corners, with pairwise-distinct colours
 *   and BT.601 lumas, so orientation and handedness survive conversion to grey.
 *
 * Every boundary is a whole number of quarter-millimetres, so pixel boundaries are computed
 * exactly (`quarters * k / 4`). At k = 4 and 16 they fall on pixel corners; at k = 10 the 2.75 and
 * 3.25 mm margins fall on pixel centres and the half-open rule assigns that column/row to the
 * border. The card is geometrically rectangular (no rounded corners).
 */
class StandardCardDesign(val pxPerMm: Int) : CardPattern {
    init {
        require(pxPerMm in 1..32) { "pxPerMm must be in 1..32: $pxPerMm" }
    }

    override val canonicalSize: PixelSize = run {
        val width = (CardGeometry.STANDARD_WIDTH_MM * pxPerMm).roundToInt()
        val size = CardGeometry.correctedSize(width)
        check(size.height.toDouble() == CardGeometry.STANDARD_HEIGHT_MM * pxPerMm) { "Non-uniform px/mm for $pxPerMm" }
        size
    }

    val margins: MarginsMm = MarginsMm(
        left = MARGIN_LEFT_Q / 4.0,
        right = MARGIN_RIGHT_Q / 4.0,
        top = MARGIN_TOP_Q / 4.0,
        bottom = MARGIN_BOTTOM_Q / 4.0,
    )

    private val cardWidthQ = (CardGeometry.STANDARD_WIDTH_MM * 4).roundToInt()
    private val cardHeightQ = (CardGeometry.STANDARD_HEIGHT_MM * 4).roundToInt()

    val artBox: CardRegion = CardRegion(
        left = px(MARGIN_LEFT_Q),
        top = px(MARGIN_TOP_Q),
        right = px(cardWidthQ - MARGIN_RIGHT_Q),
        bottom = px(cardHeightQ - MARGIN_BOTTOM_Q),
    )

    /** Orientation marker squares, keyed by the card corner they sit in. */
    val markers: Map<CardCorner, CardRegion> = run {
        val size = px(MARKER_Q)
        val a = artBox
        mapOf(
            CardCorner.TOP_LEFT to CardRegion(a.left, a.top, a.left + size, a.top + size),
            CardCorner.TOP_RIGHT to CardRegion(a.right - size, a.top, a.right, a.top + size),
            CardCorner.BOTTOM_RIGHT to CardRegion(a.right - size, a.bottom - size, a.right, a.bottom),
            CardCorner.BOTTOM_LEFT to CardRegion(a.left, a.bottom - size, a.left + size, a.bottom),
        )
    }

    private val markerList = markers.entries.map { it.value to MARKER_ARGB.getValue(it.key) }
    private val cellPx = px(CHECKER_CELL_Q)

    override fun argbAt(u: Double, v: Double): Int {
        require(u >= 0.0 && u < canonicalSize.width && v >= 0.0 && v < canonicalSize.height) { "($u, $v) is outside the card" }
        for ((region, argb) in markerList) if (region.contains(u, v)) return argb
        if (!artBox.contains(u, v)) return BORDER_ARGB
        val cell = floor((u - artBox.left) / cellPx).toInt() + floor((v - artBox.top) / cellPx).toInt()
        return if (cell % 2 == 0) ART_LIGHT_ARGB else ART_DARK_ARGB
    }

    /** Quarter-millimetres to canonical pixels; exact in Double for the supported range. */
    private fun px(quarters: Int): Double = quarters * pxPerMm / 4.0

    companion object {
        private const val MARGIN_LEFT_Q = 14 // 3.50 mm
        private const val MARGIN_RIGHT_Q = 11 // 2.75 mm
        private const val MARGIN_TOP_Q = 16 // 4.00 mm
        private const val MARGIN_BOTTOM_Q = 13 // 3.25 mm
        private const val MARKER_Q = 24 // 6 mm
        private const val CHECKER_CELL_Q = 16 // 4 mm

        const val BORDER_ARGB = 0xFFF2D14A.toInt()
        const val ART_LIGHT_ARGB = 0xFF5A8FD0.toInt()
        const val ART_DARK_ARGB = 0xFF3A6FB0.toInt()

        /** BT.601 lumas ≈ 89 (TL), 144 (TR), 14 (BR), 240 (BL). */
        val MARKER_ARGB: Map<CardCorner, Int> = mapOf(
            CardCorner.TOP_LEFT to 0xFFE02020.toInt(),
            CardCorner.TOP_RIGHT to 0xFF20E020.toInt(),
            CardCorner.BOTTOM_RIGHT to 0xFF000080.toInt(),
            CardCorner.BOTTOM_LEFT to 0xFFF0F0F0.toInt(),
        )

        /** Every colour [StandardCardDesign.argbAt] can return. */
        val PALETTE: Set<Int> = setOf(BORDER_ARGB, ART_LIGHT_ARGB, ART_DARK_ARGB) + MARKER_ARGB.values
    }
}
