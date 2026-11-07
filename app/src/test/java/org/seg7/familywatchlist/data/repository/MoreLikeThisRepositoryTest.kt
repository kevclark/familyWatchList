package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/** PLAN.md §5h: TMDB recommendations pushed through the same browse gates as structured search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoreLikeThisRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: MoreLikeThisRepository
    private val paths = mutableListOf<String>()
    private val routes = mutableMapOf<String, () -> MockResponse>()

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(paths) { paths += path }
                return routes[path]?.invoke() ?: MockResponse(code = 404)
            }
        }
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), db.reviewDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock))
        repo = MoreLikeThisRepository(db.titleDao(), api, clock, AvailabilityGate(titleRepository, providerRepository))
        runTest { db.providerDao().upsertAll(listOf(ProviderEntity(8, "P8", null, subscribed = true, displayPriority = 8))) }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun count(path: String) = synchronized(paths) { paths.count { it == path } }

    private fun recs(vararg items: Pair<Int, String>) = MockResponse(
        body = """{"page":1,"results":[${items.joinToString(",") { """{"id":${it.first},"title":"${it.second}","popularity":${it.first}.0}""" }}],"total_pages":1,"total_results":${items.size}}""",
    )

    private fun detail(id: Int, cert: String? = "12A", monetization: String = "flatrate", provider: Int = 8): MockResponse {
        val rd = cert?.let { ""","release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"$it","type":3}]}]}""" } ?: ""
        return MockResponse(
            body = """{"id":$id,"title":"T$id","watch/providers":{"results":{"GB":{"$monetization":[{"provider_id":$provider,"provider_name":"P"}]}}}$rd}""",
        )
    }

    @Test
    fun `keeps TMDB order regardless of check completion order and excludes the title itself`() = runTest {
        // Ids deliberately not ascending; the title (42) appears in its own recommendations.
        routes["/movie/42/recommendations"] = { recs(7 to "Seven", 42 to "Self", 3 to "Three", 9 to "Nine") }
        listOf(7, 3, 9).forEach { id -> routes["/movie/$id"] = { detail(id) } }

        val result = repo.load(42, MediaType.MOVIE, cap = null).last()

        assertEquals(listOf(7, 3, 9), result.titles.map { it.tmdbId })
        assertTrue(result.settled)
        assertEquals(0, count("/movie/42"))
    }

    @Test
    fun `emissions are progressive and cumulative`() = runTest {
        routes["/movie/42/recommendations"] = { recs(1 to "One", 2 to "Two") }
        routes["/movie/1"] = { detail(1) }
        routes["/movie/2"] = { detail(2) }

        val emissions = repo.load(42, MediaType.MOVIE, cap = null).toList()

        assertTrue(emissions.dropLast(1).all { !it.settled })
        assertTrue(emissions.last().settled)
        assertEquals(listOf(1, 2), emissions.last().titles.map { it.tmdbId })
        assertEquals(emissions.map { it.titles.size }, emissions.map { it.titles.size }.sorted())
    }

    @Test
    fun `paid-only unavailable and over-cap titles are dropped`() = runTest {
        routes["/movie/42/recommendations"] = { recs(1 to "Good", 2 to "RentOnly", 3 to "OtherService", 4 to "TooOld", 5 to "Unrated") }
        routes["/movie/1"] = { detail(1, cert = "PG") }
        routes["/movie/2"] = { detail(2, cert = "PG", monetization = "rent") }
        routes["/movie/3"] = { detail(3, cert = "PG", provider = 99) }
        routes["/movie/4"] = { detail(4, cert = "18") }
        routes["/movie/5"] = { detail(5, cert = null) }

        val result = repo.load(42, MediaType.MOVIE, cap = "12").last()

        // Unrated is hidden at a cap of 12 or below (shared AgeFilter rule).
        assertEquals(listOf(1), result.titles.map { it.tmdbId })
    }

    @Test
    fun `unrated is shown when the cap is above 12`() = runTest {
        routes["/movie/42/recommendations"] = { recs(5 to "Unrated", 4 to "TooOld") }
        routes["/movie/5"] = { detail(5, cert = null) }
        routes["/movie/4"] = { detail(4, cert = "18") }

        val result = repo.load(42, MediaType.MOVIE, cap = "15").last()

        assertEquals(listOf(5), result.titles.map { it.tmdbId })
    }

    @Test
    fun `nothing passing gives a settled empty result`() = runTest {
        routes["/movie/42/recommendations"] = { recs(3 to "OtherService") }
        routes["/movie/3"] = { detail(3, provider = 99) }

        val result = repo.load(42, MediaType.MOVIE, cap = null).last()

        assertTrue(result.titles.isEmpty())
        assertTrue(result.settled)
    }

    @Test
    fun `tv titles use the tv recommendations endpoint only`() = runTest {
        routes["/tv/10/recommendations"] = { MockResponse(body = """{"page":1,"results":[{"id":11,"name":"Show"}],"total_pages":1,"total_results":1}""") }
        routes["/tv/11"] = {
            MockResponse(body = """{"id":11,"name":"Show","watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":8,"provider_name":"P"}]}}},"content_ratings":{"results":[{"iso_3166_1":"GB","rating":"12"}]}}""")
        }

        val result = repo.load(10, MediaType.TV, cap = null).last()

        assertEquals(listOf(11), result.titles.map { it.tmdbId })
        assertEquals(MediaType.TV, result.titles.single().mediaType)
        assertEquals(0, count("/movie/10/recommendations"))
        assertEquals(0, count("/tv/10/similar"))
    }

    @Test
    fun `an API failure surfaces as an exception for the caller to hide the row`() = runTest {
        routes["/movie/42/recommendations"] = { MockResponse(code = 500) }

        val failure = runCatching { repo.load(42, MediaType.MOVIE, cap = null).last() }.exceptionOrNull()

        assertTrue(failure != null)
    }

    @Test
    fun `the id list is cached per title so a second load does not refetch`() = runTest {
        routes["/movie/42/recommendations"] = { recs(1 to "One") }
        routes["/movie/1"] = { detail(1) }

        repo.load(42, MediaType.MOVIE, cap = null).last()
        val second = repo.load(42, MediaType.MOVIE, cap = null).last()

        assertEquals(1, count("/movie/42/recommendations"))
        assertEquals(listOf(1), second.titles.map { it.tmdbId })
    }

    @Test
    fun `a stub does not overwrite an already detailed row`() = runTest {
        routes["/movie/42/recommendations"] = { recs(1 to "One") }
        routes["/movie/1"] = { detail(1) }
        repo.load(42, MediaType.MOVIE, cap = null).last()
        val detailed = db.titleDao().get(1, MediaType.MOVIE)!!
        assertEquals("12A", detailed.certification)

        // A different source title re-lists id 1 with a new name: the stub write must not clobber detail.
        routes["/movie/43/recommendations"] = { recs(1 to "One renamed") }
        repo.load(43, MediaType.MOVIE, cap = null).last()

        assertEquals("12A", db.titleDao().get(1, MediaType.MOVIE)!!.certification)
    }
}
