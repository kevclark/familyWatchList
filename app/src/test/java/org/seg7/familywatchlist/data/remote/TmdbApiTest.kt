package org.seg7.familywatchlist.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real client (auth interceptor + throttle interceptor + kotlinx.serialization
 * conversion) against MockWebServer, decoding realistic TMDB response fixtures — PLAN.md §7
 * M1 testing bar: "API client against MockWebServer".
 */
class TmdbApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TmdbApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = TmdbClient.create(
            baseUrl = server.url("/").toString(),
            accessToken = { "test-token" },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `movie detail decodes append_to_response fields including GB certification`() = runTest {
        server.enqueue(MockResponse(body = PADDINGTON_MOVIE_DETAIL_JSON))

        val detail = api.movieDetail(38700)

        assertEquals("Paddington", detail.title)
        assertEquals(95, detail.runtime)
        assertEquals(listOf("Family", "Comedy"), detail.genres.map { it.name })
        assertEquals(2, detail.credits?.cast?.size)
        assertEquals("Hugh Bonneville", detail.credits?.cast?.first { it.order == 0 }?.name)
        assertTrue(detail.credits?.crew.orEmpty().any { it.job == "Director" })
        assertEquals(listOf("bear", "london"), detail.keywords?.keywords?.map { it.name })
        assertEquals("dQw4w9WgXcQ", detail.videos?.results?.first { it.type == "Trailer" }?.key)
        val gbProviders = detail.watchProviders?.results?.get("GB")
        assertEquals(listOf(8), gbProviders?.flatrate?.map { it.providerId })
        val gbCertification = detail.releaseDates?.results
            ?.first { it.iso3166_1 == "GB" }
            ?.releaseDates
            ?.firstOrNull { it.certification.isNotBlank() }
            ?.certification
        assertEquals("PG", gbCertification)

        val recorded = server.takeRequest()
        assertEquals("Bearer test-token", recorded.headers["Authorization"])
        assertTrue(recorded.target.contains("append_to_response="))
    }

    @Test
    fun `tv detail decodes the results-keyed keywords wrapper and content_ratings`() = runTest {
        server.enqueue(MockResponse(body = BLUEY_TV_DETAIL_JSON))

        val detail = api.tvDetail(82728)

        assertEquals("Bluey", detail.name)
        assertEquals(listOf("family", "dog"), detail.keywords?.results?.map { it.name })
        val gbRating = detail.contentRatings?.results?.first { it.iso3166_1 == "GB" }?.rating
        assertEquals("U", gbRating)
    }

    @Test
    fun `search multi decodes mixed movie tv and person rows without failing`() = runTest {
        server.enqueue(MockResponse(body = SEARCH_MULTI_JSON))

        val page = api.searchMulti(query = "paddington")

        assertEquals(3, page.results.size)
        val movie = page.results.first { it.mediaType == "movie" }
        assertEquals("Paddington", movie.title)
        val person = page.results.first { it.mediaType == "person" }
        assertNull(person.title)
        assertNull(person.name)
    }

    @Test
    fun `discover movie decodes a page of results`() = runTest {
        server.enqueue(MockResponse(body = DISCOVER_MOVIE_JSON))

        val page = api.discoverMovies(withWatchProviders = "8|337")

        assertEquals(1, page.results.size)
        assertEquals("Paddington", page.results.first().title)
        val recorded = server.takeRequest()
        assertTrue(recorded.target.contains("watch_region=GB"))
    }

    @Test
    fun `provider list decodes the GB seed`() = runTest {
        server.enqueue(MockResponse(body = PROVIDER_LIST_JSON))

        val providers = api.movieProviders()

        assertEquals(listOf("Netflix", "Amazon Prime Video"), providers.results.map { it.providerName })
    }

    /** PLAN.md §7 M2f: watch_region is a real call-time parameter, not a silently-baked GB default. */
    @Test
    fun `discover movie sends a non-default watch_region when one is passed`() = runTest {
        server.enqueue(MockResponse(body = DISCOVER_MOVIE_JSON))

        api.discoverMovies(watchRegion = "US", withWatchProviders = "8|337")

        val recorded = server.takeRequest()
        assertTrue(recorded.target.contains("watch_region=US"))
    }

    @Test
    fun `watch provider regions decodes the live TMDB region list`() = runTest {
        server.enqueue(MockResponse(body = REGIONS_JSON))

        val regions = api.watchProviderRegions()

        assertEquals(listOf("GB", "US"), regions.results.map { it.isoCode })
        assertEquals(listOf("United Kingdom", "United States"), regions.results.map { it.englishName })
    }

    private companion object {
        val PADDINGTON_MOVIE_DETAIL_JSON = """
            {
              "id": 38700,
              "title": "Paddington",
              "release_date": "2014-11-28",
              "runtime": 95,
              "poster_path": "/poster.jpg",
              "backdrop_path": "/backdrop.jpg",
              "overview": "A young Peruvian bear travels to London.",
              "vote_average": 7.2,
              "popularity": 33.1,
              "genres": [{"id": 10751, "name": "Family"}, {"id": 35, "name": "Comedy"}],
              "credits": {
                "cast": [
                  {"id": 1, "name": "Hugh Bonneville", "order": 0},
                  {"id": 2, "name": "Sally Hawkins", "order": 1}
                ],
                "crew": [
                  {"id": 9, "name": "Paul King", "job": "Director", "department": "Directing"}
                ]
              },
              "keywords": {"keywords": [{"id": 100, "name": "bear"}, {"id": 101, "name": "london"}]},
              "videos": {
                "results": [
                  {"id": "v1", "key": "dQw4w9WgXcQ", "site": "YouTube", "type": "Trailer", "official": true}
                ]
              },
              "watch/providers": {
                "results": {
                  "GB": {
                    "link": "https://www.themoviedb.org/movie/38700-paddington/watch",
                    "flatrate": [{"provider_id": 8, "provider_name": "Netflix", "logo_path": "/n.png", "display_priority": 1}]
                  }
                }
              },
              "release_dates": {
                "results": [
                  {
                    "iso_3166_1": "GB",
                    "release_dates": [{"certification": "PG", "type": 3, "release_date": "2014-11-28T00:00:00.000Z"}]
                  },
                  {
                    "iso_3166_1": "US",
                    "release_dates": [{"certification": "PG", "type": 3, "release_date": "2015-01-16T00:00:00.000Z"}]
                  }
                ]
              }
            }
        """.trimIndent()

        val BLUEY_TV_DETAIL_JSON = """
            {
              "id": 82728,
              "name": "Bluey",
              "first_air_date": "2018-10-01",
              "episode_run_time": [7],
              "poster_path": "/bluey.jpg",
              "backdrop_path": "/bluey-bg.jpg",
              "overview": "A blue heeler pup and her family.",
              "vote_average": 8.7,
              "popularity": 200.4,
              "genres": [{"id": 10762, "name": "Kids"}],
              "credits": {"cast": [], "crew": []},
              "keywords": {"results": [{"id": 1, "name": "family"}, {"id": 2, "name": "dog"}]},
              "videos": {"results": []},
              "watch/providers": {"results": {}},
              "content_ratings": {
                "results": [
                  {"iso_3166_1": "GB", "rating": "U"},
                  {"iso_3166_1": "US", "rating": "TV-Y"}
                ]
              }
            }
        """.trimIndent()

        val SEARCH_MULTI_JSON = """
            {
              "page": 1,
              "results": [
                {"id": 38700, "media_type": "movie", "title": "Paddington", "poster_path": "/p.jpg", "release_date": "2014-11-28", "vote_average": 7.2, "popularity": 33.1},
                {"id": 82728, "media_type": "tv", "name": "Bluey", "poster_path": "/b.jpg", "first_air_date": "2018-10-01", "vote_average": 8.7, "popularity": 200.4},
                {"id": 999, "media_type": "person", "known_for_department": "Acting", "popularity": 5.0}
              ],
              "total_pages": 1,
              "total_results": 3
            }
        """.trimIndent()

        val DISCOVER_MOVIE_JSON = """
            {
              "page": 1,
              "results": [
                {"id": 38700, "title": "Paddington", "poster_path": "/p.jpg", "release_date": "2014-11-28", "vote_average": 7.2, "popularity": 33.1}
              ],
              "total_pages": 5,
              "total_results": 92
            }
        """.trimIndent()

        val PROVIDER_LIST_JSON = """
            {
              "results": [
                {"provider_id": 8, "provider_name": "Netflix", "logo_path": "/n.png", "display_priority": 1},
                {"provider_id": 9, "provider_name": "Amazon Prime Video", "logo_path": "/a.png", "display_priority": 2}
              ]
            }
        """.trimIndent()

        val REGIONS_JSON = """
            {
              "results": [
                {"iso_3166_1": "GB", "english_name": "United Kingdom", "native_name": "United Kingdom"},
                {"iso_3166_1": "US", "english_name": "United States", "native_name": "United States"}
              ]
            }
        """.trimIndent()
    }
}
