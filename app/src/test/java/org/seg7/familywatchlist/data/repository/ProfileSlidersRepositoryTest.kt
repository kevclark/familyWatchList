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
}
