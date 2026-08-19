package org.seg7.familywatchlist.ui.home

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5a's M2g refinement, exercised through Home's own "My List" carousel data: an item
 * that's since lost availability must come back flagged so the card can render dimmed, and
 * [HomeViewModel.removeFromWatchlist] must be a real, working clean-up action reachable straight
 * from that carousel — not just a details-screen detour. No subscribed providers are ever seeded
 * here, so [DiscoverRepository.discoverMovies]/`discoverTv` short-circuit to an empty list without
 * touching the network (PLAN.md §7 M2e) — this test is about the My List row alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var discoverRepository: DiscoverRepository
    private lateinit var providerRepository: ProviderRepository

    private val profileId = 7L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)

        listOf(38700 to "Spider-Man: No Way Home", 12345 to "Paddington").forEach { (id, name) ->
            db.titleDao().upsert(
                TitleEntity(
                    tmdbId = id, mediaType = MediaType.MOVIE, title = name, year = 2021,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                    certification = null, voteAverage = null, popularity = null,
                    trailerKey = null, fetchedAt = 1_000L,
                )
            )
        }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun viewModel(watchlistRepository: WatchlistRepository) =
        HomeViewModel(discoverRepository, providerRepository, watchlistRepository)

    @Test
    fun `an item that has lost availability is flagged so Home's My List carousel can dim it`() = runTest {
        val addingRepo = WatchlistRepository(db.watchlistDao(), clock) { _, _ -> true }
        addingRepo.add(38700, MediaType.MOVIE, profileId)
        addingRepo.add(12345, MediaType.MOVIE, profileId)
        val readingRepo = WatchlistRepository(db.watchlistDao(), clock) { tmdbId, _ -> tmdbId != 38700 }

        val state = viewModel(readingRepo).myList.first { it.size == 2 }

        val byId = state.associateBy { it.item.tmdbId }
        assertFalse("Spider-Man has lost availability and must be flagged", byId.getValue(38700).isAvailable)
        assertTrue("Paddington is still available and must not be flagged", byId.getValue(12345).isAvailable)
    }

    @Test
    fun `removeFromWatchlist actually removes the entry — the carousel's direct clean-up action`() = runTest {
        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _ -> true }
        watchlistRepository.add(38700, MediaType.MOVIE, profileId)
        val vm = viewModel(watchlistRepository)
        vm.myList.first { it.isNotEmpty() }

        vm.removeFromWatchlist(38700, MediaType.MOVIE)

        assertTrue(vm.myList.first { it.isEmpty() }.isEmpty())
        assertEquals(WatchlistState.REMOVED, watchlistRepository.get(38700, MediaType.MOVIE)?.state)
    }
}
