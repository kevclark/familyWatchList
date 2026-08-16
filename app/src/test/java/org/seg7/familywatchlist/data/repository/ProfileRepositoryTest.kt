package org.seg7.familywatchlist.data.repository

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
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        repo = ProfileRepository(db.profileDao(), FakeClock())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the 11th profile is rejected — PLAN md §2 hard cap of 10`() = runTest {
        repeat(10) { i ->
            val result = repo.addProfile("P$i", "avatar", null)
            assertTrue(result.isSuccess)
        }

        val eleventh = repo.addProfile("P10", "avatar", null)

        assertTrue(eleventh.isFailure)
        assertTrue(eleventh.exceptionOrNull() is MaxProfilesReachedException)
        assertEquals(10, db.profileDao().count())
    }

    @Test
    fun `profiles under the cap succeed`() = runTest {
        val result = repo.addProfile("Kev", "fox", "12")

        assertTrue(result.isSuccess)
        assertEquals(1, db.profileDao().count())
    }
}
