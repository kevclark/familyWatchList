package org.seg7.familywatchlist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.FamilyProfileWithMembers
import org.seg7.familywatchlist.ui.onboarding.OnboardingMode

/**
 * [resolveStartState] is the pure decision function behind [AppViewModel.uiState]: which of
 * Onboarding / ProfilePicker / Home to show. Covered directly (no ViewModel/Robolectric needed)
 * since it's a plain function of its five inputs.
 *
 * M2b added the `servicesSetupRequested` input, which is what fixes PLAN.md §5a's "re-entering
 * onboarding from Settings has no way back" defect — the last three individual-profile tests here
 * pin the distinction between a first run and a re-configuration, because getting them confused is
 * precisely the bug. M3d added the `familyProfile` input and the sentinel branch — see PLAN.md §4
 * "The Family profile".
 */
class AppViewModelTest {

    private fun profile(id: Long) =
        ProfileEntity(id = id, name = "P$id", avatarKey = "INITIAL|7C5C4A|", ageRatingCap = null, createdAt = 0)

    private fun familyProfile(members: List<ProfileEntity>) =
        FamilyProfileWithMembers(
            FamilyProfileEntity(name = "Family", avatarKey = "INITIAL|7C5C4A|", createdAt = 0),
            members,
        )

    @Test
    fun `onboarding incomplete always wins, regardless of active profile`() {
        val state = resolveStartState(
            onboardingComplete = false,
            servicesSetupRequested = false,
            activeProfileId = 7L,
            profiles = listOf(profile(7)),
            familyProfile = null,
        )

        assertEquals(AppStartState.Onboarding(OnboardingMode.FIRST_RUN), state)
    }

    @Test
    fun `onboarding complete but no active profile id set goes to the picker`() {
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = false,
            activeProfileId = null,
            profiles = listOf(profile(1)),
            familyProfile = null,
        )

        assertEquals(AppStartState.ProfilePicker, state)
    }

    @Test
    fun `active profile id pointing at a deleted profile also goes to the picker`() {
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = false,
            activeProfileId = 99L,
            profiles = listOf(profile(1), profile(2)),
            familyProfile = null,
        )

        assertEquals(AppStartState.ProfilePicker, state)
    }

    @Test
    fun `onboarding complete with a resolvable active profile goes Home`() {
        val target = profile(2)
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = false,
            activeProfileId = 2L,
            profiles = listOf(profile(1), target),
            familyProfile = null,
        )

        assertTrue(state is AppStartState.Home)
        assertEquals(ActiveProfile.Individual(target), (state as AppStartState.Home).activeProfile)
    }

    @Test
    fun `requesting services setup from Settings re-enters onboarding in RECONFIGURE mode`() {
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = true,
            activeProfileId = 1L,
            profiles = listOf(profile(1)),
            familyProfile = null,
        )

        // RECONFIGURE is what makes the services step the entry point and shows the close
        // affordance — the whole point of the §5a fix.
        assertEquals(AppStartState.Onboarding(OnboardingMode.RECONFIGURE), state)
    }

    @Test
    fun `a first run outranks a stale services-setup flag`() {
        val state = resolveStartState(
            onboardingComplete = false,
            servicesSetupRequested = true,
            activeProfileId = null,
            profiles = emptyList(),
            familyProfile = null,
        )

        // A user who has never onboarded must get the full flow — dropping them on the services
        // step with a "close" button would strand them with no profile and no way forward.
        assertEquals(AppStartState.Onboarding(OnboardingMode.FIRST_RUN), state)
    }

    @Test
    fun `clearing the services-setup flag returns the user to where they were`() {
        val target = profile(3)
        val duringSetup = resolveStartState(
            true, servicesSetupRequested = true, activeProfileId = 3L, profiles = listOf(target), familyProfile = null,
        )
        val afterClose = resolveStartState(
            true, servicesSetupRequested = false, activeProfileId = 3L, profiles = listOf(target), familyProfile = null,
        )

        assertEquals(AppStartState.Onboarding(OnboardingMode.RECONFIGURE), duringSetup)
        assertEquals(AppStartState.Home(ActiveProfile.Individual(target)), afterClose)
    }

    @Test
    fun `sentinel active id with a valid 2+ member family profile goes Home as Family`() {
        val kev = profile(1)
        val sam = profile(2)
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = false,
            activeProfileId = FAMILY_PROFILE_SENTINEL_ID,
            profiles = listOf(kev, sam),
            familyProfile = familyProfile(listOf(kev, sam)),
        )

        assertTrue(state is AppStartState.Home)
        val activeProfile = (state as AppStartState.Home).activeProfile
        assertTrue(activeProfile is ActiveProfile.Family)
        assertTrue(activeProfile.isFamily)
        assertEquals(FAMILY_PROFILE_SENTINEL_ID, activeProfile.id)
        assertEquals(listOf(1L, 2L), (activeProfile as ActiveProfile.Family).memberProfileIds)
    }

    @Test
    fun `sentinel active id with no family profile created yet goes to the picker`() {
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = false,
            activeProfileId = FAMILY_PROFILE_SENTINEL_ID,
            profiles = listOf(profile(1)),
            familyProfile = null,
        )

        assertEquals(AppStartState.ProfilePicker, state)
    }

    @Test
    fun `sentinel active id with a family profile that dropped below 2 members goes to the picker`() {
        val kev = profile(1)
        val state = resolveStartState(
            onboardingComplete = true,
            servicesSetupRequested = false,
            activeProfileId = FAMILY_PROFILE_SENTINEL_ID,
            profiles = listOf(kev),
            // Their co-member was deleted, cascading them out of the membership — PLAN.md §4's
            // under-2-members edge case.
            familyProfile = familyProfile(listOf(kev)),
        )

        assertEquals(AppStartState.ProfilePicker, state)
    }
}
