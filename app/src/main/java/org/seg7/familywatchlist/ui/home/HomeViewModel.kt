package org.seg7.familywatchlist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.data.recommend.FamilyBlendSlider
import org.seg7.familywatchlist.data.recommend.ExtraPicks
import org.seg7.familywatchlist.data.recommend.ExtraPicksPager
import org.seg7.familywatchlist.data.recommend.FamilyNightProgress
import org.seg7.familywatchlist.data.recommend.MoreCardState
import org.seg7.familywatchlist.data.recommend.TitleKey
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.work.RefreshCoordinator
import org.seg7.familywatchlist.work.RefreshUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    /**
     * PLAN.md §5d (M15): the shared refresh guard + log. Null keeps the pre-M15 behaviour (used by
     * tests that don't exercise refresh orchestration): Home then recomputes its own shortlist
     * directly, with no banner and no log entry.
     */
    private val refreshCoordinator: RefreshCoordinator? = null,
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
    // M10: true for the span between the debounce firing (2+ selected) and the ad-hoc blend
    // landing (success or failure) — HomeScreen uses this to withhold the "genuinely empty"
    // message while a result is still in flight, so a fresh chip tap doesn't flash "nothing
    // works for everyone" for the split second before real results arrive. False whenever fewer
    // than 2 are selected — there's no computation to be "in flight" for that case.
    private val _familyNightLoading = MutableStateFlow(false)
    private val familyNightTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    // PLAN.md §5d part 5 (M15): the staged label ("Checking availability 12/40…"), published only
    // once a blend has been running longer than FAMILY_NIGHT_PROGRESS_GRACE_MS -- a result that
    // comes straight from cache never shows any progress UI at all.
    private val _familyNightProgress = MutableStateFlow<String?>(null)
    private val refreshState: StateFlow<RefreshUiState> =
        refreshCoordinator?.state ?: MutableStateFlow(RefreshUiState.Idle)

    /**
     * PLAN.md §5e (M16): one row's in-memory "Show 30 more" state. [pager] is null until the ranked
     * pool is known (For You: first tap scores it on demand; Family Night: it arrives with the
     * blend). [titles] are the appended extras. Never persisted; see [resetExtras].
     */
    private data class ExtraRow(
        val pager: ExtraPicksPager? = null,
        val titles: List<TitleEntity> = emptyList(),
        val loading: Boolean = false,
    )

    private val _forYouExtra = MutableStateFlow(ExtraRow())
    private val _familyNightExtra = MutableStateFlow(ExtraRow())
    private var forYouExtraJob: Job? = null
    private var familyNightExtraJob: Job? = null
    /** The Family Night blend's full rest-of-pool, kept so a landed refresh can rewind the row to its first page. */
    private var familyNightRest: List<TitleKey> = emptyList()
    /** Last-known filtered pool size from the persisted recompute -- the For You card's "of N" before any on-demand scoring. */
    private val _forYouEligible = MutableStateFlow<Int?>(null)

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

    /**
     * Live, offline-first read of the active profile's (or Family's) persisted shortlist —
     * [refresh] is what regenerates it. Filtered to SUGGESTED: [observeShortlist]'s underlying
     * query returns every state for this scope/week, including DISMISSED rows [dismissTitle]
     * writes — without this filter, a just-dismissed title (which already has a shortlist row,
     * the common "For You" case) would keep rendering here until the next full recompute even
     * though the Room write itself lands immediately. Also excludes WATCHED, for the same
     * "this row shows live suggestions, not the full historical row" reason.
     */
    private val forYouShortlist: StateFlow<List<ShortlistEntryEntity>> =
        recommendationRepository.observeShortlist(recommendationRepository.currentWeekStart(), shortlistScopeKey)
            .map { entries -> entries.filter { it.state == ShortlistState.SUGGESTED } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * PLAN.md §5 screen 3's dismiss gesture, in-memory half. [forYouShortlist] above already
     * reflects a dismissal of a title that *was* on the shortlist the moment
     * [RecommendationRepository.dismissTitle]'s Room write lands (it's a live Flow off the same
     * table). Popular/Family-Night cards have no such row to begin with (see
     * [RecommendationRepository.dismissTitle]'s kdoc) — their source flows
     * ([_discover]/[_familyNightTitles]) are plain in-memory snapshots with nothing to observe,
     * so without this set a dismissed Popular/Family-Night card would linger on screen until the
     * next manual/weekly refresh. This set is never persisted itself and is cleared on process
     * death — [RecommendationRepository.dismissTitle]'s Room write is the actual persistence;
     * this only makes *this* screen instance feel instant.
     */
    private val _dismissedKeys = MutableStateFlow<Set<Pair<Int, MediaType>>>(emptySet())

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
        val isLoading: Boolean,
    )

    private data class Extras(
        val progress: String?,
        val refresh: RefreshUiState,
        val forYou: ExtraRow,
        val familyNight: ExtraRow,
        val forYouEligible: Int?,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(myList, _discover, forYouShortlist, _coldStart, _ageRatingCap) { list, discover, shortlist, coldStart, ageRatingCap ->
            HomeCore(list, discover, shortlist, coldStart, ageRatingCap)
        },
        combine(familyNightProfiles, _familyNightSelection, _familyNightTitles, _familyNightLoading) { profiles, selectedIds, titles, isLoading ->
            FamilyNightState(profiles, selectedIds, titles, isLoading)
        },
        _dismissedKeys,
        combine(_familyNightProgress, refreshState, _forYouExtra, _familyNightExtra, _forYouEligible) { progress, refresh, forYouExtra, familyExtra, eligible ->
            Extras(progress, refresh, forYouExtra, familyExtra, eligible)
        },
    ) { core, family, dismissed, extras ->
        val familyProgress = extras.progress
        val refreshUi = extras.refresh
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
        // PLAN.md §5 screen 3: instant removal of a just-dismissed card from every
        // recommendation-flavoured row — see [_dismissedKeys]'s kdoc for why this in-memory
        // filter is needed alongside RecommendationRepository.dismissTitle's Room write.
        fun List<TitleEntity>.withoutDismissed() = filterNot { (it.tmdbId to it.mediaType) in dismissed }
        val visibleForYouBase = forYouTitles.withoutDismissed()
        val visibleForYouTitles = visibleForYouBase + extras.forYou.titles.withoutDismissed()
        val dismissedKeys = dismissed.map { TitleKey(it.first, it.second) }.toSet()
        val visiblePopularMovies = discover.movies.withoutDismissed()
        val visiblePopularTv = discover.tv.withoutDismissed()
        HomeUiState(
            myList = list,
            popularMovies = visiblePopularMovies,
            popularTv = visiblePopularTv,
            forYouTitles = visibleForYouTitles,
            forYouMore = if (coldStart || shortlist.isEmpty()) null else {
                val pager = extras.forYou.pager
                val shownKeys = visibleForYouTitles.map { TitleKey(it.tmdbId, it.mediaType) }.toSet()
                val remaining = if (pager != null) {
                    pager.remaining(shownKeys + dismissedKeys).size
                } else {
                    // Before the first tap nothing is scored: use the last recompute's filtered pool
                    // size. Session dismissals were still counted in it, so take them off.
                    ((extras.forYouEligible ?: 0) - visibleForYouTitles.size - dismissedKeys.size).coerceAtLeast(0)
                }
                MoreCardState.of(visibleForYouTitles.size, remaining, extras.forYou.loading)
            },
            isColdStartForYou = coldStart,
            familyNightProfiles = family.profiles,
            familyNightSelectedIds = family.selectedIds,
            familyNightTitles = (family.titles + extras.familyNight.titles).withoutDismissed(),
            familyNightMore = extras.familyNight.pager?.let { pager ->
                val visible = (family.titles + extras.familyNight.titles).withoutDismissed()
                val shownKeys = visible.map { TitleKey(it.tmdbId, it.mediaType) }.toSet()
                MoreCardState.of(visible.size, pager.remaining(shownKeys + dismissedKeys).size, extras.familyNight.loading)
            },
            familyNightLoading = family.isLoading,
            // PLAN.md §4's 2026-08-19 design note, revised by M3g's "Cold-start Home treatment":
            // the top-scored personalised pick, not raw popularity — falling back to the popular
            // pick only while a *warm* profile's first shortlist is still computing. A cold-start
            // profile gets null here, never a popularity-pick masquerading as "your pick" —
            // HomeScreen renders the intro panel instead when isColdStartForYou is true. Sourced
            // from the already-dismissed-filtered lists so a just-dismissed hero pick can't
            // linger as the hero after its card has vanished from For You/Popular.
            hero = if (coldStart) null else visibleForYouTitles.firstOrNull() ?: visiblePopularMovies.firstOrNull() ?: visiblePopularTv.firstOrNull(),
            isLoading = discover.isLoading,
            errorMessage = discover.error,
            hasSubscribedServices = discover.hasSubscribedServices,
            ageRatingCap = ageRatingCap,
            newPickKeys = shortlist.filter { it.isNew }.map { it.tmdbId to it.mediaType }.toSet(),
            familyNightProgress = familyProgress,
            refreshState = refreshUi,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    init {
        refresh()
        viewModelScope.launch {
            // PLAN.md §5e: a landed refresh rewrites the persisted picks, so appended extras go.
            refreshState.collect { if (it is RefreshUiState.Finished) { resetExtras(); loadEligible() } }
        }
        viewModelScope.launch {
            // M11 (flicker fix): collectLatest, not plain collect. A rapid deselect/reselect can
            // debounce through more than one post-quiet-period value before this block's own slow
            // work (network round trip via refreshFamilyShortlist) finishes — with a plain
            // `.collect`, each of those runs to completion as a queue, so an intermediate,
            // already-superseded selection (e.g. the momentary <2-selected state mid-toggle) gets
            // its own full iteration and visibly sets the M10 empty-state message before the next
            // queued iteration (the real final selection) overwrites it a moment later — the
            // flicker Kev reported. `collectLatest` cancels an in-flight iteration the instant a
            // newer trigger arrives, so only the most recent selection's computation ever reaches
            // `_familyNightTitles`/`_familyNightLoading`.
            familyNightTrigger.debounce(FAMILY_NIGHT_DEBOUNCE_MS).collectLatest {
                val selected = _familyNightSelection.value
                // PLAN.md §5 screen 3 / §4a slider 4's UI-home decision: a blend only makes sense
                // for 2+ people. Below that, leave the row's data empty rather than calling
                // RecommendationRepository at all — HomeScreen hides the row itself on this same
                // condition, but this is the load-bearing gate (not just a UI nicety).
                if (selected.size < 2) {
                    _familyNightTitles.value = emptyList()
                    _familyNightLoading.value = false
                    _familyNightProgress.value = null
                    return@collectLatest
                }
                // M10: flagged for the span of the actual blend computation below — see
                // [_familyNightLoading]'s kdoc for why HomeScreen needs this (distinguishing
                // "still computing" from "computed, genuinely zero results").
                _familyNightLoading.value = true
                _familyNightProgress.value = null
                var revealed = false
                var latest: FamilyNightProgress? = null
                val reveal: Job = viewModelScope.launch {
                    delay(FAMILY_NIGHT_PROGRESS_GRACE_MS)
                    revealed = true
                    _familyNightProgress.value = (latest ?: FamilyNightProgress.BuildingProfiles).label
                }
                runCatching {
                    val region = userPreferencesRepository.region.first()
                    val slider = FamilyBlendSlider(userPreferencesRepository.familyBlendSlider.first())
                    val ranked = recommendationRepository.refreshFamilyShortlistRanked(
                        profileIds = selected.toList(),
                        region = region,
                        familyBlendSlider = slider,
                        persist = false,
                        onProgress = { progress ->
                            latest = progress
                            if (revealed) _familyNightProgress.value = progress.label
                        },
                    )
                    val entries = ranked.top
                    val byKey = titleRepository.getTitles(entries.map { it.tmdbId to it.mediaType }).associateBy { it.tmdbId to it.mediaType }
                    entries.sortedByDescending { it.score }.mapNotNull { byKey[it.tmdbId to it.mediaType] } to ranked.rest
                }.onSuccess { (titles, rest) ->
                    familyNightRest = rest
                    _familyNightExtra.value = ExtraRow(pager = if (rest.isEmpty()) null else ExtraPicksPager(rest))
                    _familyNightTitles.value = titles
                }.onFailure {
                    familyNightRest = emptyList()
                    _familyNightExtra.value = ExtraRow()
                    _familyNightTitles.value = emptyList()
                }
                reveal.cancel()
                _familyNightProgress.value = null
                _familyNightLoading.value = false
            }
        }
    }

    /**
     * Toggles one profile in/out of the who's-watching selection and (re)triggers the debounced
     * ad-hoc family blend.
     *
     * M11 (real-device flicker retest): [_familyNightLoading] is set **synchronously** here, in
     * lockstep with [_familyNightSelection] itself, rather than waiting for the debounced
     * `familyNightTrigger` collector (`init`, below) to set it later. Previously there was a real
     * window — between a tap landing here and [FAMILY_NIGHT_DEBOUNCE_MS] later when the collector
     * finally set `_familyNightLoading = true` — where `selectedIds.size >= 2` was already true
     * but `familyNightLoading` was still stale-`false`; HomeScreen's M10 empty-state condition
     * (`selectedIds.size >= 2 && !familyNightLoading`) briefly evaluated true in that window and
     * flashed "nothing works for everyone" before the debounce fired and hid it again for the
     * genuine ~7-8s computation. Setting it here closes the gap entirely instead of narrowing it.
     * The collector's own later `_familyNightLoading.value = true` (once the debounce quiets down)
     * is now redundant but harmless; its `= false` branches are untouched and still own clearing
     * the flag once a real result (or empty result) lands.
     */
    fun toggleFamilyNightProfile(profileId: Long) {
        _familyNightSelection.value = _familyNightSelection.value.let { current ->
            if (profileId in current) current - profileId else current + profileId
        }
        _familyNightLoading.value = _familyNightSelection.value.size >= 2
        // PLAN.md §5e: a new selection is a new ranking -- drop the old extras straight away.
        familyNightExtraJob?.cancel()
        familyNightRest = emptyList()
        _familyNightExtra.value = ExtraRow()
        familyNightTrigger.tryEmit(Unit)
    }

    /**
     * PLAN.md §5e: drops every appended extra batch. For You loses its pager (the persisted picks
     * changed, so the ranking has to be re-scored on the next tap); Family Night keeps the blend it
     * is still showing and just rewinds to its first page.
     */
    private fun resetExtras() {
        forYouExtraJob?.cancel()
        familyNightExtraJob?.cancel()
        _forYouExtra.value = ExtraRow()
        _familyNightExtra.value = ExtraRow(pager = if (familyNightRest.isEmpty()) null else ExtraPicksPager(familyNightRest))
    }

    private fun loadEligible() {
        viewModelScope.launch {
            _forYouEligible.value = runCatching { recommendationRepository.eligibleCandidateCount(activeProfile.id) }.getOrNull()
        }
    }

    private suspend fun resolveInOrder(keys: List<TitleKey>): List<TitleEntity> {
        val byKey = titleRepository.getTitles(keys.map { it.tmdbId to it.mediaType }).associateBy { it.tmdbId to it.mediaType }
        return keys.mapNotNull { byKey[it.tmdbId to it.mediaType] }
    }

    /** PLAN.md §5e: the Family Night end card. The ranked pool arrived with the blend, so this is instant. */
    fun showMoreFamilyNight() {
        val row = _familyNightExtra.value
        val pager = row.pager ?: return
        if (row.loading) return
        familyNightExtraJob = viewModelScope.launch {
            _familyNightExtra.value = row.copy(loading = true)
            val shown = (_familyNightTitles.value + row.titles).map { TitleKey(it.tmdbId, it.mediaType) }
            val (keys, advanced) = pager.next(shown.toSet() + dismissedKeySet())
            val resolved = runCatching { resolveInOrder(keys) }.getOrNull()
            ensureActive() // a reset while resolving must not be overwritten
            _familyNightExtra.value = if (resolved == null) row else ExtraRow(advanced, row.titles + resolved)
        }
    }

    /**
     * PLAN.md §5e: the For You end card. The first tap scores the profile's pool on demand (same
     * pipeline as the persisted refresh, under the refresh guard; skipped if a real refresh is
     * running because its [RefreshUiState.Finished] resets us anyway), later taps just page.
     */
    fun showMoreForYou() {
        val row = _forYouExtra.value
        if (row.loading) return
        forYouExtraJob = viewModelScope.launch {
            _forYouExtra.value = row.copy(loading = true)
            val result = runCatching {
                val shown = (uiState.value.forYouTitles).map { TitleKey(it.tmdbId, it.mediaType) }.toSet()
                val pager = row.pager ?: run {
                    val region = userPreferencesRepository.region.first()
                    val rank: suspend () -> List<TitleKey> = {
                        recommendationRepository.rankedExtrasForProfile(activeProfile.id, region, shown)
                    }
                    val rest = (if (refreshCoordinator != null) refreshCoordinator.runIfIdle { rank() } else rank())
                    rest?.let { ExtraPicksPager(it) }
                }
                if (pager == null) {
                    null
                } else {
                    val (keys, advanced) = pager.next(shown + dismissedKeySet())
                    ExtraRow(advanced, row.titles + resolveInOrder(keys))
                }
            }.getOrNull()
            ensureActive() // a reset while scoring must not be overwritten
            _forYouExtra.value = result ?: row
        }
    }

    private fun dismissedKeySet(): Set<TitleKey> = _dismissedKeys.value.map { TitleKey(it.first, it.second) }.toSet()

    /**
     * Fills the discover rows and regenerates this profile's shortlist. Offline-first by
     * construction for the discover half ([DiscoverRepository] serves its 24h cache without
     * touching the network when warm); the shortlist half genuinely recomputes
     * ([RecommendationRepository.refreshProfileShortlist]) every call, matching PLAN.md §4's
     * "manual pull-to-refresh does the same [as the weekly job] on demand" — cheap in practice
     * after the first run thanks to per-title TTL caching (see the class kdoc).
     */
    fun refresh() {
        loadDiscover()
        recomputeShortlist()
    }

    /**
     * PLAN.md §5d (M15): the user-initiated refresh (the Home refresh icon / retry). With a
     * [refreshCoordinator] it is a real, logged [RefreshTrigger.MANUAL] run of every profile's
     * shortlist behind the shared guard (if one is already running it simply yields -- the banner
     * is already showing), followed by reloading the discover rows, since a run invalidates their
     * cache. Without one it behaves like [refresh].
     */
    fun manualRefresh() {
        val coordinator = refreshCoordinator ?: return refresh()
        viewModelScope.launch {
            _discover.value = _discover.value.copy(isLoading = true, error = null)
            coordinator.run(RefreshTrigger.MANUAL)
            loadDiscover()
            _coldStart.value = runCatching { recommendationRepository.isColdStart(activeProfile.id) }.getOrDefault(_coldStart.value)
        }
    }

    private fun loadDiscover() {
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
    }

    /**
     * Incidental recompute of this profile's shortlist (Home opening). Runs under the refresh
     * guard when there is one: if a real refresh is in flight it is skipped, since that run
     * rewrites the same shortlist. Not logged -- the log is for refresh *runs*.
     */
    private fun recomputeShortlist() {
        viewModelScope.launch {
            runCatching {
                // PLAN.md §4b (M3j): Family is cold-start/refreshed exactly like any individual
                // profile now — the plain per-profile isColdStart/refreshProfileShortlist check,
                // called with activeProfile.id (the sentinel for Family).
                val cold = recommendationRepository.isColdStart(activeProfile.id)
                _coldStart.value = cold
                if (!cold) {
                    val region = userPreferencesRepository.region.first()
                    val recompute: suspend () -> Unit = {
                        recommendationRepository.refreshProfileShortlist(activeProfile.id, region)
                    }
                    if (refreshCoordinator != null) refreshCoordinator.runIfIdle { recompute() } else recompute()
                }
                // forYouShortlist is a live Flow off Room (observeShortlist) — the write above
                // is picked up automatically, no manual re-read needed here.
                // PLAN.md §5e: the persisted picks may have changed, so any appended extras are stale.
                forYouExtraJob?.cancel()
                _forYouExtra.value = ExtraRow()
                _forYouEligible.value = recommendationRepository.eligibleCandidateCount(activeProfile.id)
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
     * PLAN.md §5 screen 3: "long-press → dismiss ('not interested')". Updates [_dismissedKeys]
     * synchronously so the card disappears from every recommendation row this frame — see its
     * kdoc — then persists.
     *
     * PLAN.md §4c (M13 fix 2): [familyNightProfileIds] is non-null exactly when the long-press
     * came from the Family Night carousel (set from [HomeUiState.familyNightSelectedIds] at that
     * call site) — in that case the dismissal is written via
     * [RecommendationRepository.dismissAdHocFamilyNightTitle] against that exact selection's
     * ad-hoc scope, the same key [refreshFamilyShortlist]'s ad-hoc blend reads back. Null
     * everywhere else (For You/Popular/My List), where the dismissal still persists against
     * *this* profile's own scope ([activeProfile.id], which resolves to the Family sentinel when
     * Family is active) via [RecommendationRepository.dismissTitle] — unchanged from before this
     * fix, so a dismissal from those rows never leaks to a different profile's shortlist.
     */
    fun dismissTitle(tmdbId: Int, mediaType: MediaType, familyNightProfileIds: List<Long>? = null) {
        _dismissedKeys.value = _dismissedKeys.value + (tmdbId to mediaType)
        viewModelScope.launch {
            runCatching {
                if (familyNightProfileIds != null) {
                    recommendationRepository.dismissAdHocFamilyNightTitle(familyNightProfileIds, tmdbId, mediaType)
                } else {
                    recommendationRepository.dismissTitle(activeProfile.id, tmdbId, mediaType)
                }
            }
        }
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
     *
     * M11: the rule itself now lives in [FamilyBlend.isConfirmedUnderCap] — pulled out once
     * [org.seg7.familywatchlist.data.repository.RecommendationRepository]'s `scoreCandidates`
     * needed the exact same "unknown = unsafe" behaviour — this is a thin delegate kept for the
     * `TitleEntity` receiver call sites below.
     */
    private fun TitleEntity.survivesAgeCap(cap: String?): Boolean =
        FamilyBlend.isConfirmedUnderCap(certification, cap)

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

        /** PLAN.md §5d part 5: a Family Night result faster than this shows no progress UI at all. */
        const val FAMILY_NIGHT_PROGRESS_GRACE_MS = 500L
    }
}

data class HomeUiState(
    val hero: TitleEntity? = null,
    val myList: List<WatchlistItemAvailability> = emptyList(),
    val popularMovies: List<TitleEntity> = emptyList(),
    val popularTv: List<TitleEntity> = emptyList(),
    val forYouTitles: List<TitleEntity> = emptyList(),
    /** PLAN.md §5e: the For You "Show 30 more" end card; null when there is nothing more to show. */
    val forYouMore: MoreCardState? = null,
    /** PLAN.md §5e: the Family Night "Show 30 more" end card; null when exhausted or no blend yet. */
    val familyNightMore: MoreCardState? = null,
    /** True until the profile crosses PLAN.md §4's 5-event cold-start threshold. */
    val isColdStartForYou: Boolean = true,
    /** Every profile on the account — the who's-watching chip row's source list. */
    val familyNightProfiles: List<ProfileEntity> = emptyList(),
    val familyNightSelectedIds: Set<Long> = emptySet(),
    /** The ad-hoc, non-persisted blend for [familyNightSelectedIds] — only ever non-empty once 2+ are selected. */
    val familyNightTitles: List<TitleEntity> = emptyList(),
    /** M10: true while [familyNightTitles] is being (re)computed for the current selection — see [HomeViewModel]'s `_familyNightLoading` kdoc. */
    val familyNightLoading: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSubscribedServices: Boolean = false,
    /** PLAN.md §5b M3i items 5/9: the active profile's (or Family's strictest-member) age cap — null means no cap. */
    val ageRatingCap: String? = null,
    /** PLAN.md §5d part 4: For You picks not in the previous shortlist, rendered with a "New" label. */
    val newPickKeys: Set<Pair<Int, MediaType>> = emptySet(),
    /** PLAN.md §5d part 5: staged Family Night progress label; null while the result is fast or absent. */
    val familyNightProgress: String? = null,
    /** PLAN.md §5d part 2: drives the slim refresh banner. */
    val refreshState: RefreshUiState = RefreshUiState.Idle,
) {
    /** PLAN.md §4a slider 4's UI-home decision, reused verbatim: the chip row itself is only relevant with 2+ profiles on the account at all. */
    val familyNightChipsVisible: Boolean get() = familyNightProfiles.size >= 2
    /** True when there is genuinely nothing to render — drives the first-run empty state. */
    val isEmpty: Boolean
        get() = hero == null && myList.isEmpty() && popularMovies.isEmpty() && popularTv.isEmpty() && forYouTitles.isEmpty()
}
