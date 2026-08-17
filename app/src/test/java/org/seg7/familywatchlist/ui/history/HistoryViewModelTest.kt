package org.seg7.familywatchlist.ui.history

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 7: reverse-chronological, filter by profile, edit/delete.
 *
 * The interesting cases are all about the multi-tag model from §2: an event tagged with two
 * people must appear under *both* of their filters (and only once each), and deleting an event
 * must take its tags with it — [WatchEventProfileEntity] has no foreign key, so nothing
 * cascades them away automatically.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var watchEventRepository: WatchEventRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var viewModel: HistoryViewModel

    private var kevId = 0L
    private var samId = 0L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        val clock = FakeClock(startMillis = 1_000L)
        watchEventRepository = WatchEventRepository(db.watchEventDao(), db.watchlistDao())
        profileRepository = ProfileRepository(db.profileDao(), clock)

        kevId = profileRepository.addProfile("Kev", "INITIAL|7C5C4A|", null).getOrThrow()
        samId = profileRepository.addProfile("Sam", "INITIAL|4A6357|", null).getOrThrow()

        listOf(38700 to "Paddington", 12345 to "Arrival", 999 to "Taskmaster").forEach { (id, name) ->
            db.titleDao().upsert(
                TitleEntity(
                    tmdbId = id, mediaType = MediaType.MOVIE, title = name, year = 2014,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                    certification = null, voteAverage = null, popularity = null,
                    trailerKey = null, fetchedAt = 1_000L,
                )
            )
        }

        viewModel = HistoryViewModel(watchEventRepository, profileRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun log(tmdbId: Int, date: LocalDate, profiles: List<Long>) =
        watchEventRepository.logWatch(tmdbId, MediaType.MOVIE, date, null, profiles)

    @Test
    fun `history is newest first`() = runTest {
        log(38700, LocalDate.of(2026, 1, 1), listOf(kevId))
        log(12345, LocalDate.of(2026, 8, 1), listOf(kevId))
        log(999, LocalDate.of(2026, 4, 1), listOf(kevId))

        val state = viewModel.uiState.first { it.rows.size == 3 }
        assertEquals(
            listOf("Arrival", "Taskmaster", "Paddington"),
            state.rows.map { it.event.title },
        )
    }

    @Test
    fun `each row carries the profiles tagged on that event`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId, samId))

        val row = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.first()
        assertEquals(setOf("Kev", "Sam"), row.watchedBy.map { it.name }.toSet())
    }

    @Test
    fun `filtering by profile keeps only that person's watches`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId))
        log(12345, LocalDate.of(2026, 8, 2), listOf(samId))
        viewModel.uiState.first { it.rows.size == 2 }

        viewModel.setFilter(kevId)
        val kevOnly = viewModel.uiState.first { it.filterProfileId == kevId }
        assertEquals(listOf("Paddington"), kevOnly.rows.map { it.event.title })

        viewModel.setFilter(samId)
        val samOnly = viewModel.uiState.first { it.filterProfileId == samId }
        assertEquals(listOf("Arrival"), samOnly.rows.map { it.event.title })
    }

    @Test
    fun `a shared watch shows up under both people's filters, once each`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId, samId))
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.setFilter(kevId)
        assertEquals(1, viewModel.uiState.first { it.filterProfileId == kevId }.rows.size)

        viewModel.setFilter(samId)
        assertEquals(1, viewModel.uiState.first { it.filterProfileId == samId }.rows.size)
    }

    @Test
    fun `clearing the filter shows everyone again`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId))
        log(12345, LocalDate.of(2026, 8, 2), listOf(samId))
        viewModel.setFilter(kevId)
        viewModel.uiState.first { it.rows.size == 1 }

        viewModel.setFilter(null)

        assertEquals(2, viewModel.uiState.first { it.filterProfileId == null && it.rows.size == 2 }.rows.size)
    }

    @Test
    fun `an empty filter result is distinguishable from an empty history`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId))
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.setFilter(samId)

        val state = viewModel.uiState.first { it.filterProfileId == samId }
        assertTrue("no rows match this filter", state.rows.isEmpty())
        // …but history itself is not empty, so the UI must show a different message.
        assertFalse("history overall has entries", state.isEmpty)
    }

    @Test
    fun `deleting an event removes it and its profile tags`() = runTest {
        val eventId = log(38700, LocalDate.of(2026, 8, 1), listOf(kevId, samId))
        viewModel.uiState.first { it.rows.isNotEmpty() }

        viewModel.deleteEvent(eventId)

        val state = viewModel.uiState.first { it.rows.isEmpty() }
        assertTrue(state.isEmpty)
        // Orphan tags would silently skew PLAN.md §4's per-profile affinity corpus.
        assertTrue(watchEventRepository.getProfileIds(eventId).isEmpty())
    }
}
