package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.data.local.dao.NotificationPreferenceDao
import org.seg7.familywatchlist.data.local.entity.NotificationPreferenceEntity

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): the per-profile half of the notification
 * gate. The app-level master toggle lives in
 * [UserPreferencesRepository.notificationsEnabled] instead (DataStore, a single fixed key) — this
 * repository is the variable-cardinality half (up to 10 individuals + optionally Family) that
 * needs real per-row storage; see
 * [org.seg7.familywatchlist.data.local.entity.NotificationPreferenceEntity]'s kdoc for why.
 *
 * [profileId] is either a real [org.seg7.familywatchlist.data.local.entity.ProfileEntity.id] or
 * [org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID] — M3d's sentinel,
 * reused unchanged (not a second identifier scheme). A profile with no row reads as enabled
 * (default **on**, preserving pre-M3e behaviour — see the entity kdoc).
 */
class NotificationPreferencesRepository(private val dao: NotificationPreferenceDao) {

    fun observe(profileId: Long): Flow<Boolean> = dao.observe(profileId).map { it?.enabled ?: true }

    suspend fun isEnabled(profileId: Long): Boolean = dao.get(profileId)?.enabled ?: true

    suspend fun setEnabled(profileId: Long, enabled: Boolean) {
        dao.upsert(NotificationPreferenceEntity(profileId = profileId, enabled = enabled))
    }
}
