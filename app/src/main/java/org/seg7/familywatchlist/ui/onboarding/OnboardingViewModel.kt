package org.seg7.familywatchlist.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository

/** PLAN.md §5 screen 1: attribution -> subscribed services -> first profile, one-time. */
enum class OnboardingStep { ATTRIBUTION, SERVICES, PROFILE }

sealed interface ServicesLoadState {
    data object Loading : ServicesLoadState
    data object Loaded : ServicesLoadState
    data class Error(val message: String) : ServicesLoadState
}

sealed interface OnboardingCompletionState {
    data object Idle : OnboardingCompletionState
    data object Done : OnboardingCompletionState
    data class Error(val message: String) : OnboardingCompletionState
}

class OnboardingViewModel(
    private val providerRepository: ProviderRepository,
    private val profileRepository: ProfileRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _step = MutableStateFlow(OnboardingStep.ATTRIBUTION)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    val providers: StateFlow<List<ProviderEntity>> = providerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _servicesLoadState = MutableStateFlow<ServicesLoadState>(ServicesLoadState.Loading)
    val servicesLoadState: StateFlow<ServicesLoadState> = _servicesLoadState.asStateFlow()

    private val _completionState = MutableStateFlow<OnboardingCompletionState>(OnboardingCompletionState.Idle)
    val completionState: StateFlow<OnboardingCompletionState> = _completionState.asStateFlow()

    fun onAttributionAcknowledged() {
        _step.value = OnboardingStep.SERVICES
        loadServices()
    }

    fun retryLoadServices() = loadServices()

    /** Lets the user proceed without services loading (e.g. the emulator has no network yet) — they can set these up later in Settings. */
    fun skipServicesLoad() {
        _step.value = OnboardingStep.PROFILE
    }

    private fun loadServices() {
        viewModelScope.launch {
            _servicesLoadState.value = ServicesLoadState.Loading
            runCatching {
                providerRepository.seedIfEmpty()
                providerRepository.applyOnboardingDefaults()
            }.onSuccess {
                _servicesLoadState.value = ServicesLoadState.Loaded
            }.onFailure {
                _servicesLoadState.value =
                    ServicesLoadState.Error(it.message ?: "Couldn't load streaming services")
            }
        }
    }

    fun toggleProvider(providerId: Int, subscribed: Boolean) {
        viewModelScope.launch { providerRepository.setSubscribed(providerId, subscribed) }
    }

    fun onServicesConfirmed() {
        _step.value = OnboardingStep.PROFILE
    }

    fun onBack() {
        _step.value = when (_step.value) {
            OnboardingStep.ATTRIBUTION -> OnboardingStep.ATTRIBUTION
            OnboardingStep.SERVICES -> OnboardingStep.ATTRIBUTION
            OnboardingStep.PROFILE -> OnboardingStep.SERVICES
        }
    }

    fun completeOnboarding(name: String, avatarKey: String, ageRatingCap: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _completionState.value = OnboardingCompletionState.Error("Give this profile a name")
            return
        }
        viewModelScope.launch {
            profileRepository.addProfile(trimmed, avatarKey, ageRatingCap)
                .onSuccess { id ->
                    userPreferencesRepository.setActiveProfileId(id)
                    userPreferencesRepository.setOnboardingComplete(true)
                    _completionState.value = OnboardingCompletionState.Done
                }
                .onFailure {
                    _completionState.value = OnboardingCompletionState.Error(it.message ?: "Something went wrong")
                }
        }
    }
}
