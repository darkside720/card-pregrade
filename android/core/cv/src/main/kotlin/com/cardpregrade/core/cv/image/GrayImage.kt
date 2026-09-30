package com.cardpregrade.core.cv.image

import com.cardpregrade.core.cv.geometry.PixelRect

/**
 * Platform-neutral 8-bit luminance image (row-major, values 0..255). Android code converts a
 * decoded, downscaled Bitmap into this so quality measures stay pure Kotlin and JVM-testable.
 */
class GrayImage(val width: Int, val height: Int, val luma: IntArray) {
    init {
        require(width > 0 && height > 0) { "Empty image" }
        require(luma.size == width * height) { "Expected ${width * height} pixels, got ${luma.size}" }
    }

    operator fun get(x: Int, y: Int): Int = luma[y * width + x]

    /** Centered region covering [fraction] of each dimension (e.g. 0.6 → middle 60% × 60%). */
    fun centralRegion(fraction: Double): PixelRect {
        require(fraction > 0 && fraction <= 1.0)
        val w = (width * fraction).toInt().coerceAtLeast(3)
        val h = (height * fraction).toInt().coerceAtLeast(3)
        val left = (width - w) / 2
        val top = (height - h) / 2
        return PixelRect(left, top, left + w, top + h)
    }

    companion object {
        /** ITU-R BT.601 luma from packed ARGB pixels (alpha ignored). */
        fun fromArgb(width: Int, height: Int, argb: IntArray): GrayImage {
            val out = IntArray(argb.size)
            for (i in argb.indices) {
                val p = argb[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[i] = (299 * r + 587 * g + 114 * b) / 1000
            }
            return GrayImage(width, height, out)
        }
    }
}
