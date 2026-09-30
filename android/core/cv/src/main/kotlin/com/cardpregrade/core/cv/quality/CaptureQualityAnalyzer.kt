package com.cardpregrade.core.cv.quality

import com.cardpregrade.core.cv.geometry.PixelRect
import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.QualityCheck
import com.cardpregrade.core.model.QualityMetric
import com.cardpregrade.core.model.QualityVerdict
import java.util.Locale

/** Deterministic, well-known image statistics used by the capture-quality check. */
object ImageMeasures {

    /**
     * Variance of the 4-neighbour Laplacian over [region] — a standard focus measure
     * (higher = more high-frequency detail = sharper). Scale-dependent: callers must measure
     * at a fixed working resolution for results to be comparable.
     */
    fun laplacianVariance(img: GrayImage, region: PixelRect): Double {
        val x0 = maxOf(region.left, 1)
        val y0 = maxOf(region.top, 1)
        val x1 = minOf(region.right, img.width - 1)
        val y1 = minOf(region.bottom, img.height - 1)
        require(x1 > x0 && y1 > y0) { "Region too small for Laplacian" }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val lap = img[x - 1, y] + img[x + 1, y] + img[x, y - 1] + img[x, y + 1] - 4 * img[x, y]
                sum += lap
                sumSq += lap.toDouble() * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    data class ExposureStats(val meanLuma: Double, val highlightClippedFraction: Double, val shadowClippedFraction: Double)

    fun exposure(img: GrayImage, region: PixelRect, highlightLevel: Int = 250, shadowLevel: Int = 5): ExposureStats {
        var sum = 0L
        var high = 0
        var low = 0
        var n = 0
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                val v = img[x, y]
                sum += v
                if (v >= highlightLevel) high++
                if (v <= shadowLevel) low++
                n++
            }
        }
        return ExposureStats(sum.toDouble() / n, high.toDouble() / n, low.toDouble() / n)
    }
}

/**
 * Thresholds for the Phase 3 capture-quality check. **All values are provisional and
 * uncalibrated** (see docs/image-quality.md for the reasoning behind each number). Because of
 * that, only [minUsableShortSidePx] can make a photo UNUSABLE; everything else is advisory.
 */
data class CaptureQualityPolicy(
    /** Below this short side the photo cannot resolve card-edge detail at all → UNUSABLE. */
    val minUsableShortSidePx: Int,
    val retakeBelowShortSidePx: Int,
    val warnBelowShortSidePx: Int,
    /** Laplacian variance measured on a [workingLongSidePx]-wide downscale of the central region. */
    val workingLongSidePx: Int,
    val centralRegionFraction: Double,
    val sharpnessRetakeBelow: Double,
    val sharpnessWarnBelow: Double,
    val exposureRetakeBelow: Double,
    val exposureWarnBelow: Double,
    val exposureWarnAbove: Double,
    val exposureRetakeAbove: Double,
    val clippingWarnAbove: Double,
    val clippingRetakeAbove: Double,
    /** Fraction of the short side the card spans inside the portrait guide (for the px/mm estimate). */
    val guideCardWidthFraction: Double,
) {
    companion object {
        val PROVISIONAL = CaptureQualityPolicy(
            minUsableShortSidePx = 720,
            retakeBelowShortSidePx = 1500,
            warnBelowShortSidePx = 2400,
            workingLongSidePx = 1024,
            centralRegionFraction = 0.6,
            sharpnessRetakeBelow = 40.0,
            sharpnessWarnBelow = 100.0,
            exposureRetakeBelow = 40.0,
            exposureWarnBelow = 70.0,
            exposureWarnAbove = 190.0,
            exposureRetakeAbove = 220.0,
            clippingWarnAbove = 0.01,
            clippingRetakeAbove = 0.05,
            guideCardWidthFraction = 0.745,
        )
    }
}

/**
 * Phase 3 capture-quality check: resolution, sharpness, exposure and highlight clipping.
 * These describe the PHOTO, not the card. No card detection is performed, so measurements use
 * the central region of the frame where the capture guide places the card.
 */
class CaptureQualityAnalyzer(private val policy: CaptureQualityPolicy = CaptureQualityPolicy.PROVISIONAL) {

    val version = AlgorithmVersion("capture-quality", "1")

    /**
     * @param storedWidth/storedHeight full-resolution dimensions of the saved image.
     * @param working downscaled luminance image (long side ≈ [CaptureQualityPolicy.workingLongSidePx]),
     *   or null if the image could not be decoded.
     */
    fun analyze(sourceImageId: String, storedWidth: Int, storedHeight: Int, working: GrayImage?): ImageQualityResult {
        if (working == null || storedWidth <= 0 || storedHeight <= 0) {
            val check = QualityCheck(QualityMetric.DECODE, QualityVerdict.UNUSABLE, null, null, "The photo could not be decoded. Please retake.")
            return ImageQualityResult.fromChecks(sourceImageId, listOf(check), version)
        }

        val checks = mutableListOf<QualityCheck>()
        val shortSide = minOf(storedWidth, storedHeight)
        checks += resolutionCheck(shortSide)

        val region = working.centralRegion(policy.centralRegionFraction)
        val lapVar = ImageMeasures.laplacianVariance(working, region)
        checks += sharpnessCheck(lapVar)

        val exposure = ImageMeasures.exposure(working, region)
        checks += exposureCheck(exposure.meanLuma)
        checks += clippingCheck(exposure.highlightClippedFraction)

        return ImageQualityResult.fromChecks(
            sourceImageId = sourceImageId,
            checks = checks,
            algorithmVersion = version,
            blurScore = (lapVar / policy.sharpnessWarnBelow).coerceIn(0.0, 1.0),
            exposureScore = exposureScore(exposure.meanLuma),
            resolutionScore = (shortSide.toDouble() / policy.warnBelowShortSidePx).coerceIn(0.0, 1.0),
        )
    }

    fun estimatedPixelsPerMm(shortSidePx: Int): Double = shortSidePx * policy.guideCardWidthFraction / CARD_WIDTH_MM

    private fun resolutionCheck(shortSide: Int): QualityCheck {
        val pxPerMm = estimatedPixelsPerMm(shortSide)
        val detail = String.format(Locale.ROOT, "%d px short side (≈%.0f px/mm across the card)", shortSide, pxPerMm)
        val (verdict, message) = when {
            shortSide < policy.minUsableShortSidePx -> QualityVerdict.UNUSABLE to "Resolution is far too low to inspect a card: $detail."
            shortSide < policy.retakeBelowShortSidePx -> QualityVerdict.RETAKE_RECOMMENDED to "Low resolution: $detail. Retake recommended."
            shortSide < policy.warnBelowShortSidePx -> QualityVerdict.WARNING to "Moderate resolution: $detail."
            else -> QualityVerdict.GOOD to "Resolution good: $detail."
        }
        return QualityCheck(QualityMetric.RESOLUTION, verdict, shortSide.toDouble(), "px", message)
    }

    private fun sharpnessCheck(lapVar: Double): QualityCheck {
        val (verdict, message) = when {
            lapVar < policy.sharpnessRetakeBelow -> QualityVerdict.RETAKE_RECOMMENDED to "Possible blur detected. Retake recommended."
            lapVar < policy.sharpnessWarnBelow -> QualityVerdict.WARNING to "Image may be slightly soft."
            else -> QualityVerdict.GOOD to "Sharpness good."
        }
        return QualityCheck(QualityMetric.SHARPNESS, verdict, lapVar, "Laplacian variance", message)
    }

    private fun exposureCheck(mean: Double): QualityCheck {
        val (verdict, message) = when {
            mean < policy.exposureRetakeBelow -> QualityVerdict.RETAKE_RECOMMENDED to "Too dark. Add light and retake."
            mean > policy.exposureRetakeAbove -> QualityVerdict.RETAKE_RECOMMENDED to "Too bright. Reduce light or exposure and retake."
            mean < policy.exposureWarnBelow -> QualityVerdict.WARNING to "Somewhat dark."
            mean > policy.exposureWarnAbove -> QualityVerdict.WARNING to "Somewhat bright."
            else -> QualityVerdict.GOOD to "Exposure acceptable."
        }
        return QualityCheck(QualityMetric.EXPOSURE, verdict, mean, "mean luma (0–255)", message)
    }

    private fun clippingCheck(fraction: Double): QualityCheck {
        val pct = String.format(Locale.ROOT, "%.1f%%", fraction * 100)
        val (verdict, message) = when {
            fraction > policy.clippingRetakeAbove -> QualityVerdict.RETAKE_RECOMMENDED to "$pct of the center is blown out (possible glare). Retake recommended."
            fraction > policy.clippingWarnAbove -> QualityVerdict.WARNING to "$pct of the center is blown out (possible glare)."
            else -> QualityVerdict.GOOD to "No significant blown-out highlights ($pct)."
        }
        return QualityCheck(QualityMetric.HIGHLIGHT_CLIPPING, verdict, fraction, "fraction ≥250", message)
    }

    private fun exposureScore(mean: Double): Double = when {
        mean < policy.exposureWarnBelow -> mean / policy.exposureWarnBelow
        mean > policy.exposureWarnAbove -> (255 - mean) / (255 - policy.exposureWarnAbove)
        else -> 1.0
    }.coerceIn(0.0, 1.0)

    private companion object {
        const val CARD_WIDTH_MM = 63.0
    }
}
