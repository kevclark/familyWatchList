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

/**
 * Resolves which top-level screen the app should show, reactively, from two DataStore flags
 * (PLAN.md §5 screens 1-2) plus the live profile list:
 *  - onboarding not complete -> Onboarding
 *  - onboarding complete but no *resolvable* active profile (never set, or the profile it
 *    pointed at was since deleted) -> ProfilePicker
 *  - otherwise -> Home, with the resolved profile
 *
 * Driving navigation off this combined state (rather than explicit nav-graph callbacks) means
 * completing onboarding or deleting the active profile "just works" — the relevant repository
 * write causes this flow to re-emit and the UI follows.
 */
class AppViewModel(
    userPreferencesRepository: UserPreferencesRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<AppStartState> = combine(
        userPreferencesRepository.onboardingComplete,
        userPreferencesRepository.activeProfileId,
        profileRepository.observeAll(),
    ) { onboardingComplete, activeProfileId, profiles ->
        resolveStartState(onboardingComplete, activeProfileId, profiles)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppStartState.Loading,
    )
}

sealed interface AppStartState {
    data object Loading : AppStartState
    data object Onboarding : AppStartState
    data object ProfilePicker : AppStartState
    data class Home(val activeProfile: ProfileEntity) : AppStartState
}

/** Pure function so the resolution rules are unit-testable without spinning up a ViewModel. */
fun resolveStartState(
    onboardingComplete: Boolean,
    activeProfileId: Long?,
    profiles: List<ProfileEntity>,
): AppStartState {
    if (!onboardingComplete) return AppStartState.Onboarding
    val activeProfile = profiles.find { it.id == activeProfileId } ?: return AppStartState.ProfilePicker
    return AppStartState.Home(activeProfile)
}
