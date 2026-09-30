package com.cardpregrade.core.cv.analyzers

import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.cv.geometry.Quadrilateral
import com.cardpregrade.core.cv.image.CardImage
import com.cardpregrade.core.cv.image.CorrectedCardImage
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.CornerResult
import com.cardpregrade.core.model.DetectionMaturity
import com.cardpregrade.core.model.EdgeResult
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.NormalizedRect
import com.cardpregrade.core.model.SurfaceCheck
import com.cardpregrade.core.model.SurfaceResult

/*
 * Contracts for each stage of the computer-vision pipeline (see docs/architecture.md).
 *
 * Every analyzer exposes the [AlgorithmVersion] it stamps onto its output so historical
 * results remain reproducible. Implementations are swappable: deterministic OpenCV first,
 * learned models later, possibly running on a backend.
 */

/** Common metadata for all pipeline stages. */
interface VersionedAnalyzer {
    val version: AlgorithmVersion
}

sealed interface CardDetection {
    data class Detected(val outline: Quadrilateral, val confidence: Confidence) : CardDetection
    data class NotDetected(val reason: String) : CardDetection
}

/** Finds the card's four boundaries in a photograph. */
interface CardDetector : VersionedAnalyzer {
    fun detect(image: CardImage): CardDetection
}

/** Warps the detected outline into an upright, canonical-aspect card image. */
interface PerspectiveCorrector : VersionedAnalyzer {
    fun correct(image: CardImage, outline: Quadrilateral, outputSize: PixelSize): CorrectedCardImage
}

/** Normalizes colour/exposure so downstream thresholds behave consistently across devices. */
interface ImageNormalizer : VersionedAnalyzer {
    fun normalize(image: CorrectedCardImage): CorrectedCardImage
}

/** Decides whether a photograph is usable; see [com.cardpregrade.core.cv.quality.CaptureQualityAnalyzer] for the Phase 3 policy. */
interface ImageQualityAnalyzer : VersionedAnalyzer {
    fun analyze(image: CardImage, detection: CardDetection): ImageQualityResult
}

/** Measures border widths. Must return [CenteringResult.Unknown] instead of guessing. */
interface CenteringAnalyzer : VersionedAnalyzer {
    fun analyze(image: CorrectedCardImage, side: CardSide): CenteringResult
}

/** Crops and inspects all four corners of one side; returns one result per corner. */
interface CornerAnalyzer : VersionedAnalyzer {
    fun analyze(image: CorrectedCardImage, side: CardSide): List<CornerResult>
}

/** Segments and inspects all four edges of one side; returns one result per edge. */
interface EdgeAnalyzer : VersionedAnalyzer {
    fun analyze(image: CorrectedCardImage, side: CardSide): List<EdgeResult>
}

/** Looks for whitening/chipping inside a given region (used by corner and edge analyzers). */
interface WhiteningDetector : VersionedAnalyzer {
    fun detect(image: CorrectedCardImage, side: CardSide, region: NormalizedRect): List<CardDefect>
}

/** Images available for surface analysis of one side. Angled images are optional. */
data class SurfaceAnalysisInput(
    val side: CardSide,
    val straight: CorrectedCardImage,
    val lightFromLeft: CorrectedCardImage? = null,
    val lightFromRight: CorrectedCardImage? = null,
)

/**
 * Surface inspection. Designed for multi-image comparison (lighting angles reveal scratches
 * and dents that a single straight photo hides). [capabilities] declares, per check, what the
 * implementation actually does so the UI never implies an unchecked surface is clean.
 */
interface SurfaceAnalyzer : VersionedAnalyzer {
    val capabilities: Map<SurfaceCheck, DetectionMaturity>
    fun analyze(input: SurfaceAnalysisInput): SurfaceResult
}
