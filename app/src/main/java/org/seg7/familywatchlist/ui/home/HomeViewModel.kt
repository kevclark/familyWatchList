package org.seg7.familywatchlist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.dao.WatchlistItem
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository

/**
 * Home's single continuous feed (PLAN.md §5 screen 3, restructured by §5a).
 *
 * ## Which rows exist, and why one of them is still a placeholder
 * PLAN.md §5 names four rows: *My List*, *For {profile}*, *Family night*, *Popular on your
 * services*. Two of those are M3's output — [org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity]
 * has no rows until the recommender exists, and "For Kev" filled with popular titles would be a
 * lie dressed as personalisation. M2a shipped all four as empty placeholder boxes, which is
 * exactly the "wall of text boxes" Kev called out; M2b then swung the other way and omitted the
 * personalised rows outright.
 *
 * This VM builds the rows it can honestly populate with real data:
 *  - **My List** — real, from [WatchlistRepository].
 *  - **Popular films / series on your services** — real, from `/discover` filtered to the
 *    subscribed GB providers. This is also PLAN.md §4's cold-start fallback ("< 5 watch events
 *    → popular-on-your-services"), so M3 inherits it rather than replacing it.
 *
 * **For You** (PLAN.md §5a "Post-M2b decisions", 2026-08-19) is *not* built here — it's a static,
 * always-visible pre-M3 placeholder rendered directly in [HomeScreen]
 * (`ForYouPlaceholder`), since it has no data dependency yet. See that composable's kdoc for why
 * it's always shown and what M3 needs to do to retire it.
 */
class HomeViewModel(
    private val discoverRepository: DiscoverRepository,
    private val providerRepository: ProviderRepository,
    watchlistRepository: WatchlistRepository,
) : ViewModel() {

    private val _discover = MutableStateFlow(DiscoverState())
    val myList: StateFlow<List<WatchlistItem>> = watchlistRepository.observeActiveItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<HomeUiState> = combine(myList, _discover) { list, discover ->
        HomeUiState(
            myList = list,
            popularMovies = discover.movies,
            popularTv = discover.tv,
            hero = discover.movies.firstOrNull() ?: discover.tv.firstOrNull(),
            isLoading = discover.isLoading,
            errorMessage = discover.error,
            hasSubscribedServices = discover.hasSubscribedServices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    init {
        refresh()
    }

    /**
     * Fills the discover rows. Offline-first by construction: [DiscoverRepository] serves its
     * 24h cache without touching the network when it's warm (PLAN.md §3), so this is cheap to
     * call on every Home entry, and a failed network call leaves whatever was already cached
     * on screen rather than blanking the feed.
     */
    fun refresh() {
        viewModelScope.launch {
            _discover.value = _discover.value.copy(isLoading = true, error = null)
            runCatching {
                val subscribed = providerRepository.getSubscribedIds()
                // No services picked yet → an unfiltered /discover still beats an empty screen,
                // and the row title changes to match (see HomeScreen).
                val movies = discoverRepository.discoverMovies(subscribed)
                val tv = discoverRepository.discoverTv(subscribed)
                Triple(subscribed.isNotEmpty(), movies, tv)
            }.onSuccess { (hasServices, movies, tv) ->
                _discover.value = DiscoverState(
                    movies = movies,
                    tv = tv,
                    isLoading = false,
                    hasSubscribedServices = hasServices,
                )
            }.onFailure { throwable ->
                _discover.value = _discover.value.copy(
                    isLoading = false,
                    error = throwable.message ?: "Couldn't reach TMDB",
                )
            }
        }
    }

    private data class DiscoverState(
        val movies: List<TitleEntity> = emptyList(),
        val tv: List<TitleEntity> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val hasSubscribedServices: Boolean = false,
    )
}

data class HomeUiState(
    val hero: TitleEntity? = null,
    val myList: List<WatchlistItem> = emptyList(),
    val popularMovies: List<TitleEntity> = emptyList(),
    val popularTv: List<TitleEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSubscribedServices: Boolean = false,
) {
    /** True when there is genuinely nothing to render — drives the first-run empty state. */
    val isEmpty: Boolean
        get() = hero == null && myList.isEmpty() && popularMovies.isEmpty() && popularTv.isEmpty()
}
