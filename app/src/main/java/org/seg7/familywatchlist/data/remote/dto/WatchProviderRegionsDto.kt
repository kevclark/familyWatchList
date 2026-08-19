package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/watch/providers/regions` (PLAN.md §7 M2f) — every region TMDB's watch-provider data covers. */
@Serializable
data class WatchProviderRegionsResponseDto(
    val results: List<WatchProviderRegionDto> = emptyList(),
)

@Serializable
data class WatchProviderRegionDto(
    @SerialName("iso_3166_1") val isoCode: String,
    @SerialName("english_name") val englishName: String,
    @SerialName("native_name") val nativeName: String = "",
)
