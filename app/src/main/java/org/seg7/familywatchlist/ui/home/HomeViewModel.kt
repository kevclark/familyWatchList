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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.data.recommend.FamilyBlendSlider
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.FAMILY_SCOPE_KEY
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistItemAvailability
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.ui.ActiveProfile

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
 * ## Home hero (PLAN.md §4's 2026-08-19 design note, revised by the 2026-08-21 "Cold-start Home
 * treatment" note, M3g)
 * Used to be `discover.movies.firstOrNull()` — raw popularity, "a generic, impersonal pick".
 * Now sources from the profile's **top-scored personalised pick** (the first entry of the
 * already-score-sorted `For You` list) when one exists, falling back to the same popular pick
 * only while a *warm* profile's very first shortlist is still computing — a documented,
 * deliberate fallback rather than showing nothing. **Cold-start profiles never get this fallback
 * any more** (M3g): [HomeUiState.hero] is `null` for them, and [org.seg7.familywatchlist.ui.home.HomeScreen]
 * renders an introductory "getting started" panel instead of a title-shaped hero — a popular pick
 * masquerading as "your pick" was exactly the thing PLAN.md §4's cold-start note was fixing.
 *
 * ## Age-cap safety fix (PLAN.md §4's "Age-cap safety gap" note, 2026-08-21, M3g; tightened by the
 * "Residual gap found by M3g" note, M3h)
 * [DiscoverRepository.discoverMovies]/`discoverTv` apply no age-rating filtering themselves (they
 * have no notion of "for whom") — [popularMovies]/[popularTv]/the cold-start "Popular on your
 * services" row are filtered right here in [refresh], against [RecommendationRepository.resolveAgeRatingCap]'s
 * resolution of the active profile's (or Family's strictest member) cap, via [survivesAgeCap].
 * Unlike every other [FamilyBlend.isOverCap] call site (the warm recommender's scoring, Search),
 * where "unknown != unsafe" correctly lets an uncertain-certification title through, a capped
 * profile's Popular/cold-start rows require *confirmed* at-or-under-cap certification — TMDB's raw
 * `/discover` stubs carry no certification data, so the permissive default would leave a freshly-
 * discovered, never-detail-fetched title completely unfiltered for a capped child's profile. See
 * [survivesAgeCap]'s kdoc for the full reasoning. An uncapped profile is unaffected either way.
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
 *
 * ## The Family profile (PLAN.md §4b, M3j -- supersedes M3d/M3i below this heading)
 * When [activeProfile] is [ActiveProfile.Family] -- the *persistent* counterpart to the ad-hoc
 * chip row above, selected from the profile picker exactly like a person -- Home now treats it
 * exactly like any individual profile: [refresh] calls the same plain
 * [RecommendationRepository.isColdStart]/[RecommendationRepository.refreshProfileShortlist] pair
 * with `activeProfile.id` (the sentinel for Family), sourced from Family's own logged watch
 * events/ratings (which it can now own directly, per PLAN.md §4b), not a blend of its curated
 * members' vectors. [shortlistScopeKey] still resolves to [FAMILY_SCOPE_KEY] for Family --
 * [RecommendationRepository.refreshProfileShortlist] itself now writes there for the sentinel id
 * (see its kdoc), so [forYouShortlist] keeps reading the scope it always has.
 *
 * Cold-start is likewise the plain per-profile check now -- M3i's "cold only if every curated
 * member is individually cold" special case ([familyIsColdStart], since removed) is gone, because
 * Family can own its own watch events as of M3j, so its own event count is exactly the right
 * signal, the same as any real profile's.
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
    private val familyProfileRepository: FamilyProfileRepository,
    private val activeProfile: ActiveProfile,
) : ViewModel() {

    /** The scope key [forYouShortlist]/[refresh] read and write against -- see the class kdoc's "Family profile" section. */
    private val shortlistScopeKey: String =
        if (activeProfile is ActiveProfile.Family) FAMILY_SCOPE_KEY else activeProfile.id.toString()

    private val _discover = MutableStateFlow(DiscoverState())
    // PLAN.md §5b M3i item 10: pessimistic default for both kinds of profile now -- refresh()
    // resolves the real value for either (per-profile isColdStart for an Individual, "every
    // curated member is cold-start" for Family) shortly after this ViewModel is constructed; see
    // the class kdoc's "Family cold-start" section.
    private val _coldStart = MutableStateFlow(true)

    private val familyNightProfiles: StateFlow<List<ProfileEntity>> =
        profileRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _familyNightSelection = MutableStateFlow<Set<Long>>(emptySet())
    private val _familyNightTitles = MutableStateFlow<List<TitleEntity>>(emptyList())
    private val familyNightTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // PLAN.md §5b M3i items 5 and 9: the active profile's (or Family's strictest-member) age cap
    // — [refresh] resolves it via the same [RecommendationRepository.resolveAgeRatingCap] the
    // Popular-row filtering below already uses. Doubles as the source for both the avatar badge
    // (item 5) and My List's over-cap dimming (item 9) — one resolution, two renderers, rather
    // than a second lookup for each.
    private val _ageRatingCap = MutableStateFlow<String?>(null)

    // PLAN.md §7 M2f: live region Flow, not a one-shot read — a region change made in Settings
    // re-resolves every My List card's availability immediately if Home is already open.
    //
    // PLAN.md §4b (M3j): filtered to the active profile's own additions — unconditionally, no
    // toggle here (that's the full My List screen's job via `MyListViewModel.mineOnly`). This
    // used to show the *entire* shared list regardless of who was active (M2d's original design,
    // when Family couldn't own anything of its own so "whose items" wasn't a meaningful
    // question for it); now that Family can own watchlist entries symmetrically with any real
    // profile, `addedByProfileId == activeProfile.id` is exactly the same comparison for Family
    // as for anyone else — this is the actual fix for Kev's observation that Home kept showing
    // Kevu's items while Family was the active profile.
    val myList: StateFlow<List<WatchlistItemAvailability>> =
        watchlistRepository.observeActiveItemsWithAvailability(userPreferencesRepository.region)
            .map { items -> items.filter { it.item.addedByProfileId == activeProfile.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live, offline-first read of the active profile's (or Family's) persisted shortlist — [refresh] is what regenerates it. */
    private val forYouShortlist: StateFlow<List<ShortlistEntryEntity>> =
        recommendationRepository.observeShortlist(recommendationRepository.currentWeekStart(), shortlistScopeKey)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class HomeCore(
        val myList: List<WatchlistItemAvailability>,
        val discover: DiscoverState,
        val shortlist: List<ShortlistEntryEntity>,
        val coldStart: Boolean,
        val ageRatingCap: String?,
    )

    private data class FamilyNightState(
        val profiles: List<ProfileEntity>,
        val selectedIds: Set<Long>,
        val titles: List<TitleEntity>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(myList, _discover, forYouShortlist, _coldStart, _ageRatingCap) { list, discover, shortlist, coldStart, ageRatingCap ->
            HomeCore(list, discover, shortlist, coldStart, ageRatingCap)
        },
        combine(familyNightProfiles, _familyNightSelection, _familyNightTitles) { profiles, selectedIds, titles ->
            FamilyNightState(profiles, selectedIds, titles)
        },
    ) { core, family ->
        val (list, discover, shortlist, coldStart, ageRatingCap) = core
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
            // PLAN.md §4's 2026-08-19 design note, revised by M3g's "Cold-start Home treatment":
            // the top-scored personalised pick, not raw popularity — falling back to the popular
            // pick only while a *warm* profile's first shortlist is still computing. A cold-start
            // profile gets null here, never a popularity-pick masquerading as "your pick" —
            // HomeScreen renders the intro panel instead when isColdStartForYou is true.
            hero = if (coldStart) null else forYouTitles.firstOrNull() ?: discover.movies.firstOrNull() ?: discover.tv.firstOrNull(),
            isLoading = discover.isLoading,
            errorMessage = discover.error,
            hasSubscribedServices = discover.hasSubscribedServices,
            ageRatingCap = ageRatingCap,
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
                // PLAN.md §4's "Age-cap safety gap" (M3g): DiscoverRepository itself applies no
                // age-rating filtering — it has no notion of "for whom". Filter here, right
                // before these results reach the UI (Popular rows + the cold-start "Popular on
                // your services" row both read popularMovies/popularTv), reusing the exact same
                // check the real recommender's scoring path already uses.
                val ageCap = recommendationRepository.resolveAgeRatingCap(activeProfile.id)
                // PLAN.md §5b M3i items 5/9: same resolution, published for the avatar badge and
                // My List's over-cap dimming — see [_ageRatingCap]'s kdoc.
                _ageRatingCap.value = ageCap
                val filteredMovies = movies.filter { it.survivesAgeCap(ageCap) }
                val filteredTv = tv.filter { it.survivesAgeCap(ageCap) }
                Triple(subscribed.isNotEmpty(), filteredMovies, filteredTv)
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
                // PLAN.md §4b (M3j): Family is cold-start/refreshed exactly like any individual
                // profile now — the plain per-profile isColdStart/refreshProfileShortlist check,
                // called with activeProfile.id (the sentinel for Family). This replaces M3i's
                // "cold only if every curated member is individually cold" special case
                // ([familyIsColdStart], now removed) — Family can own its own watch events as of
                // M3j, so its own event count is exactly what should decide this, same as anyone.
                val cold = recommendationRepository.isColdStart(activeProfile.id)
                _coldStart.value = cold
                if (!cold) {
                    val region = userPreferencesRepository.region.first()
                    recommendationRepository.refreshProfileShortlist(activeProfile.id, region)
                }
                // forYouShortlist is a live Flow off Room (observeShortlist) — the write above
                // is picked up automatically, no manual re-read needed here.
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

    /**
     * PLAN.md §4 "Residual gap found by M3g, resolved by Kev 2026-08-21" (M3h). [FamilyBlend.isOverCap]
     * treats an unrecognised/uncached certification as "don't exclude" — the correct, deliberate
     * "unknown != unsafe" default everywhere else this check runs (the warm recommender's scoring
     * path, Search). That default is wrong specifically for this one path: TMDB's raw `/discover`
     * results are bare stubs with no certification data at all, so letting an uncertain title
     * through here means a freshly-discovered, never-viewed title can reach a capped child's
     * profile completely unfiltered — worst right at a fresh install, exactly when a new child
     * profile is most likely being set up.
     *
     * This is a targeted flip for *this call site only* — it does not touch [FamilyBlend.isOverCap]
     * itself, which stays exactly as permissive as before for every other caller. For a profile
     * with a cap set, a title only survives with *confirmed* certification data at-or-under that
     * cap; an uncapped profile ([cap] null) is completely unaffected (short-circuits true before
     * touching certification at all — zero behavioural change for the common case). No new network
     * calls: this only narrows which already-fetched candidates qualify for display.
     */
    private fun TitleEntity.survivesAgeCap(cap: String?): Boolean =
        cap == null || (certification != null && !FamilyBlend.isOverCap(certification, cap))

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
    /** PLAN.md §5b M3i items 5/9: the active profile's (or Family's strictest-member) age cap — null means no cap. */
    val ageRatingCap: String? = null,
) {
    /** PLAN.md §4a slider 4's UI-home decision, reused verbatim: the chip row itself is only relevant with 2+ profiles on the account at all. */
    val familyNightChipsVisible: Boolean get() = familyNightProfiles.size >= 2
    /** True when there is genuinely nothing to render — drives the first-run empty state. */
    val isEmpty: Boolean
        get() = hero == null && myList.isEmpty() && popularMovies.isEmpty() && popularTv.isEmpty() && forYouTitles.isEmpty()
}
