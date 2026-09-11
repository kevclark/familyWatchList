package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * PLAN.md §5c (M14): one TMDB user review snippet, scoped by `tmdbId`/`mediaType` like
 * [TitleAttributeEntity] — free on the same `append_to_response=reviews` detail call. [reviewId]
 * is TMDB's own review id (a hex string, not numeric), so it joins the composite primary key
 * rather than [ord] existing purely to disambiguate rows.
 */
@Entity(
    tableName = "reviews",
    primaryKeys = ["tmdbId", "mediaType", "reviewId"],
    indices = [Index(value = ["tmdbId", "mediaType"])],
)
data class ReviewEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val reviewId: String,
    val author: String,
    val content: String,
    val url: String,
    /** The reviewer's own optional 1-10 rating for the title; null when they left none. */
    val rating: Double?,
    /** ISO-8601 from TMDB; kept as a plain string, same posture as other display-only text fields. */
    val createdAt: String?,
)
