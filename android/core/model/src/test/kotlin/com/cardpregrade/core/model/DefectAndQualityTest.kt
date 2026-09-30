package com.cardpregrade.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefectAndQualityTest {

    private val version = AlgorithmVersion("test", "0")

    @Test
    fun `edge locations describe their section`() {
        val upperRight = DefectLocation(CardRegion.EDGE_RIGHT, NormalizedRect(0.96, 0.10, 1.0, 0.25))
        assertEquals("right edge, upper section", upperRight.describe())

        val bottomCenter = DefectLocation(CardRegion.EDGE_BOTTOM, NormalizedRect(0.40, 0.97, 0.60, 1.0))
        assertEquals("bottom edge, center section", bottomCenter.describe())

        val corner = DefectLocation(CardRegion.CORNER_TOP_LEFT, NormalizedRect(0.0, 0.0, 0.1, 0.1))
        assertEquals("top-left corner", corner.describe())
    }

    @Test
    fun `normalized rect rejects out of range and degenerate boxes`() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(-0.1, 0.0, 0.5, 0.5) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0.5, 0.0, 0.5, 0.5) }
    }

    @Test
    fun `polygon must lie inside its bounding box`() {
        assertThrows(IllegalArgumentException::class.java) {
            DefectLocation(
                CardRegion.SURFACE,
                NormalizedRect(0.1, 0.1, 0.2, 0.2),
                polygon = listOf(NormalizedPoint(0.1, 0.1), NormalizedPoint(0.5, 0.1), NormalizedPoint(0.1, 0.2)),
            )
        }
    }

    @Test
    fun `image with blocking warning cannot be marked acceptable`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageQualityResult(
                sourceImageId = "img",
                blurScore = 0.1, glareScore = null, exposureScore = null,
                resolutionScore = null, cardCoverageScore = null, perspectiveScore = null,
                acceptable = true,
                warnings = listOf(ImageQualityWarning(ImageQualityIssue.BLUR, blocking = true)),
                algorithmVersion = version,
            )
        }
    }

    @Test
    fun `unknown card has a display name`() {
        assertEquals(Card.UNKNOWN_CARD_NAME, Card(id = "c", game = CardGame.UNKNOWN).displayName)
        assertEquals("Lugia 149/147", Card(id = "c", game = CardGame.POKEMON, userLabel = "Lugia 149/147").displayName)
    }

    @Test
    fun `V1 capture protocol has the four required photos in order`() {
        assertEquals(
            listOf(
                CaptureKind.FRONT_STRAIGHT,
                CaptureKind.BACK_STRAIGHT,
                CaptureKind.FRONT_ANGLE_LEFT,
                CaptureKind.FRONT_ANGLE_RIGHT,
            ),
            CaptureProtocol.V1_STANDARD.steps.map { it.kind },
        )
    }
}
