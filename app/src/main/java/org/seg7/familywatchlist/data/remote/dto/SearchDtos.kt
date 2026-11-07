package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.Serializable

/** One row from `search/person` (PLAN.md §5f). */
@Serializable
data class PersonDto(
    val id: Int,
    val name: String,
    val popularity: Double? = null,
)

/** `person/{id}/tv_credits`: only the cast side is used (PLAN.md §5f, TV has no with_cast). */
@Serializable
data class PersonTvCreditsDto(
    val cast: List<MediaSummaryDto> = emptyList(),
)
