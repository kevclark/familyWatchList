package org.seg7.familywatchlist.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.data.remote.dto.CountryWatchProvidersDto
import org.seg7.familywatchlist.data.remote.dto.MovieDetailDto
import org.seg7.familywatchlist.data.remote.dto.VideoDto
import org.seg7.familywatchlist.data.remote.dto.VideosDto
import org.seg7.familywatchlist.data.remote.dto.WatchProviderDto
import org.seg7.familywatchlist.data.remote.dto.WatchProvidersResponseDto

/**
 * PLAN.md §1/§2: "trailer key -> Intent to YouTube. Filter to site == YouTube && type ==
 * Trailer." Exercised through the public [MovieDetailDto.toTitleEntity] mapper (the private
 * `youTubeTrailerKey` extension it delegates to isn't itself visible to tests) with fixture
 * video lists covering every branch of the selection priority: official trailer > any trailer >
 * official teaser > any teaser > null when nothing qualifies.
 */
class TmdbMappersTest {
    private fun movieWithVideos(videos: List<VideoDto>): MovieDetailDto = MovieDetailDto(
        id = 1,
        title = "Fixture",
        videos = VideosDto(results = videos),
    )

    @Test
    fun `official trailer wins over a fan-uploaded trailer`() {
        val videos = listOf(
            VideoDto(id = "a", key = "fan-trailer", site = "YouTube", type = "Trailer", official = false),
            VideoDto(id = "b", key = "official-trailer", site = "YouTube", type = "Trailer", official = true),
        )
        val entity = movieWithVideos(videos).toTitleEntity(fetchedAt = 0L)
        assertEquals("official-trailer", entity.trailerKey)
    }

    @Test
    fun `any trailer wins when none is official`() {
        val videos = listOf(
            VideoDto(id = "a", key = "fan-trailer", site = "YouTube", type = "Trailer", official = false),
        )
        val entity = movieWithVideos(videos).toTitleEntity(fetchedAt = 0L)
        assertEquals("fan-trailer", entity.trailerKey)
    }

    @Test
    fun `a teaser is accepted only when there is no trailer at all`() {
        val videos = listOf(
            VideoDto(id = "a", key = "teaser-key", site = "YouTube", type = "Teaser", official = true),
        )
        val entity = movieWithVideos(videos).toTitleEntity(fetchedAt = 0L)
        assertEquals("teaser-key", entity.trailerKey)
    }

    @Test
    fun `a trailer beats a teaser even when the teaser is official and the trailer is not`() {
        val videos = listOf(
            VideoDto(id = "a", key = "teaser-key", site = "YouTube", type = "Teaser", official = true),
            VideoDto(id = "b", key = "fan-trailer", site = "YouTube", type = "Trailer", official = false),
        )
        val entity = movieWithVideos(videos).toTitleEntity(fetchedAt = 0L)
        assertEquals("fan-trailer", entity.trailerKey)
    }

    @Test
    fun `non-YouTube videos are ignored even if typed Trailer`() {
        val videos = listOf(
            VideoDto(id = "a", key = "vimeo-trailer", site = "Vimeo", type = "Trailer", official = true),
        )
        val entity = movieWithVideos(videos).toTitleEntity(fetchedAt = 0L)
        assertNull(entity.trailerKey)
    }

    @Test
    fun `no qualifying video leaves trailerKey null`() {
        val videos = listOf(
            VideoDto(id = "a", key = "behind-the-scenes", site = "YouTube", type = "Featurette", official = true),
        )
        val entity = movieWithVideos(videos).toTitleEntity(fetchedAt = 0L)
        assertNull(entity.trailerKey)
    }

    @Test
    fun `no videos at all leaves trailerKey null`() {
        val entity = MovieDetailDto(id = 1, title = "Fixture", videos = null).toTitleEntity(fetchedAt = 0L)
        assertNull(entity.trailerKey)
    }

    /**
     * M6 (PLAN.md §5 "Paid (rent/buy) titles" addendum): `toAvailability` must map all four
     * `CountryWatchProvidersDto` buckets — flatrate/free (unchanged) and now rent/buy — into
     * their matching [ProviderKind], from the one existing per-title `/watch/providers` payload,
     * no new network call.
     */
    @Test
    fun `toAvailability maps flatrate, free, rent and buy into their matching ProviderKind`() {
        val dto = MovieDetailDto(
            id = 42,
            title = "Central Intelligence",
            watchProviders = WatchProvidersResponseDto(
                results = mapOf(
                    "GB" to CountryWatchProvidersDto(
                        flatrate = listOf(WatchProviderDto(providerId = 8, providerName = "Netflix")),
                        free = listOf(WatchProviderDto(providerId = 613, providerName = "Freevee")),
                        rent = listOf(WatchProviderDto(providerId = 2, providerName = "Apple TV")),
                        buy = listOf(WatchProviderDto(providerId = 10, providerName = "Amazon Video")),
                    )
                )
            ),
        )

        val rows = dto.toAvailability(fetchedAt = 0L)

        assertEquals(4, rows.size)
        assertEquals(MediaType.MOVIE, rows.first().mediaType)
        assertEquals(ProviderKind.FLATRATE, rows.single { it.providerId == 8 }.kind)
        assertEquals(ProviderKind.FREE, rows.single { it.providerId == 613 }.kind)
        assertEquals(ProviderKind.RENT, rows.single { it.providerId == 2 }.kind)
        assertEquals(ProviderKind.BUY, rows.single { it.providerId == 10 }.kind)
    }
}
