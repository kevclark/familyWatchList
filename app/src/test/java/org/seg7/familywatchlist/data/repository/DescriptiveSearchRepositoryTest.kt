package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.last
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.search.QueryParser
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/** PLAN.md §5f: structured search execution against MockWebServer, checking the params actually sent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DescriptiveSearchRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: DescriptiveSearchRepository
    private val requests = mutableListOf<HttpUrl>()
    private val routes = mutableMapOf<String, () -> String>()

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
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), db.reviewDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        repo = DescriptiveSearchRepository(
            db.titleDao(), db.titleAttributeDao(), api, clock, AvailabilityGate(titleRepository, providerRepository), providerRepository,
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private suspend fun subscribe(vararg ids: Int = intArrayOf(8)) {
        db.providerDao().upsertAll(ids.map { ProviderEntity(it, "P$it", null, subscribed = true, displayPriority = it) })
    }

    private fun requestsTo(path: String) = synchronized(requests) { requests.filter { it.encodedPath == path } }

    private suspend fun structured(text: String, removed: Set<String> = emptySet()): SearchPlan.Structured =
        repo.plan(QueryParser.parse(text).without(removed), allowTitleFallback = removed.isEmpty()) as SearchPlan.Structured

    private fun page(vararg items: String, totalPages: Int = 1) =
        """{"page":1,"results":[${items.joinToString(",")}],"total_pages":$totalPages,"total_results":${items.size}}"""

    private fun movie(id: Int, title: String, pop: Double = 10.0) = """{"id":$id,"title":"$title","popularity":$pop,"vote_count":500}"""
    private fun tv(id: Int, name: String, pop: Double = 10.0, genres: String = "[35]", character: String? = null, votes: Int = 500) =
        """{"id":$id,"name":"$name","popularity":$pop,"vote_count":$votes,"genre_ids":$genres${character?.let { ""","character":"$it"""" } ?: ""}}"""

    private fun movieDetail(id: Int, title: String, cert: String? = "12A", provider: Int = 8): String {
        val rd = cert?.let { ""","release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"$it","type":3}]}]}""" } ?: ""
        return """{"id":$id,"title":"$title","watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":$provider,"provider_name":"P"}]}}}$rd}"""
    }

    private fun tvDetail(id: Int, name: String, cert: String? = "12", provider: Int = 8, keywords: List<Int> = emptyList()): String {
        val cr = cert?.let { ""","content_ratings":{"results":[{"iso_3166_1":"GB","rating":"$it"}]}""" } ?: ""
        val kw = ""","keywords":{"results":[${keywords.joinToString(",") { """{"id":$it,"name":"k$it"}""" }}]}"""
        return """{"id":$id,"name":"$name","watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":$provider,"provider_name":"P"}]}}}$cr$kw}"""
    }

    // ---- media-type split -------------------------------------------------------------------

    @Test
    fun `comedy movies returns zero TV results and never calls a TV endpoint`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page(movie(1, "Funny Film")) }
        routes["/discover/tv"] = { page(tv(9, "Funny Show")) }
        routes["/movie/1"] = { movieDetail(1, "Funny Film") }
        routes["/tv/9"] = { tvDetail(9, "Funny Show") }

        val result = repo.run(structured("comedy movies"), 1, cap = null).last()

        assertEquals(listOf("Funny Film"), result.titles.map { it.title })
        assertTrue(result.titles.none { it.mediaType == MediaType.TV })
        assertTrue(requestsTo("/discover/tv").isEmpty())
        assertTrue(requestsTo("/person/1/tv_credits").isEmpty())
    }

    @Test
    fun `tv words never call discover movie`() = runTest {
        subscribe()
        routes["/discover/tv"] = { page(tv(9, "Funny Show")) }
        routes["/tv/9"] = { tvDetail(9, "Funny Show") }

        val result = repo.run(structured("comedy shows"), 1, cap = null).last()

        assertEquals(listOf("Funny Show"), result.titles.map { it.title })
        assertTrue(requestsTo("/discover/movie").isEmpty())
    }

    // ---- discover params ----------------------------------------------------------------------

    @Test
    fun `action comedies sends ANDed per-media-type genre ids, GB region, providers and vote floor`() = runTest {
        subscribe(9, 8)
        routes["/discover/movie"] = { page() }
        routes["/discover/tv"] = { page() }

        repo.run(structured("action comedies"), 1, cap = null).last()

        val m = requestsTo("/discover/movie").single()
        assertEquals("28,35", m.queryParameter("with_genres"))
        assertEquals("GB", m.queryParameter("watch_region"))
        assertEquals("8|9", m.queryParameter("with_watch_providers"))
        assertEquals("flatrate|free|rent|buy", m.queryParameter("with_watch_monetization_types"))
        assertEquals("popularity.desc", m.queryParameter("sort_by"))
        assertEquals("50", m.queryParameter("vote_count.gte"))
        assertEquals("1", m.queryParameter("page"))
        assertNull(m.queryParameter("with_cast"))
        val t = requestsTo("/discover/tv").single()
        assertEquals("10759,35", t.queryParameter("with_genres"))
        assertEquals("8|9", t.queryParameter("with_watch_providers"))
        assertNull(t.queryParameter("certification.lte"))
    }

    @Test
    fun `region is passed through, not hardcoded`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page() }
        repo.run(structured("comedy movies"), 1, cap = null, region = "IE").last()
        assertEquals("IE", requestsTo("/discover/movie").single().queryParameter("watch_region"))
    }

    @Test
    fun `age cap sends certification params on movies only, and 12 is sent as 12A`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page() }
        routes["/discover/tv"] = { page() }

        repo.run(structured("comedy"), 1, cap = "12").last()

        val m = requestsTo("/discover/movie").single()
        assertEquals("GB", m.queryParameter("certification_country"))
        assertEquals("12A", m.queryParameter("certification.lte"))
        val t = requestsTo("/discover/tv").single()
        assertNull(t.queryParameter("certification_country"))
        assertNull(t.queryParameter("certification.lte"))
    }

    @Test
    fun `an M12 equivalent cap maps to its BBFC certification and Any sends no certification params`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page() }
        repo.run(structured("comedy movies"), 1, cap = "16+").last()
        assertEquals("15", requestsTo("/discover/movie").single().queryParameter("certification.lte"))

        synchronized(requests) { requests.clear() }
        repo.run(structured("comedy movies"), 1, cap = null).last()
        val m = requestsTo("/discover/movie").single()
        assertNull(m.queryParameter("certification_country"))
        assertNull(m.queryParameter("certification.lte"))
    }

    @Test
    fun `a genre with no TV equivalent skips TV entirely`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page() }
        routes["/discover/tv"] = { page() }

        repo.run(structured("horror"), 1, cap = null).last()

        assertEquals("27", requestsTo("/discover/movie").single().queryParameter("with_genres"))
        assertTrue(requestsTo("/discover/tv").isEmpty())
    }

    @Test
    fun `a partly mapped genre pair drops only the unmapped side on TV`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page() }
        routes["/discover/tv"] = { page() }

        repo.run(structured("rom com"), 1, cap = null).last()

        assertEquals("35,10749", requestsTo("/discover/movie").single().queryParameter("with_genres"))
        assertEquals("35", requestsTo("/discover/tv").single().queryParameter("with_genres"))
    }

    @Test
    fun `nothing subscribed makes no discover calls`() = runTest {
        routes["/discover/movie"] = { page() }
        val result = repo.run(structured("comedy movies"), 1, cap = null).last()
        assertTrue(result.titles.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `paging sends the page number and reports hasMore from total_pages`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page(movie(1, "A"), totalPages = 2) }
        routes["/movie/1"] = { movieDetail(1, "A") }

        val first = repo.run(structured("comedy movies"), 1, cap = null).last()
        assertTrue(first.hasMore)
        val second = repo.run(structured("comedy movies"), 2, cap = null).last()
        assertFalse(second.hasMore)
        assertEquals("2", requestsTo("/discover/movie").last().queryParameter("page"))
    }

    // ---- keywords -----------------------------------------------------------------------------

    @Test
    fun `slapstick comedies resolves the keyword and sends with_keywords on both media`() = runTest {
        subscribe()
        routes["/search/keyword"] = { """{"results":[{"id":111,"name":"slapstick comedy"},{"id":222,"name":"slapstick"}]}""" }
        routes["/discover/movie"] = { page() }
        routes["/discover/tv"] = { page() }

        val plan = structured("slapstick comedies")
        repo.run(plan, 1, cap = null).last()

        assertEquals("slapstick", requestsTo("/search/keyword").single().queryParameter("query"))
        assertEquals("222", requestsTo("/discover/movie").single().queryParameter("with_keywords"))
        assertEquals("222", requestsTo("/discover/tv").single().queryParameter("with_keywords"))
        assertEquals(listOf("Comedy", "slapstick"), plan.interpretation.chips.map { it.label })
        assertTrue(plan.interpretation.ignored.isEmpty())
    }

    @Test
    fun `keyword matching accepts a plural but not a loose substring`() = runTest {
        routes["/search/keyword"] = { """{"results":[{"id":1,"name":"slapstick comedy"},{"id":2,"name":"heists"}]}""" }
        val plan = structured("heist comedies")
        assertEquals(listOf(2), plan.movie!!.keywordIds)

        val miss = structured("slapstick comedies")
        assertTrue(miss.movie!!.keywordIds.isEmpty())
        assertEquals(listOf("slapstick"), miss.interpretation.ignored)
    }

    @Test
    fun `keyword and person lookups are cached in memory`() = runTest {
        routes["/search/keyword"] = { """{"results":[{"id":2,"name":"heist"}]}""" }
        structured("heist comedies")
        structured("heist comedies")
        assertEquals(1, requestsTo("/search/keyword").size)
    }

    @Test
    fun `an unresolvable word with a genre is listed as ignored and the search still runs`() = runTest {
        routes["/search/keyword"] = { """{"results":[]}""" }
        val plan = structured("flibbertigibbet comedies")
        assertEquals(listOf("flibbertigibbet"), plan.interpretation.ignored)
        assertEquals(listOf("Comedy"), plan.interpretation.chips.map { it.label })
    }

    @Test
    fun `a failed multi-word phrase falls back to per-word keywords when the query is descriptive`() = runTest {
        routes["/search/person"] = { """{"results":[]}""" }
        routes["/search/keyword"] = { """{"results":[{"id":7,"name":"superhero"},{"id":8,"name":"heist"}]}""" }
        val plan = structured("superhero heist movies")
        assertEquals(listOf(7, 8), plan.movie!!.keywordIds)
        assertEquals(listOf("Movies", "superhero", "heist"), plan.interpretation.chips.map { it.label })
        // Removing one split chip drops just that word.
        val without = structured("superhero heist movies", removed = setOf("kw:superhero"))
        assertEquals(listOf(8), without.movie!!.keywordIds)
        assertTrue(without.interpretation.ignored.isEmpty())
    }

    // ---- title fallback ------------------------------------------------------------------------

    @Test
    fun `a bare title with nothing structured falls back to title search without a lookup`() = runTest {
        val plan = repo.plan(QueryParser.parse("inception"), allowTitleFallback = true)
        assertEquals(SearchPlan.Titles("inception"), plan)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a name-like run that resolves to nothing falls back to title search`() = runTest {
        routes["/search/person"] = { """{"results":[]}""" }
        routes["/search/keyword"] = { """{"results":[]}""" }
        val plan = repo.plan(QueryParser.parse("the matrix reloaded"), allowTitleFallback = true)
        assertTrue(plan is SearchPlan.Titles)
    }

    @Test
    fun `after a chip removal nothing structured is not turned into a title search`() = runTest {
        val plan = repo.plan(QueryParser.parse("comedy movies").without(setOf("genre:Comedy", "media:movie")), allowTitleFallback = false)
        val s = plan as SearchPlan.Structured
        assertTrue(s.interpretation.chips.isEmpty())
        assertNull(s.movie)
        assertNull(s.tv)
    }

    // ---- people --------------------------------------------------------------------------------

    private fun routePerson() {
        routes["/search/person"] = {
            """{"results":[{"id":5,"name":"Ryan Reynolds Jr","popularity":1.0},{"id":10859,"name":"Ryan Reynolds","popularity":50.0},{"id":6,"name":"Ryan Gosling","popularity":80.0}]}"""
        }
    }

    @Test
    fun `movies with Ryan Reynolds uses with_cast and no TV call`() = runTest {
        subscribe()
        routePerson()
        routes["/discover/movie"] = { page() }
        val plan = structured("find movies with Ryan Reynolds")

        repo.run(plan, 1, cap = null).last()

        assertEquals("ryan reynolds", requestsTo("/search/person").single().queryParameter("query"))
        assertEquals("10859", requestsTo("/discover/movie").single().queryParameter("with_cast"))
        assertEquals(listOf("Movies", "Ryan Reynolds"), plan.interpretation.chips.map { it.label })
        assertTrue(requestsTo("/discover/tv").isEmpty())
    }

    @Test
    fun `TV for a person comes from tv_credits, filtered, then gated, not from discover tv`() = runTest {
        subscribe()
        routePerson()
        routes["/person/10859/tv_credits"] = {
            """{"cast":[
              ${tv(1, "Good Comedy", pop = 30.0)},
              ${tv(2, "Wrong Genre", pop = 20.0, genres = "[18]")},
              ${tv(3, "Talk Show Guest", pop = 90.0, character = "Self")},
              ${tv(4, "Obscure", pop = 5.0, votes = 3)},
              ${tv(5, "Not Subscribed", pop = 10.0)}
            ]}"""
        }
        routes["/tv/1"] = { tvDetail(1, "Good Comedy") }
        routes["/tv/5"] = { tvDetail(5, "Not Subscribed", provider = 999) }
        routes["/discover/movie"] = { page() }

        val result = repo.run(structured("comedy with Ryan Reynolds"), 1, cap = null).last()

        assertEquals(listOf("Good Comedy"), result.titles.map { it.title })
        assertTrue(requestsTo("/discover/tv").isEmpty())
        assertEquals("10859", requestsTo("/discover/movie").single().queryParameter("with_cast"))
        // Genres, vote floor, and Self are filtered before any detail call is spent.
        assertTrue(requestsTo("/tv/2").isEmpty() && requestsTo("/tv/3").isEmpty() && requestsTo("/tv/4").isEmpty())
    }

    @Test
    fun `tv credits are paged locally`() = runTest {
        subscribe()
        routePerson()
        val cast = (1..25).joinToString(",") { tv(it, "Show $it", pop = 100.0 - it) }
        routes["/person/10859/tv_credits"] = { """{"cast":[$cast]}""" }
        (1..25).forEach { id -> routes["/tv/$id"] = { tvDetail(id, "Show $id") } }

        val first = repo.run(structured("comedy shows with Ryan Reynolds"), 1, cap = null).last()
        val second = repo.run(structured("comedy shows with Ryan Reynolds"), 2, cap = null).last()

        assertEquals(20, first.titles.size)
        assertTrue(first.hasMore)
        assertEquals(5, second.titles.size)
        assertFalse(second.hasMore)
        assertEquals(1, requestsTo("/person/10859/tv_credits").size)
    }

    @Test
    fun `two cued people are ANDed on movies and intersected on TV`() = runTest {
        subscribe()
        routes["/search/person"] = {
            """{"results":[{"id":1,"name":"Tom Hanks","popularity":9.0},{"id":2,"name":"Meg Ryan","popularity":8.0}]}"""
        }
        routes["/discover/movie"] = { page() }
        val plan = structured("movies with tom hanks and meg ryan")
        repo.run(plan, 1, cap = null).last()
        // Both lookups returned both people; each query only accepts its own name.
        assertEquals("1,2", requestsTo("/discover/movie").single().queryParameter("with_cast"))
    }

    @Test
    fun `an unknown person cue falls back to a keyword, else is ignored`() = runTest {
        routes["/search/person"] = { """{"results":[]}""" }
        routes["/search/keyword"] = { """{"results":[{"id":77,"name":"dragons"}]}""" }
        val plan = structured("movies with dragons")
        assertEquals(listOf(77), plan.movie!!.keywordIds)
        assertTrue(plan.movie!!.personIds.isEmpty())
    }

    // ---- age post-filter / unrated -------------------------------------------------------------

    @Test
    fun `age post-filter hides over-cap and unrated at 12, keeps at-cap`() = runTest {
        subscribe()
        routes["/discover/tv"] = { page(tv(1, "Adult Show", pop = 30.0), tv(2, "Kids Show", pop = 20.0), tv(3, "Unrated Show", pop = 10.0)) }
        routes["/tv/1"] = { tvDetail(1, "Adult Show", cert = "15") }
        routes["/tv/2"] = { tvDetail(2, "Kids Show", cert = "PG") }
        routes["/tv/3"] = { tvDetail(3, "Unrated Show", cert = null) }

        val result = repo.run(structured("comedy shows"), 1, cap = "12").last()

        assertEquals(listOf("Kids Show"), result.titles.map { it.title })
        assertTrue(result.unratedKeys.isEmpty())
    }

    @Test
    fun `at 15 and Any an unrated title is shown and flagged Unrated`() = runTest {
        subscribe()
        routes["/discover/tv"] = { page(tv(1, "Adult Show", pop = 30.0), tv(3, "Unrated Show", pop = 10.0)) }
        routes["/tv/1"] = { tvDetail(1, "Adult Show", cert = "15") }
        routes["/tv/3"] = { tvDetail(3, "Unrated Show", cert = null) }

        val at15 = repo.run(structured("comedy shows"), 1, cap = "15").last()
        assertEquals(listOf("Adult Show", "Unrated Show"), at15.titles.map { it.title })
        assertEquals(setOf("TV-3"), at15.unratedKeys)

        val any = repo.run(structured("comedy shows"), 1, cap = null).last()
        assertEquals(setOf("TV-3"), any.unratedKeys)
    }

    @Test
    fun `an over-cap movie that TMDB let through is still dropped by the CERT_RANK post-filter`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page(movie(1, "Sneaky 18")) }
        routes["/movie/1"] = { movieDetail(1, "Sneaky 18", cert = "18") }
        val result = repo.run(structured("comedy movies"), 1, cap = "15").last()
        assertTrue(result.titles.isEmpty())
    }

    @Test
    fun `results are ordered by popularity across media types and unavailable titles are dropped`() = runTest {
        subscribe()
        routes["/discover/movie"] = { page(movie(1, "Movie Mid", pop = 50.0), movie(2, "Movie Off Service", pop = 99.0)) }
        routes["/discover/tv"] = { page(tv(9, "Show Top", pop = 80.0)) }
        routes["/movie/1"] = { movieDetail(1, "Movie Mid") }
        routes["/movie/2"] = { movieDetail(2, "Movie Off Service", provider = 999) }
        routes["/tv/9"] = { tvDetail(9, "Show Top") }

        val result = repo.run(structured("comedy"), 1, cap = null).last()

        assertEquals(listOf("Show Top", "Movie Mid"), result.titles.map { it.title })
    }
}
