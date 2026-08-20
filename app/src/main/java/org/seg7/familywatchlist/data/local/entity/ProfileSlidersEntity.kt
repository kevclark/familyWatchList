package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import org.seg7.familywatchlist.data.recommend.RecommenderSpec

/**
 * PLAN.md §4a: per-profile storage for the three "Tune my picks" taste sliders (discovery,
 * recency, personal-match-vs-popular) plus the unrelated "Suggestion count" control (slider 5).
 * A small companion table rather than new columns on [ProfileEntity] — keeps the slider concept
 * (a distinct, independently-evolving feature) out of the profile identity/avatar/age-cap row,
 * and means a profile with no row here simply hasn't customised anything yet (the repository
 * defaults it to [org.seg7.familywatchlist.data.recommend.SliderSettings.DEFAULT] /
 * [RecommenderSpec.SHORTLIST_TARGET_SIZE] rather than needing every profile pre-seeded with a
 * row at creation time).
 *
 * No foreign key to `profiles` — same "no FK" precedent as [WatchEventProfileEntity] (see its
 * kdoc): Room has no cascading-delete-safe way to express it without extra ceremony for a table
 * this small, and an orphaned row after a profile deletion is harmless (never read without a
 * matching profileId reaching the recommender in the first place).
 *
 * The three `Double` columns mirror [org.seg7.familywatchlist.data.recommend.SliderSettings]'s
 * fields exactly, each constrained to [-1, 1] by that class's own `init` block on read/write, not
 * by the schema (SQLite has no portable numeric-range constraint via Room's annotations here).
 *
 * [suggestionCount] and [eligibleCandidateCount] are fundamentally different from the three taste
 * sliders — literal integer counts, not signed `s ∈ [-1, 1]` taste values — and store two
 * genuinely separate concerns (design corrected 2026-08-20, same day, after Kev pushed back on an
 * initial fixed max=50 UI ceiling): [suggestionCount] is what the user *requested* (never
 * silently overwritten just because this week's pool is thin); [eligibleCandidateCount] is the
 * real, last-known number of candidates actually available for this profile, persisted by
 * [org.seg7.familywatchlist.data.repository.RecommendationRepository.refreshProfileShortlist] on
 * every refresh. The "Tune my picks" slider's max is [eligibleCandidateCount] (read from this
 * last-known persisted value, not a live fetch — PLAN.md §4a slider 5); the *effective* target
 * size actually passed to `ShortlistAssembler` at refresh time is
 * `min(suggestionCount, eligibleCandidateCount)` — see [suggestionCountRange]'s kdoc for the
 * matching UI-side min/max derivation, including the "disable the slider" zero-eligible case.
 */
@Entity(tableName = "profile_sliders", primaryKeys = ["profileId"])
data class ProfileSlidersEntity(
    val profileId: Long,
    val discovery: Double = 0.0,
    val recency: Double = 0.0,
    val personalMatch: Double = 0.0,
    val suggestionCount: Int = RecommenderSpec.SHORTLIST_TARGET_SIZE,
    val eligibleCandidateCount: Int = RecommenderSpec.SHORTLIST_TARGET_SIZE,
)
