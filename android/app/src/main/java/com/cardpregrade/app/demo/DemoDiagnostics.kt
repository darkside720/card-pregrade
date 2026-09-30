package com.cardpregrade.app.demo

import com.cardpregrade.core.cv.diagnostics.DiagnosticArtifact
import com.cardpregrade.core.cv.diagnostics.DiagnosticStage
import com.cardpregrade.core.cv.diagnostics.PipelineDiagnostics
import com.cardpregrade.core.cv.diagnostics.StageDiagnostics
import com.cardpregrade.core.cv.geometry.CardRegions
import com.cardpregrade.core.model.CardCorner
import com.cardpregrade.core.model.CardEdge
import com.cardpregrade.core.model.CardSide

/**
 * Card-analysis stage descriptors for the calibration screen. None of these stages is
 * implemented yet; the only drawn output is the deterministic crop geometry from :core:cv,
 * which shows *where* crops will be taken, not analysis results.
 */
object DemoDiagnostics {
    private const val CORNER_FRACTION = 0.1
    private const val EDGE_BAND = 0.05

    fun create(side: CardSide): PipelineDiagnostics = PipelineDiagnostics(
        sessionId = "demo-session",
        pipelineVersion = "not implemented (Phase 4+)",
        stages = listOf(
            StageDiagnostics(DiagnosticStage.ORIGINAL, side, emptyList(), note = "Real accepted captures are shown above."),
            StageDiagnostics(DiagnosticStage.DETECTED_BOUNDARY, side, emptyList(), note = "CardDetector not implemented (Phase 4)."),
            StageDiagnostics(DiagnosticStage.PERSPECTIVE_CORRECTED, side, emptyList(), note = "PerspectiveCorrector not implemented (Phase 4)."),
            StageDiagnostics(DiagnosticStage.CENTERING_LINES, side, emptyList(), note = "CenteringAnalyzer not implemented (Phase 4)."),
            StageDiagnostics(
                DiagnosticStage.CORNER_CROPS, side,
                listOf(DiagnosticArtifact.Regions("Corner crops (10% of width)", CardCorner.entries.map { CardRegions.cornerRegion(it, CORNER_FRACTION) })),
                note = "Only the crop geometry exists (CardRegions); crops need a corrected image (Phase 4–5).",
            ),
            StageDiagnostics(
                DiagnosticStage.EDGE_CROPS, side,
                listOf(DiagnosticArtifact.Regions("Edge bands (5% depth, corners excluded)", CardEdge.entries.map { CardRegions.edgeRegion(it, EDGE_BAND, CORNER_FRACTION) })),
                note = "Only the band geometry exists; edge analysis arrives in Phase 5.",
            ),
            StageDiagnostics(DiagnosticStage.DEFECT_OVERLAY, side, emptyList(), note = "No defect detection exists yet (Phase 5)."),
            StageDiagnostics(
                DiagnosticStage.MEASUREMENTS, side, emptyList(),
                note = "Card-analysis measurements arrive with Phase 4. Capture-quality measurements are shown per capture above.",
            ),
        ),
    )
}
