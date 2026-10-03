package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.image.GrayImage

/**
 * A simple ARGB raster, row-major (`pixels[y * width + x]`). Test-only.
 *
 * Pixel (x, y) covers the continuous square [x, x+1) × [y, y+1); see
 * [com.cardpregrade.core.cv.geometry.PixelPoint] for the coordinate convention.
 */
class ArgbRaster(val width: Int, val height: Int, val pixels: IntArray = IntArray(checkedPixelCount(width, height))) {
    init {
        val count = checkedPixelCount(width, height)
        require(pixels.size == count) { "Expected $count pixels for ${width}x$height, got ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[index(x, y)]

    operator fun set(x: Int, y: Int, argb: Int) {
        pixels[index(x, y)] = argb
    }

    /** BT.601 luminance via [GrayImage.fromArgb] (alpha ignored). */
    fun toGrayImage(): GrayImage = GrayImage.fromArgb(width, height, pixels)

    private fun index(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) throw IndexOutOfBoundsException("($x, $y) outside ${width}x$height")
        return y * width + x
    }

    companion object {
        /** Upper bound on width × height (4096²), keeping test rasters ≤ 64 MiB. */
        const val MAX_PIXELS = 16_777_216

        /** width × height, computed without Int overflow; rejects non-positive or oversized dimensions. */
        fun checkedPixelCount(width: Int, height: Int): Int {
            require(width > 0 && height > 0) { "Raster dimensions must be positive: ${width}x$height" }
            val count = width.toLong() * height.toLong()
            require(count <= MAX_PIXELS) { "Raster ${width}x$height exceeds $MAX_PIXELS pixels" }
            return count.toInt()
        }
    }
}
