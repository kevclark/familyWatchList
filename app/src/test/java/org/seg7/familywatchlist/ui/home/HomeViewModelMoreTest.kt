package org.seg7.familywatchlist.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity
import org.seg7.familywatchlist.data.remote.AuthInterceptor
import org.seg7.familywatchlist.data.remote.ThrottleInterceptor
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.AvailabilityGate
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.RefreshAllOutcome
import org.seg7.familywatchlist.data.repository.RefreshLogRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb
import org.seg7.familywatchlist.ui.ActiveProfile
import org.seg7.familywatchlist.work.RefreshCoordinator

/**
 * PLAN.md §5e (M16): the "Show 30 more" end cards, proven at the state layer (Compose has no test
 * harness here). Each fixture serves one `/recommendations` page of N candidates, all
 * available, so the ranked pool has exactly N titles and (identical scores) rank order is the
 * tmdbId tiebreak.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelMoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var discoverRepository: DiscoverRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var titleRepository: TitleRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository
    private lateinit var watchlistRepository: WatchlistRepository

    private val today: LocalDate = LocalDate.of(2026, 8, 20)

    @Before
    fun setUp() = runBlocking {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val api = TmdbClient.create(
            baseUrl = server.url("/").toString(),
            accessToken = { "t" },
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor { "t" })
                .addInterceptor(ThrottleInterceptor(maxRequestsPerSecond = 1000))
                .build(),
        )
        discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("home_more_prefs_${System.nanoTime()}") }),
        )
        titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), db.reviewDao(), api, clock)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock, isAvailable = { _, _, _ -> true })
        recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(),
            ratingDao = db.ratingDao(),
            watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(),
            titleRepository = titleRepository,
            discoverRepository = discoverRepository,
            providerRepository = providerRepository,
            availabilityGate = AvailabilityGate(titleRepository, providerRepository),
            profileRepository = profileRepository,
            profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao()),
            familyProfileRepository = familyProfileRepository,
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))
        listOf("discover_movie" to MediaType.MOVIE, "discover_tv" to MediaType.TV).forEach { (endpoint, mediaType) ->
            (1..RecommendationRepository.CANDIDATE_PAGES).forEach { page ->
                db.discoverCacheDao().upsertAll(
                    listOf(DiscoverCacheEntity("$endpoint:8:GB:$page", tmdbId = -1, mediaType, ord = 0, fetchedAt = clock.nowMillis())),
                )
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private suspend fun warmProfile(name: String): Long {
        val id = profileRepository.addProfile(name, "avatar", null).getOrThrow()
        (1..4).forEach { tmdbId ->
            db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(tmdbId, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null)))
            db.watchEventDao().logWatch(
                WatchEventEntity(tmdbId = tmdbId, mediaType = MediaType.MOVIE, watchedAt = today.minusDays(tmdbId.toLong()), note = null),
                listOf(id),
            )
        }
        db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(5, MediaType.MOVIE, AttrType.GENRE, 18, "Drama", null)))
        db.watchEventDao().logWatch(WatchEventEntity(tmdbId = 5, mediaType = MediaType.MOVIE, watchedAt = today.minusDays(5), note = null), listOf(id))
        db.ratingDao().upsert(RatingEntity(id, 1, MediaType.MOVIE, RatingValue.UP, clock.current))
        return id
    }

    private fun enqueueRecommendations(ids: List<Int>) {
        val results = ids.joinToString(",") { """{"id":$it,"title":"Candidate $it","poster_path":"/p.jpg","release_date":"2026-08-01","vote_average":8.0,"vote_count":500,"popularity":50.0}""" }
        server.enqueue(MockResponse(body = """{"page":1,"results":[$results],"total_pages":1,"total_results":${ids.size}}"""))
    }

    private fun enqueueDetails(ids: List<Int>) = ids.forEach { cid ->
        server.enqueue(
            MockResponse(
                body = """
                    {"id":$cid,"title":"Candidate $cid","release_date":"2026-08-01","runtime":100,"vote_average":8.0,"vote_count":500,"popularity":50.0,
                     "genres":[{"id":35,"name":"Comedy"}],"credits":{"cast":[],"crew":[]},"keywords":{"keywords":[]},"videos":{"results":[]},
                     "watch/providers":{"results":{"GB":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]}}},
                     "release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"PG","type":3,"release_date":"2026-08-01T00:00:00.000Z"}]}]}}
                """.trimIndent(),
            ),
        )
    }

    private val coldActive = ActiveProfile.Individual(ProfileEntity(id = 900, name = "Cold", avatarKey = "a", ageRatingCap = null, createdAt = 0))

    private fun vm(active: ActiveProfile = coldActive, coordinator: RefreshCoordinator? = null) = HomeViewModel(
        discoverRepository, providerRepository, watchlistRepository, userPreferencesRepository, recommendationRepository,
        titleRepository, profileRepository, familyProfileRepository, active, coordinator,
    )

    /** Two warm profiles whose shared UP rating drives one /recommendations page of [poolSize] candidates; returns their ids. */
    private suspend fun familyNightFixture(poolSize: Int): Pair<Long, Long> {
        val a = warmProfile("A")
        val b = warmProfile("B")
        val ids = (2000 until 2000 + poolSize).toList()
        enqueueRecommendations(ids)
        enqueueDetails(ids)
        return a to b
    }

    private suspend fun HomeViewModel.selectAndAwait(a: Long, b: Long): HomeUiState {
        uiState.first { it.familyNightProfiles.size >= 2 }
        toggleFamilyNightProfile(a)
        toggleFamilyNightProfile(b)
        return uiState.first { it.familyNightTitles.isNotEmpty() }
    }

    @Test
    fun `Family Night shows a 31-60 card and each tap appends the next ranked batch with no duplicates`() = runTest {
        val (a, b) = familyNightFixture(75)
        val vm = vm()

        val first = vm.selectAndAwait(a, b)
        assertEquals(30, first.familyNightTitles.size)
        assertEquals("31–60 of 75", first.familyNightMore!!.rangeLabel)

        vm.showMoreFamilyNight()
        val second = vm.uiState.first { it.familyNightTitles.size == 60 }
        assertEquals("the first 30 keep their positions", first.familyNightTitles, second.familyNightTitles.take(30))
        assertEquals(60, second.familyNightTitles.map { it.tmdbId }.toSet().size)
        assertEquals("61–75 of 75", second.familyNightMore!!.rangeLabel)
        val appended = second.familyNightTitles.drop(30).map { it.tmdbId }
        assertEquals("rank order, no shuffle", appended.sorted(), appended)

        vm.showMoreFamilyNight()
        val done = vm.uiState.first { it.familyNightTitles.size == 75 }
        assertNull("exhausted pool hides the card", done.familyNightMore)
        assertEquals(75, done.familyNightTitles.map { it.tmdbId }.toSet().size)
    }

    @Test
    fun `no end card when the whole pool already fits in the first 30`() = runTest {
        val (a, b) = familyNightFixture(20)
        val state = vm().selectAndAwait(a, b)

        assertEquals(20, state.familyNightTitles.size)
        assertNull(state.familyNightMore)
    }

    @Test
    fun `Family Night extras are in-memory only -- nothing lands in shortlist_entries`() = runTest {
        val (a, b) = familyNightFixture(45)
        val vm = vm()
        vm.selectAndAwait(a, b)

        vm.showMoreFamilyNight()
        vm.uiState.first { it.familyNightTitles.size == 45 }

        val weekStart = recommendationRepository.currentWeekStart()
        val adHoc = "AD_HOC:" + listOf(a, b).sorted().joinToString(",")
        assertEquals(emptyList<Any>(), db.shortlistDao().getForScope(weekStart, adHoc))
        assertEquals(emptyList<Any>(), db.shortlistDao().getForScope(weekStart, "FAMILY"))
        assertEquals(emptyList<Any>(), db.refreshLogDao().getAll())
    }

    @Test
    fun `changing the selection drops the appended extras and the card`() = runTest {
        val (a, b) = familyNightFixture(75)
        val vm = vm()
        vm.selectAndAwait(a, b)
        vm.showMoreFamilyNight()
        vm.uiState.first { it.familyNightTitles.size == 60 }

        vm.toggleFamilyNightProfile(b) // back to a single profile

        val reset = vm.uiState.first { it.familyNightSelectedIds == setOf(a) }
        assertNull(reset.familyNightMore)
        assertTrue(reset.familyNightTitles.size <= 30)
    }

    @Test
    fun `dismissing a title from an extra batch removes it, shrinks the totals, and persists against the ad-hoc scope`() = runTest {
        val (a, b) = familyNightFixture(75)
        val vm = vm()
        vm.selectAndAwait(a, b)
        vm.showMoreFamilyNight()
        val withExtras = vm.uiState.first { it.familyNightTitles.size == 60 }
        val victim = withExtras.familyNightTitles.last()

        vm.dismissTitle(victim.tmdbId, victim.mediaType, familyNightProfileIds = listOf(a, b))

        val after = vm.uiState.first { s -> s.familyNightTitles.none { it.tmdbId == victim.tmdbId } }
        assertEquals(59, after.familyNightTitles.size)
        assertEquals("59 visible, 15 still to page", "60\u201374 of 74", after.familyNightMore!!.rangeLabel)
        val adHoc = "AD_HOC:" + listOf(a, b).sorted().joinToString(",")
        val rows = db.shortlistDao().observeForScope(recommendationRepository.currentWeekStart(), adHoc).first { it.isNotEmpty() }
        assertEquals(listOf(victim.tmdbId), rows.map { it.tmdbId })
        assertEquals(ShortlistState.DISMISSED, rows.single().state)
    }

    @Test
    fun `a dismissed title that has not been shown yet is skipped by later pages`() = runTest {
        val (a, b) = familyNightFixture(75)
        val vm = vm()
        val first = vm.selectAndAwait(a, b)
        val shown = first.familyNightTitles.map { it.tmdbId }.toSet()
        val nextUp = (2000 until 2075).first { it !in shown }

        vm.dismissTitle(nextUp, MediaType.MOVIE, familyNightProfileIds = listOf(a, b))
        vm.showMoreFamilyNight()
        val state = vm.uiState.first { it.familyNightTitles.size > 30 }

        assertTrue(state.familyNightTitles.none { it.tmdbId == nextUp })
        assertEquals("the page is still a full 30 from the remaining ranks", 60, state.familyNightTitles.size)
    }

    @Test
    fun `a landed refresh drops appended extras but keeps the row's card`() = runTest {
        val (a, b) = familyNightFixture(75)
        val coordinator = RefreshCoordinator(
            log = RefreshLogRepository(db.refreshLogDao(), clock),
            clock = clock,
            scope = CoroutineScope(Dispatchers.Unconfined),
            schedule = { java.time.DayOfWeek.FRIDAY to 13 },
            region = { "GB" },
            refreshAll = { _, _ -> RefreshAllOutcome(emptyList(), emptyList()) },
            invalidateDiscover = {},
            notificationsMasterEnabled = { false },
            profileNotificationEnabled = { false },
            postNotification = { org.seg7.familywatchlist.work.ShortlistNotifier.Result.POSTED },
            zone = ZoneOffset.UTC,
            finishedBannerMillis = 10,
        )
        val vm = vm(coordinator = coordinator)
        vm.selectAndAwait(a, b)
        vm.showMoreFamilyNight()
        vm.uiState.first { it.familyNightTitles.size == 60 }

        coordinator.run(RefreshTrigger.MANUAL)

        val reset = vm.uiState.first { it.familyNightTitles.size == 30 }
        assertEquals("31–60 of 75", reset.familyNightMore!!.rangeLabel)
    }

    @Test
    fun `For You scores on demand for ranks beyond the persisted shortlist and appends them without touching it`() = runTest {
        val id = warmProfile("Kev")
        val ids = (3000 until 3045).toList()
        enqueueRecommendations(ids)
        enqueueDetails(ids)
        val entity = profileRepository.getById(id)!!
        val vm = vm(active = ActiveProfile.Individual(entity))

        val first = vm.uiState.first { it.forYouTitles.size == 30 && it.forYouMore != null }
        assertEquals("31–45 of 45", first.forYouMore!!.rangeLabel)
        val persistedBefore = db.shortlistDao().getForScope(recommendationRepository.currentWeekStart(), id.toString())
        enqueueRecommendations(ids) // the on-demand scoring re-reads the pool; titles are cached

        vm.showMoreForYou()
        val all = vm.uiState.first { it.forYouTitles.size == 45 }

        assertEquals(45, all.forYouTitles.map { it.tmdbId }.toSet().size)
        assertEquals(first.forYouTitles, all.forYouTitles.take(30))
        assertNull("pool exhausted", all.forYouMore)
        assertEquals("persisted shortlist untouched", persistedBefore, db.shortlistDao().getForScope(recommendationRepository.currentWeekStart(), id.toString()))
        val extraIds = all.forYouTitles.drop(30).map { it.tmdbId }.toSet()
        assertTrue("an extra batch is never badged New", all.newPickKeys.none { it.first in extraIds })
    }
}
