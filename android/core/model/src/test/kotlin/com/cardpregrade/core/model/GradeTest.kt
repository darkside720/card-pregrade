package com.cardpregrade.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeTest {

    @Test
    fun `grades only allow half-point precision`() {
        assertEquals("9.5", Grade.of(9.5).format())
        assertEquals("10", Grade.of(10).format())
        assertThrows(IllegalArgumentException::class.java) { Grade.of(9.73) }
        assertThrows(IllegalArgumentException::class.java) { Grade.of(10.5) }
        assertThrows(IllegalArgumentException::class.java) { Grade.of(0.5) }
    }

    @Test
    fun `PSA scale has no 9_5 but allows lower half grades`() {
        assertFalse(GradeScale.PSA.contains(Grade.of(9.5)))
        assertTrue(GradeScale.PSA.contains(Grade.of(8.5)))
        assertTrue(GradeScale.PSA.contains(Grade.of(10)))
        assertEquals(18, GradeScale.PSA.allGrades.size)
    }

    @Test
    fun `BGS scale allows all half grades`() {
        assertTrue(GradeScale.BGS.contains(Grade.of(9.5)))
        assertEquals(19, GradeScale.BGS.allGrades.size)
    }

    @Test
    fun `ranges format with an en dash and collapse single values`() {
        assertEquals("9–10", GradeRange(GradeScale.PSA, Grade.of(9), Grade.of(10)).format())
        assertEquals("9.5–10", GradeRange(GradeScale.BGS, Grade.of(9.5), Grade.of(10)).format())
        assertEquals("10", GradeRange.single(GradeScale.BGS, Grade.of(10)).format())
    }

    @Test
    fun `invalid ranges are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GradeRange(GradeScale.PSA, Grade.of(10), Grade.of(9))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GradeRange(GradeScale.PSA, Grade.of(9.5), Grade.of(10))
        }
    }

    @Test
    fun `confidence buckets and bounds`() {
        assertEquals(ConfidenceLevel.HIGH, Confidence(0.84).level)
        assertEquals(ConfidenceLevel.MEDIUM, Confidence(0.5).level)
        assertEquals(ConfidenceLevel.LOW, Confidence(0.35).level)
        assertEquals(84, Confidence(0.84).percent)
        assertThrows(IllegalArgumentException::class.java) { Confidence(1.01) }
    }
}
