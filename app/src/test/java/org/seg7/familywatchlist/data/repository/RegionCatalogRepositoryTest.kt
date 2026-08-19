package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.seg7.familywatchlist.data.remote.TmdbClient

/**
 * PLAN.md §7 M2f: Settings' region picker sources this from TMDB's own
 * `/watch/providers/regions` rather than a hand-maintained country list. Unlike
 * [DiscoverRepository]'s 24h TTL, this data changes essentially never, so [RegionCatalogRepository]
 * fetches once and caches in memory for the rest of the process's life — these tests pin exactly
 * that: one network call ever, sorted output, reused on every subsequent call.
 */
class RegionCatalogRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: RegionCatalogRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        repo = RegionCatalogRepository(api)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `first call fetches from the network and returns regions sorted by english name`() = runTest {
        server.enqueue(MockResponse(body = REGIONS_JSON))

        val regions = repo.getRegions()

        assertEquals(listOf("France", "United Kingdom", "United States"), regions.map { it.englishName })
        assertEquals(listOf("FR", "GB", "US"), regions.map { it.code })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a second call is served from the in-memory cache, no second network call`() = runTest {
        server.enqueue(MockResponse(body = REGIONS_JSON))
        repo.getRegions()

        val regions = repo.getRegions()

        assertEquals(3, regions.size)
        assertEquals(1, server.requestCount)
    }

    private companion object {
        val REGIONS_JSON = """
            {
              "results": [
                {"iso_3166_1": "US", "english_name": "United States", "native_name": "United States"},
                {"iso_3166_1": "GB", "english_name": "United Kingdom", "native_name": "United Kingdom"},
                {"iso_3166_1": "FR", "english_name": "France", "native_name": "France"}
              ]
            }
        """.trimIndent()
    }
}
