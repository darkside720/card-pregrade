package com.cardpregrade.core.model

/**
 * Identifies the exact algorithm (or model) that produced a value, so historical predictions
 * stay reproducible and comparable after algorithms change.
 */
data class AlgorithmVersion(val name: String, val version: String) {
    override fun toString(): String = "$name@$version"
}

/** How an [InspectionResult] was produced. */
data class AnalysisProvenance(
    val pipelineVersion: String,
    val algorithms: List<AlgorithmVersion>,
    val processingMode: ProcessingMode,
    /**
     * True for fabricated demo/mock results used to evaluate UX. UI must label these
     * prominently and they must never be written to the training dataset.
     */
    val isDemo: Boolean,
)

/** Maturity of a detector; experimental output must be labelled as such in the UI. */
enum class DetectionMaturity {
    /** Validated against fixtures; still an estimate. */
    SUPPORTED,

    /** Exists but is not validated; output is shown with an explicit experimental label. */
    EXPERIMENTAL,

    /** Not implemented; the category is not checked at all. */
    NOT_IMPLEMENTED,
}
