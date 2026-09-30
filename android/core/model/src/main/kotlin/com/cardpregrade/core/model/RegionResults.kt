package com.cardpregrade.core.model

/** Whether a region was actually analysed. */
enum class AnalysisStatus {
    ANALYZED,
    SKIPPED_LOW_QUALITY,
    NOT_IMPLEMENTED,
}

/** Result for one corner of one side. Each corner is reported individually. */
data class CornerResult(
    val side: CardSide,
    val corner: CardCorner,
    /** Crop that was analysed, in normalized corrected-card coordinates. */
    val cropRegion: NormalizedRect,
    val status: AnalysisStatus,
    val defects: List<CardDefect>,
    val confidence: Confidence,
)

/** Result for one edge of one side. */
data class EdgeResult(
    val side: CardSide,
    val edge: CardEdge,
    val segmentRegion: NormalizedRect,
    val status: AnalysisStatus,
    val defects: List<CardDefect>,
    val confidence: Confidence,
)

/** Surface checks the architecture anticipates. Most are not implemented in V1. */
enum class SurfaceCheck {
    SCRATCH,
    HOLO_SCRATCH,
    PRINT_LINE,
    DENT,
    INDENTATION,
    ROLLER_MARK,
    STAIN,
    FACTORY_DEFECT,
}

/**
 * Surface result for one side. [checks] states, per check, whether it was run and at what
 * maturity, so the UI can say "not checked" instead of implying a clean surface.
 */
data class SurfaceResult(
    val side: CardSide,
    val status: AnalysisStatus,
    val checks: Map<SurfaceCheck, DetectionMaturity>,
    /** Images that contributed, e.g. straight + left/right lighting captures. */
    val sourceImageIds: List<String>,
    val defects: List<CardDefect>,
    val confidence: Confidence,
)
