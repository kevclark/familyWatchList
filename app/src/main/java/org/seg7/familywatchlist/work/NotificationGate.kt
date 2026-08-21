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
}
