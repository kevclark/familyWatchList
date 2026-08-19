package org.seg7.familywatchlist.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
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
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.remote.dto.ConfigurationDto
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto
import org.seg7.familywatchlist.data.remote.dto.MovieDetailDto
import org.seg7.familywatchlist.data.remote.dto.PagedResponseDto
import org.seg7.familywatchlist.data.remote.dto.ProviderListResponseDto
import org.seg7.familywatchlist.data.remote.dto.CountryWatchProvidersDto
import org.seg7.familywatchlist.data.remote.dto.TvDetailDto
import org.seg7.familywatchlist.data.remote.dto.WatchProviderDto
import org.seg7.familywatchlist.data.remote.dto.WatchProviderRegionsResponseDto
import org.seg7.familywatchlist.data.remote.dto.WatchProvidersResponseDto
import org.seg7.familywatchlist.data.repository.AvailabilityGate
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.SearchRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.RoutingDispatcher
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 5 + §5a. Covers search logic: the client-side movie/TV filter (§3), what
 * survives multi-search's mixed payload, the quick add-to-list toggle, and — since §5a — the
 * availability gate wired into all of it: only results with GB availability on a subscribed
 * provider ever reach [SearchUiState.results], and a stale query's in-flight availability checks
 * must never write into a newer query's state (the trickiest bit — see the dedicated test at the
 * bottom, which uses a hand-rolled [TmdbApi] fake to control exactly when a detail call resolves).
 *
 * `onSubmit()` is used throughout instead of `onQueryChange`, so the tests assert against the
 * search itself rather than racing the 350ms debounce with virtual time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var viewModel: SearchViewModel
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    private val activeProfileId = 42L

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("search_vm_prefs") }),
        )
        buildViewModel(TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" }))
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun buildViewModel(api: TmdbApi) {
        val clock = FakeClock(startMillis = 1_000L)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val gate = AvailabilityGate(titleRepository, providerRepository)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock, gate::isAvailableOnSubscribedProvider)
        viewModel = SearchViewModel(
            searchRepository = SearchRepository(db.titleDao(), api, clock, gate),
            watchlistRepository = watchlistRepository,
            providerRepository = providerRepository,
            activeProfileId = activeProfileId,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    private suspend fun subscribeNetflixOnly() {
        db.providerDao().upsertAll(
            listOf(
                ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1),
                ProviderEntity(337, "Disney Plus", null, subscribed = false, displayPriority = 2),
            )
        )
    }

    @Test
    fun `hasSubscribedServices is false with nothing subscribed, before any search runs`() = runTest {
        val state = viewModel.uiState.first { !it.hasSubscribedServices }
        assertFalse(state.hasSubscribedServices)
    }

    @Test
    fun `hasSubscribedServices flips true once a provider is subscribed`() = runTest {
        viewModel.uiState.first { !it.hasSubscribedServices }

        subscribeNetflixOnly()

        val state = viewModel.uiState.first { it.hasSubscribedServices }
        assertTrue(state.hasSubscribedServices)
    }

    @Test
    fun `multi-search keeps a movie and tv result available on a subscribed provider, drops person and the unavailable one`() = runTest {
        subscribeNetflixOnly()
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = MIXED_RESULTS_JSON) },
                "/movie/38700" to { MockResponse(body = movieDetailJson(38700, "Paddington", providerIds = listOf(8))) },
                "/tv/999" to { MockResponse(body = tvDetailJson(999, "Paddington Station", providerIds = listOf(337))) },
            )
        )

        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()

        val state = viewModel.uiState.first { it.hasSearched }
        assertEquals(listOf("Paddington"), state.results.map { it.title })
        assertEquals(listOf(MediaType.MOVIE), state.results.map { it.mediaType })
    }

    @Test
    fun `filter chips narrow the results client-side without another availability check`() = runTest {
        subscribeNetflixOnly()
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = MIXED_RESULTS_JSON) },
                "/movie/38700" to { MockResponse(body = movieDetailJson(38700, "Paddington", providerIds = listOf(8))) },
                "/tv/999" to { MockResponse(body = tvDetailJson(999, "Paddington Station", providerIds = listOf(8))) },
            )
        )
        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()
        viewModel.uiState.first { it.results.size == 2 }
        val requestsAfterSearch = server.requestCount

        viewModel.onFilterChange(SearchFilter.MOVIES)
        val movies = viewModel.uiState.first { it.filter == SearchFilter.MOVIES }
        assertEquals(listOf("Paddington"), movies.visibleResults.map { it.title })

        viewModel.onFilterChange(SearchFilter.TV)
        val tv = viewModel.uiState.first { it.filter == SearchFilter.TV }
        assertEquals(listOf("Paddington Station"), tv.visibleResults.map { it.title })

        viewModel.onFilterChange(SearchFilter.ALL)
        val all = viewModel.uiState.first { it.filter == SearchFilter.ALL }
        assertEquals(2, all.visibleResults.size)

        // Flipping chips never went back to the network — one search + one detail call per title.
        assertEquals(requestsAfterSearch, server.requestCount)
    }

    @Test
    fun `a gated result is persisted so it is usable offline after searching`() = runTest {
        subscribeNetflixOnly()
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = SINGLE_MOVIE_RESULT_JSON) },
                "/movie/38700" to { MockResponse(body = movieDetailJson(38700, "Paddington", providerIds = listOf(8))) },
            )
        )
        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()
        viewModel.uiState.first { it.results.isNotEmpty() }

        val stored = db.titleDao().get(38700, MediaType.MOVIE)
        assertEquals("Paddington", stored?.title)
        assertEquals(2014, stored?.year)
    }

    @Test
    fun `quick add puts a gated result on the shared list and tapping again takes it off`() = runTest {
        subscribeNetflixOnly()
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = SINGLE_MOVIE_RESULT_JSON) },
                "/movie/38700" to { MockResponse(body = movieDetailJson(38700, "Paddington", providerIds = listOf(8))) },
            )
        )
        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()
        val result = viewModel.uiState.first { it.results.isNotEmpty() }.results.first()

        viewModel.toggleWatchlist(result)
        val added = viewModel.uiState.first { it.isListed(result) }
        assertTrue(added.isListed(result))
        assertEquals(activeProfileId, watchlistRepository.get(result.tmdbId, result.mediaType)?.addedByProfileId)

        viewModel.toggleWatchlist(result)
        val removed = viewModel.uiState.first { !it.isListed(result) }
        assertFalse(removed.isListed(result))
        assertEquals(
            WatchlistState.REMOVED,
            watchlistRepository.get(result.tmdbId, result.mediaType)?.state,
        )
    }

    @Test
    fun `a quick add blocked by the availability gate surfaces an explanatory event instead of silently failing`() = runTest {
        // Nothing subscribed at all — PLAN.md §5a's gate blocks every add in that state. Called
        // directly against a title never fetched through search (the details screen's ＋ My List
        // is the more realistic real-world path into this — History can reach a title that's
        // since lost availability — SearchViewModel wires the same gate either way).
        val title = TitleEntity(
            tmdbId = 1, mediaType = MediaType.MOVIE, title = "No Way Home", year = 2021,
            posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
            certification = null, voteAverage = null, popularity = null, trailerKey = null,
            fetchedAt = 1_000L,
        )

        // `events` is a no-replay SharedFlow — an emission with no attached subscriber is
        // silently dropped, not queued. The collector must be attached (confirmed via
        // `CoroutineStart.UNDISPATCHED`, which runs synchronously up to `collect`'s subscribe)
        // *before* `toggleWatchlist` below, same pattern as `ProfileViewModelTest`.
        val eventChannel = Channel<SearchUiEvent>(capacity = 1)
        val collectorJob = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { eventChannel.trySend(it) }
        }

        viewModel.toggleWatchlist(title)
        val event = eventChannel.receive()

        assertTrue(event is SearchUiEvent.WatchlistBlocked)
        assertTrue((event as SearchUiEvent.WatchlistBlocked).message.contains("No Way Home"))
        assertEquals(null, watchlistRepository.get(1, MediaType.MOVIE))
        collectorJob.cancel()
    }

    @Test
    fun `clearing the query empties the screen and does not report a completed search`() = runTest {
        subscribeNetflixOnly()
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = SINGLE_MOVIE_RESULT_JSON) },
                "/movie/38700" to { MockResponse(body = movieDetailJson(38700, "Paddington", providerIds = listOf(8))) },
            )
        )
        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()
        viewModel.uiState.first { it.hasSearched }

        viewModel.onQueryChange("")

        val cleared = viewModel.uiState.first { it.query.isEmpty() }
        assertTrue(cleared.results.isEmpty())
        // hasSearched drives the "Nothing matched" message — it must not show on an empty box.
        assertFalse(cleared.hasSearched)
    }

    @Test
    fun `a blank query never reaches the network`() = runTest {
        viewModel.onQueryChange("   ")
        viewModel.onSubmit()

        assertEquals(0, server.requestCount)
    }

    /**
     * PLAN.md §7 M2f: the availability gate must resolve against the *live* region preference,
     * not a hardcoded GB — a title only available in the US should surface once the user has
     * switched region to US, using the exact same GB-shaped search flow otherwise.
     */
    @Test
    fun `search resolves availability against the live region preference, not a hardcoded GB`() = runTest {
        subscribeNetflixOnly()
        userPreferencesRepository.setRegion("US")
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = SINGLE_MOVIE_RESULT_JSON) },
                "/movie/38700" to {
                    MockResponse(
                        body = """{"id": 38700, "title": "Paddington", "release_date": "2014-11-28", "watch/providers": {"results": {"US": {"flatrate": [{"provider_id": 8, "provider_name": "Netflix"}]}}}}"""
                    )
                },
            )
        )

        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()

        val state = viewModel.uiState.first { it.hasSearched }
        assertEquals(listOf("Paddington"), state.results.map { it.title })
    }

    @Test
    fun `a failing search surfaces an error instead of crashing`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "{}"))

        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertTrue(state.results.isEmpty())
        assertTrue(state.hasSearched)
    }

    @Test
    fun `a stale query's slow-resolving availability check never overwrites a newer query's results`() = runTest {
        val oldDetailGate = CompletableDeferred<MovieDetailDto>()
        val newDetailGate = CompletableDeferred<MovieDetailDto>()
        val fakeApi = ControllableTmdbApi(
            searchResponses = mapOf(
                "old" to pagedMovie(id = 1, title = "OldMovie"),
                "new" to pagedMovie(id = 2, title = "NewMovie"),
            ),
            movieDetailGates = mapOf(1 to oldDetailGate, 2 to newDetailGate),
        )
        buildViewModel(fakeApi)
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))

        // "old" starts, and its availability check suspends on `oldDetailGate` — never resolved
        // in this test, standing in for a slow/uncached network round-trip.
        viewModel.onQueryChange("old")
        viewModel.onSubmit()
        viewModel.uiState.first { it.isSearching }

        // A new query supersedes it before the old check ever completes — this must cancel the
        // in-flight "old" work (PLAN.md §5a's cancel-on-requery).
        viewModel.onQueryChange("new")
        viewModel.onSubmit()

        // The "old" request's response arrives late, well after it was superseded — simulating
        // exactly the race PLAN.md §5a warns about. Because the coroutine that was awaiting it
        // was already cancelled, completing it now must be a no-op.
        oldDetailGate.complete(movieDetail(1, "OldMovie", providerIds = listOf(8)))
        newDetailGate.complete(movieDetail(2, "NewMovie", providerIds = listOf(8)))

        val state = viewModel.uiState.first { it.hasSearched }
        assertEquals(listOf("NewMovie"), state.results.map { it.title })
        assertTrue("OldMovie must never appear once a newer query has taken over", state.results.none { it.title == "OldMovie" })
    }

    private fun pagedMovie(id: Int, title: String): PagedResponseDto<MediaSummaryDto> = PagedResponseDto(
        page = 1,
        results = listOf(MediaSummaryDto(id = id, mediaType = "movie", title = title, releaseDate = "2020-01-01")),
        totalPages = 1,
        totalResults = 1,
    )

    private fun movieDetail(id: Int, title: String, providerIds: List<Int>): MovieDetailDto = MovieDetailDto(
        id = id,
        title = title,
        releaseDate = "2020-01-01",
        watchProviders = WatchProvidersResponseDto(
            results = mapOf(
                "GB" to CountryWatchProvidersDto(
                    flatrate = providerIds.map { WatchProviderDto(providerId = it, providerName = "Provider $it") },
                )
            )
        ),
    )

    /** A [TmdbApi] fake whose `movieDetail` suspends on a caller-supplied [CompletableDeferred] per id — lets a test control exactly when (or whether) a detail call resolves, deterministically, rather than racing real network/dispatcher timing. */
    private class ControllableTmdbApi(
        private val searchResponses: Map<String, PagedResponseDto<MediaSummaryDto>>,
        private val movieDetailGates: Map<Int, CompletableDeferred<MovieDetailDto>>,
    ) : TmdbApi {
        override suspend fun searchMulti(query: String, page: Int): PagedResponseDto<MediaSummaryDto> =
            searchResponses.getValue(query)

        override suspend fun movieDetail(id: Int, appendToResponse: String): MovieDetailDto =
            movieDetailGates.getValue(id).await()

        override suspend fun tvDetail(id: Int, appendToResponse: String): TvDetailDto =
            error("not used in this test")

        override suspend fun discoverMovies(
            watchRegion: String,
            withWatchProviders: String?,
            monetizationTypes: String,
            sortBy: String,
            page: Int,
        ): PagedResponseDto<MediaSummaryDto> = error("not used in this test")

        override suspend fun discoverTv(
            watchRegion: String,
            withWatchProviders: String?,
            monetizationTypes: String,
            sortBy: String,
            page: Int,
        ): PagedResponseDto<MediaSummaryDto> = error("not used in this test")

        override suspend fun movieRecommendations(id: Int, page: Int): PagedResponseDto<MediaSummaryDto> =
            error("not used in this test")

        override suspend fun tvRecommendations(id: Int, page: Int): PagedResponseDto<MediaSummaryDto> =
            error("not used in this test")

        override suspend fun movieProviders(watchRegion: String): ProviderListResponseDto =
            error("not used in this test")

        override suspend fun tvProviders(watchRegion: String): ProviderListResponseDto =
            error("not used in this test")

        override suspend fun configuration(): ConfigurationDto = error("not used in this test")

        override suspend fun watchProviderRegions(): WatchProviderRegionsResponseDto =
            error("not used in this test")
    }

    private fun movieDetailJson(id: Int, title: String, providerIds: List<Int>): String {
        val flatrate = providerIds.joinToString(",") { """{"provider_id": $it, "provider_name": "Provider $it"}""" }
        return """{"id": $id, "title": "$title", "release_date": "2014-11-28", "watch/providers": {"results": {"GB": {"flatrate": [$flatrate]}}}}"""
    }

    private fun tvDetailJson(id: Int, name: String, providerIds: List<Int>): String {
        val flatrate = providerIds.joinToString(",") { """{"provider_id": $it, "provider_name": "Provider $it"}""" }
        return """{"id": $id, "name": "$name", "first_air_date": "2019-01-01", "watch/providers": {"results": {"GB": {"flatrate": [$flatrate]}}}}"""
    }

    private companion object {
        /**
         * A realistic `/search/multi` payload: one movie, one TV show, one person. TMDB really
         * does mix all three into one list, which is why §3 calls for client-side filtering.
         */
        val MIXED_RESULTS_JSON = """
            {
              "page": 1,
              "results": [
                {
                  "id": 38700,
                  "media_type": "movie",
                  "title": "Paddington",
                  "release_date": "2014-11-28",
                  "poster_path": "/poster.jpg",
                  "overview": "A bear in London",
                  "vote_average": 7.2,
                  "popularity": 33.1
                },
                {
                  "id": 999,
                  "media_type": "tv",
                  "name": "Paddington Station",
                  "first_air_date": "2019-01-01",
                  "poster_path": "/tv.jpg",
                  "overview": "A series",
                  "vote_average": 6.1,
                  "popularity": 12.0
                },
                {
                  "id": 555,
                  "media_type": "person",
                  "name": "Hugh Bonneville",
                  "popularity": 9.9
                }
              ],
              "total_pages": 1,
              "total_results": 3
            }
        """.trimIndent()

        val SINGLE_MOVIE_RESULT_JSON = """
            {
              "page": 1,
              "results": [
                {
                  "id": 38700,
                  "media_type": "movie",
                  "title": "Paddington",
                  "release_date": "2014-11-28",
                  "poster_path": "/poster.jpg",
                  "overview": "A bear in London",
                  "vote_average": 7.2,
                  "popularity": 33.1
                }
              ],
              "total_pages": 1,
              "total_results": 1
            }
        """.trimIndent()
    }
}
