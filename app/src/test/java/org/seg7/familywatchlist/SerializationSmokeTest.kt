package org.seg7.familywatchlist

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.remote.dto.ConfigurationDto

/**
 * M0 smoke test: proves the kotlinx.serialization plugin generates serializers and that
 * unknown TMDB fields do not blow up parsing (the app pins `ignoreUnknownKeys`).
 */
class SerializationSmokeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses tmdb configuration payload`() {
        val payload = """
            {
              "images": {
                "base_url": "http://image.tmdb.org/t/p/",
                "secure_base_url": "https://image.tmdb.org/t/p/",
                "poster_sizes": ["w92", "w342", "original"],
                "backdrop_sizes": ["w300", "w780", "original"]
              },
              "change_keys": ["adult", "budget"]
            }
        """.trimIndent()

        val config = json.decodeFromString<ConfigurationDto>(payload)

        assertEquals("https://image.tmdb.org/t/p/", config.images.secureBaseUrl)
        assertTrue("w342 is the poster size PLAN.md §3 uses", "w342" in config.images.posterSizes)
        assertTrue("w780 is the backdrop size PLAN.md §3 uses", "w780" in config.images.backdropSizes)
    }
}
