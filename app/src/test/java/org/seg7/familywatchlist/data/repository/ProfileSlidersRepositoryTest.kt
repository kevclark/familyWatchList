package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.recommend.RecommenderSpec
import org.seg7.familywatchlist.data.recommend.SliderSettings
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/** PLAN.md §4a: per-profile slider storage — untouched profiles default to [SliderSettings.DEFAULT]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSlidersRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileSlidersRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        repo = ProfileSlidersRepository(db.profileSlidersDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a profile with no stored sliders reads as SliderSettings DEFAULT`() = runTest {
        assertEquals(SliderSettings.DEFAULT, repo.get(1L))
        assertEquals(SliderSettings.DEFAULT, repo.observe(1L).first())
    }

    @Test
    fun `set then get round-trips`() = runTest {
        val settings = SliderSettings(discovery = 0.5, recency = -0.25, personalMatch = 1.0)

        repo.set(7L, settings)

        assertEquals(settings, repo.get(7L))
        assertEquals(settings, repo.observe(7L).first())
    }

    @Test
    fun `sliders are independent per profile`() = runTest {
        repo.set(1L, SliderSettings(discovery = 1.0))
        repo.set(2L, SliderSettings(discovery = -1.0))

        assertEquals(1.0, repo.get(1L).discovery, 1e-9)
        assertEquals(-1.0, repo.get(2L).discovery, 1e-9)
    }

    @Test
    fun `setting again overwrites the previous value`() = runTest {
        repo.set(1L, SliderSettings(recency = 0.5))
        repo.set(1L, SliderSettings(recency = -0.5))

        assertEquals(-0.5, repo.get(1L).recency, 1e-9)
    }

    // --- PLAN.md §4a slider 5: the user's *requested* count ---

    @Test
    fun `a profile with no stored suggestion count reads as the RecommenderSpec default (30)`() = runTest {
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, repo.getSuggestionCount(1L))
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, repo.observeSuggestionCount(1L).first())
    }

    @Test
    fun `setSuggestionCount then getSuggestionCount round-trips`() = runTest {
        repo.setSuggestionCount(7L, 10)

        assertEquals(10, repo.getSuggestionCount(7L))
        assertEquals(10, repo.observeSuggestionCount(7L).first())
    }

    /**
     * Design corrected 2026-08-20: there is no fixed upper bound any more — a requested count can
     * legitimately exceed today's real eligible pool (see [RecommendationRepositoryTest]'s
     * min(requested, eligible) clamping tests for why that's fine). Only a sane positive-integer
     * floor is enforced here.
     */
    @Test
    fun `setSuggestionCount accepts values well beyond the old fixed 50 ceiling`() = runTest {
        repo.setSuggestionCount(1L, 200)

        assertEquals(200, repo.getSuggestionCount(1L))
    }

    @Test
    fun `setSuggestionCount rejects non-positive values`() = runTest {
        var threwForZero = false
        try {
            repo.setSuggestionCount(1L, 0)
        } catch (e: IllegalArgumentException) {
            threwForZero = true
        }
        assertEquals(true, threwForZero)

        var threwForNegative = false
        try {
            repo.setSuggestionCount(1L, -5)
        } catch (e: IllegalArgumentException) {
            threwForNegative = true
        }
        assertEquals(true, threwForNegative)
    }

    @Test
    fun `set() for the taste sliders preserves a previously stored suggestion count`() = runTest {
        repo.setSuggestionCount(1L, 12)

        repo.set(1L, SliderSettings(discovery = 0.5))

        assertEquals(12, repo.getSuggestionCount(1L))
        assertEquals(0.5, repo.get(1L).discovery, 1e-9)
    }

    @Test
    fun `setSuggestionCount preserves previously stored taste sliders`() = runTest {
        repo.set(1L, SliderSettings(discovery = 0.5, recency = -0.25, personalMatch = 1.0))

        repo.setSuggestionCount(1L, 12)

        assertEquals(SliderSettings(discovery = 0.5, recency = -0.25, personalMatch = 1.0), repo.get(1L))
        assertEquals(12, repo.getSuggestionCount(1L))
    }

    @Test
    fun `suggestion counts are independent per profile`() = runTest {
        repo.setSuggestionCount(1L, 10)
        repo.setSuggestionCount(2L, 45)

        assertEquals(10, repo.getSuggestionCount(1L))
        assertEquals(45, repo.getSuggestionCount(2L))
    }

    // --- PLAN.md §4a slider 5: the real, last-known eligible-candidate ceiling ---

    @Test
    fun `a profile with no stored eligible count reads as the RecommenderSpec default (30)`() = runTest {
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, repo.getEligibleCandidateCount(1L))
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, repo.observeEligibleCandidateCount(1L).first())
    }

    @Test
    fun `setEligibleCandidateCount then getEligibleCandidateCount round-trips`() = runTest {
        repo.setEligibleCandidateCount(7L, 342)

        assertEquals(342, repo.getEligibleCandidateCount(7L))
        assertEquals(342, repo.observeEligibleCandidateCount(7L).first())
    }

    @Test
    fun `setEligibleCandidateCount accepts zero (a genuinely empty pool)`() = runTest {
        repo.setEligibleCandidateCount(1L, 0)

        assertEquals(0, repo.getEligibleCandidateCount(1L))
    }

    @Test
    fun `setEligibleCandidateCount rejects negative values`() = runTest {
        var threw = false
        try {
            repo.setEligibleCandidateCount(1L, -1)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertEquals(true, threw)
    }

    @Test
    fun `setEligibleCandidateCount preserves the requested count and taste sliders, and vice versa`() = runTest {
        repo.set(1L, SliderSettings(discovery = 0.5, recency = -0.25, personalMatch = 1.0))
        repo.setSuggestionCount(1L, 12)

        repo.setEligibleCandidateCount(1L, 8)

        assertEquals(SliderSettings(discovery = 0.5, recency = -0.25, personalMatch = 1.0), repo.get(1L))
        assertEquals(12, repo.getSuggestionCount(1L))
        assertEquals(8, repo.getEligibleCandidateCount(1L))

        // And the reverse: changing the requested count afterwards doesn't clobber the eligible count.
        repo.setSuggestionCount(1L, 20)
        assertEquals(8, repo.getEligibleCandidateCount(1L))
    }
}
