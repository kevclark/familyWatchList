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
import org.seg7.familywatchlist.data.repository.DescriptiveSearchRepository
import org.seg7.familywatchlist.data.repository.Interpretation
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.SearchPlan
import org.seg7.familywatchlist.data.search.AgeFilter
import org.seg7.familywatchlist.data.search.AgeOption
import org.seg7.familywatchlist.data.search.QueryParser
import org.seg7.familywatchlist.data.repository.RecommendationRepository
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
    /**
     * PLAN.md §5b M3i item 4: the active profile's (or Family's strictest-member) age cap, as
     * resolved for the search that actually ran — null means "no cap", the common case. Together
     * with an empty, genuinely-searched result this drives [isPlausiblyHiddenByAgeRating]: a
     * distinct "hidden due to age rating" empty state, separate from "not on your services"/
     * "no services selected". Not a certainty (a cap being *set* doesn't prove it's *why* this
     * particular query came back empty — the title(s) might simply not exist/not be subscribed
     * either) — see that property's kdoc for the exact heuristic and its documented trade-off.
     */
    val ageRatingCap: String? = null,
    /**
     * M6 (PLAN.md §5 "Paid (rent/buy) titles" addendum): `"MEDIATYPE-tmdbId"` keys (same format
     * as [listedKeys]) for every visible result available *only* to rent/buy on a subscribed
     * provider — no FLATRATE/FREE option at all. Drives each card's "RENT/BUY" tag via
     * [isPaidOnly]. Empty by default so a screen mid-search (before badges resolve) never shows a
     * stale tag.
     */
    val paidOnlyKeys: Set<String> = emptySet(),
    /** PLAN.md §5f: what the structured search understood; null for a plain title search. */
    val interpretation: Interpretation? = null,
    /** `"MEDIATYPE-tmdbId"` keys of visible results with no certification, labelled "Unrated". */
    val unratedKeys: Set<String> = emptySet(),
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** The selected age chip (defaults to the active profile's cap, or Family's strictest). */
    val ageOption: AgeOption = AgeFilter.ANY,
) {
    val ageOptions: List<AgeOption> get() = AgeFilter.options

    /** PLAN.md §5f item 2: the "Search titles for ... instead" link shows only on a structured search. */
    val showTitleSearchLink: Boolean get() = interpretation != null

    fun isUnrated(title: TitleEntity): Boolean = listedKey(title.tmdbId, title.mediaType) in unratedKeys

    /**
     * PLAN.md §5b M3i item 4's heuristic, deliberately simple: *plausible*, not *proven*. Search
     * already gates every result through [org.seg7.familywatchlist.data.recommend.FamilyBlend
     * .isOverCap] (via [SearchRepository.search]'s `ageRatingCap` parameter) before this state is
     * ever built, so there's no cheap way from here to tell "the cap excluded something" apart
     * from "nothing matched/nothing's subscribed either" without a second, cap-free search just
     * to compare against — not worth the extra TMDB round-trip against PLAN.md §3's 4 req/s
     * budget for a copy nuance. Firing whenever a cap is *set at all* on a genuinely-empty,
     * genuinely-searched, services-are-subscribed result is strictly more informative than the
     * generic message it replaces (it only shows for capped viewers to begin with), even though
     * it can occasionally show for a query that would have been empty regardless of the cap.
     */
    val isPlausiblyHiddenByAgeRating: Boolean
        get() = hasSearched && results.isEmpty() && hasSubscribedServices && errorMessage == null && ageRatingCap != null

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

    /** M6: whether [title] should render the "RENT/BUY" tag — see [paidOnlyKeys]'s kdoc. */
    fun isPaidOnly(title: TitleEntity): Boolean = listedKey(title.tmdbId, title.mediaType) in paidOnlyKeys
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
    /** PLAN.md §5b M3i item 4: the cap actually used by the search that produced [results]. */
    val ageRatingCap: String? = null,
    /** M6: mirrors [SearchUiState.paidOnlyKeys] — see its kdoc. */
    val paidOnlyKeys: Set<String> = emptySet(),
    val interpretation: Interpretation? = null,
    val unratedKeys: Set<String> = emptySet(),
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
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
    private val recommendationRepository: RecommendationRepository,
    private val descriptiveSearchRepository: DescriptiveSearchRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    private val _search = MutableStateFlow(SearchInternal())
    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events = _events.asSharedFlow()

    /** The last query actually sent to TMDB — see the dedupe note in [runSearch]. */
    private var lastSearched: String? = null

    /** PLAN.md §5f: chip keys removed for the current query text, and the "search titles instead" override. */
    private var removedKeys: Set<String> = emptySet()
    private var titlesOnly = false
    private var lastQueryText: String? = null
    private var structuredPlan: SearchPlan.Structured? = null
    private var nextPage = 2

    /** Null until the user taps an age chip; then it wins over [defaultAge]. */
    private val _ageChoice = MutableStateFlow<AgeOption?>(null)
    private val _defaultAge = MutableStateFlow(AgeFilter.ANY)

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
        combine(_query, _filter, _search, listedKeys, hasSubscribedServices, _ageChoice, _defaultAge) { values ->
            @Suppress("UNCHECKED_CAST")
            val query = values[0] as String
            val filter = values[1] as SearchFilter
            val search = values[2] as SearchInternal
            val listed = values[3] as Set<String>
            val hasServices = values[4] as Boolean
            val ageChoice = values[5] as AgeOption?
            val defaultAge = values[6] as AgeOption
            SearchUiState(
                query = query,
                filter = filter,
                results = search.results,
                isSearching = search.isSearching,
                errorMessage = search.errorMessage,
                hasSearched = search.hasSearched,
                listedKeys = listed,
                hasSubscribedServices = hasServices,
                ageRatingCap = search.ageRatingCap,
                paidOnlyKeys = search.paidOnlyKeys,
                interpretation = search.interpretation,
                unratedKeys = search.unratedKeys,
                canLoadMore = search.canLoadMore,
                isLoadingMore = search.isLoadingMore,
                ageOption = ageChoice ?: defaultAge,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            _defaultAge.value = AgeFilter.optionForCap(recommendationRepository.resolveAgeRatingCap(activeProfileId))
        }
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
     * PLAN.md §4b (M3j): a Want-to-Watch add is attributed to whichever profile is active
     * ([org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity.addedByProfileId]) —
     * [FAMILY_PROFILE_SENTINEL_ID] is now a legitimate attribution like any real profile's id, so
     * the old "switch to a person profile" block (mirrored in [org.seg7.familywatchlist.ui.details
     * .TitleDetailViewModel.toggleWatchlist]/`.rate`) is gone.
     */
    fun toggleWatchlist(title: TitleEntity) {
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
    private fun runSearch(query: String, force: Boolean = false) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _search.value = SearchInternal()
            lastSearched = null
            lastQueryText = null
            return
        }
        // New query text resets the per-query chip removals and the titles-only override.
        if (query != lastQueryText) {
            removedKeys = emptySet()
            titlesOnly = false
            lastQueryText = query
        }
        val signature = "$query|${removedKeys.sorted()}|$titlesOnly"
        val current = _search.value
        if (!force && signature == lastSearched && current.errorMessage == null) return
        lastSearched = signature

        searchJob?.cancel()
        structuredPlan = null
        nextPage = 2
        _search.value = SearchInternal(isSearching = true)
        searchJob = viewModelScope.launch {
            val region = userPreferencesRepository.region.first()
            // PLAN.md §4/§8 (M3g): the cap comes from the age chip, which defaults to the active
            // profile's (or Family's strictest-member) cap via resolveAgeRatingCap, the same rule
            // the recommender enforces. PLAN.md §5f lets the user change it per search.
            val choice = _ageChoice.value
            val ageRatingCap = if (choice != null) choice.cap else recommendationRepository.resolveAgeRatingCap(activeProfileId)
            _search.value = _search.value.copy(ageRatingCap = ageRatingCap)
            try {
                val parsed = QueryParser.parse(query).without(removedKeys)
                val plan = when {
                    titlesOnly -> SearchPlan.Titles(query)
                    !parsed.hasStructure && removedKeys.isEmpty() -> SearchPlan.Titles(query)
                    else -> descriptiveSearchRepository.plan(parsed, allowTitleFallback = removedKeys.isEmpty())
                }
                when (plan) {
                    is SearchPlan.Titles -> collectTitles(plan.text, region, ageRatingCap)
                    is SearchPlan.Structured -> {
                        structuredPlan = plan
                        _search.value = _search.value.copy(interpretation = plan.interpretation)
                        collectStructured(plan, page = 1, base = emptyList(), region = region, cap = ageRatingCap)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.value = _search.value.copy(isSearching = false, isLoadingMore = false, hasSearched = true, errorMessage = e.message ?: "Search failed")
            }
        }
    }

    private suspend fun collectTitles(text: String, region: String, ageRatingCap: String?) {
        searchRepository.search(text, region, ageRatingCap = ageRatingCap)
            // Flow's `catch` never intercepts `CancellationException` (cancellation transparency).
            .catch { throwable ->
                _search.value = _search.value.copy(
                    isSearching = false,
                    hasSearched = true,
                    errorMessage = throwable.message ?: "Search failed",
                )
            }
            .onCompletion { cause ->
                if (cause == null) {
                    _search.value = _search.value.copy(isSearching = false, hasSearched = true)
                }
            }
            .collect { results ->
                // M6: badge-only, cache-only lookups (the availability check inside the search
                // already detail-fetched every one of these).
                val paidOnly = results
                    .filter { searchRepository.isPaidOnly(it.tmdbId, it.mediaType) }
                    .map { listedKey(it.tmdbId, it.mediaType) }
                    .toSet()
                _search.value = _search.value.copy(results = results, paidOnlyKeys = paidOnly)
            }
    }

    private suspend fun collectStructured(
        plan: SearchPlan.Structured,
        page: Int,
        base: List<TitleEntity>,
        region: String,
        cap: String?,
    ) {
        descriptiveSearchRepository.run(plan, page, cap, region)
            .catch { throwable ->
                _search.value = _search.value.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    hasSearched = true,
                    errorMessage = throwable.message ?: "Search failed",
                )
            }
            .onCompletion { cause ->
                if (cause == null) {
                    _search.value = _search.value.copy(isSearching = false, isLoadingMore = false, hasSearched = true)
                }
            }
            .collect { result ->
                val seen = base.map { listedKey(it.tmdbId, it.mediaType) }.toSet()
                val merged = base + result.titles.filter { listedKey(it.tmdbId, it.mediaType) !in seen }
                val paidOnly = merged
                    .filter { searchRepository.isPaidOnly(it.tmdbId, it.mediaType) }
                    .map { listedKey(it.tmdbId, it.mediaType) }
                    .toSet()
                _search.value = _search.value.copy(
                    results = merged,
                    paidOnlyKeys = paidOnly,
                    unratedKeys = _search.value.unratedKeys + result.unratedKeys,
                    canLoadMore = result.hasMore,
                )
            }
    }

    /** PLAN.md §5f: removing an interpretation chip reruns the search without it. */
    fun onRemoveChip(key: String) {
        removedKeys = removedKeys + key
        runSearch(_query.value.trim())
    }

    /** PLAN.md §5f: "Search titles for ... instead" runs the original `search/multi` path. */
    fun onSearchTitlesInstead() {
        titlesOnly = true
        runSearch(_query.value.trim())
    }

    /** PLAN.md §5f: picking an age chip applies to this and later searches, and reruns the current one. */
    fun onAgeOptionChange(option: AgeOption) {
        _ageChoice.value = option
        val query = _query.value.trim()
        if (query.isNotBlank() && lastSearched != null) runSearch(query, force = true)
    }

    /** PLAN.md §5f "load more": appends the next page of the structured search. */
    fun loadMore() {
        val plan = structuredPlan ?: return
        val state = _search.value
        if (!state.canLoadMore || state.isLoadingMore || state.isSearching) return
        val page = nextPage++
        _search.value = state.copy(isLoadingMore = true)
        searchJob = viewModelScope.launch {
            val region = userPreferencesRepository.region.first()
            collectStructured(plan, page, base = state.results, region = region, cap = state.ageRatingCap)
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
