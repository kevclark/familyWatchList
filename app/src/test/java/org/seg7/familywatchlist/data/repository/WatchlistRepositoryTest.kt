package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5a: adding to the shared Want-to-Watch list is gated to GB availability on a
 * subscribed provider, applied at [WatchlistRepository.add]/[WatchlistRepository.toggle] — the
 * gate is injected as a plain suspend function so this test exercises it without any TMDB/Room
 * availability plumbing (that resolution logic itself is [AvailabilityGateTest]'s job; the
 * production wiring of [AvailabilityGate] into this repository is `AppContainer`'s).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock

    private val profileId = 7L

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        clock = FakeClock(startMillis = 1_000L)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repository(isAvailable: suspend (Int, MediaType) -> Boolean) =
        WatchlistRepository(db.watchlistDao(), clock, isAvailable)

    @Test
    fun `add succeeds and writes an ACTIVE entry when the gate passes`() = runTest {
        val repo = repository { _, _ -> true }

        val result = repo.add(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.ADDED, result)
        assertEquals(WatchlistState.ACTIVE, repo.get(38700, MediaType.MOVIE)?.state)
    }

    @Test
    fun `add is rejected and writes nothing when the gate fails`() = runTest {
        val repo = repository { _, _ -> false }

        val result = repo.add(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.UNAVAILABLE, result)
        assertNull(repo.get(38700, MediaType.MOVIE))
    }

    @Test
    fun `toggle's add half is gated the same way as add`() = runTest {
        val repo = repository { _, _ -> false }

        val result = repo.toggle(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.UNAVAILABLE, result)
        assertNull(repo.get(38700, MediaType.MOVIE))
    }

    @Test
    fun `toggle's remove half is never gated — taking something off the list always works`() = runTest {
        val repo = repository { _, _ -> true }
        repo.add(38700, MediaType.MOVIE, profileId)

        // Availability having since disappeared must not block removing it.
        val neverAvailable = repository { _, _ -> false }
        val result = neverAvailable.toggle(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.REMOVED, result)
        assertEquals(WatchlistState.REMOVED, neverAvailable.get(38700, MediaType.MOVIE)?.state)
    }

    @Test
    fun `the gate is consulted with the exact title being added`() = runTest {
        val checkedIds = mutableListOf<Pair<Int, MediaType>>()
        val repo = repository { tmdbId, mediaType -> checkedIds.add(tmdbId to mediaType); true }

        repo.add(12345, MediaType.TV, profileId)

        assertEquals(listOf(12345 to MediaType.TV), checkedIds)
    }

    @Test
    fun `a repository built without an explicit gate (the 2-arg constructor) never blocks`() = runTest {
        val repo = WatchlistRepository(db.watchlistDao(), clock)

        val result = repo.add(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.ADDED, result)
    }
}
