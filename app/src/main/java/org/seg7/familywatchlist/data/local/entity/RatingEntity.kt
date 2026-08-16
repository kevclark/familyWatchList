package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity

/**
 * PLAN.md §2: Rating — one row per (profile, title). Latest write wins; this is a rating of
 * the *title*, not of a single watch event.
 */
@Entity(tableName = "ratings", primaryKeys = ["profileId", "tmdbId", "mediaType"])
data class RatingEntity(
    val profileId: Long,
    val tmdbId: Int,
    val mediaType: MediaType,
    val value: RatingValue,
    val ratedAt: Long,
)
