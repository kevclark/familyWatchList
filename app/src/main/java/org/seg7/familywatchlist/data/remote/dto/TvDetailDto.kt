package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /tv/{id}?append_to_response=credits,keywords,videos,watch/providers,content_ratings
 * (PLAN.md §3).
 */
@Serializable
data class TvDetailDto(
    val id: Int,
    val name: String,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    val genres: List<GenreDto> = emptyList(),
    val credits: CreditsDto? = null,
    val keywords: TvKeywordsDto? = null,
    val videos: VideosDto? = null,
    @SerialName("watch/providers") val watchProviders: WatchProvidersResponseDto? = null,
    @SerialName("content_ratings") val contentRatings: ContentRatingsDto? = null,
    /**
     * TV show creators live here, not in credits.crew (TMDB has no "Creator" crew job) — this
     * is what PLAN.md §2's TitleAttribute "crew: director/creator only" maps to for TV.
     */
    @SerialName("created_by") val createdBy: List<CreatedByDto> = emptyList(),
)

@Serializable
data class CreatedByDto(
    val id: Int,
    val name: String,
)
