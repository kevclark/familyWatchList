package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class KeywordDto(
    val id: Int,
    val name: String,
)

/** TMDB quirk: the movie keywords wrapper field is "keywords". */
@Serializable
data class MovieKeywordsDto(
    val keywords: List<KeywordDto> = emptyList(),
)

/** TMDB quirk: the TV keywords wrapper field is "results", not "keywords". */
@Serializable
data class TvKeywordsDto(
    val results: List<KeywordDto> = emptyList(),
)
