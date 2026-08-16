package org.seg7.familywatchlist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * PLAN.md §5 screens 1-2: the onboarding-complete flag (one-time, but reachable again from
 * Settings later) and the "active profile" selection, both backed by Jetpack DataStore
 * (PLAN.md §1). [activeProfileId] is nullable — null means "no profile currently selected",
 * which is what routes the app to the profile picker.
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    val activeProfileId: Flow<Long?> =
        dataStore.data.map { it[ACTIVE_PROFILE_ID] }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setActiveProfileId(id: Long) {
        dataStore.edit { it[ACTIVE_PROFILE_ID] = id }
    }

    suspend fun clearActiveProfileId() {
        dataStore.edit { it.remove(ACTIVE_PROFILE_ID) }
    }

    companion object {
        val ONBOARDING_COMPLETE: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_complete")
        val ACTIVE_PROFILE_ID: Preferences.Key<Long> = longPreferencesKey("active_profile_id")
    }
}
