package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.RoutingDispatcher
import org.seg7.familywatchlist.testutil.buildInMemoryDb
import mockwebserver3.MockWebServer

/**
 * PLAN.md §5a: search is search-then-check, not a plain finder — a result only survives if its
 * GB availability lands on a currently-subscribed provider. [AvailabilityGateTest] covers the
 * gate's own filtering logic in isolation; these tests pin that [SearchRepository.search] wires
 * it up end to end and settles on the right *final* answer once every concurrent availability
 * check has resolved, in original relevance order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: SearchRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val gate = AvailabilityGate(titleRepository, providerRepository)
        repo = SearchRepository(db.titleDao(), api, clock, gate)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `keeps a result on a subscribed provider, drops one on an unsubscribed provider, drops one with no GB availability`() = runTest {
        db.providerDao().upsertAll(
            listOf(
                ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1),
                ProviderEntity(337, "Disney Plus", null, subscribed = false, displayPriority = 2),
            )
        )
        server.dispatcher = RoutingDispatcher(
            mapOf(
                "/search/multi" to { MockResponse(body = MIXED_SEARCH_JSON) },
                "/movie/38700" to { MockResponse(body = detailJson(38700, "Paddington", providerIds = listOf(8))) },
                "/tv/999" to { MockResponse(body = tvDetailJson(999, "Paddington Station", providerIds = listOf(337))) },
                "/movie/1" to { MockResponse(body = detailJson(1, "No Way Home", providerIds = emptyList())) },
            )
        )

        // Original TMDB relevance order is id 38700 (subscribed provider, survives), 999 (only on
        // a provider nobody's subscribed to, dropped), 1 (no GB availability at all, dropped) —
        // the final list should preserve that order among whatever survives.
        val finalResults = repo.search("paddington").last()

        assertEquals(listOf("Paddington"), finalResults.map { it.title })
    }

    @Test
    fun `an empty raw search never spawns an availability check`() = runTest {
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":0,"total_results":0}"""))

        val finalResults = repo.search("zzzznomatch").last()

        assertEquals(emptyList<Any>(), finalResults)
        assertEquals(1, server.requestCount)
    }

    private fun detailJson(id: Int, title: String, providerIds: List<Int>): String {
        val flatrate = providerIds.joinToString(",") { """{"provider_id": $it, "provider_name": "Provider $it"}""" }
        return """{"id": $id, "title": "$title", "watch/providers": {"results": {"GB": {"flatrate": [$flatrate]}}}}"""
    }

    private fun tvDetailJson(id: Int, name: String, providerIds: List<Int>): String {
        val flatrate = providerIds.joinToString(",") { """{"provider_id": $it, "provider_name": "Provider $it"}""" }
        return """{"id": $id, "name": "$name", "watch/providers": {"results": {"GB": {"flatrate": [$flatrate]}}}}"""
    }

    private companion object {
        val MIXED_SEARCH_JSON = """
            {
              "page": 1,
              "results": [
                {"id": 38700, "media_type": "movie", "title": "Paddington", "release_date": "2014-11-28", "poster_path": "/p.jpg", "vote_average": 7.2, "popularity": 33.1},
                {"id": 999, "media_type": "tv", "name": "Paddington Station", "first_air_date": "2019-01-01", "poster_path": "/t.jpg", "vote_average": 6.1, "popularity": 12.0},
                {"id": 1, "media_type": "movie", "title": "No Way Home", "release_date": "2021-12-15", "poster_path": "/n.jpg", "vote_average": 8.0, "popularity": 500.0},
                {"id": 555, "media_type": "person", "name": "Hugh Bonneville", "popularity": 9.9}
              ],
              "total_pages": 1,
              "total_results": 4
            }
        """.trimIndent()
    }
}
