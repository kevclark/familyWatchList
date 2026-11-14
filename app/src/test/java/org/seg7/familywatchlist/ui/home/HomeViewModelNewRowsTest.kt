package org.seg7.familywatchlist.ui.home

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
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb
import org.seg7.familywatchlist.ui.ActiveProfile

/** PLAN.md §5i: the New films / New series rows as Home's view model exposes them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelNewRowsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var discoverRepository: DiscoverRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var titleRepository: TitleRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository

    private var popularMovies = emptyList<Pair<Int, String>>()
    private var newMovies = emptyList<Pair<Int, String>>()
    private var newMoviesIncluded = emptyList<Pair<Int, String>>()
    private var popularTv = emptyList<Pair<Int, String>>()
    private var newTv = emptyList<Pair<Int, String>>()
    private var failNewMovies = false

    private val activeProfile = ActiveProfile.Individual(
        ProfileEntity(id = 7L, name = "P", avatarKey = "a", ageRatingCap = null, createdAt = 0),
    )

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val t = request.target
                val body = when {
                    t.startsWith("/discover/movie") && t.contains("primary_release_date") -> {
                        if (failNewMovies) return MockResponse(code = 500)
                        page(if (t.contains("rent")) newMovies else newMoviesIncluded)
                    }
                    t.startsWith("/discover/movie") -> page(popularMovies)
                    t.startsWith("/discover/tv") && t.contains("first_air_date") -> page(newTv)
                    t.startsWith("/discover/tv") -> page(popularTv)
                    else -> return MockResponse(code = 404)
                }
                return MockResponse(body = body)
            }
        }
        server.start()
        clock = FakeClock(startMillis = 1_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("home_new_rows_${System.nanoTime()}") }),
        )
        titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), db.reviewDao(), api, clock)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)
        recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(), ratingDao = db.ratingDao(), watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(), titleRepository = titleRepository,
            discoverRepository = discoverRepository, providerRepository = providerRepository,
            availabilityGate = AvailabilityGate(titleRepository, providerRepository),
            profileRepository = profileRepository,
            profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao()),
            familyProfileRepository = familyProfileRepository, shortlistDao = db.shortlistDao(), clock = clock,
        )
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun page(entries: List<Pair<Int, String>>): String {
        val results = entries.joinToString(",") { (id, title) ->
            """{"id": $id, "title": "$title", "name": "$title", "poster_path": "/p.jpg", "release_date": "2026-10-01", "popularity": 50.0}"""
        }
        return """{"page":1,"results":[$results],"total_pages":1,"total_results":${entries.size}}"""
    }

    private fun watchlist() = WatchlistRepository(db.watchlistDao(), clock, isAvailable = { _, _, _ -> true })

    private fun viewModel(profile: ActiveProfile = activeProfile) = HomeViewModel(
        discoverRepository, providerRepository, watchlist(), userPreferencesRepository, recommendationRepository,
        titleRepository, profileRepository, familyProfileRepository, profile,
    )

    private suspend fun seedCert(id: Int, type: MediaType, cert: String?) = db.titleDao().upsert(
        TitleEntity(
            tmdbId = id, mediaType = type, title = "t$id", year = 2026, posterPath = null, backdropPath = null,
            overview = null, runtimeMin = 100, certification = cert, voteAverage = 7.0, popularity = 50.0,
            trailerKey = null, fetchedAt = clock.current,
        ),
    )

    @Test
    fun `both New rows are filled, and a rent-only film is flagged paid-only`() = runTest {
        popularMovies = listOf(1 to "Pop")
        newMovies = listOf(10 to "Fresh", 11 to "Rent only")
        newMoviesIncluded = listOf(10 to "Fresh")
        newTv = listOf(20 to "New show")

        val state = viewModel().uiState.first { it.newMovies.isNotEmpty() }

        assertEquals(listOf("Fresh", "Rent only"), state.newMovies.map { it.title })
        assertEquals(setOf(11), state.newMoviePaidOnlyIds)
        assertEquals(listOf("New show"), state.newTv.map { it.title })
        assertEquals(listOf("Pop"), state.popularMovies.map { it.title })
    }

    @Test
    fun `a capped profile sees only confirmed at-or-under-cap titles in both New rows`() = runTest {
        newMovies = listOf(10 to "Over", 11 to "At cap", 12 to "Unknown")
        newMoviesIncluded = newMovies
        newTv = listOf(20 to "Over tv", 21 to "At cap tv", 22 to "Unknown tv")
        seedCert(10, MediaType.MOVIE, "18"); seedCert(11, MediaType.MOVIE, "12"); seedCert(12, MediaType.MOVIE, null)
        seedCert(20, MediaType.TV, "18"); seedCert(21, MediaType.TV, "12"); seedCert(22, MediaType.TV, null)
        val id = profileRepository.addProfile("Kid", "avatar", "12").getOrThrow()
        val kid = ActiveProfile.Individual(profileRepository.getById(id)!!)

        val state = viewModel(kid).uiState.first { it.newMovies.isNotEmpty() && it.newTv.isNotEmpty() }

        assertEquals(setOf(11), state.newMovies.map { it.tmdbId }.toSet())
        assertEquals(setOf(21), state.newTv.map { it.tmdbId }.toSet())
    }

    @Test
    fun `a dismissed title leaves the New rows at once`() = runTest {
        newMovies = listOf(10 to "A", 11 to "B")
        newMoviesIncluded = newMovies
        newTv = listOf(20 to "S")
        val vm = viewModel()
        vm.uiState.first { it.newMovies.size == 2 && it.newTv.size == 1 }

        vm.dismissTitle(10, MediaType.MOVIE)
        vm.dismissTitle(20, MediaType.TV)

        val state = vm.uiState.first { it.newMovies.size == 1 }
        assertEquals(listOf(11), state.newMovies.map { it.tmdbId })
        assertTrue(state.newTv.isEmpty())
    }

    @Test
    fun `nothing new leaves both rows empty so the screen hides them`() = runTest {
        popularMovies = listOf(1 to "Pop")

        val state = viewModel().uiState.first { it.popularMovies.isNotEmpty() }

        assertTrue(state.newMovies.isEmpty())
        assertTrue(state.newTv.isEmpty())
    }

    @Test
    fun `a failing New films request leaves the popular rows and New series intact`() = runTest {
        failNewMovies = true
        popularMovies = listOf(1 to "Pop")
        newTv = listOf(20 to "S")

        val state = viewModel().uiState.first { it.newTv.isNotEmpty() }

        assertTrue(state.newMovies.isEmpty())
        assertEquals(listOf("Pop"), state.popularMovies.map { it.title })
    }

    @Test
    fun `the plus button adds to the watch list and flips the listed state`() = runTest {
        newTv = listOf(20 to "S")
        val vm = viewModel()
        val title = vm.uiState.first { it.newTv.isNotEmpty() }.newTv.single()

        vm.toggleWatchlist(title)

        val state = vm.uiState.first { (20 to MediaType.TV) in it.listedKeys }
        assertTrue((20 to MediaType.TV) in state.listedKeys)
    }
}
