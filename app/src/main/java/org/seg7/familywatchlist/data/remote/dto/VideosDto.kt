package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VideosDto(
    val results: List<VideoDto> = emptyList(),
)

/** PLAN.md §1: trailer key -> Intent to YouTube. Filter to site == "YouTube" && type == "Trailer". */
@Serializable
data class VideoDto(
    val id: String,
    val key: String,
    val site: String,
    val type: String,
    val official: Boolean = false,
)
