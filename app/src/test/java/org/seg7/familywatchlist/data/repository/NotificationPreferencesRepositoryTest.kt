package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): per-profile half of the notification
 * gate — default **on** for every profile (individual or the Family sentinel), preserving
 * pre-M3e behaviour until someone explicitly opts out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPreferencesRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: NotificationPreferencesRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        repo = NotificationPreferencesRepository(db.notificationPreferenceDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a profile with no stored preference defaults to enabled`() = runTest {
        assertTrue(repo.isEnabled(1L))
        assertTrue(repo.observe(1L).first())
    }

    @Test
    fun `the Family sentinel id also defaults to enabled`() = runTest {
        assertTrue(repo.isEnabled(FAMILY_PROFILE_SENTINEL_ID))
    }

    @Test
    fun `setEnabled false then true round-trips`() = runTest {
        repo.setEnabled(7L, false)
        assertEquals(false, repo.isEnabled(7L))
        assertEquals(false, repo.observe(7L).first())

        repo.setEnabled(7L, true)
        assertEquals(true, repo.isEnabled(7L))
    }

    @Test
    fun `preferences are independent per profile, including the Family sentinel`() = runTest {
        repo.setEnabled(1L, false)
        repo.setEnabled(FAMILY_PROFILE_SENTINEL_ID, false)

        assertEquals(false, repo.isEnabled(1L))
        assertEquals(false, repo.isEnabled(FAMILY_PROFILE_SENTINEL_ID))
        // A third, untouched profile is unaffected by either write above.
        assertTrue(repo.isEnabled(2L))
    }
}
