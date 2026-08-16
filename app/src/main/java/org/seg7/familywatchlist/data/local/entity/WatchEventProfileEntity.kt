package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * PLAN.md §2: WatchEventProfile — the multi-tag join table. One WatchEvent row can carry N
 * profile tags (family night = one event, several profiles).
 */
@Entity(
    tableName = "watch_event_profiles",
    primaryKeys = ["watchEventId", "profileId"],
    indices = [Index(value = ["profileId"])],
)
data class WatchEventProfileEntity(
    val watchEventId: Long,
    val profileId: Long,
)
