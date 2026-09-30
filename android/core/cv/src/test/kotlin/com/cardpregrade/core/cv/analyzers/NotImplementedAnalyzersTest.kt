package com.cardpregrade.core.cv.analyzers

import com.cardpregrade.core.cv.image.CorrectedCardImage
import com.cardpregrade.core.model.AnalysisStatus
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.CenteringUnavailableReason
import com.cardpregrade.core.model.DetectionMaturity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotImplementedAnalyzersTest {

    private val image = object : CorrectedCardImage {
        override val sourceImageId = "img-1"
        override val widthPx = 630
        override val heightPx = 880
        override val pixelsPerMm: Double? = null
    }

    @Test
    fun `placeholder centering never fabricates a measurement`() {
        val result = NotImplementedCenteringAnalyzer().analyze(image, CardSide.FRONT)
        assertEquals(CenteringResult.Unknown(CardSide.FRONT, CenteringUnavailableReason.NOT_IMPLEMENTED), result)
    }

    @Test
    fun `placeholder surface analyzer declares every check as not implemented`() {
        val analyzer = NotImplementedSurfaceAnalyzer()
        val result = analyzer.analyze(SurfaceAnalysisInput(CardSide.FRONT, straight = image))
        assertEquals(AnalysisStatus.NOT_IMPLEMENTED, result.status)
        assertTrue(result.defects.isEmpty())
        assertTrue(result.checks.values.all { it == DetectionMaturity.NOT_IMPLEMENTED })
        assertEquals(listOf("img-1"), result.sourceImageIds)
    }
}
