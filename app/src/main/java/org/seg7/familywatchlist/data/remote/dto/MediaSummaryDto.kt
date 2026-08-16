package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Generic TMDB paged list wrapper — used by search/multi, discover/*, and */recommendations. */
@Serializable
data class PagedResponseDto<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("total_results") val totalResults: Int = 0,
)

/**
 * One row from /search/multi, /discover/{movie,tv}, or /{movie,tv}/{id}/recommendations.
 * [mediaType] is only present from /search/multi (PLAN.md §3: "filter results to movie/tv
 * client-side"); discover/recommendations calls are already scoped to one media type by URL.
 */
@Serializable
data class MediaSummaryDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val popularity: Double? = null,
)
