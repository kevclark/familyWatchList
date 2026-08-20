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
    /**
     * PLAN.md §4 scoring's "voteAverage/10, min 20 votes" — the vote-count floor needs this,
     * added at M3 (schema v2 -> v3) alongside [org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity].
     * Null for rows that predate this migration or came from a summary payload with no count.
     */
    val voteCount: Int? = null,
    val popularity: Double?,
    /**
     * YouTube video key for the title's trailer, from the same `append_to_response=videos`
     * detail call (PLAN.md §2: "persisted at M1 even though trailer UI is M4 — it's free on a
     * call we're already making"). M1 shipped without it despite the plan calling for it, so
     * M2b adds the column (schema v1 → v2) and the ▶ Trailer button that consumes it.
     * Null when TMDB has no official YouTube trailer for the title.
     */
    val trailerKey: String?,
    val fetchedAt: Long,
)
