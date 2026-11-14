# Testing

What the Family Watchlist tests cover, why they exist, and where they are fragile. Decisions
behind the design are indexed in [docs/adr/README.md](adr/README.md). The specs the tests pin live
in [PLAN.md](../PLAN.md); the history of each bug is in [PROGRESS.md](../PROGRESS.md).

## Running the tests

The toolchain lives in `~/android-dev/` (no sudo). In the repo root:

```bash
source env.sh                      # bash; JDK, Android SDK, Gradle on PATH
./gradlew test assembleDebug       # the project's quality bar for every task
```

Run one class or one test:

```bash
./gradlew testDebugUnitTest --tests '*DiscoverRepositoryTest'
./gradlew testDebugUnitTest --tests 'org.seg7.familywatchlist.ui.profile.ProfileViewModelTest'
./gradlew testDebugUnitTest --tests '*HomeViewModelNewRowsTest.a capped profile*'
```

Do not run a full Gradle build while an emulator is doing its first boot. The host has 16 GB of
RAM and both together starve it (see CLAUDE.md).

Reports: `app/build/reports/tests/testDebugUnitTest/` (HTML) and
`app/build/test-results/testDebugUnitTest/*.xml`.

## Current counts

Latest full run: **711 tests, 0 failures, 0 skipped, 79 result files**, 2026-11-14 (alpha.21).

Source: summed from the `tests=` attributes in
`app/build/test-results/testDebugUnitTest/*.xml` (timestamped 2026-11-14), and cross-checked by
counting `@Test` annotations under `app/src/test` (also 711). Nothing was re-run to write this.

| Area | Classes | Tests |
|---|---|---|
| Room DAOs and migrations (`data/local`) | 14 | 62 |
| TMDB client (`data/remote`) | 3 | 15 |
| Repositories (`data/repository`) | 18 | 247 |
| Recommender (`data/recommend`) | 9 | 71 |
| Search logic (`data/search`: parser, phrase table, AgeFilter, title match) | 4 | 45 |
| Work and scheduling (`work`) | 7 | 64 |
| UI ViewModels and Compose (`ui/**`) | 22 | 206 |
| Serialization smoke | 1 | 1 |

Class counts are test files under each package (78 files; Gradle wrote 79 result files). Repository counts
include the search and Home repository tests (`DescriptiveSearchRepositoryTest` 44,
`DiscoverRepositoryTest` 27, `RecommendationRepositoryTest` 47). Area totals are summed by package from the XML.

There is no `app/src/androidTest` directory. Everything runs on the JVM.

## Test stack

Declared in `app/build.gradle.kts`, `testImplementation` only:

- **JUnit4** (`libs.junit`), plus `androidx.test.ext:junit`.
- **kotlinx-coroutines-test**: `runTest`, `StandardTestDispatcher`/`UnconfinedTestDispatcher`.
  `testutil/MainDispatcherRule.kt` swaps `Dispatchers.Main`.
- **Robolectric** (`@RunWith(RobolectricTestRunner::class)`, usually `@Config(sdk = [34])`) so
  Room, WorkManager, DataStore and Compose run on the JVM. `robolectric.logging=stdout` is set.
- **Compose UI tests** (`createComposeRule`) on Robolectric, so they run in `./gradlew test`
  instead of needing an emulator (PLAN.md section 7 testing bar).
- **OkHttp MockWebServer** for TMDB. `testutil/RoutingDispatcher.kt` routes by path so a test can
  script `/discover`, `/watch/providers`, detail and recommendation endpoints.
- **Room in-memory databases** via `testutil/InMemoryDb.kt` (uses a shared 16-thread executor
  pool, added to reduce flakes under concurrent DAO volume). **Room testing**
  (`MigrationTestHelper`) loads the exported schema JSON in `app/schemas/`; the schemas are wired
  into the `debug` source set because Robolectric reads assets from `mergeDebugAssets`.
- **WorkManager testing** (`work-testing`) for real-WorkManager scheduling tests.
- **Fakes**: `testutil/FakeClock.kt` (injected clock for TTLs, "last 2 months" windows and
  slot maths), fake notifier/gate collaborators, and a write-lagging DataStore wrapper inside
  `ProfileViewModelTest`.
- Not used: Mockito/MockK, Turbine. Flows are read with `first { predicate }` and
  `waitUntil`.

No test talks to the real network. Every HTTP call goes to MockWebServer. TMDB credentials are
not needed to run tests.

## What is tested, and why

Each entry names the rule or bug pinned. "PLAN" is PLAN.md; commit hashes are in `git log`.

### Data: Room

- **DAO tests** (`data/local/*DaoTest`): round trips against in-memory Room for
  titles, watch events, ratings, reviews, watchlist, providers, provider availability, shortlist,
  profiles, family profiles, refresh log, title attributes and the discover cache (PLAN section 2).
- **`AppDatabaseMigrationTest`** (4 tests) runs the real migration SQL against the real exported
  schemas, not hand-rolled fixtures.
  - `7to8`: widened `provider_availability`'s primary key. This pins the M6 data-loss bug found
    by `d414035` (paid rows were colliding with free ones).
  - `8to9`: resets `titles.fetchedAt` so provider caches look stale after the table was
    dropped. The reset was first (wrongly) patched into the already shipped 7-to-8 body without
    a version bump, so it never ran on devices past v8 (commits `8203297`, `f374c3c`;
    PROGRESS.md "M6 regression found on Kev's real phone").
  - `9to10`: adds `imdbId` and the `reviews` table (M14).
  - `10to11`: creates `refresh_log` and adds `is_new`, keeping existing shortlist rows (M15).
  - Gap: migrations 1-to-7 have no tests (current schema is v11).

### TMDB client

`AuthInterceptorTest`, `ThrottleInterceptorTest`, `TmdbApiTest` (15 tests): bearer header
attached without being logged, request throttling to respect TMDB limits, and DTO decoding
against MockWebServer (PLAN section 3). `TmdbMappersTest` covers DTO-to-entity mapping.
`RegionCatalogRepositoryTest` and `ProviderRepositoryTest` cover provider lists and the
provider-change cache invalidation.

### Repositories, discover and the cache

`DiscoverRepositoryTest` (27) is the busiest spec file for Home:

- 24 h TTL: first call hits the network, a repeat within 24 h does not, after 24 h it refetches
  (PLAN section 3 TTLs).
- Zero subscribed providers returns nothing and never touches the network (M2e): without a
  provider filter TMDB would return an unfiltered UK popular page.
- Region and provider set are part of the query hash, so switching either never serves stale
  data. Region is sent as given, never hardcoded to GB.
- **Rent/buy exception (M6, PLAN section 5 "Paid titles" addendum and section 5i).** Popular
  rows must request only `flatrate|free`. Only **New films** asks for `flatrate|free|rent|buy`
  (the "wide" query) and flags a film as paid-only when it appears in the wide page but not in
  the narrow flatrate/free page. The test `rent and buy are requested by New films only, never by
  popular, New series or recommendations` asserts that no other discover call contains `rent` or
  `buy`; two older tests assert popular movies and TV never widen.
- New rows: a 2-month window relative to the injected clock, vote floor, 24 h cache with dates
  moving on, empty wide page skips the narrow query, provider change drops the New rows.
- **Paging (alpha.21, PLAN section 5i).** More loads the next TMDB page, hides after the last
  page (`total_pages` is kept in a `<hash>#pages` row, no schema change), and each page is cached
  24 h. **`paid-only stays exact past page 1`**: wide page p is compared with narrow pages 1..p, so
  a paid-only film on page 2 is not mislabelled as included. `each More tap costs one wide and one
  narrow request` pins the cost. A page cached by alpha.20 without its page count is refetched
  once (this is the cache-key compatibility test).

Other repository tests:

- `RecommendationRepositoryTest` (47): shortlist building, cold start (fewer than 5 events falls
  back to "Popular on your services"), dismissals, family scope, availability, capped profiles
  (PLAN section 4).
- `AvailabilityGateTest` (12): a title is shown only if available on the user's services; paid
  availability widened by M6 (PLAN "Search and watchlist availability gating").
- `ProfileRepositoryTest`: the **11th profile is rejected**, hard cap of 10 enforced in the
  repository (PLAN section 2, CLAUDE.md quality bar). `FamilyProfileRepositoryTest` covers the
  independent Family profile (PLAN 4b).
- `MoreLikeThisRepositoryTest` (9): "More like this" from TMDB recommendations (PLAN 5h). It
  asks for no rent/buy; unavailable titles are filtered.
- `UserPreferencesRepositoryTest` (24), `NotificationPreferencesRepositoryTest`,
  `WatchEventRepositoryTest`, `WatchlistRepositoryTest`, `TitleRepositoryTest` (stubs never
  clobber a title that has full detail cached), `ProfileSlidersRepositoryTest`.

### Recommender

`data/recommend` (71 tests): `AffinityEngineTest`, `ScorerTest`, `RecommenderSpecTest`,
`FamilyBlendTest`, `ShortlistAssemblerTest`, `ShortlistDiffTest`, `ExtraPicksTest`,
`SliderSettingsTest`, `SliderAcceptanceBarTest`. They pin the content-based scoring spec in
PLAN section 4, the tunable sliders (4a), the Family blend, dismiss as a lasting negative signal
(4c, M13), the shortlist diff that drives notifications, and the "Show 30 more" extra batches
(5e, M16). Everything is local; the TMDB client is not involved.

### Search

- **`QueryParserTest`** (28) and **`PhraseTableTest`** (11) pin M17 / PLAN 5f and 5g: the parser
  is deterministic with no model calls. "find action comedies" is two ANDed genres; "with A and B"
  yields two cued people; unknown words become keyword candidates; hyphens and "A.I." normalise;
  multi-word phrases beat their single words; theme and genre words never clash; "cop show" keeps
  its TV restriction. `PhraseTableTest` checks every mapped genre id is a valid TMDB movie/TV id.
- **Alpha.18 themes table sanity test** (`there are 50 to 80 themes with unique names and
  non-empty id lists`, plus `no phrase is both a genre and a theme, and themes never swallow media
  words`, plus `the near-unused tags are never chosen`). Why: the themes table (commit `1991ea0`,
  PLAN 5g) was verified against live TMDB keyword counts. Tags with almost no titles return
  empty results, so "london" must use keyword 212 (not 382039, 14 films) and "dystopian" must use
  4565 (not 386351, 6 films). The 50 to 80 bound stops the table sprawling or collapsing.
  `the mandated entries map to the verified ids` pins the specific ids Kev asked for.
- `AgeFilterTest` (4): the certification cap rules (PLAN 5f age filter).
  `TitleMatchTest`: title-search matches shown beside structured results.
- `DescriptiveSearchRepositoryTest` (44): discover queries built from parsed chips, keyword OR
  groups, flatrate-only browsing, 20-title loads for More (`8386d36`, `1991ea0`).
- `SearchViewModelTest` (15), `DescriptiveSearchViewModelTest` (13), `SearchRepositoryTest` (6):
  state flow, chip removal, age row, load more.
- **`SearchResultsGridTest`** (commit `239add7`): a Compose layout test, because state-level tests
  could not see the bug. Title matches arrive *after* structured results are on screen and are
  prepended to a lazy grid; a lazy grid keeps the first visible item in place, so the "Titles
  matching" section sat above the viewport. The fix is `scrollToItem(0)`; the test fails without
  it.

### Home

- `HomeViewModelTest` (22): popular rows, For You, cold start, capped profile filtering.
- `HomeViewModelNewRowsTest` (18) and `NewRowsTest` (4), PLAN 5i:
  - A capped profile sees only confirmed at-or-under-cap titles in both New rows.
  - **Capped New rows cert enrichment (commit `88170dc`).** Discover results are stubs without
    certifications, so capped rows were empty. The VM now fetches certifications for stub-only
    titles (20 per page) and only shows at-or-under-cap ones. Tests: an uncapped profile never
    triggers a detail fetch, and certifications are not refetched within the TTL.
  - **Auto-advance (commit `ef3f1e2`).** An emulator check (Netflix-only, cap 12) showed every
    page-1 title was 15/18 or had no UK cert, so both rows were hidden and the More card was
    unreachable. A capped profile whose page 1 is all filtered now loads page 2 automatically; it
    stops after 3 pages and a More tap carries on. Tests also assert an uncapped profile, or a
    capped one with a passing title on page 1, does not auto-advance.
  - Paid-only flags stay exact for films on page 2; titles repeated across pages show once and
    dismissals hold on every page; a failing New films request leaves other rows intact.
- `HomeViewModelMoreTest` (8): the 31-60 "Show 30 more" card for For You and Family Night
  (5e, M16). Extras are in-memory only and never land in `shortlist_entries`.

### Details

`TitleDetailViewModelTest`, `TitleDetailMoreLikeThisTest` (7, PLAN 5h), `TrailerPlayerDialogTest`
(video selection priority from M4a, the in-app player dialog), plus `AvailabilityRowTest`
(JustWatch attribution and paid badges, M6) and `PosterCardTest`.

### Work and scheduling (M15, PLAN 5d)

- `RecommendationSchedulerTest` (11): pure delay maths. 6 am Monday boundaries (just before, just
  after, exactly at the hour must delay a full week and not zero), mid-week, Sunday just before
  midnight, Friday default and an arbitrary Tuesday 9 am.
- `RefreshSlotsTest` (14): a run finishing 40 minutes late books the next slot a week minus 40
  minutes away (fixes drift), a day late re-anchors, `mostRecentSlot`, and the catch-up
  "due" rules: a slot missed with no refresh since is due, a grace period lets the scheduled
  worker go first, and a recent failed attempt suppresses an immediate retry.
- `RecommendationSchedulerRescheduleTest` (5): drives real WorkManager under Robolectric. An
  app start does not move a scheduled job; changing the setting reschedules; the legacy periodic
  job is cancelled so two never run.
- `RefreshCoordinatorTest` (16): scheduled runs log the diff and notify; suppressed runs
  (master toggle, profile toggle, missing OS permission) log why; catch-up and manual runs never
  notify; a thrown refresh logs as failed; partial runs still notify; never two refreshes at once;
  a scheduled run waits for an in-flight catch-up; a run left RUNNING by a dead process is closed
  out as failed; `maybeCatchUp` does not hammer a failing refresh.
- `NotificationGateTest`, `NotificationGateDescribeTest`, `ShortlistNotifierTest`: the
  notification rules (diff, toggles, permission).
- There is no direct `RecommendationWorker` test; it is a thin wrapper covered through the
  coordinator.

### UI and Compose

`LogWatchFlowUiTest` (4, `createComposeRule`: one tap in the common case), `SearchResultsGridTest`,
`AboutScreenTest`, `TrailerPlayerDialogTest`, `PosterCardTest`, `AvailabilityRowTest`. Most screens
are tested at the ViewModel level, not through rendered Compose. See gaps.

### Backup and restore

`BackupRepositoryTest` (4, M4b): export then restore into a fresh database reproduces every
table; a malformed file, a wrong-version file, and a file referencing an unknown profile id all
fail cleanly and leave the database untouched.

### Profiles

- `ProfileViewModelTest` (9) and `ProfileRepositoryTest`: creating, switching, the Family sentinel
  and the 10-profile cap.
- **ProfileViewModelTest race fix (commits `5ee672d`, `aaf836c`).** `addProfile` writes the active
  profile id to DataStore after the Room insert. The 11th-profile test read the first non-null
  active id once the list reached 10 entries, which could capture an earlier profile's id. A
  `LaggingDataStore` (a DataStore wrapper that delays writes) made the old assertion fail
  deterministically (`expected:<1> but was:<3>`, then `expected:<10> but was:<9>`). The test now
  waits for each active-profile write before the next add, and for the 10th profile's id. It
  passes with the lag in place.
- `OnboardingViewModelTest`, `AppViewModelTest`, `MyListViewModelTest`, `HistoryViewModelTest`,
  `TunePicksViewModelTest` cover the rest of the per-profile screens.

### About and BuildStats

`AboutScreenTest` (4, PLAN 5j) checks that version, prompt count, hours, snapshot date, every
model row, the summary card and the per-orchestrator prompts/replies/actions render, and that
summary numbers are consistent with the rows. It reads the `BuildStats` constants, so it checks
rendering and internal consistency only. It cannot tell whether the numbers are current.
Re-deriving `BuildStats.kt` and bumping `versionName` is a manual milestone step (PROGRESS.md,
"Milestone completion checklist").

## Known gaps and fragile spots

- **runTest 60-second timeout under CPU load.** `runTest` has a 60 s real-time timeout. It has
  been seen to fire on a cold Robolectric start when the host is busy, for example a parallel
  Gradle build. The believed cause is Robolectric cold start under CPU starvation; this is not
  proven. Re-run the single class first; do not weaken the test.
- **Leaked ViewModel coroutines.** ViewModels built directly in tests never have `viewModelScope`
  cancelled. A late Room coroutine can fail on a closed DB and be blamed on a different test
  (`UncompletedCoroutinesError` / `UncaughtExceptionsBeforeTest`). Mitigations: `TunePicksViewModelTest`
  clears a `ViewModelStore` in `tearDown`, and `InMemoryDb` uses a bigger executor pool. Not a
  full fix. See PROGRESS.md M3b.
- **`LogWatchFlowUiTest` timing.** PROGRESS.md (M2c) records `createComposeRule`'s
  `UnconfinedTestDispatcher` racing the save coroutine once. Not weakened or skipped.
- **Pre-seeded `discover_cache` keys.** Tests that write cache rows by hand
  (`RecommendationRepositoryTest.setUp` builds `"$endpoint:$providers:$region:$page"` by copying
  `DiscoverRepositoryTest`'s private `queryHash`; `ProviderRepositoryTest` seeds
  `discover_movie:9:1`) break silently or fail when the key format changes, and there is no public
  seam to reuse the real hash. This happened twice, in alpha.20 (New rows) and alpha.21 (window and
  page keys). When you change a cache key, grep for `queryHash`, `discover_cache` and
  `DiscoverCacheEntity` in `app/src/test` and update them in the same change.
- **No `@Ignore`d tests.** `grep` finds none.
- **No test hits the real network.** All TMDB traffic goes to MockWebServer. This means TMDB
  contract drift (a renamed field, a changed rate limit) is invisible to the suite.
- **No tests at all for:** migrations 1 to 7, `RecommendationWorker` directly, `MainActivity` and
  navigation (`ui/nav`), the Settings screen, the Activity screen beyond `ActivityFormatTest`, the
  theme, avatars, or the Home, History, My List, Profile and Title detail screens as rendered
  Compose (their ViewModels are tested).
- **Doc and code disagreement.** `AppDatabase.kt` carries a comment that an abandoned
  `MIGRATION_10_11` sits in `git stash`; the shipped one is what `AppDatabaseMigrationTest` runs.
  Do not touch the stash.
- **Emulator-only verification, no automated equivalent:** trailer playback, fullscreen and
  rotation through the YouTube IFrame player (M4a, M8, M9), the Storage Access Framework picker
  for backup/restore, the notification permission prompt and a notification actually posting,
  WorkManager firing at a real time, keyboard-aware padding, launcher icon, scrolling and
  paging feel, real TMDB and JustWatch data (for example the alpha.21 Netflix-only cap-12 check
  that found the auto-advance bug, which unit tests had not). Results are recorded in
  PROGRESS.md and screenshots in `docs/`. See [PREVIEW.md](PREVIEW.md) for how to view the app.

## When you add a feature

1. Add tests for the spec rule, not just the happy path. Include the negative case (for example,
   the rent/buy test asserts what must **not** be requested).
2. Name the bug, PLAN section or milestone in the test's kdoc, with the commit hash if it is a
   regression, so the next reader knows why it exists. A regression test must fail without the fix.
3. Use fakes (`FakeClock`, `RoutingDispatcher`, `InMemoryDb`) and MockWebServer. Never call real
   TMDB.
4. If you change a cache key, TTL or window, update every test that seeds `discover_cache`.
5. Finish with `./gradlew test assembleDebug` green, and report the real output.
6. In the same change, update this file, `docs/ARCHITECTURE.md` and the ADRs in
   [docs/adr/README.md](adr/README.md) if a decision changed. Update PROGRESS.md.
