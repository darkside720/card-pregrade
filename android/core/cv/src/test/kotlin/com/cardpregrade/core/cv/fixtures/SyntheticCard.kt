package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.PixelRect

/**
 * Generates synthetic, copyright-free card rasters with exactly known geometry.
 *
 * The spec is the ground truth: a detector/centering analyzer run on [render] output must
 * recover [SyntheticCardSpec.cardRect] and [SyntheticCardSpec.artRect]. See
 * test-data/README.md for the fixture strategy (Python reference generates the same specs).
 */
data class SyntheticCardSpec(
    val canvasWidth: Int,
    val canvasHeight: Int,
    /** Card outline on the canvas (background around it). */
    val cardRect: PixelRect,
    /** Inner printed art box; the gap to [cardRect] is the border used for centering. */
    val artRect: PixelRect,
    val backgroundArgb: Int = 0xFF202020.toInt(),
    val borderArgb: Int = 0xFFF2D14A.toInt(),
    val artArgb: Int = 0xFF3A6FB0.toInt(),
) {
    init {
        require(cardRect.left >= 0 && cardRect.top >= 0 && cardRect.right <= canvasWidth && cardRect.bottom <= canvasHeight)
        require(artRect.left > cardRect.left && artRect.top > cardRect.top)
        require(artRect.right < cardRect.right && artRect.bottom < cardRect.bottom)
    }

    val leftBorder: Int get() = artRect.left - cardRect.left
    val rightBorder: Int get() = cardRect.right - artRect.right
    val topBorder: Int get() = artRect.top - cardRect.top
    val bottomBorder: Int get() = cardRect.bottom - artRect.bottom
}

object SyntheticCard {
    fun render(spec: SyntheticCardSpec): ArgbRaster {
        val raster = ArgbRaster(spec.canvasWidth, spec.canvasHeight)
        raster.pixels.fill(spec.backgroundArgb)
        fill(raster, spec.cardRect, spec.borderArgb)
        fill(raster, spec.artRect, spec.artArgb)
        return raster
    }

    private fun fill(raster: ArgbRaster, rect: PixelRect, argb: Int) {
        for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) raster[x, y] = argb
    }
}
