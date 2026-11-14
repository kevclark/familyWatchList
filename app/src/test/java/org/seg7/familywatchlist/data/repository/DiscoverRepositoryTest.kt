package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var repo: DiscoverRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        repo = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `first call hits the network and caches the page`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))

        val results = repo.discoverMovies(subscribedProviderIds = listOf(8, 337))

        assertEquals(1, server.requestCount)
        assertEquals(listOf("Paddington"), results.map { it.title })
    }

    @Test
    fun `repeat call within 24h TTL is served from cache, no second network call`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))
        repo.discoverMovies(subscribedProviderIds = listOf(8, 337))

        val results = repo.discoverMovies(subscribedProviderIds = listOf(8, 337))

        assertEquals(1, server.requestCount)
        assertEquals(listOf("Paddington"), results.map { it.title })
    }

    @Test
    fun `after 24h TTL elapses, the page is refetched`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))
        repo.discoverMovies(subscribedProviderIds = listOf(8, 337))
        clock.advanceBy(TimeUnit.HOURS.toMillis(25))
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington (refreshed)")))

        val results = repo.discoverMovies(subscribedProviderIds = listOf(8, 337))

        assertEquals(2, server.requestCount)
        assertEquals(listOf("Paddington (refreshed)"), results.map { it.title })
    }

    @Test
    fun `zero subscribed providers returns nothing, not an unfiltered popular-in-the-UK page — M2e`() = runTest {
        val results = repo.discoverMovies(subscribedProviderIds = emptyList())

        assertEquals(emptyList<String>(), results.map { it.title })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `discoverTv with zero subscribed providers also returns nothing and skips the network`() = runTest {
        val results = repo.discoverTv(subscribedProviderIds = emptyList())

        assertEquals(emptyList<String>(), results.map { it.title })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalidateAllCachedPages clears a cached page so the next call refetches`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))
        repo.discoverMovies(subscribedProviderIds = listOf(8, 337))

        repo.invalidateAllCachedPages()
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington (refetched)")))
        val results = repo.discoverMovies(subscribedProviderIds = listOf(8, 337))

        assertEquals(2, server.requestCount)
        assertEquals(listOf("Paddington (refetched)"), results.map { it.title })
    }

    /** PLAN.md §7 M2f: region is a real call-time parameter, threaded all the way to the network call. */
    @Test
    fun `discoverMovies sends the given region, not a hardcoded GB`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))

        repo.discoverMovies(subscribedProviderIds = listOf(8), region = "US")

        val recorded = server.takeRequest()
        assertTrue(recorded.target.contains("watch_region=US"))
    }

    /**
     * M6 regression (PLAN.md §5 "Paid (rent/buy) titles" addendum): the whole reason
     * `RecommendationRepository`'s candidate pool (Home's Popular/For You rows, the recommender's
     * scoring) is provably unaffected by M6's Search/watchlist widening is that this query param
     * — the *only* thing that decides which titles TMDB's `/discover` even returns — was never
     * touched. Pins the literal value so a future edit here can't silently leak paid titles into
     * Home without a test noticing.
     */
    @Test
    fun `discoverMovies still requests only flatrate-or-free monetization types — M6 must never widen this`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))

        repo.discoverMovies(subscribedProviderIds = listOf(8))

        val recorded = server.takeRequest()
        assertTrue(recorded.target.contains("with_watch_monetization_types=flatrate%7Cfree"))
    }

    @Test
    fun `discoverTv still requests only flatrate-or-free monetization types — M6 must never widen this`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))

        repo.discoverTv(subscribedProviderIds = listOf(8))

        val recorded = server.takeRequest()
        assertTrue(recorded.target.contains("with_watch_monetization_types=flatrate%7Cfree"))
    }

    @Test
    fun `a different region is a different query hash — switching region never serves stale cross-region data`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 1, title = "GB Result")))
        repo.discoverMovies(subscribedProviderIds = listOf(8), region = "GB")
        server.enqueue(MockResponse(body = discoverPageJson(id = 2, title = "US Result")))

        val results = repo.discoverMovies(subscribedProviderIds = listOf(8), region = "US")

        assertEquals(2, server.requestCount)
        assertEquals(listOf("US Result"), results.map { it.title })
    }

    @Test
    fun `a different provider set is a different query hash — no cross-contamination`() = runTest {
        server.enqueue(MockResponse(body = discoverPageJson(id = 1, title = "A")))
        repo.discoverMovies(subscribedProviderIds = listOf(8))
        server.enqueue(MockResponse(body = discoverPageJson(id = 2, title = "B")))

        val results = repo.discoverMovies(subscribedProviderIds = listOf(337))

        assertEquals(2, server.requestCount)
        assertEquals(listOf("B"), results.map { it.title })
    }

    @Test
    fun `discover never clobbers a title that already has full detail data cached`() = runTest {
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = 38700, mediaType = MediaType.MOVIE, title = "Paddington",
                year = 2014, posterPath = null, backdropPath = null, overview = "full overview",
                runtimeMin = 95, certification = "PG", voteAverage = 7.2, popularity = 33.1,
                trailerKey = "dQw4w9WgXcQ", fetchedAt = clock.current,
            )
        )
        server.enqueue(MockResponse(body = discoverPageJson(id = 38700, title = "Paddington")))

        repo.discoverMovies(subscribedProviderIds = listOf(8))

        val stored = db.titleDao().get(38700, MediaType.MOVIE)
        assertEquals(95, stored?.runtimeMin)
        assertEquals("PG", stored?.certification)
    }

    // ---- PLAN.md §5i: New films / New series ----

    private fun setClockTo(iso: String) { clock.current = java.time.Instant.parse(iso).toEpochMilli() }

    private fun pageOf(vararg entries: Pair<Int, String>) = """
        {"page":1,"results":[${entries.joinToString(",") { (id, t) ->
            """{"id": $id, "title": "$t", "name": "$t", "poster_path": "/p.jpg", "release_date": "2026-10-01", "popularity": 10.0}"""
        }}],"total_pages":1,"total_results":${entries.size}}
    """.trimIndent()

    @Test
    fun `newMovies asks for the last 4 months relative to the injected clock, with the paid-inclusive monetization set and a vote floor`() = runTest {
        setClockTo("2026-11-14T10:00:00Z")
        server.enqueue(MockResponse(body = pageOf(1 to "A")))
        server.enqueue(MockResponse(body = pageOf(1 to "A")))

        repo.newMovies(listOf(337, 8))

        val wide = server.takeRequest().target
        assertTrue(wide, wide.startsWith("/discover/movie"))
        assertTrue(wide, wide.contains("primary_release_date.gte=2026-07-14"))
        assertTrue(wide, wide.contains("primary_release_date.lte=2026-11-14"))
        assertTrue(wide, wide.contains("watch_region=GB"))
        assertTrue(wide, wide.contains("with_watch_providers=8%7C337"))
        assertTrue(wide, wide.contains("with_watch_monetization_types=flatrate%7Cfree%7Crent%7Cbuy"))
        assertTrue(wide, wide.contains("sort_by=popularity.desc"))
        assertTrue(wide, wide.contains("vote_count.gte=10"))
    }

    @Test
    fun `newTv asks for the last 3 months, flatrate-or-free only, with a vote floor`() = runTest {
        setClockTo("2026-11-14T10:00:00Z")
        server.enqueue(MockResponse(body = pageOf(1 to "S")))

        repo.newTv(listOf(8, 337))

        val req = server.takeRequest().target
        assertTrue(req, req.startsWith("/discover/tv"))
        assertTrue(req, req.contains("first_air_date.gte=2026-08-14"))
        assertTrue(req, req.contains("with_watch_monetization_types=flatrate%7Cfree&") || req.endsWith("with_watch_monetization_types=flatrate%7Cfree"))
        assertTrue(req, req.contains("vote_count.gte=10"))
        assertTrue(req, req.contains("with_watch_providers=8%7C337"))
        assertTrue(req, req.contains("sort_by=popularity.desc"))
    }

    /** Kev's rent/buy exception covers the New films row only: it must not leak to any other discover call. */
    @Test
    fun `rent and buy are requested by New films only, never by popular, New series or recommendations`() = runTest {
        setClockTo("2026-11-14T10:00:00Z")
        repeat(6) { server.enqueue(MockResponse(body = pageOf(1 to "A"))) }

        repo.newMovies(listOf(8))        // 2 requests: wide + included
        val wide = server.takeRequest().target
        val included = server.takeRequest().target
        repo.discoverMovies(listOf(8))
        repo.discoverTv(listOf(8))
        repo.newTv(listOf(8))
        repo.movieRecommendations(5)
        val others = List(4) { server.takeRequest().target }

        assertTrue(wide.contains("rent%7Cbuy"))
        assertFalse(included.contains("rent"))
        assertFalse(included.contains("buy"))
        others.forEach { assertFalse(it, it.contains("rent") || it.contains("buy")) }
    }

    @Test
    fun `a film in the wide page but not the included page is flagged paid-only`() = runTest {
        server.enqueue(MockResponse(body = pageOf(1 to "Included", 2 to "Rent only")))
        server.enqueue(MockResponse(body = pageOf(1 to "Included")))

        val result = repo.newMovies(listOf(8))

        assertEquals(listOf("Included", "Rent only"), result.titles.map { it.title })
        assertEquals(setOf(2), result.paidOnlyIds)
    }

    @Test
    fun `New rows are cached for 24h then refetched with dates moved on`() = runTest {
        setClockTo("2026-11-14T10:00:00Z")
        server.enqueue(MockResponse(body = pageOf(1 to "A")))
        server.enqueue(MockResponse(body = pageOf(1 to "A")))
        repo.newMovies(listOf(8))
        repo.newMovies(listOf(8))
        assertEquals(2, server.requestCount)

        clock.advanceBy(TimeUnit.HOURS.toMillis(25))
        server.enqueue(MockResponse(body = pageOf(1 to "A")))
        server.enqueue(MockResponse(body = pageOf(1 to "A")))
        repo.newMovies(listOf(8))

        assertEquals(4, server.requestCount)
        server.takeRequest(); server.takeRequest()
        val third = server.takeRequest().target
        assertTrue(third, third.contains("primary_release_date.lte=2026-11-15"))
        assertTrue(third, third.contains("primary_release_date.gte=2026-07-15"))
    }

    @Test
    fun `New rows with nothing subscribed return empty and never touch the network`() = runTest {
        assertEquals(emptyList<String>(), repo.newMovies(emptyList()).titles.map { it.title })
        assertEquals(emptyList<String>(), repo.newTv(emptyList()).map { it.title })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an empty wide page skips the included query`() = runTest {
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""))

        val result = repo.newMovies(listOf(8))

        assertEquals(emptyList<String>(), result.titles.map { it.title })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalidateAllCachedPages also drops the New rows so a provider change refetches them`() = runTest {
        server.enqueue(MockResponse(body = pageOf(1 to "S")))
        repo.newTv(listOf(8))
        repo.invalidateAllCachedPages()
        server.enqueue(MockResponse(body = pageOf(1 to "S2")))

        assertEquals(listOf("S2"), repo.newTv(listOf(8)).map { it.title })
    }

    private fun discoverPageJson(id: Int, title: String) = """
        {
          "page": 1,
          "results": [
            {"id": $id, "title": "$title", "poster_path": "/p.jpg", "release_date": "2014-11-28", "vote_average": 7.2, "popularity": 33.1}
          ],
          "total_pages": 1,
          "total_results": 1
        }
    """.trimIndent()
}
