package org.seg7.familywatchlist.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.repository.SearchRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository

/** PLAN.md §5 screen 5: "movie/TV filter chips". */
enum class SearchFilter(val label: String) {
    ALL("All"),
    MOVIES("Films"),
    TV("Series"),
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val results: List<TitleEntity> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    /** True once a query has actually run, so "no results" only shows after a real search. */
    val hasSearched: Boolean = false,
    /** `"MEDIATYPE-tmdbId"` for everything currently on the shared list — drives each card's ＋/✓. */
    val listedKeys: Set<String> = emptySet(),
) {
    /**
     * Filtering happens here rather than in the repository, so flipping a chip re-renders
     * instantly off the results already in hand instead of costing another TMDB round-trip —
     * PLAN.md §3's "filter results to movie/tv client-side", taken literally.
     */
    val visibleResults: List<TitleEntity>
        get() = when (filter) {
            SearchFilter.ALL -> results
            SearchFilter.MOVIES -> results.filter { it.mediaType == MediaType.MOVIE }
            SearchFilter.TV -> results.filter { it.mediaType == MediaType.TV }
        }

    fun isListed(title: TitleEntity): Boolean = listedKey(title.tmdbId, title.mediaType) in listedKeys
}

internal fun listedKey(tmdbId: Int, mediaType: MediaType): String = "$mediaType-$tmdbId"

/** Everything the search screen renders, in one immutable snapshot. */
private data class SearchInternal(
    val results: List<TitleEntity> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false,
)

/**
 * PLAN.md §5 screen 5: TMDB multi-search, movie/TV filter chips, quick add-to-list.
 *
 * Typing is debounced ([SEARCH_DEBOUNCE_MS]) before it reaches the network. Without it a
 * six-letter title is six requests against PLAN.md §3's 4 req/s throttle, which would queue up
 * and leave the results lagging a second behind the keyboard.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val watchlistRepository: WatchlistRepository,
    private val activeProfileId: Long,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    private val _search = MutableStateFlow(SearchInternal())

    /** The last query actually sent to TMDB — see the dedupe note in [runSearch]. */
    private var lastSearched: String? = null

    private val listedKeys: StateFlow<Set<String>> = watchlistRepository.observeActive()
        .map { entries -> entries.map { listedKey(it.tmdbId, it.mediaType) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<SearchUiState> =
        combine(_query, _filter, _search, listedKeys) { query, filter, search, listed ->
            SearchUiState(
                query = query,
                filter = filter,
                results = search.results,
                isSearching = search.isSearching,
                errorMessage = search.errorMessage,
                hasSearched = search.hasSearched,
                listedKeys = listed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            _query
                .debounce(SEARCH_DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
                .collect { runSearch(it) }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        // Clearing the box empties the screen immediately, not after the debounce elapses.
        if (query.isBlank()) {
            _search.value = SearchInternal()
            lastSearched = null
        }
    }

    fun onFilterChange(filter: SearchFilter) {
        _filter.value = filter
    }

    /** Explicit submit (keyboard "search" key) — runs the current query without waiting. */
    fun onSubmit() {
        viewModelScope.launch { runSearch(_query.value.trim()) }
    }

    fun toggleWatchlist(title: TitleEntity) {
        viewModelScope.launch {
            watchlistRepository.toggle(title.tmdbId, title.mediaType, activeProfileId)
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _search.value = SearchInternal()
            lastSearched = null
            return
        }
        // Dedupe: hitting the keyboard's search key right after typing stops would otherwise
        // fire the same query twice — once from `onSubmit`, once when the debounce elapses —
        // for two identical TMDB requests against §3's 4 req/s budget.
        //
        // The guard is `lastSearched` alone, deliberately: it's stamped *before* the request
        // goes out, so it also suppresses the duplicate while the first request is still in
        // flight (which is exactly the case here — `hasSearched` doesn't flip until a search
        // completes). A previous *failure* is still retryable, since pressing search again after
        // an error is a real user intent.
        val current = _search.value
        if (query == lastSearched && current.errorMessage == null) return
        lastSearched = query

        _search.value = current.copy(isSearching = true, errorMessage = null)
        runCatching { searchRepository.search(query) }
            .onSuccess { results ->
                _search.value = SearchInternal(results = results, isSearching = false, hasSearched = true)
            }
            .onFailure { throwable ->
                _search.value = _search.value.copy(
                    isSearching = false,
                    hasSearched = true,
                    errorMessage = throwable.message ?: "Search failed",
                )
            }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
