package org.seg7.familywatchlist.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PLAN.md §5 screens 1-2: onboarding-complete flag + active-profile selection, backed by
 * DataStore (PLAN.md §1). Each test gets its own file name so tests don't share state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesRepositoryTest {

    private fun newRepo(name: String): UserPreferencesRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(name) },
        )
        return UserPreferencesRepository(dataStore)
    }

    @Test
    fun `onboarding starts incomplete and no active profile`() = runTest {
        val repo = newRepo("prefs_defaults")

        assertEquals(false, repo.onboardingComplete.first())
        assertNull(repo.activeProfileId.first())
    }

    @Test
    fun `setOnboardingComplete persists`() = runTest {
        val repo = newRepo("prefs_onboarding")

        repo.setOnboardingComplete(true)

        assertEquals(true, repo.onboardingComplete.first())
    }

    @Test
    fun `setActiveProfileId then clearActiveProfileId round-trips`() = runTest {
        val repo = newRepo("prefs_active_profile")

        repo.setActiveProfileId(42L)
        assertEquals(42L, repo.activeProfileId.first())

        repo.clearActiveProfileId()
        assertNull(repo.activeProfileId.first())
    }
}
