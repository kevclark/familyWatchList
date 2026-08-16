package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreditsDto(
    val cast: List<CastMemberDto> = emptyList(),
    val crew: List<CrewMemberDto> = emptyList(),
)

@Serializable
data class CastMemberDto(
    val id: Int,
    val name: String,
    val order: Int = 0,
)

/** PLAN.md §2: only director/creator crew rows get stored — filtered by [job] downstream. */
@Serializable
data class CrewMemberDto(
    val id: Int,
    val name: String,
    val job: String = "",
    val department: String = "",
)
