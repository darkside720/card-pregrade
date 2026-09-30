package com.cardpregrade.core.grading

import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.AnalysisStatus
import com.cardpregrade.core.model.BorderWidths
import com.cardpregrade.core.model.CandidateStatus
import com.cardpregrade.core.model.CardCorner
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.CenteringUnavailableReason
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.CornerResult
import com.cardpregrade.core.model.Grade
import com.cardpregrade.core.model.GradeRange
import com.cardpregrade.core.model.GradeScale
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.NormalizedRect
import com.cardpregrade.core.model.RangeEstimate
import com.cardpregrade.core.model.SubgradeCategory
import com.cardpregrade.core.model.SubgradeEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {

    private val v = AlgorithmVersion("test", "0")
    private val policy = ConfidencePolicy(lowQualityImageCap = 0.4, missingCaptureCap = 0.5, centeringUnknownCap = 0.6)

    private fun measured(conf: Double) =
        CenteringResult.Measured(CardSide.FRONT, BorderWidths(1.0, 1.0, 1.0, 1.0), Confidence(conf), "img", v)

    private fun corner(conf: Double) = CornerResult(
        CardSide.FRONT, CardCorner.TOP_LEFT, NormalizedRect(0.0, 0.0, 0.1, 0.1),
        AnalysisStatus.ANALYZED, emptyList(), Confidence(conf),
    )

    private fun quality(ok: Boolean) =
        ImageQualityResult("img", null, null, null, null, null, null, ok, emptyList(), v)

    private fun input(
        centering: List<CenteringResult> = listOf(measured(0.9)),
        corners: List<CornerResult> = listOf(corner(0.85)),
        quality: List<ImageQualityResult> = listOf(quality(true)),
        allCaptures: Boolean = true,
    ) = GradingInput(quality, centering, corners, emptyList(), emptyList(), emptyList(), allCaptures)

    @Test
    fun `overall confidence is the weakest component`() {
        val result = ConservativeConfidenceCalculator(policy).calculate(input())
        assertEquals(0.85, result.overall.value, 1e-9)
        assertTrue(result.limitingReasons.isEmpty())
    }

    @Test
    fun `unacceptable image caps confidence`() {
        val result = ConservativeConfidenceCalculator(policy).calculate(input(quality = listOf(quality(false))))
        assertEquals(0.4, result.overall.value, 1e-9)
        assertEquals(1, result.limitingReasons.size)
    }

    @Test
    fun `unknown centering and missing captures cap confidence`() {
        val result = ConservativeConfidenceCalculator(policy).calculate(
            input(
                centering = listOf(measured(0.9), CenteringResult.Unknown(CardSide.BACK, CenteringUnavailableReason.BORDERS_NOT_DETECTED)),
                allCaptures = false,
            ),
        )
        assertEquals(0.5, result.overall.value, 1e-9)
        assertEquals(2, result.limitingReasons.size)
    }

    @Test
    fun `no analysis means zero confidence`() {
        val result = ConservativeConfidenceCalculator(policy).calculate(input(centering = emptyList(), corners = emptyList()))
        assertEquals(Confidence.NONE, result.overall)
    }

    @Test
    fun `uncalibrated estimator refuses to produce grades`() {
        val estimate = UncalibratedGradeEstimator().estimate(input())
        assertTrue(estimate.psa is RangeEstimate.Unavailable)
        assertTrue(estimate.bgs.overall is RangeEstimate.Unavailable)
        assertEquals(CandidateStatus.NOT_EVALUATED, estimate.bgs.blackLabelCandidate.status)
        assertTrue(estimate.bgs.blackLabelCandidate.experimental)
    }

    private fun sub(category: SubgradeCategory, low: Double, conf: Double) = SubgradeEstimate(
        category, GradeRange(GradeScale.BGS, Grade.of(low), Grade.of(10)), Confidence(conf),
    )

    @Test
    fun `candidate requires every category at 10 with high confidence`() {
        val assessor = CandidateAssessor(minCategoryConfidence = Confidence(0.9))
        val allTen = SubgradeCategory.entries.map { sub(it, 10.0, 0.95) }
        assertEquals(CandidateStatus.POTENTIAL_CANDIDATE, assessor.bgs10(allTen, allImagesAcceptable = true).status)

        val oneLow = allTen.map { if (it.category == SubgradeCategory.EDGES) sub(it.category, 9.5, 0.95) else it }
        val result = assessor.bgs10(oneLow, allImagesAcceptable = true)
        assertEquals(CandidateStatus.NOT_INDICATED, result.status)
        assertEquals(listOf("Edges estimate is below 10."), result.reasons)
    }

    @Test
    fun `candidate is withheld for low confidence missing categories or bad photos`() {
        val assessor = CandidateAssessor(minCategoryConfidence = Confidence(0.9))
        val lowConf = SubgradeCategory.entries.map { sub(it, 10.0, 0.7) }
        assertEquals(CandidateStatus.NOT_INDICATED, assessor.bgs10(lowConf, true).status)

        val missing = SubgradeCategory.entries.drop(1).map { sub(it, 10.0, 0.95) }
        assertEquals(CandidateStatus.NOT_INDICATED, assessor.bgs10(missing, true).status)

        val allTen = SubgradeCategory.entries.map { sub(it, 10.0, 0.95) }
        assertEquals(CandidateStatus.NOT_INDICATED, assessor.bgs10(allTen, allImagesAcceptable = false).status)
        assertTrue(assessor.blackLabel(allTen, true).experimental)
    }
}
