package org.seg7.familywatchlist.data.repository

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.common.today
import org.seg7.familywatchlist.data.local.dao.RatingDao
import org.seg7.familywatchlist.data.local.dao.ShortlistDao
import org.seg7.familywatchlist.data.local.dao.TitleAttributeDao
import org.seg7.familywatchlist.data.local.dao.WatchEventDao
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.recommend.AffinityEngine
import org.seg7.familywatchlist.data.recommend.AttrKey
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.data.recommend.FamilyBlendSlider
import org.seg7.familywatchlist.data.recommend.RatedWatch
import org.seg7.familywatchlist.data.recommend.RecommenderSpec
import org.seg7.familywatchlist.data.recommend.ScoredCandidate
import org.seg7.familywatchlist.data.recommend.ScoringCandidate
import org.seg7.familywatchlist.data.recommend.ScoringWeights
import org.seg7.familywatchlist.data.recommend.Scorer
import org.seg7.familywatchlist.data.recommend.ShortlistAssembler
import org.seg7.familywatchlist.data.recommend.ShortlistConfig
import org.seg7.familywatchlist.data.recommend.ShortlistSlot
import org.seg7.familywatchlist.data.recommend.TitleKey
import org.seg7.familywatchlist.data.recommend.WatchlistSignal

/** PLAN.md §4: "FAMILY" is the persisted default scope; the who's-watching chip row's ad-hoc subsets are computed on the fly and never written here. */
const val FAMILY_SCOPE_KEY: String = "FAMILY"

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): one entry per profile (individual or the
 * Family profile, via [org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID])
 * whose weekly refresh *genuinely completed* this run — [RecommendationRepository.refreshAll]
 * only ever returns entries for profiles that finished without throwing, so a profile whose
 * refresh failed (network error, TMDB hiccup, etc.) never appears here even if its own
 * notification toggle is on. [name] is carried along so
 * [org.seg7.familywatchlist.work.RecommendationWorker] doesn't need a second lookup just to
 * build the notification text.
 */
data class ProfileRefreshResult(val profileId: Long, val name: String)

/**
 * PLAN.md §4/§4a: orchestrates the pure `data/recommend` engine against real Room/TMDB data —
 * builds each profile's affinity vector, gathers and scores the candidate pool, assembles and
 * persists the weekly shortlist, and answers the family-scope "who's watching tonight" query on
 * the fly. This is the I/O layer; all the scoring math itself lives in `data/recommend` and is
 * covered by that package's fixture tests — the tests here focus on orchestration (exclusions,
 * cold start, persistence), not re-proving the formula.
 */
class RecommendationRepository(
    private val watchEventDao: WatchEventDao,
    private val ratingDao: RatingDao,
    private val watchlistDao: WatchlistDao,
    private val titleAttributeDao: TitleAttributeDao,
    private val titleRepository: TitleRepository,
    private val discoverRepository: DiscoverRepository,
    private val providerRepository: ProviderRepository,
    private val profileRepository: ProfileRepository,
    private val profileSlidersRepository: ProfileSlidersRepository,
    private val familyProfileRepository: FamilyProfileRepository,
    private val shortlistDao: ShortlistDao,
    private val clock: AppClock,
) {
    /** PLAN.md §4: "< 5 watch events for a profile -> popular-on-your-services." */
    suspend fun isColdStart(profileId: Long): Boolean =
        watchEventDao.countForProfile(profileId) < RecommenderSpec.COLD_START_EVENT_THRESHOLD

    fun observeShortlist(weekStart: LocalDate, scopeKey: String): Flow<List<ShortlistEntryEntity>> =
        shortlistDao.observeForScope(weekStart, scopeKey)

    /** This week's Monday (PLAN.md §4: "WorkManager weekly, Monday 06:00" — the shortlist's own natural cycle boundary). */
    fun currentWeekStart(): LocalDate = weekStartFor(clock.today())

    /**
     * PLAN.md §4a's slider-4 UI-home decision (Kev, 2026-08-20): "Tune my picks"/Settings shows
     * the family-blend slider control only once the account has 2+ profiles — a single-profile
     * account never has a family scope, so the control would be meaningless clutter. The scoring
     * side of this gate lives in [refreshFamilyShortlist] itself (defense in depth); this is the
     * matching UI-side signal so the control doesn't render at all below that threshold.
     */
    fun observeFamilyBlendSliderVisible(): Flow<Boolean> =
        profileRepository.observeAll().map { it.size >= 2 }

    private fun weekStartFor(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * PLAN.md §4b (M3j): the shortlist scope key for [profileId]'s own *persisted* shortlist —
     * [FAMILY_SCOPE_KEY] for the Family sentinel (matching what
     * [org.seg7.familywatchlist.ui.home.HomeViewModel] and [reasonsForShortlistEntry] both read),
     * `profileId.toString()` for a real profile. `FAMILY_PROFILE_SENTINEL_ID.toString()` (`"-1"`)
     * is deliberately never used as a scope key — every existing Family-scope reader already
     * expects [FAMILY_SCOPE_KEY], and this keeps [refreshProfileShortlist] writing to the exact
     * scope they read from without having to change any of those call sites.
     */
    private fun scopeKeyFor(profileId: Long): String =
        if (profileId == FAMILY_PROFILE_SENTINEL_ID) FAMILY_SCOPE_KEY else profileId.toString()

    /**
     * PLAN.md §5 screen 3: "long-press → dismiss ('not interested')" — the write side of a
     * gesture that, until M4a, had a fully-built read side ([excludeDismissed], already called
     * from both [refreshProfileShortlist] and [refreshFamilyShortlist]) and no way to actually
     * reach it. Per-profile by construction: [scopeKeyFor] keys on [profileId] (or
     * [FAMILY_SCOPE_KEY] for the Family sentinel), so a dismissal from one profile can never
     * suppress the same title for another — each has its own row in `shortlist_entries`.
     *
     * Two cases, because a dismissed title may or may not already have a row this week:
     *  - **Already shortlisted** (the common "For You" case): flip its existing row's `state` to
     *    [ShortlistState.DISMISSED] via [ShortlistDao.updateState] — preserves its score/reasons
     *    rather than clobbering them, though neither is read again once dismissed.
     *  - **Not shortlisted yet** (a Popular/cold-start/Family-Night row card, which isn't backed
     *    by a `shortlist_entries` row at all): insert a fresh DISMISSED placeholder row with a
     *    zero score and empty reasons — there's nothing else to record, this row exists purely so
     *    [excludeDismissed] finds it on the next recompute.
     * Both branches write to exactly the (weekStart, scopeKey, tmdbId, mediaType) tuple
     * [excludeDismissed] reads, so a title dismissed this week is guaranteed excluded from this
     * profile's *next* refresh for the rest of the week, regardless of which row it came from.
     */
    suspend fun dismissTitle(profileId: Long, tmdbId: Int, mediaType: MediaType) {
        val weekStart = currentWeekStart()
        val scopeKey = scopeKeyFor(profileId)
        val alreadyPresent = shortlistDao.getForScope(weekStart, scopeKey)
            .any { it.tmdbId == tmdbId && it.mediaType == mediaType }
        if (alreadyPresent) {
            shortlistDao.updateState(weekStart, scopeKey, tmdbId, mediaType, ShortlistState.DISMISSED)
        } else {
            shortlistDao.upsertAll(
                listOf(
                    ShortlistEntryEntity(
                        weekStart = weekStart,
                        scopeKey = scopeKey,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        score = 0.0,
                        reasons = "[]",
                        state = ShortlistState.DISMISSED,
                    ),
                ),
            )
        }
    }

    /**
     * PLAN.md §4/§4a: (re)computes and persists one profile's personalised shortlist for the
     * current week, using that profile's own slider settings. Cold-start profiles are left
     * alone — [org.seg7.familywatchlist.ui.home.HomeViewModel] sources the existing
     * "Popular on your services" row for them instead (PLAN.md §5a, already built in M2c);
     * writing an empty/misleading personalised shortlist here would be worse than writing
     * nothing.
     *
     * PLAN.md §4b (M3j): also the Family profile's own path now, called with
     * [FAMILY_PROFILE_SENTINEL_ID] from [refreshAll] — Family has no row in the `profiles` table,
     * so "does this profile exist at all" and "what's its age cap" can't come from
     * [ProfileRepository.getById] the way a real profile's do. Both are resolved through a shared
     * branch rather than forking this function's whole body: existence via
     * [familyProfileRepository]/[profileRepository] depending on which id space [profileId] is in,
     * and the cap via [resolveAgeRatingCap] (which already special-cases the sentinel to the
     * strictest-member cap — the one and only piece of Family's identity that stays derived).
     * Everything else — vector, candidate pool, scoring, slider settings, persistence — reads and
     * writes keyed purely on [profileId] (a plain `Long`, sentinel or real, [ProfileSlidersEntity]
     * and [ShortlistEntryEntity] both have no `@ForeignKey` to `profiles`) and needs no branching
     * at all, which is what keeps this a single shared function rather than two near-duplicates.
     *
     * PLAN.md §4a slider 5 ("Suggestion count", design corrected 2026-08-20): [scored]'s size —
     * the real candidate count remaining after dedup/watched/listed/dismissed/age-cap filtering,
     * right before assembly — is persisted via [ProfileSlidersRepository.setEligibleCandidateCount]
     * on *every* call, cold-start-skip aside, so "Tune my picks" always has an up-to-date ceiling
     * for its slider without a live fetch. The *effective* target passed to [ShortlistAssembler]
     * is `min(requested, eligible)`: a thin week never overwrites what the user actually asked
     * for ([ProfileSlidersRepository.getSuggestionCount] is left untouched), so the shortlist
     * automatically grows back to the full request once the pool recovers. The ad-hoc who's-
     * watching chip row ([refreshFamilyShortlist] with `persist = false`) always uses
     * [ShortlistConfig.SPEC_DEFAULT]'s fixed 30, deliberately never threading a profile's personal
     * request (or this per-profile eligible count) in — unrelated to this function's Family path.
     */
    suspend fun refreshProfileShortlist(profileId: Long, region: String): List<ShortlistEntryEntity> {
        if (isColdStart(profileId)) return emptyList()
        val exists = if (profileId == FAMILY_PROFILE_SENTINEL_ID) {
            familyProfileRepository.get() != null
        } else {
            profileRepository.getById(profileId) != null
        }
        if (!exists) return emptyList()
        val ageRatingCap = resolveAgeRatingCap(profileId)
        val sliders = profileSlidersRepository.get(profileId)
        val today = clock.today()
        val weekStart = weekStartFor(today)
        val scopeKey = scopeKeyFor(profileId)

        val vector = buildProfileVector(profileId, sliders.halfLifeDays, today)
        val pool = gatherCandidatePool(listOf(profileId), region)
        val eligible = excludeDismissed(pool, weekStart, scopeKey)
        val scored = scoreCandidates(eligible, vector, sliders.toScoringWeights(), today.year, ageRatingCap, region)

        profileSlidersRepository.setEligibleCandidateCount(profileId, scored.size)
        val requestedCount = profileSlidersRepository.getSuggestionCount(profileId)
        val effectiveTargetSize = requestedCount.coerceAtMost(scored.size)

        val assembled = ShortlistAssembler.assemble(scored, sliders.toShortlistConfig(targetSize = effectiveTargetSize))
        val entries = persistShortlist(assembled, vector, weekStart, scopeKey)
        return entries
    }

    /**
     * The weekly WorkManager job's entry point: refreshes every individual profile's own
     * shortlist, plus the Family profile's — *if one exists*.
     *
     * **PLAN.md §4b (M3j), supersedes M3d's design below:** Family's persisted shortlist is no
     * longer a blend of its curated members' vectors — it runs through the exact same
     * [refreshProfileShortlist] pipeline every individual profile uses, called with
     * [FAMILY_PROFILE_SENTINEL_ID], built from Family's *own* logged watch events/ratings (which
     * it can now own directly — see [org.seg7.familywatchlist.ui.details.TitleDetailViewModel]
     * and [org.seg7.familywatchlist.ui.logwatch.LogWatchSheet]'s M3j changes). There is no longer
     * a separate "blend everyone under [FAMILY_SCOPE_KEY]" call here at all — Family is just
     * another entry in the same loop as every real profile, gated only on whether a Family
     * profile has been created yet (same "nothing to refresh if it doesn't exist yet" posture
     * M3d already established, just no longer implemented as a special call).
     *
     * **Resolved by Kev, 2026-08-21** (PLAN.md §4's original M3d open question): "there should be
     * one [shortlist] for every profile anyway, which would include the Family profile" — this is
     * now true in the most literal sense possible: Family runs through [refreshProfileShortlist]
     * itself, not a lookalike sibling function.
     *
     * **PLAN.md §4 "Per-profile notification control" (M3e):** each profile's (and Family's, if
     * it exists) refresh is wrapped independently — one profile throwing (network error, TMDB
     * hiccup) is caught here and does *not* abort the rest of the loop the way an unhandled
     * exception used to (that old behaviour would have silently skipped every profile still
     * queued behind the failing one, not just the failing one itself). The returned
     * [ProfileRefreshResult] list is exactly the set of profiles that genuinely finished this
     * run — [org.seg7.familywatchlist.work.RecommendationWorker] uses it, together with the
     * master/per-profile notification toggles, to decide who the weekly notification mentions;
     * a profile that failed (or, for Family, a still-cold-start Family that
     * [refreshProfileShortlist] itself skipped) never appears here regardless of its own toggle.
     *
     * [familyBlendSlider] is no longer read by this function at all — it only ever fed the old
     * blend call, which is gone. It stays as a parameter (default [FamilyBlendSlider.DEFAULT])
     * only because [org.seg7.familywatchlist.work.RecommendationWorker] still passes one through;
     * removing the parameter is a separate, non-M3j cleanup.
     */
    suspend fun refreshAll(
        region: String,
        familyBlendSlider: FamilyBlendSlider = FamilyBlendSlider.DEFAULT,
    ): List<ProfileRefreshResult> {
        val profiles = profileRepository.observeAll().first()
        val completed = mutableListOf<ProfileRefreshResult>()
        profiles.forEach { profile ->
            runCatching { refreshProfileShortlist(profile.id, region) }
                .onSuccess { completed += ProfileRefreshResult(profile.id, profile.name) }
        }
        val family = familyProfileRepository.get()
        if (family != null) {
            runCatching { refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, region) }
                .onSuccess { completed += ProfileRefreshResult(FAMILY_PROFILE_SENTINEL_ID, family.profile.name) }
        }
        return completed
    }

    /**
     * PLAN.md §4: family-scope blend for [profileIds] — the "who's watching tonight?" chip row's
     * query, computed fresh for whatever subset is currently selected. [persist] is true only for
     * the weekly job's default "everyone" run (writes [FAMILY_SCOPE_KEY]); an ad-hoc chip-row
     * subset is never written to [ShortlistEntryEntity] — see [FAMILY_SCOPE_KEY]'s kdoc.
     *
     * PLAN.md §4a's slider-4 UI-home decision (Kev, 2026-08-20): the family blend slider is only
     * shown/relevant with 2+ profiles on the account, and hiding it must never silently change
     * scoring. Enforced here, not just by the UI that hides the control: [familyBlendSlider] is
     * ignored (pinned to [FamilyBlendSlider.DEFAULT]) whenever the *whole account* — not just
     * this call's [profileIds] — has fewer than 2 profiles, so a stale/leftover non-zero stored
     * value from a since-deleted second profile can never leak into a solo-profile household's
     * scoring even if some future caller forgets to gate on the UI side.
     */
    suspend fun refreshFamilyShortlist(
        profileIds: List<Long>,
        region: String,
        familyBlendSlider: FamilyBlendSlider,
        persist: Boolean,
    ): List<ShortlistEntryEntity> {
        if (profileIds.isEmpty()) return emptyList()
        val today = clock.today()
        val weekStart = weekStartFor(today)
        val scopeKey = if (persist) FAMILY_SCOPE_KEY else adHocScopeKey(profileIds)

        val profiles = profileIds.mapNotNull { profileRepository.getById(it) }
        if (profiles.isEmpty()) return emptyList()

        val accountProfileCount = profileRepository.observeAll().first().size
        val effectiveSlider = if (accountProfileCount < 2) FamilyBlendSlider.DEFAULT else familyBlendSlider

        val vectors = profiles.map { buildProfileVector(it.id, profileSlidersRepository.get(it.id).halfLifeDays, today) }
        val blended = FamilyBlend.blendVectors(vectors, effectiveSlider.toFamilyBlendWeights())
        val strictestCap = FamilyBlend.strictestCap(profiles.map { it.ageRatingCap })

        val pool = gatherCandidatePool(profiles.map { it.id }, region)
        val eligible = excludeDismissed(pool, weekStart, scopeKey)
        val scored = scoreCandidates(eligible, blended, ScoringWeights.SPEC_DEFAULT, today.year, strictestCap, region)
        val assembled = ShortlistAssembler.assemble(scored, ShortlistConfig.SPEC_DEFAULT)

        return if (persist) {
            persistShortlist(assembled, blended, weekStart, scopeKey)
        } else {
            assembled.map { it.toEntity(weekStart, scopeKey, reasonsFor(blended, it.candidate.title)) }
        }
    }

    /** A stable, order-independent key for an ad-hoc who's-watching subset — never persisted, only used to exclude that subset's own this-session dismissals if the UI ever adds that. */
    private fun adHocScopeKey(profileIds: List<Long>): String = "AD_HOC:" + profileIds.sorted().joinToString(",")

    /**
     * PLAN.md §5 screen 4: "Because you liked …" reason line — present only when [tmdbId]/
     * [mediaType] has a current, still-SUGGESTED entry in [profileId]'s *own* persisted shortlist
     * ([scopeKeyFor]: `profileId.toString()` for a real profile, [FAMILY_SCOPE_KEY] for the
     * Family sentinel now that Family has its own persisted shortlist, PLAN.md §4b/M3j — the
     * who's-watching chip row's ad-hoc blends are still never persisted in the first place, so
     * they could never be looked up this way regardless). Returns null (render nothing) when
     * there's no such entry — reached via Search/My List/History, or a title that was suggested
     * earlier but has since been dismissed, watched, or simply didn't make this week's recompute.
     */
    suspend fun reasonsForShortlistEntry(profileId: Long, tmdbId: Int, mediaType: MediaType): List<String>? {
        val entry = shortlistDao.getSuggestedEntry(currentWeekStart(), scopeKeyFor(profileId), tmdbId, mediaType) ?: return null
        return parseReasons(entry.reasons)
    }

    /** The exact inverse of [reasonsFor]'s `Json.encodeToString(JsonArray.serializer(), ...)` output. */
    private fun parseReasons(json: String): List<String> =
        runCatching { Json.parseToJsonElement(json).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }
            .getOrDefault(emptyList())

    private suspend fun persistShortlist(
        assembled: List<ShortlistSlot>,
        vector: Map<AttrKey, Double>,
        weekStart: LocalDate,
        scopeKey: String,
    ): List<ShortlistEntryEntity> {
        val entries = assembled.map { it.toEntity(weekStart, scopeKey, reasonsFor(vector, it.candidate.title)) }
        // Clear this cycle's previously-SUGGESTED rows first — a recompute (slider change,
        // manual refresh) replaces the shortlist, it doesn't just add to it. See
        // ShortlistDao.deleteSuggestedForScope's kdoc for why DISMISSED/WATCHED rows survive.
        shortlistDao.deleteSuggestedForScope(weekStart, scopeKey)
        shortlistDao.upsertAll(entries)
        return entries
    }

    private fun ShortlistSlot.toEntity(weekStart: LocalDate, scopeKey: String, reasons: String): ShortlistEntryEntity =
        ShortlistEntryEntity(
            weekStart = weekStart,
            scopeKey = scopeKey,
            tmdbId = candidate.title.tmdbId,
            mediaType = candidate.title.mediaType,
            score = candidate.score,
            reasons = reasons,
            state = ShortlistState.SUGGESTED,
        )

    /** PLAN.md §5: "'Because you liked …' reason line" — the candidate's top 3 attributes by this vector's affinity, positive contributions only. */
    private suspend fun reasonsFor(vector: Map<AttrKey, Double>, title: TitleKey): String {
        val attrs = titleAttributeDao.getForTitle(title.tmdbId, title.mediaType)
        val names = attrs
            .mapNotNull { attr -> vector[AttrKey(attr.attrType, attr.attrId)]?.let { value -> attr.name to value } }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        return Json.encodeToString(JsonArray.serializer(), buildJsonArray { names.forEach { add(it) } })
    }

    /** PLAN.md §4: builds one profile's IDF-damped, per-type-L2-normalised affinity vector from their Room data. */
    private suspend fun buildProfileVector(profileId: Long, halfLifeDays: Double, today: LocalDate): Map<AttrKey, Double> {
        val events = watchEventDao.getForProfile(profileId)
        val ratings = ratingDao.getForProfile(profileId).associateBy { it.tmdbId to it.mediaType }
        val watches = events.map { event ->
            val attrs = titleAttributeDao.getForTitle(event.tmdbId, event.mediaType).toAttrKeys()
            RatedWatch(
                title = TitleKey(event.tmdbId, event.mediaType),
                attributes = attrs,
                watchedAt = event.watchedAt,
                rating = ratings[event.tmdbId to event.mediaType]?.value,
            )
        }
        val watchlistSignals = watchlistDao.observeByState(WatchlistState.ACTIVE).first()
            .filter { it.addedByProfileId == profileId }
            .map { entry ->
                val attrs = titleAttributeDao.getForTitle(entry.tmdbId, entry.mediaType).toAttrKeys()
                WatchlistSignal(
                    title = TitleKey(entry.tmdbId, entry.mediaType),
                    attributes = attrs,
                    addedAt = Instant.ofEpochMilli(entry.addedAt).atZone(ZoneOffset.UTC).toLocalDate(),
                )
            }
        return AffinityEngine.buildAffinityVector(watches, watchlistSignals, today, halfLifeDays)
    }

    /**
     * PLAN.md §4's candidate pool: `/discover` on subscribed GB providers (top ~120 per media
     * type, [CANDIDATE_PAGES] pages of 20) union `/recommendations` for the top-5 UP-rated
     * titles across the given profiles. Excludes titles already watched by any of [profileIds]
     * and anything currently on the shared ACTIVE Want-to-Watch list (PLAN.md §4: "Listed titles
     * are excluded from shortlist candidates — you already know about them").
     */
    private suspend fun gatherCandidatePool(profileIds: List<Long>, region: String): List<TitleKey> {
        val subscribed = providerRepository.getSubscribedIds()
        val movieStubs = (1..CANDIDATE_PAGES).flatMap { page -> discoverRepository.discoverMovies(subscribed, region, page) }
        val tvStubs = (1..CANDIDATE_PAGES).flatMap { page -> discoverRepository.discoverTv(subscribed, region, page) }

        val topUpRated = profileIds
            .flatMap { ratingDao.getForProfile(it) }
            .filter { it.value == RatingValue.UP }
            .sortedByDescending { it.ratedAt }
            .distinctBy { it.tmdbId to it.mediaType }
            .take(TOP_UP_RATED_FOR_RECOMMENDATIONS)
        val recommendationStubs = topUpRated.flatMap { rating ->
            when (rating.mediaType) {
                MediaType.MOVIE -> discoverRepository.movieRecommendations(rating.tmdbId)
                MediaType.TV -> discoverRepository.tvRecommendations(rating.tmdbId)
            }
        }

        val watched = profileIds.flatMap { watchEventDao.getForProfile(it) }
            .map { TitleKey(it.tmdbId, it.mediaType) }
            .toHashSet()
        val listed = watchlistDao.observeByState(WatchlistState.ACTIVE).first()
            .map { TitleKey(it.tmdbId, it.mediaType) }
            .toHashSet()

        return (movieStubs + tvStubs + recommendationStubs)
            .map { TitleKey(it.tmdbId, it.mediaType) }
            .distinct()
            .filterNot { it in watched || it in listed }
    }

    private suspend fun excludeDismissed(pool: List<TitleKey>, weekStart: LocalDate, scopeKey: String): List<TitleKey> {
        val dismissed = shortlistDao.getForScope(weekStart, scopeKey)
            .filter { it.state == ShortlistState.DISMISSED }
            .map { TitleKey(it.tmdbId, it.mediaType) }
            .toHashSet()
        return pool.filterNot { it in dismissed }
    }

    /**
     * PLAN.md §4: detail-fetches (offline-first via [TitleRepository.ensureFresh]) each
     * candidate, drops anything over [ageCap], and scores the rest against [vector].
     */
    private suspend fun scoreCandidates(
        pool: List<TitleKey>,
        vector: Map<AttrKey, Double>,
        weights: ScoringWeights,
        todayYear: Int,
        ageCap: String?,
        region: String,
    ): List<ScoredCandidate> {
        return pool.mapNotNull { key ->
            val title = titleRepository.ensureFresh(key.tmdbId, key.mediaType, region)
            if (FamilyBlend.isOverCap(title.certification, ageCap)) return@mapNotNull null
            val attrEntities = titleAttributeDao.getForTitle(key.tmdbId, key.mediaType)
            val attrs = attrEntities.toAttrKeys()
            val candidate = ScoringCandidate(key, attrs, title.voteAverage, title.voteCount, title.year)
            ScoredCandidate(
                title = key,
                score = Scorer.score(vector, candidate, todayYear, weights),
                quality = Scorer.tmdbQuality(title.voteAverage, title.voteCount),
                primaryGenreId = attrEntities.firstOrNull { it.attrType == AttrType.GENRE }?.attrId,
            )
        }
    }

    /**
     * PLAN.md §4/§8 (M3g safety fix): resolves the effective age cap for [profileId] — a real
     * profile's own [org.seg7.familywatchlist.data.local.entity.ProfileEntity.ageRatingCap], or
     * (when [profileId] is [FAMILY_PROFILE_SENTINEL_ID]) the strictest cap among the Family
     * profile's *curated* members via [FamilyBlend.strictestCap] — the exact same rule
     * [refreshFamilyShortlist] already applies to its own candidates. Exposed so every other
     * title-surfacing path (`DiscoverRepository`'s results via `HomeViewModel`, `SearchRepository`)
     * can gate on the same rule the real recommender uses without re-deriving it. Null means
     * "don't filter" — no Family profile yet, or a real profile with no cap set.
     */
    suspend fun resolveAgeRatingCap(profileId: Long): String? =
        if (profileId == FAMILY_PROFILE_SENTINEL_ID) {
            familyProfileRepository.get()?.let { family -> FamilyBlend.strictestCap(family.members.map { it.ageRatingCap }) }
        } else {
            profileRepository.getById(profileId)?.ageRatingCap
        }

    private fun List<TitleAttributeEntity>.toAttrKeys(): List<AttrKey> = map { AttrKey(it.attrType, it.attrId) }

    companion object {
        /** PLAN.md §4: "top ~120 by popularity per media type" — 6 pages of 20. */
        const val CANDIDATE_PAGES: Int = 6
        const val TOP_UP_RATED_FOR_RECOMMENDATIONS: Int = 5
    }
}
