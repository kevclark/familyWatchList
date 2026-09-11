package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * append_to_response=reviews (PLAN.md §5c): one row from TMDB's own user-submitted review list,
 * free on the same detail call as [ExternalIdsDto]. Reuses the existing generic
 * [PagedResponseDto] wrapper via `MovieDetailDto.reviews`/`TvDetailDto.reviews` — TMDB's reviews
 * response shape matches that paging envelope exactly, no new paging DTO needed.
 */
@Serializable
data class ReviewDto(
    val id: String,
    val author: String,
    val content: String,
    val url: String,
    @SerialName("author_details") val authorDetails: AuthorDetailsDto? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** The reviewer's own optional 1-10 rating for the title, shown alongside their review text. */
@Serializable
data class AuthorDetailsDto(
    val rating: Double? = null,
)
