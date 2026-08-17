package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
