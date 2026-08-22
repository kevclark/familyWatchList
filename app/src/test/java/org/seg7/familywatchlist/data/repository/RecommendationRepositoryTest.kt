package org.seg7.familywatchlist.data.repository

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.recommend.FamilyBlendSlider
import org.seg7.familywatchlist.data.recommend.RecommenderSpec
import org.seg7.familywatchlist.data.remote.AuthInterceptor
import org.seg7.familywatchlist.data.remote.ThrottleInterceptor
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §4/§4a's orchestration layer — exclusions (watched/listed/dismissed/age-cap), cold
 * start, and persistence. Deliberately not re-proving the scoring math itself (that's
 * `data/recommend`'s job, covered by its own fixture tests); a zero-subscribed-provider setup
 * skips the `/discover` pages entirely (PLAN.md §7 M2e precedent — no network call at all with
 * nothing subscribed) so each test controls its candidate pool purely through one
 * `/recommendations` response, keeping the MockWebServer scripting small and exact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecommendationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var repo: RecommendationRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileSlidersRepository: ProfileSlidersRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository

    private val today: LocalDate = LocalDate.of(2026, 8, 20)

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        // A much higher per-second cap than PLAN.md §3's real 4 req/s: this suite's suggestion-
        // count tests deliberately detail-fetch a large (>30) candidate pool to prove a per-profile
        // override actually shrinks the assembled shortlist below the fixed 30 default (rather than
        // silently still landing on 30) — the real throttle would turn that into tens of seconds of
        // real Thread.sleep for no correctness benefit; ThrottleInterceptor itself has its own
        // dedicated test (ThrottleInterceptorTest) so nothing here is un-tested by relaxing it.
        val api = TmdbClient.create(
            baseUrl = server.url("/").toString(),
            accessToken = { "t" },
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor { "t" })
                .addInterceptor(ThrottleInterceptor(maxRequestsPerSecond = 1000))
                .build(),
        )

        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao())
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)

        repo = RecommendationRepository(
            watchEventDao = db.watchEventDao(),
            ratingDao = db.ratingDao(),
            watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(),
            titleRepository = titleRepository,
            discoverRepository = discoverRepository,
            providerRepository = providerRepository,
            profileRepository = profileRepository,
            profileSlidersRepository = profileSlidersRepository,
            familyProfileRepository = familyProfileRepository,
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    /**
     * Seeds >= COLD_START_EVENT_THRESHOLD watch events, one rated UP so a /recommendations call
     * fires. Titles 1-4 are tagged Comedy(35), title 5 is tagged Drama(18) — a *mix*, not all the
     * same genre, so PLAN.md §4's IDF damping (which fully zeroes an attribute present on every
     * single watched title) doesn't wash Comedy's affinity out to exactly 0 the way a
     * single-genre fixture would.
     */
    private suspend fun seedWarmProfile(ageRatingCap: String? = null, name: String = "Kev"): Long {
        val id = profileRepository.addProfile(name, "avatar", ageRatingCap).getOrThrow()
        listOf(1, 2, 3, 4).forEach { tmdbId ->
            db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(tmdbId, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null)))
            db.watchEventDao().logWatch(
                WatchEventEntity(tmdbId = tmdbId, mediaType = MediaType.MOVIE, watchedAt = today.minusDays(tmdbId.toLong()), note = null),
                listOf(id),
            )
        }
        db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(5, MediaType.MOVIE, AttrType.GENRE, 18, "Drama", null)))
        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 5, mediaType = MediaType.MOVIE, watchedAt = today.minusDays(5), note = null),
            listOf(id),
        )
        db.ratingDao().upsert(RatingEntity(id, 1, MediaType.MOVIE, RatingValue.UP, clock.current))
        return id
    }

    /**
     * PLAN.md §4b (M3j): the Family sentinel's own equivalent of [seedWarmProfile] — logs events
     * directly against [FAMILY_PROFILE_SENTINEL_ID] (never a real profile id), proving Family can
     * genuinely own its own watch/rating history now. Deliberately uses a *distinct* tmdbId range
     * (501-505) from [seedWarmProfile]'s (1-5) so a test can seed both a warm real profile and a
     * warm Family and still tell their two candidate pools apart by which `/recommendations` id
     * fires — title 501's UP rating drives Family's own `/recommendations` call.
     */
    private suspend fun seedWarmFamily() {
        listOf(501, 502, 503, 504).forEach { tmdbId ->
            db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(tmdbId, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null)))
            db.watchEventDao().logWatch(
                WatchEventEntity(tmdbId = tmdbId, mediaType = MediaType.MOVIE, watchedAt = today.minusDays(tmdbId.toLong()), note = null),
                listOf(FAMILY_PROFILE_SENTINEL_ID),
            )
        }
        db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(505, MediaType.MOVIE, AttrType.GENRE, 18, "Drama", null)))
        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 505, mediaType = MediaType.MOVIE, watchedAt = today.minusDays(5), note = null),
            listOf(FAMILY_PROFILE_SENTINEL_ID),
        )
        db.ratingDao().upsert(RatingEntity(FAMILY_PROFILE_SENTINEL_ID, 501, MediaType.MOVIE, RatingValue.UP, clock.current))
    }

    @Test
    fun `cold start profiles are skipped entirely — no network, no persisted shortlist`() = runTest {
        val id = profileRepository.addProfile("New", "avatar", null).getOrThrow()
        // Only 3 events — below RecommenderSpec.COLD_START_EVENT_THRESHOLD (5).
        listOf(1, 2, 3).forEach { tmdbId ->
            db.watchEventDao().logWatch(WatchEventEntity(tmdbId = tmdbId, mediaType = MediaType.MOVIE, watchedAt = today, note = null), listOf(id))
        }

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a candidate already watched by the profile is excluded before it ever reaches the network`() = runTest {
        val id = seedWarmProfile()
        db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(999, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null)))
        db.watchEventDao().logWatch(WatchEventEntity(tmdbId = 999, mediaType = MediaType.MOVIE, watchedAt = today, note = null), listOf(id))
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Already Watched")))

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        assertEquals(1, server.requestCount) // the /recommendations call only — no detail fetch for an excluded candidate
    }

    @Test
    fun `a candidate on the shared active watchlist is excluded`() = runTest {
        val id = seedWarmProfile()
        db.watchlistDao().upsert(WatchlistEntryEntity(999, MediaType.MOVIE, addedByProfileId = id, addedAt = clock.current, state = WatchlistState.ACTIVE))
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Already Listed")))

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a candidate DISMISSED earlier this cycle is excluded`() = runTest {
        val id = seedWarmProfile()
        val weekStart = repo.currentWeekStart()
        db.shortlistDao().upsertAll(
            listOf(ShortlistEntryEntity(weekStart, id.toString(), 999, MediaType.MOVIE, score = 0.0, reasons = "[]", state = ShortlistState.DISMISSED))
        )
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Dismissed This Cycle")))

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        // The pre-existing DISMISSED row survives untouched — this profile's scope still has exactly it.
        val stored = db.shortlistDao().getForScope(weekStart, id.toString())
        assertEquals(1, stored.size)
        assertEquals(ShortlistState.DISMISSED, stored.single().state)
    }

    /**
     * PLAN.md §5 screen 3's "long-press → dismiss ('not interested')" write path
     * ([RecommendationRepository.dismissTitle]), proven end-to-end against the read path
     * ([excludeDismissed], already covered above for a row seeded directly) and proven per-profile:
     * a dismissal from one profile's own scope never suppresses the same title for a different
     * profile's own refresh.
     */
    @Test
    fun `dismissTitle excludes a candidate from that profile's next refresh, but not from a different profile's`() = runTest {
        val dismisser = seedWarmProfile(name = "Dismisser")
        val other = seedWarmProfile(name = "Other")

        repo.dismissTitle(dismisser, 999, MediaType.MOVIE)

        // The dismisser's own refresh: /recommendations only, no detail fetch — excluded before
        // scoring, exactly like the pre-seeded-DISMISSED-row test above.
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Dismissed By Me")))
        val dismisserEntries = repo.refreshProfileShortlist(dismisser, region = "GB")
        assertEquals(emptyList<ShortlistEntryEntity>(), dismisserEntries)
        assertEquals(1, server.requestCount)

        // The other profile's refresh: the same candidate is genuinely eligible and scores —
        // this profile's own scope was never touched by the dismissal above. No second
        // /recommendations response to enqueue: both profiles rated the same tmdbId (1) UP
        // ([seedWarmProfile]'s fixture), and DiscoverRepository.movieRecommendations caches by
        // tmdbId (not by profile) — the dismisser's refresh above already warmed that cache
        // entry, so only the detail fetch for 999 hits the network here.
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Not Dismissed By Other", genreId = 35, genreName = "Comedy", certification = "PG")))
        val otherEntries = repo.refreshProfileShortlist(other, region = "GB")
        assertEquals(listOf(999), otherEntries.map { it.tmdbId })

        val weekStart = repo.currentWeekStart()
        assertEquals(ShortlistState.DISMISSED, db.shortlistDao().getForScope(weekStart, dismisser.toString()).single { it.tmdbId == 999 }.state)
        assertEquals(ShortlistState.SUGGESTED, db.shortlistDao().getForScope(weekStart, other.toString()).single { it.tmdbId == 999 }.state)
    }

    /**
     * The other of [RecommendationRepository.dismissTitle]'s two write branches — a title that
     * already has a live SUGGESTED row this week (the common "dismiss straight off a For You
     * card" case) gets that row flipped to DISMISSED in place, rather than a second row being
     * inserted alongside it.
     */
    @Test
    fun `dismissTitle flips an already-SUGGESTED row rather than duplicating it`() = runTest {
        val id = seedWarmProfile()
        val weekStart = repo.currentWeekStart()
        db.shortlistDao().upsertAll(
            listOf(ShortlistEntryEntity(weekStart, id.toString(), 999, MediaType.MOVIE, score = 4.2, reasons = "[\"Comedy\"]", state = ShortlistState.SUGGESTED)),
        )

        repo.dismissTitle(id, 999, MediaType.MOVIE)

        val stored = db.shortlistDao().getForScope(weekStart, id.toString())
        assertEquals(1, stored.size)
        assertEquals(ShortlistState.DISMISSED, stored.single().state)
    }

    @Test
    fun `a candidate over the profile's age cap is excluded after its detail is fetched`() = runTest {
        val id = seedWarmProfile(ageRatingCap = "12")
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Too Old")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Too Old", genreId = 35, genreName = "Comedy", certification = "18")))

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        assertEquals(2, server.requestCount) // recommendations + the one detail fetch needed to learn the certification
    }

    @Test
    fun `a matching, eligible candidate is scored, assembled, and persisted with a reason`() = runTest {
        val id = seedWarmProfile()
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Great Match")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Great Match", genreId = 35, genreName = "Comedy", certification = "PG")))

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(999, entry.tmdbId)
        assertEquals(MediaType.MOVIE, entry.mediaType)
        assertEquals(ShortlistState.SUGGESTED, entry.state)
        assertTrue("expected a positive score, was ${entry.score}", entry.score > 0.0)
        assertTrue("expected 'Comedy' to be cited as a reason, was ${entry.reasons}", entry.reasons.contains("Comedy"))

        val weekStart = repo.currentWeekStart()
        val persisted = db.shortlistDao().getForScope(weekStart, id.toString())
        assertEquals(listOf(999), persisted.map { it.tmdbId })
    }

    /**
     * PLAN.md §4b (M3j): [RecommendationRepository.refreshProfileShortlist] called directly with
     * [FAMILY_PROFILE_SENTINEL_ID] — the exact call [RecommendationRepository.refreshAll] now
     * makes for Family — must behave identically to a real profile's own call: cold-start skip
     * (no network, nothing persisted) when Family has fewer than
     * [RecommenderSpec.COLD_START_EVENT_THRESHOLD] events *of its own*, warm scoring/persistence
     * once it has enough. Both proven here directly against the function, not just observed
     * indirectly through `refreshAll`.
     */
    @Test
    fun `refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID) is cold-start skipped below its own event threshold`() = runTest {
        val m1 = profileRepository.addProfile("M1", "avatar", null).getOrThrow()
        val m2 = profileRepository.addProfile("M2", "avatar", null).getOrThrow()
        familyProfileRepository.save("Family", "avatar", listOf(m1, m2)).getOrThrow()
        // Only 3 events tagged to the sentinel — below the threshold.
        listOf(501, 502, 503).forEach { tmdbId ->
            db.watchEventDao().logWatch(
                WatchEventEntity(tmdbId = tmdbId, mediaType = MediaType.MOVIE, watchedAt = today, note = null),
                listOf(FAMILY_PROFILE_SENTINEL_ID),
            )
        }

        val entries = repo.refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID) scores and persists once Family itself is warm`() = runTest {
        val m1 = profileRepository.addProfile("M1", "avatar", null).getOrThrow()
        val m2 = profileRepository.addProfile("M2", "avatar", null).getOrThrow()
        familyProfileRepository.save("Family", "avatar", listOf(m1, m2)).getOrThrow()
        seedWarmFamily()
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 7000, title = "Family's Own Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 7000, title = "Family's Own Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        val entries = repo.refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, region = "GB")

        assertEquals(1, entries.size)
        assertEquals(7000, entries.single().tmdbId)
        val weekStart = repo.currentWeekStart()
        assertEquals(listOf(7000), db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY).map { it.tmdbId })
    }

    /**
     * PLAN.md §4b (M3j): no `family_profile` row persisted yet is treated exactly like "no such
     * profile" for a real id — [refreshProfileShortlist]'s existence check resolves through
     * [FamilyProfileRepository] for the sentinel rather than [ProfileRepository], and must still
     * bail out cleanly (no crash, nothing persisted) rather than assuming a Family profile always
     * exists.
     */
    @Test
    fun `refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID) is a no-op when no Family profile has been created yet`() = runTest {
        // Even with 5+ events seeded directly against the sentinel (so cold-start alone wouldn't
        // explain an empty result), no family_profile row exists at all.
        seedWarmFamily()

        val entries = repo.refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
        assertEquals(0, server.requestCount)
    }

    /**
     * PLAN.md §4b (M3j): Family's age cap stays the one and only *derived* piece of its identity
     * — resolved via [RecommendationRepository.resolveAgeRatingCap] (strictest among curated
     * members), not a cap of its own. Proven end-to-end through `refreshProfileShortlist` itself:
     * a curated member capped at "12" must cause an 18-rated candidate to be excluded from
     * Family's own persisted shortlist, exactly like [RecommendationRepository.refreshFamilyShortlist]
     * already proved for the old blend path.
     */
    @Test
    fun `refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID) excludes a candidate over the strictest curated member's age cap`() = runTest {
        val cappedMember = profileRepository.addProfile("Kid", "avatar", ageRatingCap = "12").getOrThrow()
        val uncappedMember = profileRepository.addProfile("Adult", "avatar", ageRatingCap = null).getOrThrow()
        familyProfileRepository.save("Family", "avatar", listOf(cappedMember, uncappedMember)).getOrThrow()
        seedWarmFamily()
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 7000, title = "Too Old For The Kid")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 7000, title = "Too Old For The Kid", genreId = 35, genreName = "Comedy", certification = "18")))

        val entries = repo.refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, region = "GB")

        assertEquals(emptyList<ShortlistEntryEntity>(), entries)
    }

    /**
     * Regression test for a real bug caught during this milestone's live emulator verification:
     * moving a "Tune my picks" slider grew the persisted shortlist from 8 to 9 rows instead of
     * replacing it — `upsertAll` only ever adds/updates rows for tmdbIds present in the new
     * list, so a candidate that scored well last time but doesn't make this recompute's cut
     * lingered forever. Fixed by `ShortlistDao.deleteSuggestedForScope` clearing this cycle's
     * SUGGESTED rows before the new set is written. A stale row is seeded directly here (rather
     * than via two live recomputes) to sidestep `DiscoverRepository`'s legitimate 24h
     * `/recommendations` cache, which would otherwise serve the first call's candidate again on
     * the second call regardless of this bug.
     */
    @Test
    fun `a recompute clears a previously-SUGGESTED candidate that no longer makes the cut`() = runTest {
        val id = seedWarmProfile()
        val weekStart = repo.currentWeekStart()
        // A stale leftover from an earlier computation, for a candidate that will not appear
        // in this cycle's pool at all.
        db.shortlistDao().upsertAll(
            listOf(ShortlistEntryEntity(weekStart, id.toString(), 777, MediaType.MOVIE, score = 0.9, reasons = "[]", state = ShortlistState.SUGGESTED))
        )
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Fresh Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Fresh Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        repo.refreshProfileShortlist(id, region = "GB")

        val persisted = db.shortlistDao().getForScope(weekStart, id.toString())
        assertEquals(listOf(999), persisted.map { it.tmdbId })
    }

    @Test
    fun `refreshFamilyShortlist with persist=true writes the FAMILY scope`() = runTest {
        val a = seedWarmProfile()
        val b = seedWarmProfile()
        // Both profiles rated the same title (tmdbId=1) UP via seedWarmProfile — the pool's
        // top-UP-rated gathering de-duplicates by title, so exactly one /recommendations call
        // fires here, not one per profile.
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Family Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Family Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        val entries = repo.refreshFamilyShortlist(listOf(a, b), region = "GB", FamilyBlendSlider.DEFAULT, persist = true)

        assertEquals(1, entries.size)
        val weekStart = repo.currentWeekStart()
        val persisted = db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY)
        assertEquals(listOf(999), persisted.map { it.tmdbId })
    }

    @Test
    fun `refreshFamilyShortlist with persist=false (who's-watching chip subset) computes but never writes to Room`() = runTest {
        val a = seedWarmProfile()
        val b = seedWarmProfile()
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Ad Hoc Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Ad Hoc Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        val entries = repo.refreshFamilyShortlist(listOf(a, b), region = "GB", FamilyBlendSlider.DEFAULT, persist = false)

        assertEquals(1, entries.size)
        val weekStart = repo.currentWeekStart()
        assertEquals(emptyList<ShortlistEntryEntity>(), db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY))
    }

    /**
     * PLAN.md §4a's slider-4 UI-home decision (Kev, 2026-08-20): the family blend control is only
     * shown once the account has 2+ profiles. This is the observable contract "Tune my picks"/
     * Settings gates its visibility on; [RecommendationRepository.refreshFamilyShortlist] itself
     * also pins to [FamilyBlendSlider.DEFAULT] below that threshold as defense in depth (see its
     * kdoc) — not separately re-verified end-to-end here since a blend of fewer than 2 distinct
     * profile vectors is mean==min (weight-invariant) regardless of which slider value is passed.
     */
    @Test
    fun `observeFamilyBlendSliderVisible is false with one profile, true once a second exists`() = runTest {
        seedWarmProfile()

        assertEquals(false, repo.observeFamilyBlendSliderVisible().first())

        seedWarmProfile()

        assertEquals(true, repo.observeFamilyBlendSliderVisible().first())
    }

    /**
     * PLAN.md §4a slider 5 ("Suggestion count"): proves the per-profile *requested* count
     * actually threads into the real [ShortlistAssembler] run, not just into storage. A
     * 35-candidate pool (deliberately > the fixed 30 default) is the point — if
     * [RecommendationRepository.refreshProfileShortlist] silently ignored the per-profile request
     * and kept using [RecommenderSpec.SHORTLIST_TARGET_SIZE], this would land on 30, not the
     * requested 10; a smaller pool couldn't distinguish "correctly capped at 10" from "capped by
     * an undersized pool" the same way. (This case is entirely below the real eligible ceiling —
     * min(10, 35) = 10 — so the min(requested, eligible) clamp itself is a no-op here; that
     * clamping logic gets its own dedicated test below.)
     */
    @Test
    fun `a profile's requested suggestion count overrides the fixed 30 default for that profile's own shortlist`() = runTest {
        val id = seedWarmProfile()
        profileSlidersRepository.setSuggestionCount(id, 10)
        val candidateIds = (1000 until 1035).toList() // 35 candidates, comfortably above the fixed default of 30
        server.enqueue(MockResponse(body = recommendationsJsonMulti(candidateIds)))
        candidateIds.forEach { cid ->
            server.enqueue(MockResponse(body = movieDetailJson(id = cid, title = "Candidate $cid", genreId = 35, genreName = "Comedy", certification = "PG")))
        }

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(10, entries.size)
    }

    /**
     * PLAN.md §4a slider 5 (design corrected 2026-08-20): `refreshProfileShortlist` persists the
     * *real* eligible-candidate count on every refresh — [scored]'s size, post dedup/watched/
     * listed/dismissed/age-cap filtering — so "Tune my picks" always has a fresh, real ceiling
     * for its slider's max without a live fetch.
     */
    @Test
    fun `refreshProfileShortlist persists the real eligible-candidate count on every refresh`() = runTest {
        val id = seedWarmProfile()
        val candidateIds = (3000 until 3010).toList() // exactly 10 eligible candidates this week
        server.enqueue(MockResponse(body = recommendationsJsonMulti(candidateIds)))
        candidateIds.forEach { cid ->
            server.enqueue(MockResponse(body = movieDetailJson(id = cid, title = "Candidate $cid", genreId = 35, genreName = "Comedy", certification = "PG")))
        }

        repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(10, profileSlidersRepository.getEligibleCandidateCount(id))
    }

    /**
     * PLAN.md §4a slider 5's core new mechanism: "store what the user asked for separately from
     * what they get." A requested count above this week's real pool must clamp the *effective*
     * target (what's actually assembled/persisted) without silently overwriting the stored
     * request — the request survives a thin week untouched.
     */
    @Test
    fun `a requested count above the real eligible pool is clamped, but the stored request survives untouched`() = runTest {
        val id = seedWarmProfile()
        profileSlidersRepository.setSuggestionCount(id, 30) // the spec default request
        val candidateIds = (4000 until 4008).toList() // only 8 eligible candidates this thin week
        server.enqueue(MockResponse(body = recommendationsJsonMulti(candidateIds)))
        candidateIds.forEach { cid ->
            server.enqueue(MockResponse(body = movieDetailJson(id = cid, title = "Candidate $cid", genreId = 35, genreName = "Comedy", certification = "PG")))
        }

        val entries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(8, entries.size) // clamped to the real pool, not the requested 30
        assertEquals(30, profileSlidersRepository.getSuggestionCount(id)) // the original request is untouched
        assertEquals(8, profileSlidersRepository.getEligibleCandidateCount(id))
    }

    /**
     * PLAN.md §4a slider 5: "if the pool recovers next week, they're back to their full chosen
     * count automatically, no re-entry needed." Two sequential refreshes for the same profile —
     * a thin week (8 eligible), then (after advancing the clock past both the `/recommendations`
     * 24h cache TTL and into a new ISO week, so the second refresh genuinely re-fetches rather
     * than serving cached candidates) a recovered week (35 eligible) — prove the *stored* request
     * of 30 is honoured in full the moment the pool allows it again, with no user action between
     * the two refreshes.
     */
    @Test
    fun `the effective target recovers automatically once the pool grows back, with no re-entry needed`() = runTest {
        val id = seedWarmProfile()
        profileSlidersRepository.setSuggestionCount(id, 30)

        val thinPool = (5000 until 5008).toList() // 8 eligible
        server.enqueue(MockResponse(body = recommendationsJsonMulti(thinPool)))
        thinPool.forEach { cid ->
            server.enqueue(MockResponse(body = movieDetailJson(id = cid, title = "Candidate $cid", genreId = 35, genreName = "Comedy", certification = "PG")))
        }
        val thinWeekEntries = repo.refreshProfileShortlist(id, region = "GB")
        assertEquals(8, thinWeekEntries.size)
        assertEquals(30, profileSlidersRepository.getSuggestionCount(id))

        clock.advanceBy(Duration.ofDays(8).toMillis()) // past the 24h /recommendations cache TTL and into a new week

        val recoveredPool = (6000 until 6035).toList() // 35 eligible, comfortably above the requested 30
        server.enqueue(MockResponse(body = recommendationsJsonMulti(recoveredPool)))
        recoveredPool.forEach { cid ->
            server.enqueue(MockResponse(body = movieDetailJson(id = cid, title = "Candidate $cid", genreId = 35, genreName = "Comedy", certification = "PG")))
        }
        val recoveredWeekEntries = repo.refreshProfileShortlist(id, region = "GB")

        assertEquals(30, recoveredWeekEntries.size) // back to the full original request automatically
    }

    /**
     * PLAN.md §4a slider 5's explicit scope boundary: "Family-scope shortlists stay at the fixed
     * default (30) — tying 'how many' to any one profile's personal preference doesn't make sense
     * for a blended family list." Profile [a] has a personal override of 10, but the family blend
     * (which includes [a]) must still land on the fixed default, proving the override never leaks
     * into [RecommendationRepository.refreshFamilyShortlist].
     */
    @Test
    fun `refreshFamilyShortlist ignores a member profile's personal suggestion count and stays at the fixed 30 default`() = runTest {
        val a = seedWarmProfile()
        val b = seedWarmProfile()
        profileSlidersRepository.setSuggestionCount(a, 10)
        // Both profiles rated the same title (tmdbId=1) UP via seedWarmProfile, so exactly one
        // /recommendations call fires (same de-dup precedent as the persist=true test above).
        val candidateIds = (2000 until 2035).toList() // 35 candidates, comfortably above the fixed default of 30
        server.enqueue(MockResponse(body = recommendationsJsonMulti(candidateIds)))
        candidateIds.forEach { cid ->
            server.enqueue(MockResponse(body = movieDetailJson(id = cid, title = "Candidate $cid", genreId = 35, genreName = "Comedy", certification = "PG")))
        }

        val entries = repo.refreshFamilyShortlist(listOf(a, b), region = "GB", FamilyBlendSlider.DEFAULT, persist = true)

        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, entries.size)
    }

    /**
     * PLAN.md §4's M3d open question on [RecommendationRepository.refreshAll], resolved by Kev
     * 2026-08-21: refresh every profile, plus the Family profile's *own real curated membership*
     * (never "every profile on the account"). Proven concretely, not just by absence of a bug:
     * `c` is a genuine third profile on the account with its own distinct UP rating (title 6,
     * driving candidate 4000) that neither `a` nor `b` (the Family profile's actual two members)
     * shares — if `refreshAll` still blended "everyone" the old way, candidate 4000 would leak
     * into the FAMILY scope. It never appears there; only 999 (from `a`/`b`'s shared rating) does.
     */
    /**
     * PLAN.md §4b (M3j), supersedes the M3d blend design tested here previously: `refreshAll`'s
     * Family entry now runs through the exact same [RecommendationRepository.refreshProfileShortlist]
     * pipeline every individual profile uses, called with [FAMILY_PROFILE_SENTINEL_ID] — its
     * *own* logged history, never a blend of its curated members' vectors. Proven concretely: `a`
     * and `b` (Family's curated members) share a rating that drives candidate 999, while Family
     * itself has its own distinct rating ([seedWarmFamily]) that drives a different candidate
     * (7000) — the FAMILY scope must see only 7000, never 999, proving Family's shortlist is
     * genuinely independent of its members' history now, not a blend over it.
     */
    @Test
    fun `refreshAll's Family entry refreshes Family's own shortlist from its own history, never a blend of its members`() = runTest {
        val a = seedWarmProfile()
        val b = seedWarmProfile()
        familyProfileRepository.save("Family", "avatar", listOf(a, b)).getOrThrow()
        seedWarmFamily()

        // a's refresh: /recommendations(1) -> 999, then a detail fetch for 999.
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Member Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Member Pick", genreId = 35, genreName = "Comedy", certification = "PG")))
        // b's refresh: same rating (title 1) as a -> both /recommendations(1) and title 999's
        // detail are already cached (24h TTL / 30-day TTL respectively) -- no further requests.
        // Family's own refresh: its own distinct rating (title 501) -> a genuinely new
        // /recommendations call and detail fetch, entirely separate from a/b's candidate pool.
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 7000, title = "Family's Own Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 7000, title = "Family's Own Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        repo.refreshAll(region = "GB")

        val weekStart = repo.currentWeekStart()
        assertEquals(listOf(999), db.shortlistDao().getForScope(weekStart, a.toString()).map { it.tmdbId })
        assertEquals(listOf(999), db.shortlistDao().getForScope(weekStart, b.toString()).map { it.tmdbId })
        assertEquals(
            "Family's own persisted shortlist must reflect its own logged history (candidate 7000), never a/b's shared candidate 999",
            listOf(7000),
            db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY).map { it.tmdbId },
        )
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `refreshAll with no Family profile created yet refreshes profiles only -- no 'blend everyone' fallback`() = runTest {
        val a = seedWarmProfile()
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Solo Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Solo Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        repo.refreshAll(region = "GB")

        val weekStart = repo.currentWeekStart()
        assertEquals(listOf(999), db.shortlistDao().getForScope(weekStart, a.toString()).map { it.tmdbId })
        assertEquals(emptyList<ShortlistEntryEntity>(), db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY))
    }

    /**
     * PLAN.md §4b (M3j): supersedes the old M3d "skip Family below its 2-member floor" behaviour
     * tested here previously — that gate only ever made sense for a *blend*, which needs 2+
     * vectors to mean anything. Family's shortlist is now sourced from its own history, so
     * `refreshAll` no longer checks member count at all before refreshing it: even after cascading
     * down to a single curated member, a Family profile that's *itself* warm still gets its own
     * persisted shortlist.
     */
    @Test
    fun `refreshAll refreshes Family's own shortlist even after it drops below the old 2-member floor`() = runTest {
        val a = seedWarmProfile()
        val b = seedWarmProfile()
        familyProfileRepository.save("Family", "avatar", listOf(a, b)).getOrThrow()
        profileRepository.delete(profileRepository.getById(b)!!)
        seedWarmFamily()

        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Solo Member Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Solo Member Pick", genreId = 35, genreName = "Comedy", certification = "PG")))
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 7000, title = "Family's Own Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 7000, title = "Family's Own Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        repo.refreshAll(region = "GB")

        val weekStart = repo.currentWeekStart()
        assertEquals(
            "Family must still get its own persisted shortlist despite having only 1 curated member left",
            listOf(7000),
            db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY).map { it.tmdbId },
        )
    }

    /**
     * PLAN.md §4 "Per-profile notification control" (M3e): [RecommendationRepository.refreshAll]'s
     * return value is what [org.seg7.familywatchlist.work.RecommendationWorker] uses to decide
     * who this week's notification can even mention — proves both the individual profiles and
     * the Family profile (keyed by [FAMILY_PROFILE_SENTINEL_ID], not its own `family_profile.id`)
     * come back with their real display names.
     */
    @Test
    fun `refreshAll's returned list carries every completed profile plus Family, keyed by the sentinel id`() = runTest {
        val a = seedWarmProfile(name = "Kev")
        val b = seedWarmProfile(name = "Sam")
        familyProfileRepository.save("The Family", "avatar", listOf(a, b)).getOrThrow()

        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Shared Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Shared Pick", genreId = 35, genreName = "Comedy", certification = "PG")))
        // b shares a's title-1 UP rating, and the Family refresh (curated to a, b) does too --
        // all three reuse the same already-cached /recommendations(1) + title-999 detail.

        val completed = repo.refreshAll(region = "GB")

        assertEquals(
            setOf(
                ProfileRefreshResult(a, "Kev"),
                ProfileRefreshResult(b, "Sam"),
                ProfileRefreshResult(FAMILY_PROFILE_SENTINEL_ID, "The Family"),
            ),
            completed.toSet(),
        )
    }

    /**
     * PLAN.md §4 "Per-profile notification control" (M3e): a profile's refresh throwing (network
     * error, TMDB hiccup) must not silently swallow every other profile still queued behind it in
     * the loop -- the old unwrapped `forEach` would have let that exception propagate straight
     * out of `refreshAll`, aborting the whole run. `failing` is seeded first (so its failing
     * request is the very first one dequeued) and given its own distinct UP rating (title 6, not
     * shared with `succeeding`) so its /recommendations call can't be served from a cache
     * `succeeding` already warmed.
     */
    @Test
    fun `refreshAll excludes a profile whose refresh throws, without blocking or excluding the others`() = runTest {
        val failing = seedWarmProfile(name = "Failing")
        db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(6, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null)))
        db.ratingDao().upsert(RatingEntity(failing, 6, MediaType.MOVIE, RatingValue.UP, clock.current))
        val succeeding = seedWarmProfile(name = "Succeeding")

        server.enqueue(MockResponse(code = 500)) // failing's /recommendations(1) call -- throws, nothing cached
        server.enqueue(MockResponse(body = recommendationsJson(candidateId = 999, title = "Shared Pick")))
        server.enqueue(MockResponse(body = movieDetailJson(id = 999, title = "Shared Pick", genreId = 35, genreName = "Comedy", certification = "PG")))

        val completed = repo.refreshAll(region = "GB")

        assertEquals(listOf(ProfileRefreshResult(succeeding, "Succeeding")), completed)
        val weekStart = repo.currentWeekStart()
        assertEquals(emptyList<ShortlistEntryEntity>(), db.shortlistDao().getForScope(weekStart, failing.toString()))
        assertEquals(listOf(999), db.shortlistDao().getForScope(weekStart, succeeding.toString()).map { it.tmdbId })
    }

    private fun recommendationsJsonMulti(candidateIds: List<Int>): String {
        val results = candidateIds.joinToString(",\n") { cid ->
            """{"id": $cid, "title": "Candidate $cid", "poster_path": "/p.jpg", "release_date": "2026-08-01", "vote_average": 8.0, "vote_count": 500, "popularity": 50.0}"""
        }
        return """
            {
              "page": 1,
              "results": [$results],
              "total_pages": 1,
              "total_results": ${candidateIds.size}
            }
        """.trimIndent()
    }

    private fun recommendationsJson(candidateId: Int, title: String) = """
        {
          "page": 1,
          "results": [
            {"id": $candidateId, "title": "$title", "poster_path": "/p.jpg", "release_date": "2026-08-01", "vote_average": 8.0, "vote_count": 500, "popularity": 50.0}
          ],
          "total_pages": 1,
          "total_results": 1
        }
    """.trimIndent()

    private fun movieDetailJson(id: Int, title: String, genreId: Int, genreName: String, certification: String) = """
        {
          "id": $id,
          "title": "$title",
          "release_date": "2026-08-01",
          "runtime": 100,
          "vote_average": 8.0,
          "vote_count": 500,
          "popularity": 50.0,
          "genres": [{"id": $genreId, "name": "$genreName"}],
          "credits": {"cast": [], "crew": []},
          "keywords": {"keywords": []},
          "videos": {"results": []},
          "watch/providers": {"results": {}},
          "release_dates": {
            "results": [
              {"iso_3166_1": "GB", "release_dates": [{"certification": "$certification", "type": 3, "release_date": "2026-08-01T00:00:00.000Z"}]}
            ]
          }
        }
    """.trimIndent()
}
