package org.seg7.familywatchlist.data.backup

import kotlinx.serialization.Serializable
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.WatchlistState

/**
 * PLAN.md §2 "Backup/restore" + §5 screen 8 "JSON backup/restore" + §8's AI/ML guardrail
 * ("never export/share the TMDB cache"). One JSON document, written/read via Storage Access
 * Framework (M4b) — see [org.seg7.familywatchlist.data.repository.BackupRepository].
 *
 * **Scope — user data only, TMDB cache deliberately excluded.** This mirrors PLAN.md §2's
 * explicit list (Profiles, WatchEvents, Ratings, WatchlistEntries, Provider.subscribed) plus the
 * things added by milestones after §2 was written that are just as clearly *this app's own*
 * data, not TMDB's: the Family profile + its membership (M3d), per-profile slider settings
 * (M3/M3a), per-profile notification preferences (M3e), and the handful of app-level user
 * preferences (region, notifications master switch, refresh schedule, family blend slider).
 * Everything else PLAN.md §2 defines — Title, TitleAttribute, ProviderAvailability, ShortlistEntry,
 * DiscoverCache, and the `Provider` catalog rows themselves (only `subscribed` is ours; the
 * provider list is TMDB's) — is a queryable, TTL'd cache TMDB provides and is **never** included
 * here. Losing this file doesn't lose anything about what's streaming where; that refetches on
 * the next launch/refresh exactly as it does after a fresh install.
 *
 * **Versioning.** [version] is a plain incrementing integer, checked exactly on import
 * ([org.seg7.familywatchlist.data.repository.BackupRepository.RestoreOutcome.Error] on any
 * mismatch rather than attempting a best-effort partial read) — the schema is small enough that
 * "reject and ask the user to re-export" is a better failure mode than silent data loss from a
 * partially-understood older/newer file. Bump [CURRENT_VERSION] and add explicit migration logic
 * in [org.seg7.familywatchlist.data.repository.BackupRepository] the day this shape actually
 * needs to change; there is deliberately no forward-compatibility machinery here yet because
 * there's only ever been one shape.
 *
 * **IDs are preserved, not regenerated**, on restore ([ProfileBackup.id], [WatchEventBackup.id])
 * — every other table in this payload (`watchEvents[].profileIds`, `ratings[].profileId`,
 * `watchlistEntries[].addedByProfileId`, `profileSliders[].profileId`,
 * `notificationPreferences[].profileId`, `familyProfileMemberIds`) references a profile by that
 * same id, so re-generating ids on import would silently break every cross-reference in the same
 * file.
 */
@Serializable
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    /** ISO-8601 instant string, informational only — not read back on restore. */
    val exportedAt: String,
    val profiles: List<ProfileBackup>,
    val familyProfile: FamilyProfileBackup?,
    /** [ProfileBackup.id] values — [FamilyProfileEntity]'s curated membership (PLAN.md §2). */
    val familyProfileMemberIds: List<Long>,
    val watchEvents: List<WatchEventBackup>,
    val ratings: List<RatingBackup>,
    val watchlistEntries: List<WatchlistEntryBackup>,
    /** TMDB provider ids the household subscribes to — the *only* `Provider` column exported. */
    val subscribedProviderIds: List<Int>,
    val profileSliders: List<ProfileSlidersBackup>,
    val notificationPreferences: List<NotificationPreferenceBackup>,
    val userPreferences: UserPreferencesBackup,
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

@Serializable
data class ProfileBackup(
    val id: Long,
    val name: String,
    val avatarKey: String,
    val ageRatingCap: String?,
    val createdAt: Long,
)

@Serializable
data class FamilyProfileBackup(
    val name: String,
    val avatarKey: String,
    val createdAt: Long,
)

@Serializable
data class WatchEventBackup(
    val id: Long,
    val tmdbId: Int,
    val mediaType: MediaType,
    /** ISO-8601 date, e.g. "2026-08-22" — [java.time.LocalDate.toString]'s own format. */
    val watchedAt: String,
    val note: String?,
    /** Denormalized from `watch_event_profiles` — the multi-tag join table, inlined per event. */
    val profileIds: List<Long>,
)

@Serializable
data class RatingBackup(
    val profileId: Long,
    val tmdbId: Int,
    val mediaType: MediaType,
    val value: RatingValue,
    val ratedAt: Long,
)

@Serializable
data class WatchlistEntryBackup(
    val tmdbId: Int,
    val mediaType: MediaType,
    val addedByProfileId: Long,
    val addedAt: Long,
    val state: WatchlistState,
)

@Serializable
data class ProfileSlidersBackup(
    val profileId: Long,
    val discovery: Double,
    val recency: Double,
    val personalMatch: Double,
    val suggestionCount: Int,
    val eligibleCandidateCount: Int,
)

@Serializable
data class NotificationPreferenceBackup(
    val profileId: Long,
    val enabled: Boolean,
)

/**
 * The subset of [org.seg7.familywatchlist.data.repository.UserPreferencesRepository]'s DataStore
 * preferences that are genuinely portable household settings, not device/session state. Excludes
 * `onboardingComplete`, `activeProfileId`, `servicesSetupRequested`,
 * `notificationPermissionRequested`, `regionServicesMismatch` and `accentColor` — all either
 * this-device/this-session flags or cosmetic, none of them "what did we tell the app about our
 * household" in the sense the rest of this payload is.
 */
@Serializable
data class UserPreferencesBackup(
    val region: String,
    val notificationsEnabled: Boolean,
    val refreshDayOfWeek: String,
    val refreshHour: Int,
    val familyBlendSlider: Double,
)
