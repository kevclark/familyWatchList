package org.seg7.familywatchlist.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.dao.AvailabilityBadge
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistAddResult
import org.seg7.familywatchlist.data.repository.WatchlistRepository

data class TitleDetailUiState(
    val title: TitleEntity? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val crew: List<String> = emptyList(),
    val availability: List<AvailabilityBadge> = emptyList(),
    val isListed: Boolean = false,
    /** The *active* profile's thumbs on this title; null means no opinion recorded. */
    val myRating: RatingValue? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

/** PLAN.md §5a: one-shot UI event — a blocked ＋ My List tap, surfaced as a Snackbar rather than a silent no-op. */
sealed interface TitleDetailUiEvent {
    data class WatchlistBlocked(val message: String) : TitleDetailUiEvent
}

/**
 * PLAN.md §5 screen 4. Offline-first exactly as §3 requires: everything rendered comes from
 * Room Flows, and the network is only asked to *fill* those Flows.
 *
 * [TitleRepository.ensureFresh] is fired once on entry — it's a no-op when the cached row is
 * inside its TTL, and otherwise refetches in the background while the stale (or stub, if the
 * user arrived from search) row stays on screen. That's what makes tapping a search result feel
 * instant: the poster and name are already cached, and runtime/cert/cast/availability populate
 * a beat later rather than gating the whole screen behind a spinner.
 */
class TitleDetailViewModel(
    private val titleRepository: TitleRepository,
    private val watchlistRepository: WatchlistRepository,
    private val ratingRepository: RatingRepository,
    private val tmdbId: Int,
    private val mediaType: MediaType,
    private val activeProfileId: Long,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _events = MutableSharedFlow<TitleDetailUiEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<TitleDetailUiState> = combine(
        titleRepository.observeTitle(tmdbId, mediaType),
        titleRepository.observeAttributes(tmdbId, mediaType),
        titleRepository.observeAvailability(tmdbId, mediaType),
        watchlistRepository.observeIsListed(tmdbId, mediaType),
        combine(
            ratingRepository.observeForTitle(tmdbId, mediaType),
            _refreshing,
            _error,
        ) { ratings, refreshing, error -> Triple(ratings, refreshing, error) },
    ) { title, attributes, availability, isListed, (ratings, refreshing, error) ->
        TitleDetailUiState(
            title = title,
            genres = attributes.named(AttrType.GENRE),
            cast = attributes.filter { it.attrType == AttrType.CAST }.sortedBy { it.ord ?: Int.MAX_VALUE }.map { it.name },
            crew = attributes.named(AttrType.CREW),
            availability = availability,
            isListed = isListed,
            myRating = ratings[activeProfileId],
            isRefreshing = refreshing,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TitleDetailUiState(isRefreshing = true))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            runCatching {
                val region = userPreferencesRepository.region.first()
                titleRepository.ensureFresh(tmdbId, mediaType, region)
            }.onFailure { _error.value = it.message ?: "Couldn't refresh this title" }
            _refreshing.value = false
        }
    }

    /**
     * PLAN.md §5 screen 4's "＋ My List toggle". PLAN.md §5a: blocked with an explanatory event
     * (not a silent no-op) unless the title currently has GB availability on a subscribed
     * provider — this is the main real-world path into that gate, since details is reachable
     * ungated from History for a title that's since stopped streaming anywhere the family pays
     * for. Removing is never blocked.
     */
    fun toggleWatchlist() {
        viewModelScope.launch {
            val title = uiState.value.title
            val region = userPreferencesRepository.region.first()
            when (watchlistRepository.toggle(tmdbId, mediaType, activeProfileId, region)) {
                WatchlistAddResult.UNAVAILABLE -> _events.emit(
                    TitleDetailUiEvent.WatchlistBlocked(
                        "${title?.title ?: "This title"} isn't currently on any of your services, so it can't be added."
                    )
                )
                WatchlistAddResult.ADDED, WatchlistAddResult.REMOVED -> Unit
            }
        }
    }

    /** Thumbs for the active profile; tapping the current value again clears it. */
    fun rate(value: RatingValue) {
        viewModelScope.launch {
            ratingRepository.setOrToggle(activeProfileId, tmdbId, mediaType, value)
        }
    }

    private fun List<TitleAttributeEntity>.named(type: AttrType): List<String> =
        filter { it.attrType == type }.map { it.name }
}
