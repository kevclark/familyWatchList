package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response shape of the append_to_response "watch/providers" block, and of /watch/providers/{movie,tv} list rows. */
@Serializable
data class WatchProvidersResponseDto(
    val results: Map<String, CountryWatchProvidersDto> = emptyMap(),
)

@Serializable
data class CountryWatchProvidersDto(
    val link: String? = null,
    val flatrate: List<WatchProviderDto> = emptyList(),
    val free: List<WatchProviderDto> = emptyList(),
)

@Serializable
data class WatchProviderDto(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = 0,
)

/** /watch/providers/movie and /watch/providers/tv (provider seed list, PLAN.md §3). */
@Serializable
data class ProviderListResponseDto(
    val results: List<WatchProviderDto> = emptyList(),
)
