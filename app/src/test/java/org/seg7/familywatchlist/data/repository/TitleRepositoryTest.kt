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
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TitleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var repo: TitleRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        repo = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `refresh writes title, attributes, and GB availability from one detail call`() = runTest {
        server.enqueue(MockResponse(body = MOVIE_DETAIL_JSON))

        val entity = repo.refresh(38700, MediaType.MOVIE)

        assertEquals("Paddington", entity.title)
        assertEquals("PG", entity.certification)
        assertEquals(clock.current, entity.fetchedAt)

        val attrs = db.titleAttributeDao().getForTitle(38700, MediaType.MOVIE)
        assertTrue(attrs.any { it.attrType == AttrType.GENRE && it.name == "Family" })
        assertTrue(attrs.any { it.attrType == AttrType.CAST })
        assertTrue(attrs.any { it.attrType == AttrType.CREW })

        val availability = db.providerAvailabilityDao().getForTitle(38700, MediaType.MOVIE)
        assertEquals(1, availability.size)
        assertEquals(8, availability.first().providerId)
    }

    @Test
    fun `ensureFresh serves the cached row without a network call when fresh`() = runTest {
        db.titleDao().upsert(cachedTitle(fetchedAt = clock.current))

        val entity = repo.ensureFresh(1, MediaType.MOVIE)

        assertEquals(0, server.requestCount)
        assertEquals("Cached Title", entity.title)
    }

    @Test
    fun `ensureFresh refetches a stub-only row even though its fetchedAt is brand new`() = runTest {
        // PLAN.md §5a's availability gate calls ensureFresh on a title SearchRepository just
        // stubbed a moment earlier — the stub's own fetchedAt is fresh, but it was never
        // actually detail-fetched, so serving it as-is would mean provider availability (and
        // thus the gate) never resolves for any newly-searched title. isProviderDataStale must
        // treat "no runtime and no certification" as stale regardless of how recent fetchedAt is.
        db.titleDao().upsert(stubTitle(fetchedAt = clock.current))
        server.enqueue(MockResponse(body = MOVIE_DETAIL_JSON))

        val entity = repo.ensureFresh(38700, MediaType.MOVIE)

        assertEquals(1, server.requestCount)
        assertEquals("PG", entity.certification)
    }

    @Test
    fun `ensureFresh refetches once the 7-day provider TTL has elapsed`() = runTest {
        db.titleDao().upsert(cachedTitle(fetchedAt = clock.current))
        clock.advanceBy(TimeUnit.DAYS.toMillis(8))
        server.enqueue(MockResponse(body = MOVIE_DETAIL_JSON))

        val entity = repo.ensureFresh(38700, MediaType.MOVIE)

        assertEquals(1, server.requestCount)
        assertEquals("Paddington", entity.title)
    }

    @Test
    fun `isProviderDataStale trips before isMetadataStale — 7d provider TTL is stricter than 30d metadata TTL`() {
        val fetchedAt = clock.current
        clock.advanceBy(TimeUnit.DAYS.toMillis(10))
        val title = cachedTitle(fetchedAt = fetchedAt)

        assertTrue(repo.isProviderDataStale(title))
        assertFalse(repo.isMetadataStale(title))
    }

    @Test
    fun `neither TTL has elapsed for a just-fetched title`() {
        val title = cachedTitle(fetchedAt = clock.current)

        assertFalse(repo.isProviderDataStale(title))
        assertFalse(repo.isMetadataStale(title))
    }

    @Test
    fun `both TTLs have elapsed after 31 days`() {
        val fetchedAt = clock.current
        clock.advanceBy(TimeUnit.DAYS.toMillis(31))
        val title = cachedTitle(fetchedAt = fetchedAt)

        assertTrue(repo.isProviderDataStale(title))
        assertTrue(repo.isMetadataStale(title))
    }

    /** A row that's already been through a full detail fetch — has runtime/certification, the signal [TitleRepository.isProviderDataStale] uses to tell it apart from a search/discover stub. */
    private fun cachedTitle(fetchedAt: Long) = TitleEntity(
        tmdbId = 1,
        mediaType = MediaType.MOVIE,
        title = "Cached Title",
        year = 2020,
        posterPath = null,
        backdropPath = null,
        overview = null,
        runtimeMin = 95,
        certification = "PG",
        voteAverage = null,
        popularity = null,
        trailerKey = null,
        fetchedAt = fetchedAt,
    )

    /** A search/discover-shaped stub — no runtime, no certification, exactly what [SearchRepository]/[DiscoverRepository] persist before any detail fetch. */
    private fun stubTitle(fetchedAt: Long) = TitleEntity(
        tmdbId = 38700,
        mediaType = MediaType.MOVIE,
        title = "Paddington",
        year = 2014,
        posterPath = "/poster.jpg",
        backdropPath = null,
        overview = null,
        runtimeMin = null,
        certification = null,
        voteAverage = 7.2,
        popularity = 33.1,
        trailerKey = null,
        fetchedAt = fetchedAt,
    )

    private companion object {
        val MOVIE_DETAIL_JSON = """
            {
              "id": 38700,
              "title": "Paddington",
              "release_date": "2014-11-28",
              "runtime": 95,
              "poster_path": "/poster.jpg",
              "backdrop_path": "/backdrop.jpg",
              "overview": "A bear in London",
              "vote_average": 7.2,
              "popularity": 33.1,
              "genres": [{"id": 10751, "name": "Family"}],
              "credits": {
                "cast": [{"id": 1, "name": "Hugh Bonneville", "order": 0}],
                "crew": [{"id": 9, "name": "Paul King", "job": "Director", "department": "Directing"}]
              },
              "keywords": {"keywords": [{"id": 100, "name": "bear"}]},
              "videos": {"results": []},
              "watch/providers": {
                "results": {
                  "GB": {"flatrate": [{"provider_id": 8, "provider_name": "Netflix", "display_priority": 1}]}
                }
              },
              "release_dates": {
                "results": [
                  {"iso_3166_1": "GB", "release_dates": [{"certification": "PG", "type": 3, "release_date": "2014-11-28T00:00:00.000Z"}]}
                ]
              }
            }
        """.trimIndent()
    }
}
