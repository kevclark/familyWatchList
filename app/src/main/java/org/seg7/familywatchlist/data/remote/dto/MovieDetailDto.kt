package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /movie/{id}?append_to_response=credits,keywords,videos,watch/providers,release_dates,
 * external_ids,reviews (PLAN.md §3, §5c) — one round-trip fills Title, TitleAttribute,
 * ProviderAvailability, trailer key, UK certification, the real IMDb id, and TMDB review
 * snippets.
 */
@Serializable
data class MovieDetailDto(
    val id: Int,
    val title: String,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    val genres: List<GenreDto> = emptyList(),
    val credits: CreditsDto? = null,
    val keywords: MovieKeywordsDto? = null,
    val videos: VideosDto? = null,
    @SerialName("watch/providers") val watchProviders: WatchProvidersResponseDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
    val reviews: PagedResponseDto<ReviewDto>? = null,
)
