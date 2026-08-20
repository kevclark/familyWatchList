package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity

/**
 * PLAN.md §4a: per-profile storage for the three "Tune my picks" sliders (discovery, recency,
 * personal-match-vs-popular). A small companion table rather than new columns on [ProfileEntity]
 * — keeps the slider concept (a distinct, independently-evolving feature) out of the profile
 * identity/avatar/age-cap row, and means a profile with no row here simply hasn't customised
 * anything yet (the repository defaults it to [org.seg7.familywatchlist.data.recommend.SliderSettings.DEFAULT]
 * rather than needing every profile pre-seeded with a row at creation time).
 *
 * No foreign key to `profiles` — same "no FK" precedent as [WatchEventProfileEntity] (see its
 * kdoc): Room has no cascading-delete-safe way to express it without extra ceremony for a table
 * this small, and an orphaned row after a profile deletion is harmless (never read without a
 * matching profileId reaching the recommender in the first place).
 *
 * The three columns mirror [org.seg7.familywatchlist.data.recommend.SliderSettings]'s fields
 * exactly, each constrained to [-1, 1] by that class's own `init` block on read/write, not by the
 * schema (SQLite has no portable numeric-range constraint via Room's annotations here).
 */
@Entity(tableName = "profile_sliders", primaryKeys = ["profileId"])
data class ProfileSlidersEntity(
    val profileId: Long,
    val discovery: Double = 0.0,
    val recency: Double = 0.0,
    val personalMatch: Double = 0.0,
)
