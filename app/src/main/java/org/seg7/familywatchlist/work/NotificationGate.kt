package org.seg7.familywatchlist.work

import org.seg7.familywatchlist.data.repository.ProfileRefreshResult

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): the pure "who does this week's
 * notification mention" decision, deliberately kept free of Room/DataStore/Android so it's
 * unit-testable without Robolectric or a real device.
 *
 * Three independent gates, all of which combine here except the OS permission check (that one
 * stays inside [ShortlistNotifier] itself — see its kdoc — since it's Android-API-only and
 * already existed pre-M3e; this function only adds the *new* two):
 *  1. [masterEnabled] — [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.notificationsEnabled]
 *  2. each [completed] entry's own per-profile toggle (queried via [perProfileEnabled])
 *  3. (checked separately, inside [ShortlistNotifier.notifyShortlistReady]) the OS
 *     `POST_NOTIFICATIONS` permission
 *
 * [completed] is already scoped to profiles that genuinely finished refreshing this run (see
 * [org.seg7.familywatchlist.data.repository.RecommendationRepository.refreshAll]'s kdoc) — a
 * profile that failed to refresh is never passed in here at all, so it can never end up notified
 * regardless of its own toggle.
 */
object NotificationGate {
    fun profilesToNotify(
        completed: List<ProfileRefreshResult>,
        masterEnabled: Boolean,
        perProfileEnabled: (profileId: Long) -> Boolean,
    ): List<ProfileRefreshResult> {
        if (!masterEnabled) return emptyList()
        return completed.filter { perProfileEnabled(it.profileId) }
    }

    /**
     * PLAN.md §5d (M15): the one-line explanation stored in the refresh log -- posted, or
     * suppressed and why (master off, profile(s) off, OS permission/blocked). Pure so the exact
     * wording is unit-tested. [posted] is the notifier's result, or null when the notifier was
     * never reached (master off, or every profile off).
     */
    fun describe(
        completed: List<ProfileRefreshResult>,
        masterEnabled: Boolean,
        toNotify: List<ProfileRefreshResult>,
        posted: ShortlistNotifier.Result?,
    ): String {
        if (!masterEnabled) return "Not sent: notifications are turned off in Settings"
        if (completed.isEmpty()) return "Not sent: no profile finished refreshing"
        val off = completed.filterNot { c -> toNotify.any { it.profileId == c.profileId } }.map { it.name }
        if (toNotify.isEmpty()) return "Not sent: notifications are off for ${off.joinToString(", ")}"
        return when (posted) {
            ShortlistNotifier.Result.POSTED -> {
                val base = "Posted for ${toNotify.joinToString(", ") { it.name }}"
                if (off.isEmpty()) base else "$base (off for ${off.joinToString(", ")})"
            }
            ShortlistNotifier.Result.PERMISSION_DENIED -> "Not sent: Android notification permission was denied"
            ShortlistNotifier.Result.BLOCKED_BY_SYSTEM -> "Not sent: notifications are blocked for this app in Android settings"
            ShortlistNotifier.Result.NOTHING_TO_POST, null -> "Not sent: nothing to post"
        }
    }
}
