package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PLAN.md §4 "The Family profile" (Kev, 2026-08-21): a first-class, *persistent* alternative to
 * the ad-hoc who's-watching chip row (M3c) — "the same people, basically every time." Deliberately
 * "effectively a singleton" per Kev ("just one" — a single household, not multiple named groups),
 * so unlike [ProfileEntity] this has a **fixed** primary key ([SINGLETON_ID]) rather than
 * `autoGenerate` — there is never more than one row, so there's nothing for an auto-increment id
 * to disambiguate. `name` defaults to "Family" at creation but is editable, same as its avatar.
 *
 * This table's own id is *not* what makes the Family profile distinguishable as "the active
 * profile" — see [FAMILY_PROFILE_SENTINEL_ID] for that (a completely separate concern: this id
 * lives in the `family_profile` table, the sentinel lives in
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.activeProfileId], and they
 * are deliberately different values so a stray `==` comparison between the two spaces can never
 * accidentally compile-and-pass).
 */
@Entity(tableName = "family_profile")
data class FamilyProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val name: String,
    val avatarKey: String,
    val createdAt: Long,
) {
    companion object {
        const val SINGLETON_ID: Long = 1L
    }
}

/**
 * PLAN.md §4's recommended mechanism for distinguishing "the Family profile is active" from a
 * real [ProfileEntity.id] wherever
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.activeProfileId] (and
 * everything it flows into — `ActiveProfile.id`, `HomeViewModel`'s constructor param, etc.) is
 * read: a reserved **negative** id. Room's `autoGenerate = true` primary keys on [ProfileEntity]
 * start at 1 and only ever increase, so a real profile id can never be negative — this sentinel
 * can never collide with one, now or in the future, without touching the `profiles` table or its
 * cap logic at all (PLAN.md §4's own stated rationale for recommending this over, say, a wrapper/
 * nullable type).
 *
 * Every site that reads `activeProfileId` and needs to branch on "is Family active, or a real
 * person" does so with an explicit `== FAMILY_PROFILE_SENTINEL_ID` check — see
 * [org.seg7.familywatchlist.ui.ActiveProfile] for the richer wrapper built on top of this for
 * navigation/display, and PROGRESS.md's M3d entry for the full list of call sites this reasoning
 * was applied to.
 */
const val FAMILY_PROFILE_SENTINEL_ID: Long = -1L
