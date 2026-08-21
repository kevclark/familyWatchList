package org.seg7.familywatchlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.FamilyProfileWithMembers
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.ui.onboarding.OnboardingMode

/**
 * Resolves which top-level screen the app should show, reactively, from three DataStore flags
 * (PLAN.md §5 screens 1-2) plus the live profile list and the live Family profile (PLAN.md §4,
 * M3d):
 *  - onboarding not complete -> Onboarding, first-run flow
 *  - services setup requested from Settings -> Onboarding, re-configuration flow (services step
 *    only, with a way out) — PLAN.md §5a known defect #2
 *  - onboarding complete but no *resolvable* active profile (never set, the profile it pointed at
 *    was since deleted, or it points at [FAMILY_PROFILE_SENTINEL_ID] but there's no valid Family
 *    profile to resolve to any more) -> ProfilePicker
 *  - otherwise -> Home, with the resolved [ActiveProfile]
 *
 * Driving navigation off this combined state (rather than explicit nav-graph callbacks) means
 * completing onboarding, asking for services setup, deleting the active profile, or a Family
 * profile dropping below its member floor all "just work" — the relevant repository write causes
 * this flow to re-emit and the UI follows.
 */
class AppViewModel(
    userPreferencesRepository: UserPreferencesRepository,
    profileRepository: ProfileRepository,
    familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<AppStartState> = combine(
        userPreferencesRepository.onboardingComplete,
        userPreferencesRepository.servicesSetupRequested,
        userPreferencesRepository.activeProfileId,
        profileRepository.observeAll(),
        familyProfileRepository.observe(),
    ) { onboardingComplete, servicesSetupRequested, activeProfileId, profiles, familyProfile ->
        resolveStartState(onboardingComplete, servicesSetupRequested, activeProfileId, profiles, familyProfile)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppStartState.Loading,
    )
}

sealed interface AppStartState {
    data object Loading : AppStartState
    data class Onboarding(val mode: OnboardingMode) : AppStartState
    data object ProfilePicker : AppStartState
    data class Home(val activeProfile: ActiveProfile) : AppStartState
}

/** Pure function so the resolution rules are unit-testable without spinning up a ViewModel. */
fun resolveStartState(
    onboardingComplete: Boolean,
    servicesSetupRequested: Boolean,
    activeProfileId: Long?,
    profiles: List<ProfileEntity>,
    familyProfile: FamilyProfileWithMembers?,
): AppStartState {
    // A genuine first run outranks a stale re-configuration flag: someone who has never
    // onboarded needs the full flow, and offering them a "close" button would strand them.
    if (!onboardingComplete) return AppStartState.Onboarding(OnboardingMode.FIRST_RUN)
    if (servicesSetupRequested) return AppStartState.Onboarding(OnboardingMode.RECONFIGURE)

    if (activeProfileId == FAMILY_PROFILE_SENTINEL_ID) {
        // PLAN.md §4's under-2-members edge case (M3d build-agent judgment call, documented in
        // the report): a Family profile that has dropped below its 2-member floor (its last
        // "extra" member got deleted, cascading their membership row away) is not a valid active
        // selection any more — bounce to the picker rather than showing a broken/misleading Home,
        // same as a deleted individual profile already does below. It stays visible-but-flagged
        // on the picker itself (see ProfilePickerScreen), not hidden outright.
        return if (familyProfile != null && familyProfile.hasEnoughMembers) {
            AppStartState.Home(ActiveProfile.Family(familyProfile.profile, familyProfile.memberIds))
        } else {
            AppStartState.ProfilePicker
        }
    }

    val activeProfile = profiles.find { it.id == activeProfileId } ?: return AppStartState.ProfilePicker
    return AppStartState.Home(ActiveProfile.Individual(activeProfile))
}
