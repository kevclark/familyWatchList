package org.seg7.familywatchlist.ui.logwatch

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 6, and the highest-stakes logic in M2b — this is where user data is
 * actually written, and where PLAN.md §2's two cross-cutting rules land: multi-tagging one
 * event with N profiles, and auto-flipping a listed title to WATCHED.
 *
 * `today` is injected as a fixed date rather than read from the wall clock, so the
 * future-date rule can be tested without the test itself depending on when it runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogWatchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var watchEventRepository: WatchEventRepository
    private lateinit var ratingRepository: RatingRepository
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var titleRepository: TitleRepository

    private val today = LocalDate.of(2026, 8, 17)
    private val tmdbId = 38700
    private val mediaType = MediaType.MOVIE

    private var kevId = 0L
    private var samId = 0L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)

        watchEventRepository = WatchEventRepository(db.watchEventDao(), db.watchlistDao())
        ratingRepository = RatingRepository(db.ratingDao(), clock)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        titleRepository = TitleRepository(
            db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock,
        )

        kevId = profileRepository.addProfile("Kev", "INITIAL|7C5C4A|", null).getOrThrow()
        samId = profileRepository.addProfile("Sam", "INITIAL|4A6357|", null).getOrThrow()
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = tmdbId, mediaType = mediaType, title = "Paddington", year = 2014,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = 95,
                certification = "PG", voteAverage = 7.2, popularity = 33.1,
                trailerKey = null, fetchedAt = 1_000L,
            )
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun viewModel(editingEventId: Long? = null) = LogWatchViewModel(
        watchEventRepository = watchEventRepository,
        ratingRepository = ratingRepository,
        titleRepository = titleRepository,
        profileRepository = profileRepository,
        tmdbId = tmdbId,
        mediaType = mediaType,
        activeProfileId = kevId,
        today = today,
        editingEventId = editingEventId,
    )

    @Test
    fun `the sheet opens ready to save - today's date, active profile ticked`() = runTest {
        val vm = viewModel()

        val state = vm.uiState.first { it.profiles.isNotEmpty() }
        assertEquals(today, state.watchedAt)
        assertEquals(setOf(kevId), state.selectedProfileIds)
        assertEquals("Paddington", state.titleName)
    }

    @Test
    fun `saving with the defaults writes one event tagged with the active profile`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.save()

        vm.uiState.first { it.saved }
        val events = db.watchEventDao().observeAll().first()
        assertEquals(1, events.size)
        assertEquals(today, events.first().watchedAt)
        assertEquals(listOf(kevId), watchEventRepository.getProfileIds(events.first().id))
    }

    @Test
    fun `family night is one event tagged with several profiles, per PLAN section 2`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.toggleProfile(samId)
        vm.save()

        vm.uiState.first { it.saved }
        val events = db.watchEventDao().observeAll().first()
        assertEquals("one event, not one per person", 1, events.size)
        assertEquals(setOf(kevId, samId), watchEventRepository.getProfileIds(events.first().id).toSet())
    }

    @Test
    fun `logging a watch auto-flips an active watchlist entry to WATCHED`() = runTest {
        watchlistRepository.add(tmdbId, mediaType, kevId)
        assertEquals(WatchlistState.ACTIVE, watchlistRepository.get(tmdbId, mediaType)?.state)

        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }
        vm.save()
        vm.uiState.first { it.saved }

        assertEquals(WatchlistState.WATCHED, watchlistRepository.get(tmdbId, mediaType)?.state)
    }

    @Test
    fun `per-profile thumbs set in the sheet are written as ratings`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.toggleProfile(samId)
        vm.setRating(kevId, RatingValue.UP)
        vm.setRating(samId, RatingValue.DOWN)
        vm.save()
        vm.uiState.first { it.saved }

        assertEquals(RatingValue.UP, ratingRepository.get(kevId, tmdbId, mediaType)?.value)
        assertEquals(RatingValue.DOWN, ratingRepository.get(samId, tmdbId, mediaType)?.value)
    }

    @Test
    fun `thumbs are optional - saving without any writes no ratings`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.save()
        vm.uiState.first { it.saved }

        assertNull(ratingRepository.get(kevId, tmdbId, mediaType))
    }

    @Test
    fun `tapping the same thumb twice clears it rather than re-setting it`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.setRating(kevId, RatingValue.UP)
        assertEquals(RatingValue.UP, vm.uiState.first { it.ratings.isNotEmpty() }.ratings[kevId])

        vm.setRating(kevId, RatingValue.UP)
        assertNull(vm.uiState.first { it.ratings.isEmpty() }.ratings[kevId])
    }

    @Test
    fun `untagging someone drops the thumbs that were set for them`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.toggleProfile(samId)
        vm.setRating(samId, RatingValue.UP)
        vm.uiState.first { it.ratings.containsKey(samId) }

        vm.toggleProfile(samId)

        val state = vm.uiState.first { samId !in it.selectedProfileIds }
        assertNull("a rating for someone who didn't watch it is meaningless", state.ratings[samId])
    }

    @Test
    fun `saving with nobody tagged is rejected and writes nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.toggleProfile(kevId) // untick the only selected profile
        vm.save()

        val state = vm.uiState.first { it.validationError != null }
        assertTrue(state.validationError!!.contains("at least one person"))
        assertTrue(db.watchEventDao().observeAll().first().isEmpty())
    }

    @Test
    fun `a future date is rejected and writes nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.first { it.profiles.isNotEmpty() }

        vm.setDate(today.plusDays(1))
        vm.save()

        val state = vm.uiState.first { it.validationError != null }
        assertTrue(state.validationError!!.contains("future"))
        assertTrue(db.watchEventDao().observeAll().first().isEmpty())
    }

    @Test
    fun `today itself is allowed - the boundary is 'after today', not 'before today'`() {
        assertNull(LogWatchViewModel.validate(setOf(1L), today, today))
        assertNotNull(LogWatchViewModel.validate(setOf(1L), today.plusDays(1), today))
        assertNull(LogWatchViewModel.validate(setOf(1L), today.minusDays(30), today))
        assertNotNull(LogWatchViewModel.validate(emptySet(), today, today))
    }

    @Test
    fun `editing loads the existing event's date and tags rather than the defaults`() = runTest {
        val watchedAt = LocalDate.of(2026, 7, 1)
        val eventId = watchEventRepository.logWatch(tmdbId, mediaType, watchedAt, null, listOf(samId))

        val vm = viewModel(editingEventId = eventId)

        val state = vm.uiState.first { it.selectedProfileIds == setOf(samId) }
        assertEquals(watchedAt, state.watchedAt)
        assertTrue(state.isEditing)
    }

    @Test
    fun `saving an edit updates the event in place instead of logging a second one`() = runTest {
        val eventId = watchEventRepository.logWatch(
            tmdbId, mediaType, LocalDate.of(2026, 7, 1), null, listOf(samId),
        )
        val vm = viewModel(editingEventId = eventId)
        vm.uiState.first { it.selectedProfileIds == setOf(samId) }

        vm.setDate(LocalDate.of(2026, 8, 1))
        vm.toggleProfile(kevId)
        vm.save()
        vm.uiState.first { it.saved }

        val events = db.watchEventDao().observeAll().first()
        assertEquals(1, events.size)
        assertEquals(eventId, events.first().id)
        assertEquals(LocalDate.of(2026, 8, 1), events.first().watchedAt)
        assertEquals(setOf(kevId, samId), watchEventRepository.getProfileIds(eventId).toSet())
    }
}
