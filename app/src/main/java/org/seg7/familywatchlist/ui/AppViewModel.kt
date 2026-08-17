package org.seg7.familywatchlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.ui.onboarding.OnboardingMode

/**
 * Resolves which top-level screen the app should show, reactively, from three DataStore flags
 * (PLAN.md §5 screens 1-2) plus the live profile list:
 *  - onboarding not complete -> Onboarding, first-run flow
 *  - services setup requested from Settings -> Onboarding, re-configuration flow (services step
 *    only, with a way out) — PLAN.md §5a known defect #2
 *  - onboarding complete but no *resolvable* active profile (never set, or the profile it
 *    pointed at was since deleted) -> ProfilePicker
 *  - otherwise -> Home, with the resolved profile
 *
 * Driving navigation off this combined state (rather than explicit nav-graph callbacks) means
 * completing onboarding, asking for services setup, or deleting the active profile "just work"
 * — the relevant repository write causes this flow to re-emit and the UI follows.
 */
class AppViewModel(
    userPreferencesRepository: UserPreferencesRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<AppStartState> = combine(
        userPreferencesRepository.onboardingComplete,
        userPreferencesRepository.servicesSetupRequested,
        userPreferencesRepository.activeProfileId,
        profileRepository.observeAll(),
    ) { onboardingComplete, servicesSetupRequested, activeProfileId, profiles ->
        resolveStartState(onboardingComplete, servicesSetupRequested, activeProfileId, profiles)
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
    data class Home(val activeProfile: ProfileEntity) : AppStartState
}

/** Pure function so the resolution rules are unit-testable without spinning up a ViewModel. */
fun resolveStartState(
    onboardingComplete: Boolean,
    servicesSetupRequested: Boolean,
    activeProfileId: Long?,
    profiles: List<ProfileEntity>,
): AppStartState {
    // A genuine first run outranks a stale re-configuration flag: someone who has never
    // onboarded needs the full flow, and offering them a "close" button would strand them.
    if (!onboardingComplete) return AppStartState.Onboarding(OnboardingMode.FIRST_RUN)
    if (servicesSetupRequested) return AppStartState.Onboarding(OnboardingMode.RECONFIGURE)
    val activeProfile = profiles.find { it.id == activeProfileId } ?: return AppStartState.ProfilePicker
    return AppStartState.Home(activeProfile)
}
