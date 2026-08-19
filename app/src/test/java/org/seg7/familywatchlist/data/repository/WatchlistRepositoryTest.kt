package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
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

    private fun repository(isAvailable: suspend (Int, MediaType, String) -> Boolean) =
        WatchlistRepository(db.watchlistDao(), clock, isAvailable)

    @Test
    fun `add succeeds and writes an ACTIVE entry when the gate passes`() = runTest {
        val repo = repository { _, _, _ -> true }

        val result = repo.add(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.ADDED, result)
        assertEquals(WatchlistState.ACTIVE, repo.get(38700, MediaType.MOVIE)?.state)
    }

    @Test
    fun `add is rejected and writes nothing when the gate fails`() = runTest {
        val repo = repository { _, _, _ -> false }

        val result = repo.add(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.UNAVAILABLE, result)
        assertNull(repo.get(38700, MediaType.MOVIE))
    }

    @Test
    fun `toggle's add half is gated the same way as add`() = runTest {
        val repo = repository { _, _, _ -> false }

        val result = repo.toggle(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.UNAVAILABLE, result)
        assertNull(repo.get(38700, MediaType.MOVIE))
    }

    @Test
    fun `toggle's remove half is never gated — taking something off the list always works`() = runTest {
        val repo = repository { _, _, _ -> true }
        repo.add(38700, MediaType.MOVIE, profileId)

        // Availability having since disappeared must not block removing it.
        val neverAvailable = repository { _, _, _ -> false }
        val result = neverAvailable.toggle(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.REMOVED, result)
        assertEquals(WatchlistState.REMOVED, neverAvailable.get(38700, MediaType.MOVIE)?.state)
    }

    @Test
    fun `the gate is consulted with the exact title being added`() = runTest {
        val checkedIds = mutableListOf<Pair<Int, MediaType>>()
        val repo = repository { tmdbId, mediaType, _ -> checkedIds.add(tmdbId to mediaType); true }

        repo.add(12345, MediaType.TV, profileId)

        assertEquals(listOf(12345 to MediaType.TV), checkedIds)
    }

    /** PLAN.md §7 M2f: region is a real call-time parameter into the gate, not silently GB. */
    @Test
    fun `add threads the given region into the availability check, not a hardcoded GB`() = runTest {
        val checkedRegions = mutableListOf<String>()
        val repo = repository { _, _, region -> checkedRegions.add(region); true }

        repo.add(38700, MediaType.MOVIE, profileId, region = "US")
        repo.toggle(12345, MediaType.TV, profileId, region = "FR")

        assertEquals(listOf("US", "FR"), checkedRegions)
    }

    @Test
    fun `a repository built without an explicit gate (the 2-arg constructor) never blocks`() = runTest {
        val repo = WatchlistRepository(db.watchlistDao(), clock)

        val result = repo.add(38700, MediaType.MOVIE, profileId)

        assertEquals(WatchlistAddResult.ADDED, result)
    }

    /**
     * PLAN.md §5a's M2g refinement: [WatchlistRepository.observeActiveItemsWithAvailability]
     * is the state-computation logic behind "dim this card" — it must flag an item that's since
     * lost availability, and leave one that still has it alone, without the gate being consulted
     * at add-time affecting the read afterwards (the gate here can freely disagree with whatever
     * it said when the item was added, exactly like real life once a service drops a title).
     */
    @Test
    fun `observeActiveItemsWithAvailability flags a listed item that has since lost availability`() = runTest {
        seedTitle(38700, "Spider-Man: No Way Home")
        seedTitle(12345, "Paddington")
        // Both added while available…
        val addingRepo = repository { _, _, _ -> true }
        addingRepo.add(38700, MediaType.MOVIE, profileId)
        clock.advanceBy(1_000)
        addingRepo.add(12345, MediaType.MOVIE, profileId)

        // …but only 38700 has since lost it.
        val readingRepo = repository { tmdbId, _, _ -> tmdbId != 38700 }
        val rows = readingRepo.observeActiveItemsWithAvailability(flowOf("GB")).first()

        val byId = rows.associateBy { it.item.tmdbId }
        assertFalse("an item that's lost availability must be flagged unavailable", byId.getValue(38700).isAvailable)
        assertTrue("an item still available must not be flagged", byId.getValue(12345).isAvailable)
    }

    @Test
    fun `observeActiveItemsWithAvailability marks every item available when the gate still passes for all of them`() = runTest {
        seedTitle(38700, "Paddington")
        seedTitle(12345, "Arrival")
        val repo = repository { _, _, _ -> true }
        repo.add(38700, MediaType.MOVIE, profileId)
        repo.add(12345, MediaType.MOVIE, profileId)

        val rows = repo.observeActiveItemsWithAvailability(flowOf("GB")).first()

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.isAvailable })
    }

    @Test
    fun `observeActiveItemsWithAvailability only surfaces ACTIVE entries, same as observeActiveItems`() = runTest {
        seedTitle(38700, "Paddington")
        val repo = repository { _, _, _ -> true }
        repo.add(38700, MediaType.MOVIE, profileId)
        repo.remove(38700, MediaType.MOVIE)

        val rows = repo.observeActiveItemsWithAvailability(flowOf("GB")).first()

        assertTrue(rows.isEmpty())
    }

    private suspend fun seedTitle(tmdbId: Int, title: String) {
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = tmdbId, mediaType = MediaType.MOVIE, title = title, year = 2021,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                certification = null, voteAverage = null, popularity = null, trailerKey = null,
                fetchedAt = 1_000L,
            )
        )
    }
}
