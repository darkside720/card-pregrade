package com.cardpregrade.app.demo

import com.cardpregrade.app.ui.locationLabel
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.InspectionWarningCode
import com.cardpregrade.core.model.RangeEstimate
import com.cardpregrade.core.model.SubgradeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoInspectionTest {

    private val result = DemoInspection.create()

    @Test
    fun `demo result is always flagged as demo`() {
        assertTrue(result.provenance.isDemo)
        assertTrue(result.warnings.any { it.code == InspectionWarningCode.DEMO_DATA })
    }

    @Test
    fun `demo matches the requested mock values`() {
        assertEquals("Lugia 149/147", result.cardLabel)
        assertEquals("9–10", (result.estimate.psa as RangeEstimate.Available).range.format())
        assertEquals("9.5–10", (result.estimate.bgs.overall as RangeEstimate.Available).range.format())
        assertEquals("10", result.subgrade(SubgradeCategory.CENTERING)?.range?.format())
        assertEquals("9.5", result.subgrade(SubgradeCategory.CORNERS)?.range?.format())
        assertEquals(82, result.overallConfidence.percent)
        assertEquals(3, result.defects.size)
        assertEquals("Back · right edge, upper section", DemoInspection.whitening.locationLabel())
        assertEquals(84, DemoInspection.whitening.confidence.percent)
    }

    @Test
    fun `centering matches the example measurements`() {
        val front = result.centering.first() as CenteringResult.Measured
        assertEquals("51.2 / 48.8", front.leftRight.format())
        assertEquals("50.4 / 49.6", front.topBottom.format())
        val back = result.centering.last() as CenteringResult.Measured
        assertEquals("50.8 / 49.2", back.leftRight.format())
        assertEquals("52.1 / 47.9", back.topBottom.format())
    }

    @Test
    fun `edge defects lie inside the edge segment that reports them`() {
        result.edges.forEach { edge ->
            edge.defects.forEach { d ->
                val box = d.location.boundingBox
                val seg = edge.segmentRegion
                assertTrue(
                    "${d.id} outside ${edge.side} ${edge.edge}",
                    box.left >= seg.left && box.right <= seg.right && box.top >= seg.top && box.bottom <= seg.bottom,
                )
            }
        }
        assertEquals(2, result.edges.sumOf { it.defects.size })
    }

    @Test
    fun `every PSA limiting factor references a known defect`() {
        val ids = result.defects.map { it.id }.toSet()
        val factors = (result.estimate.psa as RangeEstimate.Available).limitingFactors
        assertTrue(factors.isNotEmpty())
        assertTrue(factors.flatMap { it.defectIds }.all { it in ids })
    }
}
