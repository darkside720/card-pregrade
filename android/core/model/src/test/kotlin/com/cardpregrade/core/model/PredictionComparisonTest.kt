package com.cardpregrade.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionComparisonTest {

    private val psa9to10 = GradeRange(GradeScale.PSA, Grade.of(9), Grade.of(10))

    @Test
    fun `outcome classification`() {
        assertEquals(ComparisonOutcome.WITHIN_RANGE, PredictionComparison.outcome(psa9to10, Grade.of(9)))
        assertEquals(ComparisonOutcome.WITHIN_RANGE, PredictionComparison.outcome(psa9to10, Grade.of(10)))
        assertEquals(ComparisonOutcome.ACTUAL_BELOW_RANGE, PredictionComparison.outcome(psa9to10, Grade.of(8)))
        assertEquals(
            ComparisonOutcome.ACTUAL_ABOVE_RANGE,
            PredictionComparison.outcome(GradeRange(GradeScale.PSA, Grade.of(7), Grade.of(8)), Grade.of(9)),
        )
        assertEquals(ComparisonOutcome.NO_PREDICTION, PredictionComparison.outcome(null, Grade.of(9)))
        assertEquals(ComparisonOutcome.NO_ACTUAL, PredictionComparison.outcome(psa9to10, null))
    }
}
