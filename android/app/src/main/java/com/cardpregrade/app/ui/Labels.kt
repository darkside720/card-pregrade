package com.cardpregrade.app.ui

import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.CardGame
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.ConfidenceLevel
import com.cardpregrade.core.model.DefectCategory
import com.cardpregrade.core.model.DefectSeverity
import com.cardpregrade.core.model.DetectionMaturity
import com.cardpregrade.core.model.ScanStatus
import com.cardpregrade.core.model.SubgradeCategory
import com.cardpregrade.core.model.SurfaceCheck

/* User-facing wording for domain enums. Kept in one place so language stays consistently hedged. */

fun CardGame.label(): String = when (this) {
    CardGame.POKEMON -> "Pokémon"
    CardGame.ONE_PIECE -> "One Piece TCG"
    CardGame.UNKNOWN -> "Unknown / other"
}

fun CardSide.label(): String = when (this) {
    CardSide.FRONT -> "Front"
    CardSide.BACK -> "Back"
}

fun DefectSeverity.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

fun DefectCategory.label(): String = when (this) {
    DefectCategory.WHITENING -> "Whitening"
    DefectCategory.EDGE_CHIP -> "Edge chip"
    DefectCategory.CORNER_DAMAGE -> "Corner damage"
    DefectCategory.IRREGULAR_CUT -> "Irregular cut"
    DefectCategory.SCRATCH -> "Scratch"
    DefectCategory.PRINT_LINE -> "Print line"
    DefectCategory.DENT -> "Dent"
    DefectCategory.STAIN -> "Stain"
    DefectCategory.CENTERING -> "Centering"
    DefectCategory.UNKNOWN -> "Unclassified"
}

fun DetectionMaturity.label(): String = when (this) {
    DetectionMaturity.SUPPORTED -> "Supported"
    DetectionMaturity.EXPERIMENTAL -> "Experimental"
    DetectionMaturity.NOT_IMPLEMENTED -> "Not checked"
}

fun SurfaceCheck.label(): String = when (this) {
    SurfaceCheck.SCRATCH -> "Scratches"
    SurfaceCheck.HOLO_SCRATCH -> "Holo scratches"
    SurfaceCheck.PRINT_LINE -> "Print lines"
    SurfaceCheck.DENT -> "Dents"
    SurfaceCheck.INDENTATION -> "Indentations"
    SurfaceCheck.ROLLER_MARK -> "Roller marks"
    SurfaceCheck.STAIN -> "Stains"
    SurfaceCheck.FACTORY_DEFECT -> "Factory defects"
}

fun SubgradeCategory.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

fun ScanStatus.label(): String = when (this) {
    ScanStatus.CREATED -> "Created"
    ScanStatus.CAPTURING -> "Capture in progress"
    ScanStatus.CAPTURED -> "Captured"
    ScanStatus.ANALYZING -> "Analyzing"
    ScanStatus.ANALYZED -> "Analyzed"
    ScanStatus.FAILED -> "Failed"
}

fun Confidence.label(): String {
    val level = when (level) {
        ConfidenceLevel.HIGH -> "high"
        ConfidenceLevel.MEDIUM -> "medium"
        ConfidenceLevel.LOW -> "low"
    }
    return "$percent% ($level)"
}

/** e.g. "Back · right edge, upper section". */
fun CardDefect.locationLabel(): String = "${side.label()} · ${location.describe()}"

fun com.cardpregrade.core.model.QualityVerdict.label(): String = when (this) {
    com.cardpregrade.core.model.QualityVerdict.GOOD -> "Good"
    com.cardpregrade.core.model.QualityVerdict.WARNING -> "Warning"
    com.cardpregrade.core.model.QualityVerdict.RETAKE_RECOMMENDED -> "Retake recommended"
    com.cardpregrade.core.model.QualityVerdict.UNUSABLE -> "Unusable"
}

fun com.cardpregrade.core.model.QualityMetric.label(): String = when (this) {
    com.cardpregrade.core.model.QualityMetric.DECODE -> "Readable image"
    com.cardpregrade.core.model.QualityMetric.RESOLUTION -> "Resolution"
    com.cardpregrade.core.model.QualityMetric.SHARPNESS -> "Sharpness"
    com.cardpregrade.core.model.QualityMetric.EXPOSURE -> "Exposure"
    com.cardpregrade.core.model.QualityMetric.HIGHLIGHT_CLIPPING -> "Highlights (glare)"
}
