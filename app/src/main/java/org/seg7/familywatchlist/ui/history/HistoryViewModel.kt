package org.seg7.familywatchlist.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.dao.WatchEventItem
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository

/** One history row: the event, its title, and who was tagged on it. */
data class HistoryRow(
    val event: WatchEventItem,
    val watchedBy: List<ProfileEntity>,
)

data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
    val profiles: List<ProfileEntity> = emptyList(),
    /** null = "Everyone" (PLAN.md §5 screen 7's "filter by profile"). */
    val filterProfileId: Long? = null,
    val isEmpty: Boolean = false,
)

/**
 * PLAN.md §5 screen 7: reverse-chronological watch history, filterable by profile, with
 * edit/delete on each event.
 *
 * Ordering comes from the DAO (`ORDER BY watchedAt DESC, id DESC`) rather than being re-sorted
 * here — the id tiebreak matters, because several titles logged on the same date must still come
 * back newest-logged-first rather than in arbitrary order.
 *
 * Filtering is applied in memory over the tag map. The rows already need that map to render
 * "who watched" chips, so filtering it costs nothing extra and — unlike a parameterised query —
 * doesn't re-hit the database every time the chip row changes.
 */
class HistoryViewModel(
    private val watchEventRepository: WatchEventRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val _filterProfileId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        watchEventRepository.observeAllItems(),
        watchEventRepository.observeTagsByEvent(),
        profileRepository.observeAll(),
        _filterProfileId,
    ) { events, tagsByEvent, profiles, filterProfileId ->
        val profilesById = profiles.associateBy { it.id }
        val rows = events
            .filter { event ->
                filterProfileId == null || filterProfileId in tagsByEvent[event.id].orEmpty()
            }
            .map { event ->
                HistoryRow(
                    event = event,
                    watchedBy = tagsByEvent[event.id].orEmpty().mapNotNull { profilesById[it] },
                )
            }
        HistoryUiState(
            rows = rows,
            profiles = profiles,
            filterProfileId = filterProfileId,
            // "Nothing logged yet" and "nothing matches this filter" are different messages,
            // so emptiness is reported against the *unfiltered* list.
            isEmpty = events.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(profileId: Long?) {
        _filterProfileId.value = profileId
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { watchEventRepository.deleteWatch(eventId) }
    }
}
