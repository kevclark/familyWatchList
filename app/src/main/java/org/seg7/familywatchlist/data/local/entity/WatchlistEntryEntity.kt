package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity

/**
 * PLAN.md §2: WatchlistEntry — one shared family "Want to Watch" list, tagged with who added
 * each title. Logging a watch of a listed title auto-flips its state to WATCHED (repository
 * layer responsibility, not enforced by the schema).
 */
@Entity(tableName = "watchlist_entries", primaryKeys = ["tmdbId", "mediaType"])
data class WatchlistEntryEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val addedByProfileId: Long,
    val addedAt: Long,
    val state: WatchlistState,
)
