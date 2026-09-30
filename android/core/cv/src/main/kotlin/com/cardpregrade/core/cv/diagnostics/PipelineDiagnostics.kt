package com.cardpregrade.core.cv.diagnostics

import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.NormalizedRect

/**
 * Intermediate artefacts recorded while running the pipeline, consumed by the developer
 * calibration screen. Recording is opt-in (developer mode) so normal scans pay no cost.
 */
enum class DiagnosticStage(val title: String) {
    ORIGINAL("Original image"),
    DETECTED_BOUNDARY("Detected card boundary"),
    PERSPECTIVE_CORRECTED("Perspective-corrected image"),
    CENTERING_LINES("Centering lines"),
    CORNER_CROPS("Corner crops"),
    EDGE_CROPS("Edge crops"),
    DEFECT_OVERLAY("Defect overlay"),
    MEASUREMENTS("Intermediate CV measurements"),
}

sealed interface DiagnosticArtifact {
    val label: String

    /** A rendered/intermediate image stored locally (e.g. a debug PNG in app cache). */
    data class Image(override val label: String, val localUri: String) : DiagnosticArtifact

    /** Region annotations to draw over the stage image. */
    data class Regions(override val label: String, val regions: List<NormalizedRect>) : DiagnosticArtifact

    /** A scalar measurement, e.g. "laplacian variance" = 412.7. Null means "not measured". */
    data class Measurement(override val label: String, val value: Double?, val unit: String? = null) : DiagnosticArtifact
}

data class StageDiagnostics(
    val stage: DiagnosticStage,
    val side: CardSide?,
    val artifacts: List<DiagnosticArtifact>,
    val durationMillis: Long? = null,
    val note: String? = null,
)

data class PipelineDiagnostics(
    val sessionId: String,
    val pipelineVersion: String,
    val stages: List<StageDiagnostics>,
)

/** Sink that pipeline stages report into. The default discards everything. */
fun interface DiagnosticsRecorder {
    fun record(stage: StageDiagnostics)

    companion object {
        val NONE = DiagnosticsRecorder { }
    }
}
