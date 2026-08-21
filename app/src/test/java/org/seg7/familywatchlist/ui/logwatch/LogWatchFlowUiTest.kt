package org.seg7.familywatchlist.ui.logwatch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
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
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * PLAN.md §7's single named Compose UI test: **the log-watch flow**. Everything else in M2b is
 * covered at the ViewModel level, where the logic actually lives; this exists because logging a
 * watch is the one flow where the *wiring* — chip taps reaching the ViewModel, validation
 * surfacing in the UI, Save writing through to Room — is the thing that can break without any
 * unit test noticing.
 *
 * It runs on the JVM under Robolectric (not `androidTest`) so it's part of `./gradlew test` and
 * needs no booted emulator, and it drives [LogWatchSheetContent] rather than [LogWatchSheet] —
 * the `ModalBottomSheet` wrapper adds window and animation machinery with no behaviour of its
 * own, and is a well-known source of flakiness under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogWatchFlowUiTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var watchEventRepository: WatchEventRepository
    private lateinit var ratingRepository: RatingRepository
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var titleRepository: TitleRepository

    private val today = LocalDate.of(2026, 8, 17)
    private val tmdbId = 38700
    private var kevId = 0L
    private var samId = 0L

    @Before
    fun setUp() = runBlocking {
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
                tmdbId = tmdbId, mediaType = MediaType.MOVIE, title = "Paddington", year = 2014,
                posterPath = "/poster.jpg", backdropPath = null, overview = null, runtimeMin = 95,
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

    /** Set by the sheet's onSaved callback — proves the sheet asks to close itself. */
    @Volatile
    private var dismissed = false
    private lateinit var viewModel: LogWatchViewModel

    private fun setSheet() {
        viewModel = LogWatchViewModel(
            watchEventRepository = watchEventRepository,
            ratingRepository = ratingRepository,
            titleRepository = titleRepository,
            profileRepository = profileRepository,
            tmdbId = tmdbId,
            mediaType = MediaType.MOVIE,
            initialSelectedProfileIds = setOf(kevId),
            today = today,
            editingEventId = null,
        )
        composeRule.setContent {
            FamilyWatchListTheme {
                LogWatchSheetContent(
                    viewModel = viewModel,
                    onSaved = { dismissed = true },
                    onCancel = {},
                )
            }
        }
    }

    /**
     * Clicks Save and waits until the write has actually landed.
     *
     * The signal is the ViewModel's own `saved` state, which only flips after `logWatch` and any
     * ratings have completed — a plain flag set from the `onSaved` callback would be written on
     * whichever thread Room resumed the write on and polled from the test thread.
     *
     * It's polled through `waitUntil` on `StateFlow.value` (a synchronised read, and the sheet
     * is an active collector so the value stays current) rather than collected inside
     * `runBlocking`: blocking this thread would starve the very Main dispatcher the ViewModel's
     * coroutines need, and the wait would deadlock instead of timing out.
     */
    private fun saveAndAwait() {
        // Scroll first: ticking a second person adds another per-profile thumbs row, which is
        // enough to push Save below the fold in the test window. Without this the click lands
        // outside the viewport and silently does nothing.
        composeRule.onNodeWithTag(LogWatchTags.SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.saved }
        composeRule.waitForIdle()
    }

    @Test
    fun `the common case is one tap - open the sheet, hit Log it, the watch is recorded`() {
        setSheet()

        composeRule.onNodeWithText("Paddington").assertIsDisplayed()
        // Defaults are already correct: today's date and the active profile.
        composeRule.onNodeWithText("Today").assertIsDisplayed()

        saveAndAwait()

        val events = runBlocking { db.watchEventDao().observeAll().first() }
        assertEquals(1, events.size)
        assertEquals(today, events.first().watchedAt)
        assertEquals(listOf(kevId), runBlocking { watchEventRepository.getProfileIds(events.first().id) })
        assertTrue("the sheet closes itself on a successful save", dismissed)
    }

    @Test
    fun `ticking a second person records one shared event, not two`() {
        setSheet()

        composeRule.onNodeWithTag(LogWatchTags.profileChip(samId)).performClick()
        saveAndAwait()

        val events = runBlocking { db.watchEventDao().observeAll().first() }
        assertEquals(1, events.size)
        assertEquals(
            setOf(kevId, samId),
            runBlocking { watchEventRepository.getProfileIds(events.first().id) }.toSet(),
        )
    }

    @Test
    fun `unticking everyone blocks the save and shows why`() {
        setSheet()

        // Kev is ticked by default; untick him so nobody is selected.
        composeRule.onNodeWithTag(LogWatchTags.profileChip(kevId)).performClick()
        composeRule.onNodeWithTag(LogWatchTags.SAVE).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LogWatchTags.ERROR).assertIsDisplayed()
        assertTrue("nothing was written", runBlocking { db.watchEventDao().observeAll().first() }.isEmpty())
        assertTrue("the sheet stays open", !dismissed)
    }

    @Test
    fun `logging a listed title flips it to WATCHED, end to end through the UI`() {
        runBlocking { watchlistRepository.add(tmdbId, MediaType.MOVIE, kevId) }
        setSheet()

        saveAndAwait()

        assertEquals(
            WatchlistState.WATCHED,
            runBlocking { watchlistRepository.get(tmdbId, MediaType.MOVIE)?.state },
        )
    }
}
