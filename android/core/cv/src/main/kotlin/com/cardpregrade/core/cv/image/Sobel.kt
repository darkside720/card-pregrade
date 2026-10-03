package com.cardpregrade.core.cv.image

/**
 * 3 × 3 Sobel derivatives of a [GrayImage], evaluated only where the whole kernel lies inside
 * the image (no clamping, mirroring or zero padding).
 *
 * Kernels, with rows top to bottom (y − 1, y, y + 1) and columns left to right (x − 1, x, x + 1):
 *
 * ```
 * dx:  -1  0 +1        dy:  -1 -2 -1
 *      -2  0 +2              0  0  0
 *      -1  0 +1             +1 +2 +1
 * ```
 *
 * - **Sign:** dx > 0 where intensity increases towards +x (right); dy > 0 where intensity
 *   increases towards +y, which is **down** in image coordinates.
 * - **Scale:** on a linear ramp of slope s grey levels per pixel the response is 8·s. For luma in
 *   0..255 every response lies in [−1020, 1020]; values outside the [GrayImage] contract are not
 *   checked.
 * - **Domain:** centres (x, y) with 1 ≤ x ≤ width − 2 and 1 ≤ y ≤ height − 2. An image narrower or
 *   shorter than 3 pixels has no valid centre. Anything outside the domain throws
 *   [IllegalArgumentException].
 * - **Coordinates:** the value for pixel (x, y) estimates the gradient at the pixel centre, the
 *   continuous point (x + 0.5, y + 0.5) in the [com.cardpregrade.core.cv.geometry.PixelPoint]
 *   convention.
 *
 * Integer arithmetic only; nothing is allocated per pixel (profiles allocate one array).
 */
object Sobel {

    /** Horizontal derivative at pixel (x, y). */
    fun dx(image: GrayImage, x: Int, y: Int): Int {
        requireCentre(image, x, y)
        return dxUnchecked(image, x, y)
    }

    /** Vertical derivative at pixel (x, y); positive when intensity increases downward. */
    fun dy(image: GrayImage, x: Int, y: Int): Int {
        requireCentre(image, x, y)
        return dyUnchecked(image, x, y)
    }

    /**
     * [dx] for every centre x in the half-open range [fromX, untilX) on row [y]; element i is
     * the response at pixel (fromX + i, y). Requires 1 ≤ y ≤ height − 2 and
     * 1 ≤ fromX ≤ untilX ≤ width − 1; an empty range (fromX == untilX) returns an empty array.
     * Ranges are never truncated.
     */
    fun rowDx(image: GrayImage, y: Int, fromX: Int, untilX: Int): IntArray {
        require(y >= 1 && y <= image.height - 2) { "Row $y has no valid Sobel centres (height ${image.height})" }
        require(fromX >= 1 && fromX <= untilX && untilX <= image.width - 1) {
            "Invalid x range [$fromX, $untilX) for width ${image.width}: need 1 ≤ fromX ≤ untilX ≤ ${image.width - 1}"
        }
        return IntArray(untilX - fromX) { dxUnchecked(image, fromX + it, y) }
    }

    /**
     * [dy] for every centre y in the half-open range [fromY, untilY) on column [x]; element i is
     * the response at pixel (x, fromY + i). Requires 1 ≤ x ≤ width − 2 and
     * 1 ≤ fromY ≤ untilY ≤ height − 1; an empty range (fromY == untilY) returns an empty array.
     * Ranges are never truncated.
     */
    fun columnDy(image: GrayImage, x: Int, fromY: Int, untilY: Int): IntArray {
        require(x >= 1 && x <= image.width - 2) { "Column $x has no valid Sobel centres (width ${image.width})" }
        require(fromY >= 1 && fromY <= untilY && untilY <= image.height - 1) {
            "Invalid y range [$fromY, $untilY) for height ${image.height}: need 1 ≤ fromY ≤ untilY ≤ ${image.height - 1}"
        }
        return IntArray(untilY - fromY) { dyUnchecked(image, x, fromY + it) }
    }

    private fun requireCentre(image: GrayImage, x: Int, y: Int) {
        require(x >= 1 && x <= image.width - 2 && y >= 1 && y <= image.height - 2) {
            "($x, $y) is not a valid Sobel centre in a ${image.width}x${image.height} image"
        }
    }

    private fun dxUnchecked(image: GrayImage, x: Int, y: Int): Int {
        val p = image.luma
        val w = image.width
        val above = (y - 1) * w
        val row = y * w
        val below = (y + 1) * w
        return (p[above + x + 1] + 2 * p[row + x + 1] + p[below + x + 1]) -
            (p[above + x - 1] + 2 * p[row + x - 1] + p[below + x - 1])
    }

    private fun dyUnchecked(image: GrayImage, x: Int, y: Int): Int {
        val p = image.luma
        val w = image.width
        val above = (y - 1) * w
        val below = (y + 1) * w
        return (p[below + x - 1] + 2 * p[below + x] + p[below + x + 1]) -
            (p[above + x - 1] + 2 * p[above + x] + p[above + x + 1])
    }
}
