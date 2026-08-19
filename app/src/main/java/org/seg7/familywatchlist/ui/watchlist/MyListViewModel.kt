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
) : ViewModel() {

    private val _mineOnly = MutableStateFlow(false)

    val uiState: StateFlow<MyListUiState> = combine(
        watchlistRepository.observeActiveItemsWithAvailability(),
        profileRepository.observeAll(),
        _mineOnly,
    ) { items, profiles, mineOnly ->
        val profilesById = profiles.associateBy { it.id }
        val rows = items
            .filter { !mineOnly || it.item.addedByProfileId == activeProfileId }
            .map { MyListRow(item = it.item, addedBy = profilesById[it.item.addedByProfileId], isAvailable = it.isAvailable) }
        MyListUiState(rows = rows, mineOnly = mineOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState())

    fun setMineOnly(mineOnly: Boolean) {
        _mineOnly.value = mineOnly
    }

    fun remove(tmdbId: Int, mediaType: MediaType) {
        viewModelScope.launch { watchlistRepository.remove(tmdbId, mediaType) }
    }
}
