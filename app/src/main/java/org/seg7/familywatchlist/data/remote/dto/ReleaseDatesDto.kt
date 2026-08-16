package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** append_to_response=release_dates (movies) — used to pull the UK (GB) certification. */
@Serializable
data class ReleaseDatesDto(
    val results: List<ReleaseDatesCountryDto> = emptyList(),
)

@Serializable
data class ReleaseDatesCountryDto(
    @SerialName("iso_3166_1") val iso3166_1: String,
    @SerialName("release_dates") val releaseDates: List<ReleaseDateDto> = emptyList(),
)

@Serializable
data class ReleaseDateDto(
    val certification: String = "",
    val type: Int = 0,
)
