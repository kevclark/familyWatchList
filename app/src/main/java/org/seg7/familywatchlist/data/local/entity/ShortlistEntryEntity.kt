package org.seg7.familywatchlist.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.LocalDate

/**
 * PLAN.md §2: ShortlistEntry — weekly recommendation output (recommender lands in M3; the
 * schema is part of M1's data layer). scopeKey is a profileId.toString() or the literal
 * "FAMILY". [reasons] is a JSON-encoded list of contributing attributes.
 */
@Entity(
    tableName = "shortlist_entries",
    primaryKeys = ["weekStart", "scopeKey", "tmdbId", "mediaType"],
)
data class ShortlistEntryEntity(
    val weekStart: LocalDate,
    val scopeKey: String,
    val tmdbId: Int,
    val mediaType: MediaType,
    val score: Double,
    val reasons: String,
    val state: ShortlistState,
    /**
     * PLAN.md §5d (M15): true when this title was not in the scope's previous shortlist at the
     * refresh that wrote it (or was already "new" and no real refresh run has happened since).
     * Drives Home's "New" badge; the same diff feeds the Activity log (see
     * [org.seg7.familywatchlist.data.recommend.ShortlistDiff]).
     */
    @ColumnInfo(defaultValue = "0")
    val isNew: Boolean = false,
)
