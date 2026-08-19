package org.seg7.familywatchlist.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository

/** PLAN.md §5 screen 1: attribution -> subscribed services -> first profile, one-time. */
enum class OnboardingStep { ATTRIBUTION, SERVICES, PROFILE }

/**
 * PLAN.md §5a known defect: "Re-entering onboarding from Settings has no way back."
 *
 * [FIRST_RUN] is the original three-step flow. [RECONFIGURE] is what Settings → "Services &
 * attribution setup" now enters: it starts on the services step (there's no point replaying
 * attribution and profile creation to change which services you have), and it shows a close
 * affordance so the user can back out having changed nothing.
 */
enum class OnboardingMode { FIRST_RUN, RECONFIGURE }

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
    private val mode: OnboardingMode,
) : ViewModel() {

    private val _step = MutableStateFlow(
        if (mode == OnboardingMode.RECONFIGURE) OnboardingStep.SERVICES else OnboardingStep.ATTRIBUTION,
    )
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    /** Substring filter over provider names — PLAN.md §5a: "substring match … is enough". */
    private val _serviceQuery = MutableStateFlow("")
    val serviceQuery: StateFlow<String> = _serviceQuery.asStateFlow()

    private val allProviders: StateFlow<List<ProviderEntity>> = providerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The list the picker renders. Subscribed services are pinned to the top *within* the
     * filtered set — with 100+ GB providers, a user who ticks Netflix and scrolls on would
     * otherwise lose sight of what they've already chosen.
     */
    val providers: StateFlow<List<ProviderEntity>> =
        combine(allProviders, _serviceQuery) { providers, query ->
            providers
                .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<ProviderEntity> { it.subscribed }.thenBy { it.displayPriority })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Count of subscribed services regardless of the current filter — the picker's running total. */
    val subscribedCount: StateFlow<Int> = allProviders
        .map { providers -> providers.count { it.subscribed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _servicesLoadState = MutableStateFlow<ServicesLoadState>(ServicesLoadState.Loading)
    val servicesLoadState: StateFlow<ServicesLoadState> = _servicesLoadState.asStateFlow()

    private val _completionState = MutableStateFlow<OnboardingCompletionState>(OnboardingCompletionState.Idle)
    val completionState: StateFlow<OnboardingCompletionState> = _completionState.asStateFlow()

    /** Whether the close/back-out affordance should be shown (re-configuration only). */
    val canDismiss: Boolean get() = mode == OnboardingMode.RECONFIGURE

    init {
        // Re-configuration lands straight on the services step, so its data has to load
        // immediately rather than waiting for the attribution step's Continue.
        if (mode == OnboardingMode.RECONFIGURE) loadServices()
    }

    fun onAttributionAcknowledged() {
        _step.value = OnboardingStep.SERVICES
        loadServices()
    }

    fun retryLoadServices() = loadServices()

    fun onServiceQueryChange(query: String) {
        _serviceQuery.value = query
    }

    /** Lets the user proceed without services loading (e.g. the emulator has no network yet) — they can set these up later in Settings. */
    fun skipServicesLoad() {
        if (mode == OnboardingMode.RECONFIGURE) dismiss() else _step.value = OnboardingStep.PROFILE
    }

    private fun loadServices() {
        viewModelScope.launch {
            _servicesLoadState.value = ServicesLoadState.Loading
            runCatching {
                // PLAN.md §7 M2f: seedIfEmpty only ever acts before any provider exists at all
                // (see its kdoc), so this is only ever the current region in practice — but
                // threading the live value through rather than relying on the GB default keeps
                // this call site honest about where region comes from.
                providerRepository.seedIfEmpty(userPreferencesRepository.region.first())
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

    /**
     * "Continue" on the services step. In first-run that's the next step; in re-configuration
     * there is no next step — the services *were* the reason for being here, and the toggles
     * have already been written straight through to the database, so this just leaves.
     */
    fun onServicesConfirmed() {
        if (mode == OnboardingMode.RECONFIGURE) dismiss() else _step.value = OnboardingStep.PROFILE
    }

    /**
     * The way out (PLAN.md §5a known defect). Clearing the flag is enough on its own —
     * [org.seg7.familywatchlist.ui.AppViewModel] recomputes the top-level screen off it, so the
     * user reappears exactly where they were with `onboardingComplete` never having been
     * disturbed.
     */
    fun dismiss() {
        viewModelScope.launch {
            userPreferencesRepository.setServicesSetupRequested(false)
            // PLAN.md §7 M2f: revisiting the services step (even just to back out of it) is the
            // documented "easy path to fix it" for a region-change mismatch — clear the notice.
            userPreferencesRepository.clearRegionServicesMismatch()
        }
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
