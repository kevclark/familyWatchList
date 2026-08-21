package org.seg7.familywatchlist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.recommend.FamilyBlendSlider
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistItemAvailability
import org.seg7.familywatchlist.data.repository.WatchlistRepository

/**
 * Home's single continuous feed (PLAN.md §5 screen 3, restructured by §5a).
 *
 * ## For You, now real (M3)
 * PLAN.md §5 names four rows: *My List*, *For {profile}*, *Family night*, *Popular on your
 * services*. Through M2, *For You* was a static "coming soon" placeholder (PLAN.md §5a's
 * Post-M2b decision) because the recommender didn't exist. It's real now:
 *  - **Cold start** (< 5 logged events, [RecommendationRepository.isColdStart]): still labelled
 *    "Popular on your services" and sourced from the same `/discover` data as the Popular rows
 *    below — PLAN.md §4's own cold-start fallback, not a new mechanism.
 *  - **Warm profiles**: sourced from [RecommendationRepository.observeShortlist] — the
 *    *persisted* weekly shortlist, read live off Room (offline-first; no network on a passive
 *    Home visit). [refresh] (init, and the manual pull-to-refresh/refresh-icon action) is what
 *    actually recomputes it via [RecommendationRepository.refreshProfileShortlist] — PLAN.md §4:
 *    "Manual pull-to-refresh on Home does the same [as the weekly job] on demand." Recomputing
 *    is cheap after the first time in practice: candidate titles are cached for 30 days
 *    ([TitleRepository]'s TTL), so only genuinely new/stale candidates ever hit the network.
 *
 * ## Home hero (PLAN.md §4's 2026-08-19 design note)
 * Used to be `discover.movies.firstOrNull()` — raw popularity, "a generic, impersonal pick".
 * Now sources from the profile's **top-scored personalised pick** (the first entry of the
 * already-score-sorted `For You` list) when one exists, falling back to the same popular pick as
 * before for cold-start profiles or while the very first shortlist is still computing — a
 * documented, deliberate fallback rather than showing nothing.
 *
 * ## Family Night (M3c)
 * The who's-watching chip row: [familyNightProfiles] lists every profile on the account (chip row
 * only ever rendered by [org.seg7.familywatchlist.ui.home.HomeScreen] once there are 2+ — same
 * gating PLAN.md §4a slider 4 established, reused as-is rather than reinvented); tapping a chip
 * toggles it in/out of [_familyNightSelection]. Fewer than 2 selected means the row has nothing
 * meaningful to blend, so [familyNightTrigger]'s handler leaves [_familyNightTitles] empty without
 * touching the network or [RecommendationRepository] at all. 2+ selected debounces (mirroring
 * [org.seg7.familywatchlist.ui.tune.TunePicksViewModel]'s slider-recompute pattern — don't refire
 * on every rapid tap) into [RecommendationRepository.refreshFamilyShortlist] with `persist =
 * false` — the ad-hoc, non-persisted path that method's kdoc documents as built specifically for
 * this chip row — using the shared family-blend-slider preference already stored from M3
 * ([UserPreferencesRepository.familyBlendSlider]), not a second mechanism.
 */
@OptIn(FlowPreview::class)
class HomeViewModel(
    private val discoverRepository: DiscoverRepository,
    private val providerRepository: ProviderRepository,
    private val watchlistRepository: WatchlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val recommendationRepository: RecommendationRepository,
    private val titleRepository: TitleRepository,
    private val profileRepository: ProfileRepository,
    private val activeProfileId: Long,
) : ViewModel() {

    private val _discover = MutableStateFlow(DiscoverState())
    private val _coldStart = MutableStateFlow(true)

    private val familyNightProfiles: StateFlow<List<ProfileEntity>> =
        profileRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _familyNightSelection = MutableStateFlow<Set<Long>>(emptySet())
    private val _familyNightTitles = MutableStateFlow<List<TitleEntity>>(emptyList())
    private val familyNightTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // PLAN.md §7 M2f: live region Flow, not a one-shot read — a region change made in Settings
    // re-resolves every My List card's availability immediately if Home is already open.
    val myList: StateFlow<List<WatchlistItemAvailability>> =
        watchlistRepository.observeActiveItemsWithAvailability(userPreferencesRepository.region)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live, offline-first read of this profile's persisted shortlist — [refresh] is what regenerates it. */
    private val forYouShortlist: StateFlow<List<ShortlistEntryEntity>> =
        recommendationRepository.observeShortlist(recommendationRepository.currentWeekStart(), activeProfileId.toString())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class HomeCore(
        val myList: List<WatchlistItemAvailability>,
        val discover: DiscoverState,
        val shortlist: List<ShortlistEntryEntity>,
        val coldStart: Boolean,
    )

    private data class FamilyNightState(
        val profiles: List<ProfileEntity>,
        val selectedIds: Set<Long>,
        val titles: List<TitleEntity>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(myList, _discover, forYouShortlist, _coldStart) { list, discover, shortlist, coldStart ->
            HomeCore(list, discover, shortlist, coldStart)
        },
        combine(familyNightProfiles, _familyNightSelection, _familyNightTitles) { profiles, selectedIds, titles ->
            FamilyNightState(profiles, selectedIds, titles)
        },
    ) { core, family ->
        val (list, discover, shortlist, coldStart) = core
        // Shortlist entries carry only (tmdbId, mediaType, score) — resolve to cached TitleEntity
        // rows for rendering. Offline-first: every shortlisted candidate was already detail-fetched
        // while scoring, so this is a plain cache read, no network.
        val forYouTitles = if (coldStart || shortlist.isEmpty()) {
            emptyList()
        } else {
            val byKey = titleRepository.getTitles(shortlist.map { it.tmdbId to it.mediaType }).associateBy { it.tmdbId to it.mediaType }
            shortlist.sortedByDescending { it.score }.mapNotNull { byKey[it.tmdbId to it.mediaType] }
        }
        HomeUiState(
            myList = list,
            popularMovies = discover.movies,
            popularTv = discover.tv,
            forYouTitles = forYouTitles,
            isColdStartForYou = coldStart,
            familyNightProfiles = family.profiles,
            familyNightSelectedIds = family.selectedIds,
            familyNightTitles = family.titles,
            // PLAN.md §4's 2026-08-19 design note: the top-scored personalised pick, not raw
            // popularity — falling back to the same "popular on your services" pick only when
            // there's genuinely no personalised data yet (cold start / shortlist still empty).
            hero = forYouTitles.firstOrNull() ?: discover.movies.firstOrNull() ?: discover.tv.firstOrNull(),
            isLoading = discover.isLoading,
            errorMessage = discover.error,
            hasSubscribedServices = discover.hasSubscribedServices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    init {
        refresh()
        viewModelScope.launch {
            familyNightTrigger.debounce(FAMILY_NIGHT_DEBOUNCE_MS).collect {
                val selected = _familyNightSelection.value
                // PLAN.md §5 screen 3 / §4a slider 4's UI-home decision: a blend only makes sense
                // for 2+ people. Below that, leave the row's data empty rather than calling
                // RecommendationRepository at all — HomeScreen hides the row itself on this same
                // condition, but this is the load-bearing gate (not just a UI nicety).
                if (selected.size < 2) {
                    _familyNightTitles.value = emptyList()
                    return@collect
                }
                runCatching {
                    val region = userPreferencesRepository.region.first()
                    val slider = FamilyBlendSlider(userPreferencesRepository.familyBlendSlider.first())
                    val entries = recommendationRepository.refreshFamilyShortlist(
                        profileIds = selected.toList(),
                        region = region,
                        familyBlendSlider = slider,
                        persist = false,
                    )
                    val byKey = titleRepository.getTitles(entries.map { it.tmdbId to it.mediaType }).associateBy { it.tmdbId to it.mediaType }
                    entries.sortedByDescending { it.score }.mapNotNull { byKey[it.tmdbId to it.mediaType] }
                }.onSuccess { _familyNightTitles.value = it }
                    .onFailure { _familyNightTitles.value = emptyList() }
            }
        }
    }

    /** Toggles one profile in/out of the who's-watching selection and (re)triggers the debounced ad-hoc family blend. */
    fun toggleFamilyNightProfile(profileId: Long) {
        _familyNightSelection.value = _familyNightSelection.value.let { current ->
            if (profileId in current) current - profileId else current + profileId
        }
        familyNightTrigger.tryEmit(Unit)
    }

    /**
     * Fills the discover rows and regenerates this profile's shortlist. Offline-first by
     * construction for the discover half ([DiscoverRepository] serves its 24h cache without
     * touching the network when warm); the shortlist half genuinely recomputes
     * ([RecommendationRepository.refreshProfileShortlist]) every call, matching PLAN.md §4's
     * "manual pull-to-refresh does the same [as the weekly job] on demand" — cheap in practice
     * after the first run thanks to per-title TTL caching (see the class kdoc).
     */
    fun refresh() {
        viewModelScope.launch {
            _discover.value = _discover.value.copy(isLoading = true, error = null)
            runCatching {
                val subscribed = providerRepository.getSubscribedIds()
                val region = userPreferencesRepository.region.first()
                // No services picked yet → DiscoverRepository returns empty results rather than
                // an unfiltered "popular in the UK" page (PLAN.md §7 M2e); the hero/rows collapse
                // to Home's existing empty state (HomeHeroEmpty / PosterCarousel hiding on empty).
                val movies = discoverRepository.discoverMovies(subscribed, region)
                val tv = discoverRepository.discoverTv(subscribed, region)
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
        viewModelScope.launch {
            runCatching {
                val cold = recommendationRepository.isColdStart(activeProfileId)
                _coldStart.value = cold
                if (!cold) {
                    val region = userPreferencesRepository.region.first()
                    recommendationRepository.refreshProfileShortlist(activeProfileId, region)
                    // forYouShortlist is a live Flow off Room (observeShortlist) — the write above
                    // is picked up automatically, no manual re-read needed here.
                }
            }
            // A failed shortlist recompute leaves whatever was already persisted/cached on
            // screen (same offline-first posture as the discover half above) rather than
            // blanking the row — no separate error surface for this half is needed.
        }
    }

    /**
     * PLAN.md §5a M2g: the direct clean-up action on a dimmed "My List" carousel card — removes
     * without a detour through the details screen. Removing is never gated (see
     * [WatchlistRepository.remove]'s kdoc), so this is a plain delegation.
     */
    fun removeFromWatchlist(tmdbId: Int, mediaType: MediaType) {
        viewModelScope.launch { watchlistRepository.remove(tmdbId, mediaType) }
    }

    private data class DiscoverState(
        val movies: List<TitleEntity> = emptyList(),
        val tv: List<TitleEntity> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val hasSubscribedServices: Boolean = false,
    )

    companion object {
        /** Same order of magnitude as [org.seg7.familywatchlist.ui.tune.TunePicksViewModel.RECOMPUTE_DEBOUNCE_MS] — don't refire on every rapid chip tap. */
        const val FAMILY_NIGHT_DEBOUNCE_MS = 400L
    }
}

data class HomeUiState(
    val hero: TitleEntity? = null,
    val myList: List<WatchlistItemAvailability> = emptyList(),
    val popularMovies: List<TitleEntity> = emptyList(),
    val popularTv: List<TitleEntity> = emptyList(),
    val forYouTitles: List<TitleEntity> = emptyList(),
    /** True until the profile crosses PLAN.md §4's 5-event cold-start threshold. */
    val isColdStartForYou: Boolean = true,
    /** Every profile on the account — the who's-watching chip row's source list. */
    val familyNightProfiles: List<ProfileEntity> = emptyList(),
    val familyNightSelectedIds: Set<Long> = emptySet(),
    /** The ad-hoc, non-persisted blend for [familyNightSelectedIds] — only ever non-empty once 2+ are selected. */
    val familyNightTitles: List<TitleEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSubscribedServices: Boolean = false,
) {
    /** PLAN.md §4a slider 4's UI-home decision, reused verbatim: the chip row itself is only relevant with 2+ profiles on the account at all. */
    val familyNightChipsVisible: Boolean get() = familyNightProfiles.size >= 2
    /** True when there is genuinely nothing to render — drives the first-run empty state. */
    val isEmpty: Boolean
        get() = hero == null && myList.isEmpty() && popularMovies.isEmpty() && popularTv.isEmpty() && forYouTitles.isEmpty()
}
