package org.seg7.familywatchlist.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.dao.WatchlistItem
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository

/**
 * A list entry resolved against the profile who added it (PLAN.md §2's `addedByProfileId`), plus
 * whether it currently has GB availability on a subscribed provider — PLAN.md §5a's M2g
 * refinement, so the screen can render an item that's since lost availability dimmed rather than
 * identical to everything else.
 */
data class MyListRow(
    val item: WatchlistItem,
    val addedBy: ProfileEntity?,
    val isAvailable: Boolean,
)

data class MyListUiState(
    val rows: List<MyListRow> = emptyList(),
    /** PLAN.md §2: "filters to the active profile with a 'whole family' toggle". */
    val mineOnly: Boolean = false,
    /**
     * PLAN.md §5b M3i item 9: the *viewing* profile's (or Family's strictest-member) age cap —
     * resolved once via [RecommendationRepository.resolveAgeRatingCap], the same rule Home's
     * avatar badge/Popular-row filtering and Search already use. Null means "no cap" — every row
     * renders normally. The screen (not this state) combines this with each row's
     * [WatchlistItem.certification] via [org.seg7.familywatchlist.data.recommend.FamilyBlend
     * .isOverCap] to decide which rows dim, so there's exactly one place that check happens.
     */
    val ageRatingCap: String? = null,
) {
    val visibleItems: List<MyListRow>
        get() = rows
}

/**
 * PLAN.md §5's Want-to-Watch list. The list itself is shared and unfiltered in the database —
 * the "added by me" toggle is a *view* over it, never a separate list, because §2 is explicit
 * that there is one family list.
 */
class MyListViewModel(
    private val watchlistRepository: WatchlistRepository,
    profileRepository: ProfileRepository,
    private val activeProfileId: Long,
    userPreferencesRepository: UserPreferencesRepository,
    recommendationRepository: RecommendationRepository,
) : ViewModel() {

    private val _mineOnly = MutableStateFlow(false)

    // PLAN.md §5b M3i item 9: resolved once at construction — [activeProfileId] is fixed for the
    // lifetime of one MyListViewModel instance (the screen is keyed on it,
    // `viewModel(key = "mylist-$activeProfileId")` in MyListScreen), so there's no live
    // preference this needs to react to the way region does. `resolveAgeRatingCap` itself already
    // handles the FAMILY_PROFILE_SENTINEL_ID case transparently (strictest cap among curated
    // members) — see its kdoc — so this works unchanged whether [activeProfileId] is a real
    // profile or the Family sentinel.
    private val _ageRatingCap = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MyListUiState> = combine(
        // PLAN.md §7 M2f: live region Flow — see HomeViewModel.myList's kdoc for why.
        watchlistRepository.observeActiveItemsWithAvailability(userPreferencesRepository.region),
        profileRepository.observeAll(),
        _mineOnly,
        _ageRatingCap,
    ) { items, profiles, mineOnly, ageRatingCap ->
        val profilesById = profiles.associateBy { it.id }
        val rows = items
            .filter { !mineOnly || it.item.addedByProfileId == activeProfileId }
            .map { MyListRow(item = it.item, addedBy = profilesById[it.item.addedByProfileId], isAvailable = it.isAvailable) }
        MyListUiState(rows = rows, mineOnly = mineOnly, ageRatingCap = ageRatingCap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState())

    init {
        viewModelScope.launch {
            _ageRatingCap.value = recommendationRepository.resolveAgeRatingCap(activeProfileId)
        }
    }

    fun setMineOnly(mineOnly: Boolean) {
        _mineOnly.value = mineOnly
    }

    fun remove(tmdbId: Int, mediaType: MediaType) {
        viewModelScope.launch { watchlistRepository.remove(tmdbId, mediaType) }
    }
}
