package com.cardpregrade.core.cv.image

/**
 * Platform-neutral handle to an image flowing through the pipeline.
 *
 * Implementations will wrap an OpenCV `Mat` or an Android `Bitmap` (added with the OpenCV
 * implementation module in Phase 4). Keeping this module free of both lets interfaces and
 * geometry be unit-tested on the JVM and reused by a future backend port.
 */
interface CardImage {
    /** Id of the [com.cardpregrade.core.model.CapturedImage] this image derives from. */
    val sourceImageId: String
    val widthPx: Int
    val heightPx: Int
}

/** A card image that has been perspective-corrected to a canonical, upright rectangle. */
interface CorrectedCardImage : CardImage {
    /** Pixels per millimetre after correction, if the physical card size is assumed known. */
    val pixelsPerMm: Double?
}
