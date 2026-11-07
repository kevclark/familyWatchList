package org.seg7.familywatchlist.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.AvailabilityGate
import org.seg7.familywatchlist.data.repository.DescriptiveSearchRepository
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.SearchRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.data.search.AgeFilter
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/** PLAN.md §5f: chip removal reruns, age chip defaults (profile / Family), age chip reruns, titles-instead link, load more. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DescriptiveSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var profileRepository: ProfileRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository
    private lateinit var prefs: UserPreferencesRepository
    private val requests = mutableListOf<HttpUrl>()
    private val routes = mutableMapOf<String, () -> String>()
    private lateinit var builder: (Long) -> SearchViewModel

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request.url }
                val path = request.url.encodedPath
                val route = routes.entries.firstOrNull { (prefix, _) -> path == prefix || path.startsWith("$prefix/") }
                return route?.let { MockResponse(body = it.value()) } ?: MockResponse(code = 404)
            }
        }
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        prefs = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("desc_search_vm_prefs") }),
        )
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), db.reviewDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val gate = AvailabilityGate(titleRepository, providerRepository)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)
        val recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(), ratingDao = db.ratingDao(), watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(), titleRepository = titleRepository, discoverRepository = discoverRepository,
            providerRepository = providerRepository, availabilityGate = gate, profileRepository = profileRepository,
            profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao()),
            familyProfileRepository = familyProfileRepository, shortlistDao = db.shortlistDao(), clock = clock,
        )
        builder = { profileId ->
            SearchViewModel(
                searchRepository = SearchRepository(db.titleDao(), api, clock, gate),
                watchlistRepository = WatchlistRepository(db.watchlistDao(), clock, gate::isAvailableOnSubscribedProvider),
                providerRepository = providerRepository,
                activeProfileId = profileId,
                userPreferencesRepository = prefs,
                recommendationRepository = recommendationRepository,
                descriptiveSearchRepository = DescriptiveSearchRepository(db.titleDao(), db.titleAttributeDao(), api, clock, gate, providerRepository),
            )
        }
        runBlocking { db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1))) }
        routes["/discover/movie"] = { """{"page":1,"results":[{"id":1,"title":"Funny Film","popularity":9.0,"vote_count":500}],"total_pages":2}""" }
        routes["/discover/tv"] = { """{"page":1,"results":[],"total_pages":1}""" }
        routes["/search/multi"] = { """{"page":1,"results":[]}""" }
        routes["/movie/1"] = {
            """{"id":1,"title":"Funny Film","watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":8,"provider_name":"N"}]}}},
               "release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"PG","type":3}]}]}}"""
        }
    }

    private fun runBlocking(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun requestsTo(path: String) = synchronized(requests) { requests.filter { it.encodedPath == path } }

    @Test
    fun `removing a chip reruns the search without it`() = runTest {
        val vm = builder(1L)
        vm.onQueryChange("comedy movies")
        vm.onSubmit()
        val first = vm.uiState.first { it.hasSearched && it.interpretation != null }
        assertEquals(listOf("Movies", "Comedy"), first.interpretation!!.chips.map { it.label })
        assertEquals("35", requestsTo("/discover/movie").single().queryParameter("with_genres"))
        assertEquals(listOf("Funny Film"), first.results.map { it.title })

        vm.onRemoveChip("genre:Comedy")

        val second = vm.uiState.first { it.hasSearched && it.interpretation?.chips?.map { c -> c.label } == listOf("Movies") }
        assertEquals(listOf("Movies"), second.interpretation!!.chips.map { it.label })
        val calls = requestsTo("/discover/movie")
        assertEquals(2, calls.size)
        assertNull(calls.last().queryParameter("with_genres"))
        // Media chip removal also works: movies and TV are both searched again.
        vm.onRemoveChip("media:movie")
        vm.uiState.first { it.hasSearched && it.interpretation?.chips?.isEmpty() == true }
        assertEquals(2, requestsTo("/discover/movie").size)
    }

    @Test
    fun `editing the query text clears earlier chip removals`() = runTest {
        val vm = builder(1L)
        vm.onQueryChange("comedy movies")
        vm.onSubmit()
        vm.uiState.first { it.interpretation != null && it.hasSearched }
        vm.onRemoveChip("genre:Comedy")
        vm.uiState.first { it.interpretation?.chips?.map { c -> c.label } == listOf("Movies") && it.hasSearched }

        vm.onQueryChange("comedy movies please")
        vm.onSubmit()
        val state = vm.uiState.first { it.interpretation?.chips?.map { c -> c.label } == listOf("Movies", "Comedy") && it.hasSearched }
        assertTrue(state.showTitleSearchLink)
    }

    @Test
    fun `age chip defaults to the profile's cap`() = runTest {
        val id = profileRepository.addProfile("Sam", "a", "12A").getOrThrow()
        val vm = builder(id)
        val state = vm.uiState.first { it.ageOption.label == "12" }
        assertEquals("12", state.ageOption.cap)
        assertEquals(listOf("U", "PG", "12", "15", "18", "Any"), state.ageOptions.map { it.label })
    }

    @Test
    fun `a profile with no cap defaults to Any`() = runTest {
        val id = profileRepository.addProfile("Adult", "a", null).getOrThrow()
        val vm = builder(id)
        assertEquals(AgeFilter.ANY, vm.uiState.first().ageOption)
    }

    @Test
    fun `Family defaults to the strictest member's cap`() = runTest {
        val a = profileRepository.addProfile("Kid", "a", "PG").getOrThrow()
        val b = profileRepository.addProfile("Teen", "b", "15").getOrThrow()
        familyProfileRepository.save("Family", "f", listOf(a, b)).getOrThrow()
        val vm = builder(FAMILY_PROFILE_SENTINEL_ID)
        val state = vm.uiState.first { it.ageOption.label != "Any" }
        assertEquals("PG", state.ageOption.label)
    }

    @Test
    fun `changing the age chip reruns the search with that cap and sends certification params`() = runTest {
        val vm = builder(1L)
        vm.onQueryChange("comedy movies")
        vm.onSubmit()
        vm.uiState.first { it.hasSearched && it.interpretation != null }
        assertNull(requestsTo("/discover/movie").single().queryParameter("certification.lte"))

        vm.onAgeOptionChange(AgeFilter.options.first { it.label == "PG" })

        vm.uiState.first { it.ageOption.label == "PG" && it.hasSearched && it.ageRatingCap == "PG" && !it.isSearching && requestsTo("/discover/movie").size == 2 }
        assertEquals("PG", requestsTo("/discover/movie").last().queryParameter("certification.lte"))
        assertEquals("GB", requestsTo("/discover/movie").last().queryParameter("certification_country"))
    }

    @Test
    fun `Search titles instead runs the title search and drops the interpretation`() = runTest {
        val vm = builder(1L)
        vm.onQueryChange("comedy movies")
        vm.onSubmit()
        vm.uiState.first { it.hasSearched && it.interpretation != null }
        assertTrue(requestsTo("/search/multi").isEmpty())

        vm.onSearchTitlesInstead()

        val state = vm.uiState.first { it.interpretation == null && it.hasSearched && !it.isSearching }
        assertFalse(state.showTitleSearchLink)
        assertEquals("comedy movies", requestsTo("/search/multi").single().queryParameter("query"))
    }

    @Test
    fun `a plain title query never shows interpretation chips or touches discover`() = runTest {
        val vm = builder(1L)
        vm.onQueryChange("inception")
        vm.onSubmit()
        val state = vm.uiState.first { it.hasSearched && !it.isSearching }
        assertNull(state.interpretation)
        assertTrue(requestsTo("/discover/movie").isEmpty())
    }

    @Test
    fun `load more appends the next page and hides once exhausted`() = runTest {
        routes["/discover/movie"] = {
            val page = requestsTo("/discover/movie").last().queryParameter("page")
            if (page == "1") """{"page":1,"results":[{"id":1,"title":"Funny Film","popularity":9.0,"vote_count":500}],"total_pages":2}"""
            else """{"page":2,"results":[{"id":2,"title":"Funnier Film","popularity":8.0,"vote_count":500}],"total_pages":2}"""
        }
        routes["/movie/2"] = {
            """{"id":2,"title":"Funnier Film","watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":8,"provider_name":"N"}]}}},
               "release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"PG","type":3}]}]}}"""
        }
        val vm = builder(1L)
        vm.onQueryChange("comedy movies")
        vm.onSubmit()
        val first = vm.uiState.first { it.hasSearched && !it.isSearching && it.results.size == 1 }
        assertTrue(first.canLoadMore)

        vm.loadMore()

        val second = vm.uiState.first { it.results.size == 2 && !it.isLoadingMore }
        assertEquals(listOf("Funny Film", "Funnier Film"), second.results.map { it.title })
        assertFalse(second.canLoadMore)
    }
}
