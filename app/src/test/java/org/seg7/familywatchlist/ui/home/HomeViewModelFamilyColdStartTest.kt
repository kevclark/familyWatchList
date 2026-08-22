package org.seg7.familywatchlist.ui.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN.md §5b M3i item 10: direct, fake-driven unit tests for [familyIsColdStart] — the pure
 * boolean combination [HomeViewModel.refresh] uses to decide whether the Family profile itself is
 * cold-start ("every curated member is individually below the 5-event threshold"). Deliberately
 * separate from the live-Room [HomeViewModelTest] suite: driving this same logic end-to-end
 * through the reactive `uiState` StateFlow needs the Family branch's several real Room round-trips
 * to reliably outrace the (near-instant, no-network) discover branch before a `uiState.first {
 * ... }` predicate observes the *real* computed value — fine for proving a positive "flips to
 * false" transition (see `HomeViewModelTest`'s "Family stays warm..." test), but not safe for
 * proving a "stays true" one, since [HomeViewModel]'s own pessimistic initial default already
 * happens to match. These tests prove the actual logic instead, with no Room/timing involved.
 */
class HomeViewModelFamilyColdStartTest {

    @Test
    fun `every member cold-start means Family is cold-start`() = runTest {
        val cold = familyIsColdStart(memberIds = listOf(1L, 2L)) { true }
        assertTrue(cold)
    }

    @Test
    fun `even one warm member means Family is not cold-start`() = runTest {
        val warmMemberId = 2L
        val cold = familyIsColdStart(memberIds = listOf(1L, warmMemberId)) { profileId -> profileId != warmMemberId }
        assertFalse(cold)
    }

    @Test
    fun `every member warm means Family is not cold-start`() = runTest {
        val cold = familyIsColdStart(memberIds = listOf(1L, 2L)) { false }
        assertFalse(cold)
    }

    /**
     * PLAN.md §5b M3i item 10: an empty membership list (no Family profile persisted yet) is
     * deliberately *not* cold-start — see [familyIsColdStart]'s kdoc for why "vacuously every
     * member is cold" isn't the right call here.
     */
    @Test
    fun `no membership at all is not cold-start`() = runTest {
        val cold = familyIsColdStart(memberIds = emptyList()) { true }
        assertFalse(cold)
    }
}
