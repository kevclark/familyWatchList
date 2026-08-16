package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * PLAN.md §2: WatchEvent — one row per "we watched this", multi-tagged with profiles via
 * [WatchEventProfileEntity] (family night = one event, N profiles).
 */
@Entity(
    tableName = "watch_events",
    indices = [Index(value = ["tmdbId", "mediaType"])],
)
data class WatchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val mediaType: MediaType,
    val watchedAt: LocalDate,
    val note: String?,
)
