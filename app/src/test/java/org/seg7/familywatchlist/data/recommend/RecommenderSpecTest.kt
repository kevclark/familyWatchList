package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PLAN.md §4a slider 5's edge-case handling (design corrected 2026-08-20): [suggestionCountRange]
 * derives the "Tune my picks" suggestion-count slider's min/max from the profile's real eligible
 * candidate count, adapting the min downward as the ceiling shrinks and disabling the control
 * entirely (`null`) once there's nothing eligible at all.
 */
class RecommenderSpecTest {

    @Test
    fun `a normal pool (well above the baseline floor) uses the fixed floor as min and the real count as max`() {
        val range = suggestionCountRange(eligibleCandidateCount = 342)

        assertEquals(RecommenderSpec.SUGGESTION_COUNT_MIN..342, range)
    }

    @Test
    fun `a pool exactly at the baseline floor produces a single-point range`() {
        val range = suggestionCountRange(eligibleCandidateCount = RecommenderSpec.SUGGESTION_COUNT_MIN)

        assertEquals(RecommenderSpec.SUGGESTION_COUNT_MIN..RecommenderSpec.SUGGESTION_COUNT_MIN, range)
    }

    @Test
    fun `a pool below the baseline floor adapts the min down instead of presenting an inverted range`() {
        assertEquals(3..3, suggestionCountRange(eligibleCandidateCount = 3))
        assertEquals(2..2, suggestionCountRange(eligibleCandidateCount = 2))
        assertEquals(1..1, suggestionCountRange(eligibleCandidateCount = 1))
    }

    @Test
    fun `a pool of exactly zero disables the control (null) rather than an inverted or empty range`() {
        assertNull(suggestionCountRange(eligibleCandidateCount = 0))
    }

    @Test
    fun `a negative eligible count (should never happen, but must not crash) also disables the control`() {
        assertNull(suggestionCountRange(eligibleCandidateCount = -1))
    }

    @Test
    fun `min never exceeds max for any non-negative eligible count`() {
        (0..50).forEach { eligible ->
            val range = suggestionCountRange(eligible)
            if (range != null) {
                assert(range.first <= range.last) { "min ${range.first} > max ${range.last} for eligible=$eligible" }
            }
        }
    }
}
