package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDB `/configuration` response (PLAN.md §3 — fetched once to build image URLs).
 *
 * First real DTO in the project; it also serves as the M0 proof that the
 * kotlinx.serialization compiler plugin is wired into the build.
 */
@Serializable
data class ConfigurationDto(
    @SerialName("images") val images: ImagesConfigDto,
)

@Serializable
data class ImagesConfigDto(
    @SerialName("secure_base_url") val secureBaseUrl: String,
    @SerialName("poster_sizes") val posterSizes: List<String> = emptyList(),
    @SerialName("backdrop_sizes") val backdropSizes: List<String> = emptyList(),
)
