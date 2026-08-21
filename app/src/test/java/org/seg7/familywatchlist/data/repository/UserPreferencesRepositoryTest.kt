package org.seg7.familywatchlist.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun newRepo(name: String): UserPreferencesRepository = newRepoAndStore(name).first

    /** Also returns the raw [androidx.datastore.core.DataStore] for tests that need to write a corrupt/unrecognised value directly, bypassing the repository's own setters. */
    private fun newRepoAndStore(name: String): Pair<UserPreferencesRepository, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(name) },
        )
        return UserPreferencesRepository(dataStore) to dataStore
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

    @Test
    fun `accent colour defaults to OBSIDIAN`() = runTest {
        val repo = newRepo("prefs_accent_defaults")

        assertEquals(AccentColor.OBSIDIAN, repo.accentColor.first())
    }

    @Test
    fun `setAccentColor round-trips for every candidate`() = runTest {
        val repo = newRepo("prefs_accent_roundtrip")

        for (candidate in AccentColor.entries) {
            repo.setAccentColor(candidate)
            assertEquals(candidate, repo.accentColor.first())
        }
    }

    @Test
    fun `region defaults to GB`() = runTest {
        val repo = newRepo("prefs_region_defaults")

        assertEquals("GB", repo.region.first())
    }

    @Test
    fun `setRegion round-trips`() = runTest {
        val repo = newRepo("prefs_region_roundtrip")

        for (code in listOf("US", "FR", "GB", "AU")) {
            repo.setRegion(code)
            assertEquals(code, repo.region.first())
        }
    }

    @Test
    fun `region falls back to the default on a corrupted stored value`() = runTest {
        val (repo, dataStore) = newRepoAndStore("prefs_region_corrupt")
        dataStore.updateData { it.toMutablePreferences().apply { this[stringPreferencesKey("region")] = "not-a-code" } }

        assertEquals("GB", repo.region.first())
    }

    @Test
    fun `regionServicesMismatch starts false`() = runTest {
        val repo = newRepo("prefs_region_mismatch_default")

        assertFalse(repo.regionServicesMismatch.first())
    }

    @Test
    fun `setRegion to a genuinely different value flags regionServicesMismatch`() = runTest {
        val repo = newRepo("prefs_region_mismatch_flip")

        repo.setRegion("US")

        assertTrue(repo.regionServicesMismatch.first())
    }

    @Test
    fun `setRegion to the same value already in effect does not flag a mismatch`() = runTest {
        val repo = newRepo("prefs_region_mismatch_noop")

        repo.setRegion("GB") // same as the implicit default already in effect

        assertFalse(repo.regionServicesMismatch.first())
    }

    @Test
    fun `clearRegionServicesMismatch resets the flag`() = runTest {
        val repo = newRepo("prefs_region_mismatch_clear")
        repo.setRegion("US")
        assertTrue(repo.regionServicesMismatch.first())

        repo.clearRegionServicesMismatch()

        assertFalse(repo.regionServicesMismatch.first())
    }

    @Test
    fun `familyBlendSlider defaults to 0`() = runTest {
        val repo = newRepo("prefs_family_blend_defaults")

        assertEquals(0.0, repo.familyBlendSlider.first(), 1e-9)
    }

    @Test
    fun `setFamilyBlendSlider round-trips across the full range`() = runTest {
        val repo = newRepo("prefs_family_blend_roundtrip")

        for (value in listOf(-1.0, -0.5, 0.0, 0.5, 1.0)) {
            repo.setFamilyBlendSlider(value)
            assertEquals(value, repo.familyBlendSlider.first(), 1e-9)
        }
    }

    @Test
    fun `setFamilyBlendSlider rejects an out-of-range value`() = runTest {
        val repo = newRepo("prefs_family_blend_reject")

        try {
            repo.setFamilyBlendSlider(1.5)
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `familyBlendSlider falls back to 0 on a corrupted stored value`() = runTest {
        val (repo, dataStore) = newRepoAndStore("prefs_family_blend_corrupt")
        dataStore.updateData { it.toMutablePreferences().apply { this[doublePreferencesKey("family_blend_slider")] = 4.0 } }

        assertEquals(0.0, repo.familyBlendSlider.first(), 1e-9)
    }

    /**
     * PLAN.md §4 "Per-profile notification control" (M3e): default **on** — before this pass
     * there was no in-app master switch at all, so defaulting off would silently change existing
     * behaviour for everyone rather than being a genuine opt-out.
     */
    @Test
    fun `notificationsEnabled defaults to true`() = runTest {
        val repo = newRepo("prefs_notifications_defaults")

        assertTrue(repo.notificationsEnabled.first())
    }

    @Test
    fun `setNotificationsEnabled round-trips both ways`() = runTest {
        val repo = newRepo("prefs_notifications_roundtrip")

        repo.setNotificationsEnabled(false)
        assertFalse(repo.notificationsEnabled.first())

        repo.setNotificationsEnabled(true)
        assertTrue(repo.notificationsEnabled.first())
    }

    /** PLAN.md §4 "Configurable schedule" (M3f): Friday 06:00, replacing the old Monday 06:00 hardcoded literal. */
    @Test
    fun `refresh schedule defaults to Friday 06 00`() = runTest {
        val repo = newRepo("prefs_refresh_schedule_defaults")

        assertEquals(DayOfWeek.FRIDAY, repo.refreshDayOfWeek.first())
        assertEquals(6, repo.refreshHour.first())
    }

    @Test
    fun `setRefreshSchedule round-trips a non-default day and hour`() = runTest {
        val repo = newRepo("prefs_refresh_schedule_roundtrip")

        repo.setRefreshSchedule(DayOfWeek.TUESDAY, 9)

        assertEquals(DayOfWeek.TUESDAY, repo.refreshDayOfWeek.first())
        assertEquals(9, repo.refreshHour.first())
    }

    @Test
    fun `setRefreshSchedule round-trips for every day of the week`() = runTest {
        val repo = newRepo("prefs_refresh_schedule_every_day")

        for (day in DayOfWeek.entries) {
            repo.setRefreshSchedule(day, 6)
            assertEquals(day, repo.refreshDayOfWeek.first())
        }
    }

    @Test
    fun `setRefreshSchedule rejects an out-of-range hour`() = runTest {
        val repo = newRepo("prefs_refresh_schedule_reject")

        try {
            repo.setRefreshSchedule(DayOfWeek.FRIDAY, 24)
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `refreshDayOfWeek falls back to the default on a corrupted stored value`() = runTest {
        val (repo, dataStore) = newRepoAndStore("prefs_refresh_day_corrupt")
        dataStore.updateData { it.toMutablePreferences().apply { this[stringPreferencesKey("refresh_day_of_week")] = "not-a-day" } }

        assertEquals(DayOfWeek.FRIDAY, repo.refreshDayOfWeek.first())
    }

    @Test
    fun `refreshHour falls back to the default on an out-of-range stored value`() = runTest {
        val (repo, dataStore) = newRepoAndStore("prefs_refresh_hour_corrupt")
        dataStore.updateData { it.toMutablePreferences().apply { this[intPreferencesKey("refresh_hour")] = 99 } }

        assertEquals(6, repo.refreshHour.first())
    }
}
