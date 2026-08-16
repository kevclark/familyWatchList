package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** append_to_response=content_ratings (TV) — used to pull the UK (GB) certification. */
@Serializable
data class ContentRatingsDto(
    val results: List<ContentRatingDto> = emptyList(),
)

@Serializable
data class ContentRatingDto(
    @SerialName("iso_3166_1") val iso3166_1: String,
    val rating: String = "",
)
