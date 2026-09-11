package org.seg7.familywatchlist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * append_to_response=external_ids (PLAN.md §5c): TMDB's free cross-reference to the title's
 * real IMDb id, on the *same* /movie/{id} and /tv/{id} detail call the app already makes — no
 * new API/key, zero extra round-trips. Used to build a real "View on IMDb" link.
 */
@Serializable
data class ExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null,
)
