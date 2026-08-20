package org.seg7.familywatchlist.data.repository

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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

    private val today: LocalDate = LocalDate.of(2026, 8, 20)

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })

        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        val profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao())

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
    private suspend fun seedWarmProfile(ageRatingCap: String? = null): Long {
        val id = profileRepository.addProfile("Kev", "avatar", ageRatingCap).getOrThrow()
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
