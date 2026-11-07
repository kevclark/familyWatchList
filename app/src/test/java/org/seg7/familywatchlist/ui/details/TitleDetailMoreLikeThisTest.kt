package org.seg7.familywatchlist.ui.details

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.AvailabilityGate
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.MoreLikeThisRepository
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
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/** PLAN.md §5h: the detail screen's "More like this" row, from ViewModel state through to the composable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TitleDetailMoreLikeThisTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var titleRepository: TitleRepository
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var ratingRepository: RatingRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var moreLikeThis: MoreLikeThisRepository
    private val paths = mutableListOf<String>()
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private var profileId = 0L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(paths) { paths += request.url.encodedPath }
                return routes[request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
            }
        }
        server.start()
        clock = FakeClock(startMillis = 1_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), db.reviewDao(), api, clock)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        ratingRepository = RatingRepository(db.ratingDao(), clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val gate = AvailabilityGate(titleRepository, providerRepository)
        recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(),
            ratingDao = db.ratingDao(),
            watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(),
            titleRepository = titleRepository,
            discoverRepository = discoverRepository,
            providerRepository = providerRepository,
            availabilityGate = gate,
            profileRepository = ProfileRepository(db.profileDao(), clock),
            profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao()),
            familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock),
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("more_like_this_prefs_${System.nanoTime()}") }),
        )
        moreLikeThis = MoreLikeThisRepository(db.titleDao(), api, clock, gate)
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "P8", null, subscribed = true, displayPriority = 8)))
        // The profile has a cap of 12, so an 18 recommendation must not appear.
        profileId = db.profileDao().insert(ProfileEntity(name = "Kid", avatarKey = "a", ageRatingCap = "12", createdAt = 1L))
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = 42, mediaType = MediaType.MOVIE, title = "Source", year = 2000, posterPath = null, backdropPath = null,
                overview = null, runtimeMin = 90, certification = "PG", voteAverage = 7.0, voteCount = 100, popularity = 5.0,
                trailerKey = null, fetchedAt = clock.current,
            )
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun count(path: String) = synchronized(paths) { paths.count { it == path } }

    private fun viewModel() = TitleDetailViewModel(
        titleRepository, watchlistRepository, ratingRepository, recommendationRepository,
        42, MediaType.MOVIE, profileId, userPreferencesRepository, moreLikeThis,
    )

    private fun recs(vararg items: Pair<Int, String>) = MockResponse(
        body = """{"page":1,"results":[${items.joinToString(",") { """{"id":${it.first},"title":"${it.second}","popularity":1.0}""" }}],"total_pages":1,"total_results":${items.size}}""",
    )

    private fun detail(id: Int, cert: String) = MockResponse(
        body = """{"id":$id,"title":"T$id","watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":8,"provider_name":"P"}]}}},"release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"$cert","type":3}]}]}}""",
    )

    @Test
    fun `row holds passing recommendations in order and applies the active profile's cap`() = runTest {
        routes["/movie/42/recommendations"] = { recs(7 to "Seven", 8 to "Eighteen", 3 to "Three") }
        routes["/movie/7"] = { detail(7, "PG") }
        routes["/movie/8"] = { detail(8, "18") }
        routes["/movie/3"] = { detail(3, "12A") }

        val state = viewModel().uiState.first { it.title != null && !it.moreLikeThisLoading }

        assertEquals(listOf(7, 3), state.moreLikeThis.map { it.tmdbId })
    }

    @Test
    fun `nothing passing means an empty settled row, which the screen hides`() = runTest {
        routes["/movie/42/recommendations"] = { recs(8 to "Eighteen") }
        routes["/movie/8"] = { detail(8, "18") }

        val state = viewModel().uiState.first { it.title != null && !it.moreLikeThisLoading }

        assertTrue(state.moreLikeThis.isEmpty())
    }

    @Test
    fun `API failure hides the row with no error surfaced`() = runTest {
        routes["/movie/42/recommendations"] = { MockResponse(code = 500) }

        val state = viewModel().uiState.first { it.title != null && !it.moreLikeThisLoading }

        assertTrue(state.moreLikeThis.isEmpty())
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `reopening the screen does not refetch recommendations`() = runTest {
        routes["/movie/42/recommendations"] = { recs(7 to "Seven") }
        routes["/movie/7"] = { detail(7, "PG") }

        viewModel().uiState.first { it.title != null && !it.moreLikeThisLoading && it.moreLikeThis.isNotEmpty() }
        val again = viewModel().uiState.first { it.title != null && !it.moreLikeThisLoading }

        assertEquals(listOf(7), again.moreLikeThis.map { it.tmdbId })
        assertEquals(1, count("/movie/42/recommendations"))
    }

    // ---- composable -----------------------------------------------------------------------

    private fun entity(id: Int, name: String, type: MediaType = MediaType.MOVIE) = TitleEntity(
        tmdbId = id, mediaType = type, title = name, year = null, posterPath = null, backdropPath = null, overview = null,
        runtimeMin = null, certification = null, voteAverage = null, voteCount = null, popularity = null,
        trailerKey = null, fetchedAt = 0L,
    )

    @Test
    fun `row renders its title and posters and tapping one opens that title`() {
        val opened = mutableListOf<Pair<Int, MediaType>>()
        composeRule.setContent {
            FamilyWatchListTheme {
                MoreLikeThisRow(
                    titles = listOf(entity(7, "Spooks"), entity(9, "The Capture", MediaType.TV)),
                    loading = false,
                    onOpenTitle = { id, type -> opened += id to type },
                )
            }
        }

        composeRule.onNodeWithText("More like this").assertExists()
        composeRule.onNodeWithText("Spooks").assertExists()
        composeRule.onNodeWithText("The Capture").performClick()

        assertEquals(listOf(9 to MediaType.TV), opened)
    }

    @Test
    fun `empty and settled renders nothing`() {
        composeRule.setContent {
            FamilyWatchListTheme { MoreLikeThisRow(titles = emptyList(), loading = false, onOpenTitle = { _, _ -> }) }
        }
        composeRule.onAllNodesWithText("More like this").assertCountEquals(0)
    }

    @Test
    fun `empty while loading shows only the quiet header`() {
        composeRule.setContent {
            FamilyWatchListTheme { MoreLikeThisRow(titles = emptyList(), loading = true, onOpenTitle = { _, _ -> }) }
        }
        composeRule.onNodeWithText("More like this").assertExists()
    }
}
