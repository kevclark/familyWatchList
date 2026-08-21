package org.seg7.familywatchlist.ui.details

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 4's "Because you liked …" reason line (M3c):
 * [RecommendationRepository.reasonsForShortlistEntry] is what [TitleDetailViewModel] calls to
 * decide whether to show it — present only when this exact (tmdbId, mediaType) currently has a
 * still-SUGGESTED entry in the *active profile's own* persisted shortlist, parsed from that
 * entry's `reasons` JSON; absent for every other path (Search/My List/History, a DISMISSED entry,
 * or one that belongs to a different profile's/scope's shortlist).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TitleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var titleRepository: TitleRepository
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var ratingRepository: RatingRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    private val profileId = 1L
    private val tmdbId = 42
    private val mediaType = MediaType.MOVIE

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })

        titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        ratingRepository = RatingRepository(db.ratingDao(), clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(),
            ratingDao = db.ratingDao(),
            watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(),
            titleRepository = titleRepository,
            discoverRepository = discoverRepository,
            providerRepository = providerRepository,
            profileRepository = ProfileRepository(db.profileDao(), clock),
            profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao()),
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("detail_vm_prefs_${System.nanoTime()}") }),
        )

        // A fully-detailed, freshly-fetched row — TitleDetailViewModel's init-time refresh()
        // calls TitleRepository.ensureFresh(), which treats this as already fresh (non-stub,
        // within TTL) and never touches the network, keeping this suite MockWebServer-free.
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = tmdbId, mediaType = mediaType, title = "Toy Story 2", year = 1999,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = 92,
                certification = "U", voteAverage = 8.0, voteCount = 500, popularity = 50.0,
                trailerKey = null, fetchedAt = clock.current,
            )
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun viewModel() = TitleDetailViewModel(
        titleRepository,
        watchlistRepository,
        ratingRepository,
        recommendationRepository,
        tmdbId,
        mediaType,
        profileId,
        userPreferencesRepository,
    )

    @Test
    fun `no reason line when this title has no shortlist entry at all`() = runTest {
        val state = viewModel().uiState.first { it.title != null }

        assertNull(state.reasons)
    }

    @Test
    fun `reason line renders the parsed attribute names from a real SUGGESTED entry`() = runTest {
        val weekStart = recommendationRepository.currentWeekStart()
        db.shortlistDao().upsertAll(
            listOf(
                ShortlistEntryEntity(
                    weekStart = weekStart,
                    scopeKey = profileId.toString(),
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    score = 0.9,
                    reasons = """["John Lasseter","Animation","Comedy"]""",
                    state = ShortlistState.SUGGESTED,
                )
            )
        )

        val state = viewModel().uiState.first { it.reasons != null }

        assertEquals(listOf("John Lasseter", "Animation", "Comedy"), state.reasons)
    }

    @Test
    fun `a DISMISSED entry for this title never produces a reason line`() = runTest {
        val weekStart = recommendationRepository.currentWeekStart()
        db.shortlistDao().upsertAll(
            listOf(
                ShortlistEntryEntity(
                    weekStart = weekStart,
                    scopeKey = profileId.toString(),
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    score = 0.9,
                    reasons = """["Comedy"]""",
                    state = ShortlistState.DISMISSED,
                )
            )
        )

        val state = viewModel().uiState.first { it.title != null }

        assertNull(state.reasons)
    }

    @Test
    fun `a SUGGESTED entry under a different profile's scope does not leak into this profile's reason line`() = runTest {
        val weekStart = recommendationRepository.currentWeekStart()
        db.shortlistDao().upsertAll(
            listOf(
                ShortlistEntryEntity(
                    weekStart = weekStart,
                    scopeKey = "999", // a different profileId's own scope
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    score = 0.9,
                    reasons = """["Comedy"]""",
                    state = ShortlistState.SUGGESTED,
                )
            )
        )

        val state = viewModel().uiState.first { it.title != null }

        assertNull(state.reasons)
    }
}
