package com.cardpregrade.core.model

/**
 * Orientation of stored pixels, derived from the EXIF `Orientation` tag.
 *
 * Policy (ADR 0011): captured JPEG bytes are kept exactly as the camera wrote them (no
 * re-encode), and the EXIF orientation is recorded. Every consumer that decodes pixels
 * must apply [rotationDegrees] / [mirrored] — use the app's oriented loader, never a raw decode.
 */
data class ImageOrientation(
    /** Clockwise rotation to apply to stored pixels to make them upright: 0, 90, 180 or 270. */
    val rotationDegrees: Int,
    /** True for the mirrored EXIF variants (2, 4, 5, 7) — not produced by rear-camera captures. */
    val mirrored: Boolean,
    /** Raw EXIF value, or null if the file had no orientation tag. */
    val exifValue: Int?,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "Unsupported rotation $rotationDegrees" }
    }

    val swapsDimensions: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270

    /** Upright (display) width/height for stored pixel dimensions. */
    fun orientedSize(storedWidth: Int, storedHeight: Int): Pair<Int, Int> =
        if (swapsDimensions) storedHeight to storedWidth else storedWidth to storedHeight

    companion object {
        val UPRIGHT = ImageOrientation(0, mirrored = false, exifValue = null)

        /** Maps EXIF orientation 1..8 (ExifInterface.ORIENTATION_*). Unknown/0 means upright. */
        fun fromExif(value: Int?): ImageOrientation = when (value) {
            2 -> ImageOrientation(0, mirrored = true, exifValue = value)
            3 -> ImageOrientation(180, mirrored = false, exifValue = value)
            4 -> ImageOrientation(180, mirrored = true, exifValue = value)
            5 -> ImageOrientation(90, mirrored = true, exifValue = value)
            6 -> ImageOrientation(90, mirrored = false, exifValue = value)
            7 -> ImageOrientation(270, mirrored = true, exifValue = value)
            8 -> ImageOrientation(270, mirrored = false, exifValue = value)
            else -> ImageOrientation(0, mirrored = false, exifValue = value)
        }
    }
}
