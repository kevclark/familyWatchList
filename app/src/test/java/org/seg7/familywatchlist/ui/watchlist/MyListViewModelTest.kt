package org.seg7.familywatchlist.ui.watchlist

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §2/§5: the Want-to-Watch list is one *shared* family list tagged with who added each
 * title, and the "added by me" toggle is a view over it — never a second list. These tests pin
 * that distinction, plus the added-by attribution the My List screen renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var clock: FakeClock

    private var kevId = 0L
    private var samId = 0L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        clock = FakeClock(startMillis = 1_000L)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock)
        profileRepository = ProfileRepository(db.profileDao(), clock)

        kevId = profileRepository.addProfile("Kev", "INITIAL|7C5C4A|", null).getOrThrow()
        samId = profileRepository.addProfile("Sam", "INITIAL|4A6357|", null).getOrThrow()

        listOf(38700 to "Paddington", 12345 to "Arrival").forEach { (id, name) ->
            db.titleDao().upsert(
                TitleEntity(
                    tmdbId = id, mediaType = MediaType.MOVIE, title = name, year = 2014,
                    posterPath = "/p.jpg", backdropPath = null, overview = null, runtimeMin = null,
                    certification = null, voteAverage = null, popularity = null,
                    trailerKey = null, fetchedAt = 1_000L,
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun viewModel() = MyListViewModel(watchlistRepository, profileRepository, kevId)

    @Test
    fun `the list is shared - it shows titles added by anyone by default`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        clock.advanceBy(1_000)
        watchlistRepository.add(12345, MediaType.MOVIE, samId)

        val state = viewModel().uiState.first { it.rows.size == 2 }
        assertEquals(setOf("Paddington", "Arrival"), state.rows.map { it.item.title }.toSet())
    }

    @Test
    fun `each row is attributed to the profile that added it`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, samId)

        val row = viewModel().uiState.first { it.rows.isNotEmpty() }.rows.first()
        assertEquals("Sam", row.addedBy?.name)
    }

    @Test
    fun `'added by me' filters to the active profile without touching the shared list`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        clock.advanceBy(1_000)
        watchlistRepository.add(12345, MediaType.MOVIE, samId)
        val vm = viewModel()
        vm.uiState.first { it.rows.size == 2 }

        vm.setMineOnly(true)

        val mine = vm.uiState.first { it.mineOnly }
        assertEquals(listOf("Paddington"), mine.rows.map { it.item.title })
        // The underlying list is untouched — both entries are still ACTIVE in the database.
        assertEquals(2, db.watchlistDao().observeByState(WatchlistState.ACTIVE).first().size)
    }

    @Test
    fun `newest addition comes first`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        clock.advanceBy(60_000)
        watchlistRepository.add(12345, MediaType.MOVIE, kevId)

        val state = viewModel().uiState.first { it.rows.size == 2 }
        assertEquals(listOf("Arrival", "Paddington"), state.rows.map { it.item.title })
    }

    @Test
    fun `removing a title drops it from the list`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        val vm = viewModel()
        vm.uiState.first { it.rows.isNotEmpty() }

        vm.remove(38700, MediaType.MOVIE)

        assertTrue(vm.uiState.first { it.rows.isEmpty() }.rows.isEmpty())
        // REMOVED, not deleted — PLAN.md §2 models this as a state on the entry.
        assertEquals(WatchlistState.REMOVED, watchlistRepository.get(38700, MediaType.MOVIE)?.state)
    }

    @Test
    fun `re-adding a removed title credits whoever added it back`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        watchlistRepository.remove(38700, MediaType.MOVIE)
        clock.advanceBy(90_000)

        watchlistRepository.toggle(38700, MediaType.MOVIE, samId)

        val entry = watchlistRepository.get(38700, MediaType.MOVIE)
        assertEquals(WatchlistState.ACTIVE, entry?.state)
        assertEquals(samId, entry?.addedByProfileId)
        // The fresh timestamp matters to PLAN.md §4's recencyWeight(addedAt) watchlist signal.
        assertEquals(clock.current, entry?.addedAt)
    }
}
