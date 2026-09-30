package com.cardpregrade.app.demo

import com.cardpregrade.core.cv.geometry.CardRegions
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.AnalysisProvenance
import com.cardpregrade.core.model.AnalysisStatus
import com.cardpregrade.core.model.BgsEstimate
import com.cardpregrade.core.model.BorderWidths
import com.cardpregrade.core.model.CandidateIndicator
import com.cardpregrade.core.model.CandidateStatus
import com.cardpregrade.core.model.CardCorner
import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.CardEdge
import com.cardpregrade.core.model.CardGame
import com.cardpregrade.core.model.CardRegion
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.CornerResult
import com.cardpregrade.core.model.DefectCategory
import com.cardpregrade.core.model.DefectLocation
import com.cardpregrade.core.model.DefectSeverity
import com.cardpregrade.core.model.DetectionMaturity
import com.cardpregrade.core.model.EdgeResult
import com.cardpregrade.core.model.Grade
import com.cardpregrade.core.model.GradeEstimate
import com.cardpregrade.core.model.GradeLimitingFactor
import com.cardpregrade.core.model.GradeRange
import com.cardpregrade.core.model.GradeScale
import com.cardpregrade.core.model.ImageQualityIssue
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.ImageQualityWarning
import com.cardpregrade.core.model.InspectionResult
import com.cardpregrade.core.model.InspectionWarning
import com.cardpregrade.core.model.InspectionWarningCode
import com.cardpregrade.core.model.NormalizedRect
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.RangeEstimate
import com.cardpregrade.core.model.SubgradeCategory
import com.cardpregrade.core.model.SubgradeEstimate
import com.cardpregrade.core.model.SurfaceCheck
import com.cardpregrade.core.model.SurfaceResult
import java.time.Instant

/**
 * FABRICATED sample inspection used only to evaluate the results UX.
 *
 * No photograph is involved. [AnalysisProvenance.isDemo] is true, which the UI uses to show
 * the "DEMO / MOCK ANALYSIS" banner and which repositories use to refuse persistence.
 */
object DemoInspection {
    const val RESULT_ID = "demo"

    private val MOCK = AlgorithmVersion("demo-mock", "0")
    private const val CORNER_FRACTION = 0.1
    private const val EDGE_BAND = 0.05

    private const val FRONT = "demo-front-straight"
    private const val BACK = "demo-back-straight"
    private const val FRONT_LEFT = "demo-front-light-left"
    private const val FRONT_RIGHT = "demo-front-light-right"

    val whitening = CardDefect(
        id = "demo-defect-1",
        side = CardSide.BACK,
        category = DefectCategory.WHITENING,
        location = DefectLocation(CardRegion.EDGE_RIGHT, NormalizedRect(0.955, 0.12, 1.0, 0.26)),
        severity = DefectSeverity.MINOR,
        confidence = Confidence(0.84),
        description = "Possible whitening",
        sourceImageId = BACK,
        algorithmVersion = MOCK,
        maturity = DetectionMaturity.SUPPORTED,
    )

    val printLine = CardDefect(
        id = "demo-defect-2",
        side = CardSide.FRONT,
        category = DefectCategory.PRINT_LINE,
        location = DefectLocation(CardRegion.SURFACE, NormalizedRect(0.12, 0.30, 0.88, 0.325)),
        severity = DefectSeverity.TRACE,
        confidence = Confidence(0.34),
        description = "Possible print line (holo area)",
        sourceImageId = FRONT_LEFT,
        algorithmVersion = MOCK,
        maturity = DetectionMaturity.EXPERIMENTAL,
    )

    val factoryCut = CardDefect(
        id = "demo-defect-3",
        side = CardSide.FRONT,
        category = DefectCategory.IRREGULAR_CUT,
        location = DefectLocation(CardRegion.EDGE_BOTTOM, NormalizedRect(0.35, 0.968, 0.62, 1.0)),
        severity = DefectSeverity.TRACE,
        confidence = Confidence(0.29),
        description = "Possible factory-cut irregularity",
        sourceImageId = FRONT,
        algorithmVersion = MOCK,
        maturity = DetectionMaturity.EXPERIMENTAL,
    )

    private val allDefects = listOf(whitening, printLine, factoryCut)

    fun create(createdAt: Instant = Instant.parse("2026-09-01T12:00:00Z")): InspectionResult {
        val corners = CardSide.entries.flatMap { side ->
            CardCorner.entries.map { corner ->
                CornerResult(
                    side = side,
                    corner = corner,
                    cropRegion = CardRegions.cornerRegion(corner, CORNER_FRACTION),
                    status = AnalysisStatus.ANALYZED,
                    defects = emptyList(),
                    confidence = Confidence(0.86),
                )
            }
        }
        val edges = CardSide.entries.flatMap { side ->
            CardEdge.entries.map { edge ->
                val region = CardRegion.of(edge)
                EdgeResult(
                    side = side,
                    edge = edge,
                    segmentRegion = CardRegions.edgeRegion(edge, EDGE_BAND, CORNER_FRACTION),
                    status = AnalysisStatus.ANALYZED,
                    defects = allDefects.filter { it.side == side && it.location.region == region },
                    confidence = Confidence(0.84),
                )
            }
        }
        val surface = listOf(
            SurfaceResult(
                side = CardSide.FRONT,
                status = AnalysisStatus.ANALYZED,
                checks = SurfaceCheck.entries.associateWith {
                    if (it == SurfaceCheck.PRINT_LINE) DetectionMaturity.EXPERIMENTAL else DetectionMaturity.NOT_IMPLEMENTED
                },
                sourceImageIds = listOf(FRONT, FRONT_LEFT, FRONT_RIGHT),
                defects = listOf(printLine),
                confidence = Confidence(0.6),
            ),
            SurfaceResult(
                side = CardSide.BACK,
                status = AnalysisStatus.NOT_IMPLEMENTED,
                checks = SurfaceCheck.entries.associateWith { DetectionMaturity.NOT_IMPLEMENTED },
                sourceImageIds = listOf(BACK),
                defects = emptyList(),
                confidence = Confidence.NONE,
            ),
        )

        val notTen = listOf("Corners estimate is below 10.", "Edges estimate is below 10.", "Surface estimate is below 10.")

        return InspectionResult(
            id = RESULT_ID,
            sessionId = "demo-session",
            cardId = "demo-card",
            cardGame = CardGame.POKEMON,
            cardLabel = "Lugia 149/147",
            createdAt = createdAt,
            imageQuality = listOf(
                quality(FRONT, glare = 0.92),
                quality(BACK, glare = 0.94),
                quality(FRONT_LEFT, glare = 0.81),
                quality(FRONT_RIGHT, glare = 0.55, warnings = listOf(ImageQualityWarning(ImageQualityIssue.GLARE, blocking = false))),
            ),
            centering = listOf(
                CenteringResult.Measured(CardSide.FRONT, BorderWidths(25.6, 24.4, 25.2, 24.8), Confidence(0.9), FRONT, MOCK),
                CenteringResult.Measured(CardSide.BACK, BorderWidths(25.4, 24.6, 26.05, 23.95), Confidence(0.88), BACK, MOCK),
            ),
            corners = corners,
            edges = edges,
            surface = surface,
            defects = allDefects,
            estimate = GradeEstimate(
                psa = RangeEstimate.Available(
                    GradeRange(GradeScale.PSA, Grade.of(9), Grade.of(10)),
                    limitingFactors = listOf(
                        GradeLimitingFactor("Possible minor whitening on the back right edge (upper section).", listOf(whitening.id)),
                        GradeLimitingFactor("Possible print line on the front surface (low confidence, experimental).", listOf(printLine.id)),
                    ),
                ),
                bgs = BgsEstimate(
                    overall = RangeEstimate.Available(
                        GradeRange(GradeScale.BGS, Grade.of(9.5), Grade.of(10)),
                        limitingFactors = listOf(
                            GradeLimitingFactor("Edges: possible minor whitening on the back right edge.", listOf(whitening.id)),
                        ),
                    ),
                    bgs10Candidate = CandidateIndicator(CandidateStatus.NOT_INDICATED, experimental = false, reasons = notTen),
                    blackLabelCandidate = CandidateIndicator(CandidateStatus.NOT_INDICATED, experimental = true, reasons = notTen),
                ),
                subgrades = listOf(
                    subgrade(SubgradeCategory.CENTERING, 10.0, 0.88),
                    subgrade(SubgradeCategory.CORNERS, 9.5, 0.86),
                    subgrade(SubgradeCategory.EDGES, 9.5, 0.84, listOf(whitening.id, factoryCut.id)),
                    subgrade(SubgradeCategory.SURFACE, 9.5, 0.6, listOf(printLine.id)),
                ),
                ruleSetVersion = MOCK,
            ),
            overallConfidence = Confidence(0.82),
            warnings = listOf(
                InspectionWarning(InspectionWarningCode.DEMO_DATA, "Sample data — no photograph was analyzed."),
                InspectionWarning(
                    InspectionWarningCode.EXPERIMENTAL_DETECTIONS_PRESENT,
                    "2 potential defects come from experimental detectors and may be false positives.",
                ),
                InspectionWarning(
                    InspectionWarningCode.SURFACE_ANALYSIS_LIMITED,
                    "Most surface checks are not implemented; the surface was not fully inspected.",
                ),
                InspectionWarning(
                    InspectionWarningCode.PHOTOGRAPHIC_LIMITATIONS,
                    "Professional in-person inspection may find defects that photographs cannot show.",
                ),
            ),
            provenance = AnalysisProvenance(
                pipelineVersion = "demo-mock-0",
                algorithms = listOf(MOCK),
                processingMode = ProcessingMode.LOCAL_ONLY,
                isDemo = true,
            ),
        )
    }

    private fun quality(id: String, glare: Double, warnings: List<ImageQualityWarning> = emptyList()) = ImageQualityResult(
        sourceImageId = id,
        blurScore = 0.9,
        glareScore = glare,
        exposureScore = 0.85,
        resolutionScore = 0.95,
        cardCoverageScore = 0.8,
        perspectiveScore = 0.9,
        acceptable = true,
        warnings = warnings,
        algorithmVersion = MOCK,
    )

    private fun subgrade(category: SubgradeCategory, value: Double, confidence: Double, limiting: List<String> = emptyList()) =
        SubgradeEstimate(
            category = category,
            range = GradeRange.single(GradeScale.BGS, Grade.of(value)),
            confidence = Confidence(confidence),
            limitingDefectIds = limiting,
        )
}
