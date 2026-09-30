package com.cardpregrade.core.cv.pipeline

import com.cardpregrade.core.cv.diagnostics.DiagnosticsRecorder
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.CornerResult
import com.cardpregrade.core.model.EdgeResult
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.ScanSession
import com.cardpregrade.core.model.SurfaceResult

/** Everything the CV stages produced for a session, before grade estimation. */
data class VisualInspection(
    val imageQuality: List<ImageQualityResult>,
    val centering: List<CenteringResult>,
    val corners: List<CornerResult>,
    val edges: List<EdgeResult>,
    val surface: List<SurfaceResult>,
    val pipelineVersion: String,
)

/** Progress reported to the Analysis screen. */
sealed interface PipelineProgress {
    data class StageStarted(val stageName: String) : PipelineProgress
    data class ImageRejected(val image: CapturedImage, val quality: ImageQualityResult) : PipelineProgress
    data class Completed(val inspection: VisualInspection) : PipelineProgress
    data class Failed(val message: String) : PipelineProgress
}

/**
 * Orchestrates CardDetector → PerspectiveCorrector → ImageNormalizer → quality gate →
 * centering / corners / edges / surface for every capture in a session.
 *
 * The on-device implementation runs with [ProcessingMode.LOCAL_ONLY]. A future remote
 * implementation (backend worker) must implement the same contract and may only be selected
 * after explicit user consent for that scan.
 */
interface InspectionPipeline {
    val processingMode: ProcessingMode
    val pipelineVersion: String

    suspend fun run(
        session: ScanSession,
        diagnostics: DiagnosticsRecorder = DiagnosticsRecorder.NONE,
        onProgress: (PipelineProgress) -> Unit = {},
    ): PipelineProgress
}
