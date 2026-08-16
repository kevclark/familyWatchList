package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity

/**
 * PLAN.md §2: Title. Composite PK (tmdbId, mediaType) — the same tmdbId can exist as both a
 * movie and a TV show. [fetchedAt] drives the 30-day metadata TTL (PLAN.md §3).
 */
@Entity(tableName = "titles", primaryKeys = ["tmdbId", "mediaType"])
data class TitleEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val runtimeMin: Int?,
    /** UK certification, e.g. "12", "15", "18". Null if TMDB has no GB release/content rating. */
    val certification: String?,
    val voteAverage: Double?,
    val popularity: Double?,
    val fetchedAt: Long,
)
