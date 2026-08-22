package org.seg7.familywatchlist.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.seg7.familywatchlist.data.remote.dto.MovieDetailDto
import org.seg7.familywatchlist.data.remote.dto.VideoDto
import org.seg7.familywatchlist.data.remote.dto.VideosDto

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
}
