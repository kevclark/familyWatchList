package org.seg7.familywatchlist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.ProfileEntity

/**
 * [resolveStartState] is the pure decision function behind [AppViewModel.uiState]: which of
 * Onboarding / ProfilePicker / Home to show. Covered directly (no ViewModel/Robolectric needed)
 * since it's a plain function of its three inputs.
 */
class AppViewModelTest {

    private fun profile(id: Long) = ProfileEntity(id = id, name = "P$id", avatarKey = "x|000000", ageRatingCap = null, createdAt = 0)

    @Test
    fun `onboarding incomplete always wins, regardless of active profile`() {
        val state = resolveStartState(
            onboardingComplete = false,
            activeProfileId = 7L,
            profiles = listOf(profile(7)),
        )

        assertEquals(AppStartState.Onboarding, state)
    }

    @Test
    fun `onboarding complete but no active profile id set goes to the picker`() {
        val state = resolveStartState(
            onboardingComplete = true,
            activeProfileId = null,
            profiles = listOf(profile(1)),
        )

        assertEquals(AppStartState.ProfilePicker, state)
    }

    @Test
    fun `active profile id pointing at a deleted profile also goes to the picker`() {
        val state = resolveStartState(
            onboardingComplete = true,
            activeProfileId = 99L,
            profiles = listOf(profile(1), profile(2)),
        )

        assertEquals(AppStartState.ProfilePicker, state)
    }

    @Test
    fun `onboarding complete with a resolvable active profile goes Home`() {
        val target = profile(2)
        val state = resolveStartState(
            onboardingComplete = true,
            activeProfileId = 2L,
            profiles = listOf(profile(1), target),
        )

        assertTrue(state is AppStartState.Home)
        assertEquals(target, (state as AppStartState.Home).activeProfile)
    }
}
