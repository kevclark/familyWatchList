package org.seg7.familywatchlist.ui.search

import kotlinx.coroutines.flow.first
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
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.SearchRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 5. Covers the parts of search that are *logic* rather than layout: the
 * client-side movie/TV filter (§3: "filter results to movie/tv client-side"), what survives
 * multi-search's mixed payload, and the quick add-to-list toggle.
 *
 * The API is exercised through MockWebServer and the real Retrofit client, matching the M1
 * test style — a hand-rolled fake would prove the ViewModel talks to a fake, not that it
 * decodes what TMDB actually sends.
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

    private val activeProfileId = 42L

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock)
        viewModel = SearchViewModel(
            searchRepository = SearchRepository(db.titleDao(), api, clock),
            watchlistRepository = watchlistRepository,
            activeProfileId = activeProfileId,
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `multi-search keeps movies and tv and drops person results`() = runTest {
        server.enqueue(MockResponse(body = MIXED_RESULTS_JSON))

        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()

        val state = viewModel.uiState.first { it.results.isNotEmpty() }
        assertEquals(listOf("Paddington", "Paddington Station"), state.results.map { it.title })
        assertEquals(
            listOf(MediaType.MOVIE, MediaType.TV),
            state.results.map { it.mediaType },
        )
    }

    @Test
    fun `filter chips narrow the results client-side without another request`() = runTest {
        server.enqueue(MockResponse(body = MIXED_RESULTS_JSON))
        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()
        viewModel.uiState.first { it.results.size == 2 }

        viewModel.onFilterChange(SearchFilter.MOVIES)
        val movies = viewModel.uiState.first { it.filter == SearchFilter.MOVIES }
        assertEquals(listOf("Paddington"), movies.visibleResults.map { it.title })

        viewModel.onFilterChange(SearchFilter.TV)
        val tv = viewModel.uiState.first { it.filter == SearchFilter.TV }
        assertEquals(listOf("Paddington Station"), tv.visibleResults.map { it.title })

        viewModel.onFilterChange(SearchFilter.ALL)
        val all = viewModel.uiState.first { it.filter == SearchFilter.ALL }
        assertEquals(2, all.visibleResults.size)

        // One request total: the filter never went back to the network.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `results are persisted so a title is usable offline after searching`() = runTest {
        server.enqueue(MockResponse(body = MIXED_RESULTS_JSON))
        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()
        viewModel.uiState.first { it.results.isNotEmpty() }

        val stored = db.titleDao().get(38700, MediaType.MOVIE)
        assertEquals("Paddington", stored?.title)
        assertEquals(2014, stored?.year)
    }

    @Test
    fun `quick add puts a result on the shared list and tapping again takes it off`() = runTest {
        server.enqueue(MockResponse(body = MIXED_RESULTS_JSON))
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
    fun `clearing the query empties the screen and does not report a completed search`() = runTest {
        server.enqueue(MockResponse(body = MIXED_RESULTS_JSON))
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

    @Test
    fun `a failing search surfaces an error instead of crashing`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "{}"))

        viewModel.onQueryChange("paddington")
        viewModel.onSubmit()

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertTrue(state.results.isEmpty())
        assertTrue(state.hasSearched)
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
    }
}
