package org.seg7.familywatchlist.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.SearchRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistAddResult
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
    /**
     * True from the moment a search starts until every result's availability check has
     * settled — PLAN.md §5a's "lightweight in-progress indicator while checks are resolving".
     * Results already confirmed available render immediately in [visibleResults] while this
     * stays true, rather than being held back behind a full-screen spinner.
     */
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    /** True once a query has actually run, so "no results" only shows after a real search. */
    val hasSearched: Boolean = false,
    /** `"MEDIATYPE-tmdbId"` for everything currently on the shared list — drives each card's ＋/✓. */
    val listedKeys: Set<String> = emptySet(),
    /**
     * PLAN.md §5a's M2g refinement: search can never surface a result with nothing subscribed
     * (the gate has no provider to pass against — see [org.seg7.familywatchlist.data.repository.AvailabilityGate]),
     * but an empty result list looks identical whether that's *why*, or whether services are
     * subscribed and the query itself just matched nothing available. This distinguishes the two
     * so the empty state can explain the right one. Defaults `true` so the screen never
     * flashes the wrong message before [ProviderRepository.observeSubscribed] has emitted.
     */
    val hasSubscribedServices: Boolean = true,
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

/** PLAN.md §5a: one-shot UI events that don't belong in [SearchUiState] (nothing to render continuously, just show once). */
sealed interface SearchUiEvent {
    /** A quick-add was blocked because the title isn't on a subscribed provider right now. */
    data class WatchlistBlocked(val message: String) : SearchUiEvent
}

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
    private val providerRepository: ProviderRepository,
    private val activeProfileId: Long,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    private val _search = MutableStateFlow(SearchInternal())
    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events = _events.asSharedFlow()

    /** The last query actually sent to TMDB — see the dedupe note in [runSearch]. */
    private var lastSearched: String? = null

    /**
     * The currently-collecting [SearchRepository.search] flow, if any. PLAN.md §5a: "in-flight
     * availability checks for a stale query must be cancelled when a new one starts" — cancelling
     * this job cancels [SearchRepository.search]'s `channelFlow` producer (and every availability
     * check it launched as a child coroutine), so a slow-resolving old batch can never write over
     * a newer query's results. Every path that starts a new search cancels the previous job first.
     */
    private var searchJob: Job? = null

    private val listedKeys: StateFlow<Set<String>> = watchlistRepository.observeActive()
        .map { entries -> entries.map { listedKey(it.tmdbId, it.mediaType) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * PLAN.md §5a's M2g refinement: drives which "nothing here" message the screen shows —
     * distinct from a subscribed-but-nothing-matched result, see [SearchUiState.hasSubscribedServices].
     */
    private val hasSubscribedServices: StateFlow<Boolean> = providerRepository.observeSubscribed()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val uiState: StateFlow<SearchUiState> =
        combine(_query, _filter, _search, listedKeys, hasSubscribedServices) { query, filter, search, listed, hasServices ->
            SearchUiState(
                query = query,
                filter = filter,
                results = search.results,
                isSearching = search.isSearching,
                errorMessage = search.errorMessage,
                hasSearched = search.hasSearched,
                listedKeys = listed,
                hasSubscribedServices = hasServices,
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
            searchJob?.cancel()
            _search.value = SearchInternal()
            lastSearched = null
        }
    }

    fun onFilterChange(filter: SearchFilter) {
        _filter.value = filter
    }

    /** Explicit submit (keyboard "search" key) — runs the current query without waiting. */
    fun onSubmit() {
        runSearch(_query.value.trim())
    }

    /**
     * PLAN.md §4 (M3d): a Want-to-Watch add is attributed to a real person
     * ([org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity.addedByProfileId]) —
     * blocked with the same explanatory-event pattern as the availability gate below (not a
     * silent no-op, and never writing [FAMILY_PROFILE_SENTINEL_ID] as an attribution) when the
     * Family profile is active, mirroring how [org.seg7.familywatchlist.ui.details
     * .TitleDetailViewModel.toggleWatchlist]/`.rate` handle the same case.
     */
    fun toggleWatchlist(title: TitleEntity) {
        if (activeProfileId == FAMILY_PROFILE_SENTINEL_ID) {
            viewModelScope.launch {
                _events.emit(
                    SearchUiEvent.WatchlistBlocked("Switch to a person profile to add titles to the list.")
                )
            }
            return
        }
        viewModelScope.launch {
            val region = userPreferencesRepository.region.first()
            when (watchlistRepository.toggle(title.tmdbId, title.mediaType, activeProfileId, region)) {
                WatchlistAddResult.UNAVAILABLE -> _events.emit(
                    SearchUiEvent.WatchlistBlocked(
                        "${title.title} isn't currently on any of your services, so it can't be added."
                    )
                )
                WatchlistAddResult.ADDED, WatchlistAddResult.REMOVED -> Unit
            }
        }
    }

    /**
     * Dedupe: hitting the keyboard's search key right after typing stops would otherwise fire
     * the same query twice — once from `onSubmit`, once when the debounce elapses — for two
     * identical TMDB requests against §3's 4 req/s budget.
     *
     * The guard is `lastSearched` alone, deliberately: it's stamped *before* the request goes
     * out, so it also suppresses the duplicate while the first request is still in flight (which
     * is exactly the case here — `hasSearched` doesn't flip until a search completes). A previous
     * *failure* is still retryable, since pressing search again after an error is a real user
     * intent.
     *
     * PLAN.md §5a's cancel-on-requery: every accepted (non-deduped) query first cancels
     * [searchJob] — the coroutine collecting the *previous* query's [SearchRepository.search]
     * flow — before launching a new one. That cancellation propagates into the repository's
     * `channelFlow` and every availability check it started, so a stale batch can never emit
     * into `_search` after a newer query has taken over.
     */
    private fun runSearch(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _search.value = SearchInternal()
            lastSearched = null
            return
        }
        val current = _search.value
        if (query == lastSearched && current.errorMessage == null) return
        lastSearched = query

        searchJob?.cancel()
        _search.value = SearchInternal(isSearching = true)
        searchJob = viewModelScope.launch {
            val region = userPreferencesRepository.region.first()
            searchRepository.search(query, region)
                // Flow's `catch` never intercepts `CancellationException` (cancellation
                // transparency) — only genuine failures (a bad network call, a decode error)
                // land here, so a cancelled-by-requery job falls straight through to
                // `onCompletion` below instead of being mistaken for an error.
                .catch { throwable ->
                    _search.value = SearchInternal(
                        isSearching = false,
                        hasSearched = true,
                        errorMessage = throwable.message ?: "Search failed",
                    )
                }
                .onCompletion { cause ->
                    // `cause == null` on normal completion; a `CancellationException` here means
                    // a newer query already replaced this job, in which case leaving the (stale)
                    // `isSearching = true` state alone is correct — the new job overwrites it.
                    if (cause == null) {
                        _search.value = _search.value.copy(isSearching = false, hasSearched = true)
                    }
                }
                .collect { results -> _search.value = _search.value.copy(results = results) }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
