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

    /**
     * Set when the user opens Settings → "Services & attribution setup" (PLAN.md §5a known
     * defect: re-entered onboarding had no way back).
     *
     * This is a *separate* flag rather than reusing [onboardingComplete], which is what M2a
     * did — flipping onboardingComplete back to false made re-configuration indistinguishable
     * from a first run, so the flow replayed attribution and profile creation and offered no
     * exit. Keeping the two apart means the app can tell "this user has never set up" from
     * "this user wants to change their services", show the right entry step, and offer a close
     * button that just clears this flag and drops them back where they were.
     */
    val servicesSetupRequested: Flow<Boolean> =
        dataStore.data.map { it[SERVICES_SETUP_REQUESTED] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setServicesSetupRequested(requested: Boolean) {
        dataStore.edit { it[SERVICES_SETUP_REQUESTED] = requested }
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
        val SERVICES_SETUP_REQUESTED: Preferences.Key<Boolean> =
            booleanPreferencesKey("services_setup_requested")
    }
}
