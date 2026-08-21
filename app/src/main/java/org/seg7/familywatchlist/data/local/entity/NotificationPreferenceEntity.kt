package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): whether *this* profile's completed weekly
 * refresh contributes to the shortlist-ready notification — every individual [ProfileEntity] and
 * the Family profile (keyed by [FAMILY_PROFILE_SENTINEL_ID], M3d's sentinel, reused here rather
 * than inventing a second identifier scheme — see that constant's kdoc) each get their own row.
 *
 * A variable-cardinality set (up to 10 individuals + optionally Family) doesn't fit DataStore's
 * fixed-key model the way the single app-level master toggle
 * ([org.seg7.familywatchlist.data.repository.UserPreferencesRepository.notificationsEnabled])
 * does, so this is a small companion table — same "no FK, profile with no row = untouched
 * default" precedent as [ProfileSlidersEntity] (see its kdoc): an orphaned row after a profile
 * deletion is harmless (never read without a matching, still-live profile id reaching the
 * notification gate in the first place).
 *
 * Default **on** ([enabled] = true) — Kev's explicit ask (PLAN.md §4): "preserving today's
 * existing behaviour until someone explicitly opts out, not an opt-in cold start for a feature
 * that already exists." A missing row (repository layer,
 * [org.seg7.familywatchlist.data.repository.NotificationPreferencesRepository]) reads as enabled,
 * matching this default.
 */
@Entity(tableName = "profile_notification_prefs", primaryKeys = ["profileId"])
data class NotificationPreferenceEntity(
    val profileId: Long,
    val enabled: Boolean = true,
)
