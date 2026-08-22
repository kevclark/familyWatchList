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
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
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
    private lateinit var ratingRepository: RatingRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository
    private lateinit var viewModel: HistoryViewModel

    private var kevId = 0L
    private var samId = 0L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        val clock = FakeClock(startMillis = 1_000L)
        watchEventRepository = WatchEventRepository(db.watchEventDao(), db.watchlistDao())
        profileRepository = ProfileRepository(db.profileDao(), clock)
        ratingRepository = RatingRepository(db.ratingDao(), clock)
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)

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

        viewModel = HistoryViewModel(watchEventRepository, profileRepository, ratingRepository, familyProfileRepository)
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

    /**
     * PLAN.md §5b M3i item 2: each tagged profile's current rating rides along on the row —
     * proven here against two profiles tagged on the same event with two different rating
     * values, plus a third, untagged profile's rating (Sam rating a title she isn't tagged as
     * having watched — the read-model is per-(profile, title), not per-(profile, event), so this
     * confirms only *tagged* profiles' ratings surface on a given row).
     */
    @Test
    fun `each row carries every tagged profile's current rating`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId, samId))
        ratingRepository.rate(kevId, 38700, MediaType.MOVIE, RatingValue.UP)
        ratingRepository.rate(samId, 38700, MediaType.MOVIE, RatingValue.DOWN)
        // Sam also has an opinion on a title she isn't tagged as watching here — must not leak in.
        ratingRepository.rate(samId, 12345, MediaType.MOVIE, RatingValue.UP)

        val row = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.first()

        assertEquals(mapOf(kevId to RatingValue.UP, samId to RatingValue.DOWN), row.ratings)
    }

    /** PLAN.md §5b M3i item 2: an untagged/unrated title's row has no ratings at all — nothing to render. */
    @Test
    fun `a row with no ratings carries an empty ratings map`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId))

        val row = viewModel.uiState.first { it.rows.isNotEmpty() }.rows.first()

        assertTrue(row.ratings.isEmpty())
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

    /**
     * PLAN.md §4b (M3j): with no Family profile persisted yet, the filter chip row must offer
     * only real profiles — same as before this milestone.
     */
    @Test
    fun `no Family filter option is offered before a Family profile exists`() = runTest {
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId))

        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertEquals(setOf(kevId, samId), state.profiles.map { it.id }.toSet())
    }

    /**
     * PLAN.md §4b (M3j): once a Family profile exists, it's a selectable filter option
     * (id = [FAMILY_PROFILE_SENTINEL_ID]) alongside every real profile — and selecting it filters
     * to events Family itself was tagged on (the M3j dual-tag), same mechanism as any real
     * profile's filter.
     */
    @Test
    fun `Family becomes a selectable filter option once it exists, and filters to its own tagged events`() = runTest {
        familyProfileRepository.save("Family", "avatar", listOf(kevId, samId)).getOrThrow()
        log(38700, LocalDate.of(2026, 8, 1), listOf(kevId, samId, FAMILY_PROFILE_SENTINEL_ID))
        log(12345, LocalDate.of(2026, 8, 2), listOf(kevId)) // not tagged to Family

        val withOption = viewModel.uiState.first { it.profiles.any { p -> p.id == FAMILY_PROFILE_SENTINEL_ID } }
        assertEquals(setOf(kevId, samId, FAMILY_PROFILE_SENTINEL_ID), withOption.profiles.map { it.id }.toSet())

        viewModel.setFilter(FAMILY_PROFILE_SENTINEL_ID)
        val familyOnly = viewModel.uiState.first { it.filterProfileId == FAMILY_PROFILE_SENTINEL_ID }
        assertEquals(listOf("Paddington"), familyOnly.rows.map { it.event.title })

        // The synthetic Family filter option must never leak into a row's own "watched by" avatar
        // list — that already shows every real member via the dual-tag, so a redundant "Family"
        // avatar there would just be clutter (see HistoryViewModel's kdoc).
        assertEquals(setOf("Kev", "Sam"), familyOnly.rows.single().watchedBy.map { it.name }.toSet())
    }
}
