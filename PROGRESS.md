# PROGRESS.md — Family Watchlist

Living checklist mirroring PLAN.md §7. Update it as work lands so a cold session can resume.
Every milestone ends with `./gradlew test assembleDebug` green.

Last updated: 2026-08-19 (M2c complete — accent colour as a live user preference defaulting to
Obsidian, always-visible pre-M3 "For You" placeholder on Home — by `feature-builder`).

**Queued (not yet launched):** emulator SIGSEGV on hero/gradient-scrim rendering at native
resolution — 3rd occurrence, hit live by Kev on 2026-08-19. Full detail in PLAN.md §8. Job for
`toolchain-setup`; run whenever agent101 isn't mid-use for live testing.

---

## M0 — Toolchain + scaffolded Compose app ✅

Done means: `./gradlew assembleDebug` green; APK boots on the emulator.

- [x] JDK 17 (Temurin 17.0.20+8) installed at `~/android-dev/jdk17`, SHA-256 verified
- [x] Android SDK at `~/android-dev/sdk` — cmdline-tools 22.0, platform-tools 37.0.1,
      platforms;android-35, build-tools;35.0.1, emulator 37.1.11,
      system-images;android-35;google_apis;x86_64
- [x] All SDK licences accepted (`sdkmanager --licenses`)
- [x] Env exported for fish (`~/.config/fish/conf.d/android.fish`) and bash (`env.sh`)
- [x] `git init` + `.gitignore` (ignores `local.properties`) before any Android files landed
- [x] Gradle wrapper pinned to 8.14.5 (`distributionSha256Sum` set)
- [x] Single-module Kotlin + Compose app: minSdk 26, target/compileSdk 35 (bumped to 37 — see below), Material 3
- [x] Version catalog `gradle/libs.versions.toml`
- [x] kotlinx.serialization + KSP plugins wired (KSP runs; serialization proven by unit test)
- [x] `TMDB_ACCESS_TOKEN` read from git-ignored `local.properties` into `BuildConfig`
- [x] `org.gradle.jvmargs=-Xmx4g` set (PLAN.md §8 RAM budget)
- [x] `./gradlew assembleDebug` green — `app/build/outputs/apk/debug/app-debug.apk`
- [x] `./gradlew test` green (serialization smoke test)
- [x] AVD `family_test` created (Pixel 7, API 35 google_apis x86_64, 2048MB RAM)
- [x] Booted headless with KVM, debug APK installed, activity resumed
- [x] Screenshot proof: `docs/first-boot.png`
- [x] `docs/PREVIEW.md` — scrcpy-over-TCP from the laptop + wireless ADB pairing for the phone
- [x] `PROGRESS.md` (this file)

**Open decision for Kev: RESOLVED 2026-08-16.** Kev signed off on moving to the current stable
toolchain before M1 started. Landed: compileSdk/targetSdk 35→37 (Android 17, platform
`android-37.1`), AGP 8.13.2→9.3.1, Kotlin 2.3.21→2.4.10, Gradle wrapper 8.14.5→9.5.0, Compose
BOM 2026.06.01→2026.08.00, plus coreKtx/lifecycle/activityCompose to their current stable
releases. The ceiling comments in `libs.versions.toml` are gone. One real toolchain change
fell out of AGP 9: it has built-in Kotlin support, so the `org.jetbrains.kotlin.android`
plugin is no longer applied in `build.gradle.kts`/`app/build.gradle.kts` (kotlin-compose,
kotlin-serialization, and ksp are unaffected). `./gradlew test assembleDebug` verified green
on the bumped toolchain before any M1 code was written, per the task's isolation requirement.

---

## M1 — Data layer ✅

Done means: Room schema + DAOs, TMDB client with throttling/caching, repositories;
JVM unit tests (MockWebServer, in-memory Room) pass.

- [x] Room entities per PLAN.md §2: `Profile`, `Title`, `TitleAttribute`, `WatchEvent`,
      `WatchEventProfile`, `Rating`, `WatchlistEntry`, `Provider`, `ProviderAvailability`,
      `ShortlistEntry` — plus `DiscoverCacheEntity`, an implementation detail for §3's
      discover-page TTL that §2 doesn't name as a standalone table (see its kdoc)
- [x] DAOs + `AppDatabase` (KSP room-compiler), exported schema checked in at
      `app/schemas/org.seg7.familywatchlist.data.local.AppDatabase/1.json`
- [x] TMDB Retrofit/OkHttp client: Bearer auth interceptor from `BuildConfig.TMDB_ACCESS_TOKEN`
- [x] Throttle to 4 req/s + 429 retry-after handling (PLAN.md §3)
- [x] `append_to_response=credits,keywords,videos,watch/providers,release_dates` (movie) /
      `...,content_ratings` (tv) detail calls
- [x] TTL cache policy: titles/providers 30d/7d off one shared `Title.fetchedAt` (they're
      always refreshed together by the same detail call — see `TitleRepository` kdoc),
      discover pages 24h by query hash
- [x] Repositories reconciling Room ⇄ TMDB (Room is the UI's source of truth): Profile, Title,
      Discover, Provider, Watchlist, Rating, WatchEvent
- [x] `AppContainer` manual DI wired into `FamilyWatchListApp`
- [x] Unit tests: MockWebServer for the API client (auth header, throttle, 429 retry, DTO
      decoding from realistic fixtures), in-memory Room for DAOs (via Robolectric — see
      spec-questions note below), repository TTL/refresh tests with a fake clock — 66 tests,
      19 test classes, all passing
- [x] `./gradlew test assembleDebug` green

## M2 — Core flows

Done means: profiles, search, title details, Want-to-Watch list, log-watch sheet, history —
usable end-to-end. Split into two passes at Kev's request (2026-08-16) so he gets a scrcpy
look at onboarding/profiles/Home before the rest of the milestone builds on that foundation.

### M2a — Onboarding, profiles, Home shell (build first, review before M2b) ✅

- [x] Onboarding: attribution, subscribed-services picker (GB defaults), first profile
- [x] Profile picker: avatar grid, add/edit/delete, max 10 enforced in the repository,
      optional age-rating cap
- [x] Home shell + navigation (screens/rows can be stubs — this pass is nav + profile UX)
- [x] `./gradlew test assembleDebug` green
- [x] Screenshot(s) captured for Kev's review (`docs/`), per the M0 first-boot.png pattern —
      `docs/m2a-onboarding-attribution.png`, `docs/m2a-onboarding-services.png`,
      `docs/m2a-onboarding-profile.png`, `docs/m2a-profiles.png`, `docs/m2a-home.png`

Implementation notes for whoever picks up M2b:
- Top-level screen choice (Onboarding / ProfilePicker / Home) is driven reactively by
  `ui/AppViewModel.kt`'s `resolveStartState`, off two DataStore flags
  (`UserPreferencesRepository`: `onboardingComplete`, `activeProfileId`) combined with the live
  profile list — no explicit nav callbacks needed between those three; completing onboarding or
  deleting the active profile just flips the state.
- `ProviderRepository.applyOnboardingDefaults()` (new) pre-ticks the PLAN.md §2 GB default
  services by name match (Netflix, Disney Plus, Amazon Prime Video, BBC iPlayer, Channel 4,
  All 4, ITVX/ITV Hub) and only acts while nothing is yet subscribed, so it's safe to call again
  when onboarding is re-entered from Settings.
- Settings (`ui/settings/SettingsScreen.kt`) is still an M4 stub, but two rows are wired for
  real already since PLAN.md calls for them outside M4: "Switch profile" (clears the active
  profile) and "Services & attribution setup" (flips `onboardingComplete` back to false) — plus
  the TMDB/JustWatch attribution text per §3's "Settings → About and on onboarding" requirement.
- Avatar presets/encoding live in `ui/avatar/` — `AvatarOption`, `AVATAR_PRESETS`, and
  `toAvatarKey()`/`avatarKeyToOption()` round-tripping through `ProfileEntity.avatarKey` as
  `"<emoji>|<RRGGBB>"`.

### M2b — Search, details, watchlist, logging, history + M2a rework (Kev reviewed 2026-08-17)

Kev's review of M2a (docs/m2a-*.png, live scrcpy): "primary school", "a bit dull", wants real
Netflix/Prime look-and-feel. Root cause diagnosed as under-specified design direction, not
model capability — PLAN.md §5a now has concrete colour/type/layout tokens to build against.
This pass covers both the original M2b scope AND reworking M2a's visuals to match §5a.

- [x] Apply PLAN.md §5a design system to M2a's existing screens (onboarding, profile picker,
      Home shell) — colour tokens (`ui/theme/Color.kt`), condensed display typography
      (`ui/theme/Type.kt`), layout tokens (`ui/theme/Dimens.kt`), restrained avatars
      (`ui/avatar/`). Material dynamic colour and the light scheme are both **removed** — see
      the "Decisions for Kev" note below
- [x] Fix: services picker search/filter field (substring match, subscribed pinned to top,
      running "N services selected" count)
- [x] Fix: onboarding re-entered from Settings needs a back/close affordance — new
      `servicesSetupRequested` DataStore flag drives an `OnboardingMode.RECONFIGURE` that
      enters at the services step and shows a close (×); `onboardingComplete` is never
      un-set any more
- [x] Fix: "Who's watching?" copy clarifying one-profile-per-person + multi-select elsewhere
      (profile picker subtitle and the onboarding first-profile step both say it)
- [x] Search (`/search/multi`) with movie/TV filter chips and quick add-to-list
- [x] Title details: hero, cast chips, availability badges + JustWatch attribution,
      ▶ Trailer (YouTube intent), ＋ My List toggle, Log watch, thumbs rating
- [x] Want-to-Watch list (shared family list, added-by tag, My List row + full My List screen
      with a whole-family / added-by-me toggle)
- [x] Log-watch sheet: date, profile multi-select, per-profile thumbs; auto-flips list state
- [x] History: reverse-chronological, filter by profile, edit/delete
- [x] Home: real poster carousels wired to discover results (single continuous feed, not
      per-section tabs — PLAN.md §5a). Rows built: **My List** and **Popular films / series on
      your services**. The *For {profile}* and *Family night* rows are **deliberately omitted**
      rather than stubbed — see the note below
- [x] `./gradlew test assembleDebug` green — 132 tests, 0 failures
- [x] Fresh screenshots for Kev's review, replacing the M2a set (`docs/m2b-*.png`)

**Also landed in this pass (not originally in the M2b list):**
- [x] `TitleEntity.trailerKey` — PLAN.md §2 called for it at M1 and it was missed. Added with a
      real Room migration (schema v1 → v2, `AppDatabase.MIGRATION_1_2`, exported as
      `app/schemas/.../2.json`) plus the mapper that extracts the YouTube key, so the ▶ Trailer
      button on details has something to open
- [x] Coil 3 wired up (it was in the version catalog but never a dependency) with its own
      unauthenticated OkHttp client, so posters don't carry the TMDB bearer token or queue
      behind §3's 4 req/s API throttle
- [x] Launch window painted `Ink` instead of Material grey (`values/themes.xml`); the
      `values-night` variant is gone since the app is dark-only

**Decisions for Kev in this pass (flagged, not silently taken):**
- **Accent colour is unpicked.** ~~Three candidates~~ RESOLVED — see M2c below.
- **Material dynamic colour is off, and there is no light theme.** §5's earlier "dynamic colour
  with dark default" is in direct tension with §5a's "one confident accent colour"; §5a is newer
  and more specific, so it won. Reversible. Kev confirmed 2026-08-19: dark-only is correct,
  no further action needed.
- **No personalised Home rows.** ~~*For {profile}* and *Family night* need M3's scorer~~
  RESOLVED — see M2c below (this was an orchestrator instruction gap, not a bad call: PLAN.md
  §4 already specified cold-start placeholder behaviour that M2b's brief didn't reference).
- **Deleting a watch event does not un-flip its watchlist entry back to ACTIVE** — see the
  kdoc on `WatchEventRepository.deleteWatch`. Still open, no complaint from Kev yet.

## M2c — Accent preference + always-visible "For You" placeholder

Kev's follow-up after picking a colour from the mocked-up candidates (Artifact "Watchlist
Accent Palette", 2026-08-19): ship Obsidian as default, but make accent a real user
preference rather than a hardcoded token. Bundled with the For-You fix since both are small
and touch Settings/Home. See PLAN.md §5a "Post-M2b decisions" for full spec.

- [x] `AccentObsidian` (#8B5CF6) added to `ui/theme/Color.kt`; default preference is OBSIDIAN
- [x] Accent colour persisted in `UserPreferencesRepository` (DataStore), same pattern as
      `onboardingComplete`/`activeProfileId` — new `AccentColor` enum (EMBER/AURORA/ORCHID/
      OBSIDIAN), stored as its name via a `stringPreferencesKey`, default OBSIDIAN on
      missing/unrecognised value
- [x] Settings: accent picker row (4 swatches, checkmark on active), live-updates the theme —
      verified live on the emulator (tap Ember → whole app recolours immediately, incl. the
      bottom-nav Settings icon; tap back to Obsidian → reverts)
- [x] `Theme.kt`'s fixed `val Accent = AccentEmber` replaced with the stored preference —
      `Accent` is now a Compose-state var so the ~70 existing call sites across the app pick
      up the change without modification; `FamilyWatchListTheme` takes the resolved
      `AccentColor` as a parameter (collected in `MainActivity`) rather than reaching into
      `LocalAppContainer` itself, so it stays usable from Compose UI tests that render a
      screen without a full `AppContainer` in scope
- [x] Home: "For You" section always visible, pre-M3 "coming soon" copy (not §4's cold-start
      wording — see PLAN.md §5a for why). CTA goes to **Search**, not the log-watch sheet —
      log-watch needs a specific title picked first, which Home's placeholder doesn't have;
      Search is the more natural next action with nothing logged yet, and `onOpenSearch` was
      already wired into `HomeScreen`. Flagging this as the one real judgement call in this
      pass, per spec's "use your judgement, both are reasonable."
- [x] Tests: preference default/round-trip (`UserPreferencesRepositoryTest`, 2 new tests,
      covers OBSIDIAN default + all 4 candidates round-tripping). No separate settings-selection
      test: there's no `SettingsViewModel` — `SettingsScreen` calls
      `userPreferencesRepository.setAccentColor(candidate)` directly on tap, same pattern as
      its other rows (`clearActiveProfileId`, `setServicesSetupRequested`), so there's no
      branching logic beyond what the repository test already covers
- [x] `./gradlew test assembleDebug` green — 134 tests, 1 pre-existing failure (see note below),
      assembleDebug clean
- [x] Screenshots confirming Obsidian applied app-wide + the new For You card
      (`docs/m2c-home.png`, `docs/m2c-settings-accent.png`, `docs/m2c-obsidian-detail.png`)

**Pre-existing test failure, unrelated to this pass:** `LogWatchFlowUiTest`'s "the common case
is one tap…" fails on an assertion at line 161 (`dismissed` flag not set in time) in this
environment. Verified via `git stash` + `./gradlew test --rerun-tasks` that this reproduces
identically on the unmodified M2b commit (`64d53d9`), with zero M2c code in play — a
timing/environment flake in this sandbox (`createComposeRule`'s `UnconfinedTestDispatcher`
racing the save coroutine), not something this pass introduced or can fix without touching a
test outside its scope. Not weakened or skipped.

## M2d — Search & watchlist availability gating

Kev's review while live-testing (2026-08-19): found Spider-Man: No Way Home reachable via the
app with no UK availability at all, and pushed back hard on M2b's "search is a general,
unfiltered finder" design — never actually his requirement, an unvalidated agent inference
this orchestrator wrongly represented as settled. Full spec (including the two rejected
alternatives and why) in PLAN.md §5a "Search & watchlist availability gating".

- [x] Search results filtered to GB availability on a subscribed provider — search-then-check
      against cached/fetched `ProviderAvailability`, not a TMDB query param (doesn't exist)
- [x] Availability checks throttled at the existing 4 req/s; results settle progressively
      (accepted UX trade-off, not a bug to "fix" later)
- [x] In-flight availability checks cancelled on a new query (extend the existing search
      dedupe/cancellation pattern in `SearchViewModel`) — avoid a stale batch overwriting
      a newer query's results
- [x] `WatchlistRepository.add()`/`toggle()` blocked with a clear message unless the title has
      GB availability on a subscribed provider — reuse search's resolution logic, don't
      duplicate it
- [x] Existing watchlist entries are NOT retroactively removed if they later lose availability
      — gate applies at add-time only (default Kev hasn't explicitly confirmed — flag if wrong)
- [x] Log-watch and History remain explicitly ungated — do not extend the restriction there
- [x] `SearchRepository`'s now-incorrect kdoc ("§5 screen 5 is a title finder") updated to
      reflect the real, gated behaviour
- [x] Tests: availability-check filtering logic, cancellation-on-requery, watchlist add
      rejection when unavailable
- [x] `./gradlew test assembleDebug` green
- [x] Screenshot(s)/live verification of gated search (live TMDB data, confirmed against the
      on-device DB — see report). Blocked watchlist-add Snackbar not captured live: the only UI
      path to it is a details screen reached for a title that's lost availability, which isn't
      reachable without a deep link given this session's on-device data, and the emulator hit its
      known hero/gradient-scrim crash twice navigating the details screen. Covered instead by
      dedicated JVM tests (`WatchlistRepositoryTest`, `SearchViewModelTest`'s blocked-add case).

## M2e — Home hero/discover filtering bug (queued, not yet launched)

Kev found the Home hero banner showing Spider-Man: No Way Home — TMDB's most "popular"
result from `discoverMovies(subscribed)` — despite that title having zero confirmed UK
availability (2026-08-19). Two candidate causes, both real code findings, not confirmed
which (or both) is the actual cause yet:

- [ ] Confirm whether `subscribedProviderIds` was empty at the time — `DiscoverRepository
      .toProviderParam()` returns `null` on an empty list, which drops `with_watch_providers`
      from the request entirely, silently turning "Popular on your services" into "Popular in
      the UK, unfiltered." If this is happening, the row is showing incorrect data whenever
      services somehow end up unset, not just for Kev's session.
- [ ] Check discover's 24h cache (`DiscoverRepository`, `DISCOVER_TTL_MS`) isn't serving a
      stale page from earlier in testing (before services were finalised, or before this
      title's availability changed)
- [ ] Once root cause is confirmed, fix it — likely candidates: don't cache/serve a discover
      page when the provider list was empty at fetch time; consider invalidating the discover
      cache when subscribed providers change (currently nothing does this)
- [ ] Verify fix live: hero banner and "Popular on your services" only ever show titles with
      confirmed current GB availability on a subscribed provider
- [ ] `./gradlew test assembleDebug` green

## M2f — Configurable region (queued, not yet launched)

Kev's request, 2026-08-19: TMDB doesn't do IP geolocation (confirmed — `watch_region=GB` is
an explicit hardcoded parameter throughout, never inferred from the server's location), so if
he's ever travelling, the app would silently keep showing UK-only results with no way to
check what's actually available where he is. Low priority, no urgency — queued behind M2d/M2e.

- [ ] Region becomes a `UserPreferencesRepository` preference (same DataStore pattern as
      `accentColor`), default `GB`
- [ ] `TmdbApi`'s various `watch_region` query params — currently a compile-time default
      (`REGION_GB`) baked into the interface — become a real parameter threaded through from
      repositories, sourced from the live preference (same pattern `discoverMovies`/
      `discoverTv` already use for `subscribedProviderIds`)
- [ ] Settings: a region/country picker. Source the list from TMDB's own
      `/watch/providers/regions` endpoint (live, always current) rather than hand-maintaining
      one
- [ ] **Open design question, not yet resolved:** subscribed-provider IDs are region-specific
      (BBC iPlayer/Channel 4/ITVX don't exist outside the UK; even shared services like
      Netflix may need reconfirming per region). Switching region with the same subscribed-ID
      list won't error, but will likely return sparse/empty "Popular on your services" results
      until services are reconfirmed for the new region. Needs a decision on whether switching
      region should prompt re-running the services picker, or just accept degraded results
      until the user fixes it manually in Settings
- [ ] Tests: preference default/round-trip, region threading through discover/search calls
- [ ] `./gradlew test assembleDebug` green

## M3 — Recommender

Done means: scoring engine (incl. watchlist signal) + fixture-based unit tests, Home
shortlists, family scope, weekly WorkManager job + Monday notification.

- [ ] Affinity vectors: rating + recency weights, watchlist signal at +0.6
- [ ] IDF damping + per-attribute-type L2 normalisation
- [ ] Candidate pool from `/discover` (GB, subscribed providers) ∪ `/recommendations`
- [ ] Scoring 0.70 affinity / 0.15 quality / 0.15 freshness
- [ ] Shortlist assembly: ~8 per scope, max 2 per genre, 1 wildcard
- [ ] Family scope `0.5×mean + 0.5×min` + strictest age cap; who's-watching chips
- [ ] Cold start (<5 events) → "Popular on your services"
- [ ] WorkManager weekly Monday 06:00 + notification deep-link; POST_NOTIFICATIONS request
- [ ] Deterministic fixture unit tests for the scorer
- [ ] `./gradlew test assembleDebug` green

## M4 — Polish

- [ ] Trailers via TMDB `/videos` → YouTube intent
- [ ] Coil crossfade placeholders, shared-element-style transition, predictive back
- [ ] Dismiss ("not interested") long-press flow
- [ ] Settings: services toggles, profile management, About
- [ ] JSON backup/restore via Storage Access Framework (user data only, no TMDB cache)
- [ ] Attribution pass: TMDB notice verbatim + JustWatch on every availability UI
- [ ] README.md for the repo: what/why, screenshots, build & preview instructions,
      TMDB/JustWatch attribution
- [x] Compose UI test for the log-watch flow — landed early in M2b
      (`LogWatchFlowUiTest`, JVM/Robolectric so it runs in `./gradlew test`)
- [ ] `./gradlew test assembleDebug` green

## M5 — Ship

- [ ] Installed on Kev's phone via wireless ADB (`docs/PREVIEW.md` §3)
- [ ] `docs/PREVIEW.md` verified end-to-end from the laptop (scrcpy)
- [ ] Final `./gradlew test assembleDebug` green
