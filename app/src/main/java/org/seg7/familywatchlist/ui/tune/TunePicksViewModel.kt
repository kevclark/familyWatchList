package org.seg7.familywatchlist.ui.tune

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.recommend.SliderSettings
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository

/**
 * PLAN.md §4a: "Tune my picks" — the four sliders. Changing any of them recomputes that
 * profile's shortlist immediately, debounced ~300-500ms after the user stops dragging, mirroring
 * [org.seg7.familywatchlist.ui.search.SearchViewModel]'s existing debounce pattern (don't
 * recompute on every drag frame).
 *
 * The family-blend slider (PLAN.md §4a's slider 4) is intentionally separate state here, not
 * part of [SliderSettings] — it's a shared app-level preference, not this profile's own setting
 * (see [UserPreferencesRepository.familyBlendSlider]'s kdoc), and is only ever shown/editable
 * once [familyBlendVisible] is true (2+ profiles on the account — PLAN.md §4a's UI-home
 * decision). Changing it doesn't recompute *this* profile's own shortlist (it has no effect on
 * a single-profile view); the family scope picks it up the next time it's computed.
 */
@OptIn(FlowPreview::class)
class TunePicksViewModel(
    private val profileId: Long,
    private val profileSlidersRepository: ProfileSlidersRepository,
    private val recommendationRepository: RecommendationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _sliders = MutableStateFlow(SliderSettings.DEFAULT)
    val sliders: StateFlow<SliderSettings> = _sliders.asStateFlow()

    private val _familyBlend = MutableStateFlow(0.0)
    val familyBlend: StateFlow<Double> = _familyBlend.asStateFlow()

    val familyBlendVisible: StateFlow<Boolean> = recommendationRepository.observeFamilyBlendSliderVisible()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isRecomputing = MutableStateFlow(false)
    val isRecomputing: StateFlow<Boolean> = _isRecomputing.asStateFlow()

    private val recomputeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val familyBlendTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            _sliders.value = profileSlidersRepository.get(profileId)
            _familyBlend.value = userPreferencesRepository.familyBlendSlider.first()
        }
        viewModelScope.launch {
            recomputeTrigger.debounce(RECOMPUTE_DEBOUNCE_MS).collect {
                _isRecomputing.value = true
                profileSlidersRepository.set(profileId, _sliders.value)
                val region = userPreferencesRepository.region.first()
                recommendationRepository.refreshProfileShortlist(profileId, region)
                _isRecomputing.value = false
            }
        }
        viewModelScope.launch {
            familyBlendTrigger.debounce(RECOMPUTE_DEBOUNCE_MS).collect {
                userPreferencesRepository.setFamilyBlendSlider(_familyBlend.value)
            }
        }
    }

    fun onDiscoveryChange(value: Float) = updateSliders { it.copy(discovery = value.toRange()) }
    fun onRecencyChange(value: Float) = updateSliders { it.copy(recency = value.toRange()) }
    fun onPersonalMatchChange(value: Float) = updateSliders { it.copy(personalMatch = value.toRange()) }

    fun onFamilyBlendChange(value: Float) {
        _familyBlend.value = value.toRange()
        familyBlendTrigger.tryEmit(Unit)
    }

    fun resetToDefaults() {
        _sliders.value = SliderSettings.DEFAULT
        recomputeTrigger.tryEmit(Unit)
    }

    private inline fun updateSliders(transform: (SliderSettings) -> SliderSettings) {
        _sliders.value = transform(_sliders.value)
        recomputeTrigger.tryEmit(Unit)
    }

    private fun Float.toRange(): Double = toDouble().coerceIn(-1.0, 1.0)

    companion object {
        /** Same order of magnitude as [org.seg7.familywatchlist.ui.search.SearchViewModel.SEARCH_DEBOUNCE_MS] — PLAN.md §4a: "~300-500ms". */
        const val RECOMPUTE_DEBOUNCE_MS = 400L
    }
}
