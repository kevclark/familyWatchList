# PROGRESS.md — Family Watchlist

Living checklist mirroring PLAN.md §7. Update it as work lands so a cold session can resume.
Every milestone ends with `./gradlew test assembleDebug` green.

Last updated: 2026-08-21 (M3d — first-class persistent Family profile — by `feature-builder`).

**✅ RESOLVED 2026-08-19 (`toolchain-setup`):** emulator SIGSEGV on hero/gradient-scrim
rendering. Root-caused from a core dump to an out-of-bounds write in the emulator's deprecated
**SwiftShader GLES** driver's JIT-compiled sampling code — not an app bug. **Fix: boot the
emulator with `-gpu swangle`** (ANGLE → SwiftShader Vulkan) instead of
`-gpu swiftshader_indirect`. 60 scripted hero/scrim navigation cycles clean, versus a crash
within 1–3 cycles before. Native 1080x2400 is stable again; the old `-skin 720x1600`
reduced-resolution workaround is obsolete. Note the flag **must** be on the command line —
setting `hw.gpu.mode` in `config.ini` looks like it works but doesn't. Standard boot command in
`docs/PREVIEW.md` §1; full evidence in PLAN.md §8.

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
      (The emulator crash was fixed on 2026-08-19 — `-gpu swangle`, PLAN.md §8 — so a live
      capture of this Snackbar is possible in a future pass if it's still wanted.)

**Real bug fixed along the way (not scope creep — gating was silently broken without it):**
`TitleRepository.isProviderDataStale` judged freshness by timestamp alone, so a search/discover
stub (fresh `fetchedAt`, but no runtime/certification — i.e. never actually detail-fetched)
looked "fresh" and `ensureFresh` never fetched real availability data. Every search would have
silently returned zero results. Fixed by also treating a stub-only row as stale (reusing the
existing runtime/certification heuristic already used elsewhere). Also fixed, unrelated:
`LogWatchSheet.kt`'s "Today" chip called `LocalDate.now()` directly instead of the sheet's
injected clock, breaking `LogWatchFlowUiTest` the moment a run crossed a real midnight —
threaded the injected `today` through properly.

**Two open questions for Kev, not yet answered — implemented literally per spec, flagged
rather than silently resolved. Both since confirmed by Kev and resolved in M2g (below):**
- **Search with zero subscribed providers returns nothing at all.** ~~Home's "Popular on your
  services" row has an explicit fallback for this case (PLAN.md §4's cold start); Search has no
  such carve-out specified, so it now just returns an empty result for every query until at
  least one service is subscribed.~~ **Resolved M2g:** Search now shows an explicit "No services
  selected" empty state in this case, distinct from a genuine no-results-for-this-query message.
- **Existing watchlist entries aren't pruned when they lose availability** — ~~implemented as
  documented in PLAN.md §5a, but that specific default was never explicitly confirmed by Kev,
  only assumed reasonable by the orchestrator.~~ **Resolved M2g:** the "don't auto-remove" default
  stands (confirmed by Kev), refined with dimmed rendering + a direct remove action instead.

## M2e — Home hero/discover filtering bug ✅

Kev found the Home hero banner showing Spider-Man: No Way Home — TMDB's most "popular"
result from `discoverMovies(subscribed)` — despite that title having zero confirmed UK
availability (2026-08-19). Two candidate causes were proposed; root cause confirmed by
inspecting live on-device DB state (`adb shell run-as org.seg7.familywatchlist sqlite3
databases/family_watchlist.db`, same technique as M2d) and cross-checking against a live
call to TMDB's own `/movie/{id}/watch/providers`, per M2d's precedent:

- [x] **Confirmed `subscribedProviderIds` was NOT empty.** `providers` table had exactly 6
      subscribed rows (Netflix 8, Amazon Prime Video 9, BBC iPlayer 38, ITVX 41, Channel 4
      103, Disney Plus 337). The only `discover_cache` rows present were under queryHash
      `discover_movie:8,9,38,41,103,337:1` — the fully-subscribed hash — with no leftover
      rows under an empty-list hash. **This candidate cause did not explain today's
      occurrence.**
- [x] **Confirmed the 24h cache was NOT stale.** `fetchedAtForQuery` for that hash was
      ~21.4h old (fetched 2026-08-19 00:30 UTC, checked ~21:54 UTC) — inside the 24h TTL,
      and the query hash itself proves it was fetched *with* the current (already-finalised)
      provider set, not an earlier unfiltered/pre-finalisation one. **This candidate cause
      also did not explain today's occurrence.**
- [x] **Actual root cause: TMDB's own live GB provider data, not an app bug.** Spider-Man:
      No Way Home (tmdbId 634649) was ord=0 in that cache, and its `provider_availability`
      row (fetched 19:44 UTC today, well inside the 7-day TTL) lists `providerId=38
      (BBC iPlayer), kind=FREE`. A direct live call to
      `GET /movie/634649/watch/providers` (bearer-authed, token never printed) returned the
      identical GB payload: `free: [BBC iPlayer]`, `flatrate: [Sky Go, Now TV Cinema]` —
      matching our cached row exactly. The `WatchProvidersDto → ProviderAvailabilityEntity`
      mapping (`TmdbMappers.kt`) was also checked and is correct (flatrate/free map 1:1, no
      swapped kinds). So TMDB itself is currently asserting this title is free on BBC
      iPlayer in GB; `discoverMovies`'s `with_watch_providers=8,9,38,41,103,337&
      with_watch_monetization_types=flatrate|free` correctly surfaced it on that basis. This
      is the TMDB-data-quality risk PLAN.md §8 already names ("UK broadcaster catch-up
      availability is best-effort, imperfect") — not a filtering defect in this app's code.
      Whether BBC iPlayer genuinely carries it right now (a limited broadcast-tie-in window,
      say) is outside the app's control; **not fixed and not fixable here** — flagging to
      Kev rather than hacking around TMDB's data (e.g. blacklisting a title) would be wrong.
- [x] **Fixed the real latent bug anyway, unconditionally, regardless of root cause:**
      `DiscoverRepository.discoverMovies`/`discoverTv` now short-circuit to `emptyList()`
      when `subscribedProviderIds` is empty, before touching cache or network — a discover
      call with nothing subscribed now shows nothing (Home's existing hero-empty / carousel-
      hides-when-empty states handle this for free) instead of silently falling back to an
      unfiltered "popular in the UK" page. `HomeViewModel`'s stale comment describing the old
      unfiltered-fallback behaviour was corrected.
- [x] **Fixed the cache-staleness gap the checklist flagged, as defense-in-depth** (confirmed
      not today's cause, but a real gap — "currently nothing does this" — worth closing):
      `ProviderRepository.setSubscribed()` now calls
      `DiscoverRepository.invalidateAllCachedPages()` (new `DiscoverCacheDao.deleteAll()`)
      on every subscribe/unsubscribe, so a provider change can no longer leave a stale
      cached page reflecting the old provider set for up to 24h. Verified live: toggled BBC
      iPlayer off in Settings → Streaming services (real UI path, not a test double) and
      confirmed via `sqlite3` that all 40 `discover_cache` rows were gone immediately after.
- [x] Verified fix live: with the empty-subscribed-providers case forced on-device, Home's
      hero collapsed to the existing "Nothing here yet" empty state and both Popular rows
      disappeared entirely (no unfiltered page rendered) — screenshotted. Restored the
      original 6 subscribed providers and confirmed hero/rows return; spot-checked two
      "Popular films on your services" entries (The Last House, Spider-Man: Homecoming)
      against a live TMDB `/watch/providers` call — both have genuine GB availability on a
      subscribed provider (Netflix; BBC iPlayer respectively), confirming the discover
      filter itself is working correctly.
- [x] Tests added: `DiscoverRepositoryTest` (empty-list movies/TV → no results, no network
      call; `invalidateAllCachedPages` forces a refetch), `DiscoverCacheDaoTest` (`deleteAll`
      wipes every query), `ProviderRepositoryTest` (`setSubscribed` invalidates all cached
      discover pages). All existing `ProviderRepository(...)` call sites across the test
      suite updated for the new `DiscoverRepository` constructor parameter.
- [x] `./gradlew test assembleDebug` green (155 tests, 31 classes, 0 failures/errors)

## M2f — Configurable region ✅

Kev's request, 2026-08-19: TMDB doesn't do IP geolocation (confirmed — `watch_region=GB` is
an explicit hardcoded parameter throughout, never inferred from the server's location), so if
he's ever travelling, the app would silently keep showing UK-only results with no way to
check what's actually available where he is. Low priority, no urgency — queued behind M2d/M2e.

- [x] Region becomes a `UserPreferencesRepository` preference (same DataStore pattern as
      `accentColor`), default `GB`. Stored as a plain string (not an enum — the valid set comes
      from TMDB's own region list, not a fixed set this app hand-maintains); a missing or
      malformed value (anything not exactly 2 characters) falls back to `GB`
- [x] `TmdbApi`'s `watch_region` query params on `discoverMovies`/`discoverTv`/`movieProviders`/
      `tvProviders` are unchanged at the interface level (still default to `REGION_GB` — purely
      a test convenience, see below), but every repository method that reaches them
      (`DiscoverRepository.discoverMovies`/`discoverTv`, `ProviderRepository.seedIfEmpty`) now
      takes `region` as a real parameter and every production call site (`HomeViewModel`,
      `OnboardingViewModel`) threads the live `UserPreferencesRepository.region` value through
      explicitly, same pattern `discoverMovies`/`discoverTv` already use for
      `subscribedProviderIds`. `TitleRepository.ensureFresh`/`refresh`, `AvailabilityGate`,
      `SearchRepository.search`, and `WatchlistRepository.add`/`toggle` all gained the same
      `region` parameter — the detail call itself (`movieDetail`/`tvDetail`) has no
      `watch_region` param at all (TMDB returns every country in one response), so region only
      matters at *extraction* time: `TmdbMappers.toAvailability` now picks the given region's key
      out of the multi-country `watch/providers` payload instead of a hardcoded `"GB"`. UK
      certification extraction (`gbCertification()`) is deliberately left hardcoded to GB — see
      the judgment-call note below.
- [x] Settings: a region/country picker (`RegionPickerSheet` in `SettingsScreen.kt`), a modal
      bottom sheet with the same substring-filter pattern as the onboarding services picker.
      Sourced from TMDB's own `/watch/providers/regions` (new `RegionCatalogRepository`,
      `TmdbApi.watchProviderRegions()`) rather than a hand-maintained country list. Cached
      in-memory for the process's life (no TTL logic — this data changes essentially never)
- [x] **Open design question, resolved:** subscribed-provider IDs are region-specific.
      Recommended default implemented as specified in PLAN.md §8: switching region does **not**
      auto-clear the subscribed list (would be surprising/lossy) — instead
      `UserPreferencesRepository.regionServicesMismatch` flags true on any genuine region change,
      driving a dismissible **inline notice** in Settings (not a blocking modal) with a
      "Review services" button into the existing services picker; the flag clears once that
      picker has actually been revisited (`OnboardingViewModel.dismiss()`/
      `onServicesConfirmed()`), not the instant the button is tapped. Verified live: switching
      GB→US showed the notice immediately; switching back US→GB re-showed it (any genuine change
      flags it, symmetrically).
      **Real correctness fix that fell out of this (not scope creep — silently wrong data
      otherwise):** `provider_availability` rows carry no region column (PLAN.md §2 modelled
      them as GB-only), so a title detail-fetched under the old region would otherwise look
      "fresh" for up to 7 more days and keep showing the *old* region's providers mislabeled as
      the new region's. `TitleRepository.invalidateAllProviderData()` (new, backed by
      `TitleDao.expireAllFetchedAt()`) forces every cached title stale on a region change,
      called from the region picker's `onSelect`. Discover pages needed no equivalent explicit
      invalidation: region is now folded into `DiscoverRepository`'s cache-key hash, so an
      old-region page simply sits unreached under its own hash rather than being served as if it
      were the new region's data — same "invalidate on preference change" precedent as M2e's
      `setSubscribed` → `invalidateAllCachedPages`.
- [x] Tests: preference default/round-trip + corrupted-value fallback + mismatch-flag
      set/clear (`UserPreferencesRepositoryTest`), region threading through
      discover/search/provider/watchlist calls with an assertion that a non-GB region actually
      reaches the network/mapper (`DiscoverRepositoryTest`, `TitleRepositoryTest`,
      `ProviderRepositoryTest`, `AvailabilityGateTest`, `SearchRepositoryTest`,
      `SearchViewModelTest`, `WatchlistRepositoryTest`, `HomeViewModelTest`), regions-list
      fetch/cache logic (`RegionCatalogRepositoryTest`, `TmdbApiTest`), and
      `invalidateAllProviderData` forcing a real refetch (`TitleRepositoryTest`)
- [x] `./gradlew test assembleDebug` green — 184 tests, 33 classes, 0 failures/errors;
      assembleDebug clean

**Judgment call, not previously flagged:** region parameters default to `TmdbApi.REGION_GB` at
the repository-method level (mirroring the interface-level convenience that already existed)
rather than being strictly required with no default. This meant the ~15 pre-existing tests that
construct these repositories or call these methods and have nothing to do with region didn't
need touching just to pass a literal `"GB"` they don't care about. Every real production call
site threads the live preference explicitly regardless of the default — verified by dedicated
new tests that a non-GB region actually reaches the network/mapper. If Kev would rather these be
strictly required (no default) to close off any chance of a future call site silently forgetting
to pass region, that's a mechanical follow-up, not a design change.

**Live verification (emulator, `-gpu swangle`, renderer confirmed via
`dumpsys SurfaceFlinger | grep GLES:` before trusting the session):** opened Settings → Region,
confirmed the picker lists live TMDB regions (fetched, not hand-maintained) with working
substring filter; selected United States — Settings' Region row updated to "US right now" and
the inline mismatch notice appeared immediately with a working "Review services" button; on-device
`sqlite3` inspection of `family_watchlist.db` confirmed every title's `fetchedAt` reset to `0` at
that exact moment (`invalidateAllProviderData` firing) and `discover_cache.queryHash` now
carries the region (e.g. `discover_movie:...:GB:1`). Opened Spider-Man: No Way Home's details
screen (same title from M2e/M2g) under the US region — "Where to watch" correctly showed **Disney
Plus** (real US TMDB data), replacing GB's BBC iPlayer/Sky Go/Now TV Cinema; on-device DB
confirmed `provider_availability` now held `providerId=337` (Disney Plus) only. Switched back to
United Kingdom — the mismatch notice re-appeared (symmetric), `fetchedAt` reset to `0` again, and
the details screen correctly reverted to **BBC iPlayer (FREE) / Sky Go / Now TV Cinema**,
matching M2e's original GB findings exactly. Full round-trip (GB→US→GB) verified against live
TMDB data, not a mock.

## M2g — Search empty-state message + unavailable watchlist item treatment ✅

Kev's answers to M2d's two open questions, 2026-08-19. Both confirmed, not just proposed —
built as specified:

- [x] Search shows explicit textual feedback when zero providers are subscribed (rather than a
      silent empty result list) — same spirit as Home's cold-start message, adapted for Search.
      `SearchViewModel` now sources `hasSubscribedServices` from
      `ProviderRepository.observeSubscribed()` (new constructor param) and threads it into
      `SearchUiState`; `SearchScreen` checks it ahead of the searching/error/query branches so it
      applies immediately, before any query is typed, not just after a search returns empty. The
      message ("No services selected" / "Search only shows what's on a service you're subscribed
      to — choose some in Settings to see results.") is kept clearly distinct from the pre-existing
      "Nothing available on your services matched '{query}'" wording so the two empty-result
      causes never read as the same thing — a "Choose your services" button jumps straight to the
      Settings tab.
- [x] Watchlist items that have lost availability render visually dimmed/greyed directly on
      their card, in every context they appear — Home's My List carousel AND the full My
      List/watchlist screen — not only discoverable by tapping into the details screen.
      `WatchlistRepository.observeActiveItemsWithAvailability()` (new) reuses the exact same
      `isAvailable` check `add()` already gates on (in production,
      `AvailabilityGate.isAvailableOnSubscribedProvider` — not duplicated), resolved per item in
      parallel on every emission (no extra caching/throttling layer — PLAN.md §5a's own note that
      a family watchlist is short enough for this to be fine). `HomeViewModel.myList` and
      `MyListViewModel`'s rows both carry the resulting `isAvailable` flag through to
      `PosterCard`'s new `dimmed` parameter (poster art alpha 0.4, caption swapped to "Not on your
      services", title colour dimmed) in both places.
- [x] An explicit remove/clean-up action for an unavailable item is reachable from that same
      list context directly — no detour through details required. Two mechanisms, one per
      screen: the full My List screen already had a direct ✓-badge remove (pre-existing, from
      M2b) that this pass reuses as-is; Home's compact carousel had no such control at all, so
      `PosterCard` gained a dedicated `onRemoveUnavailable` slot — a small Crimson ✕ badge shown
      **only** on dimmed cards (available items are untouched, per spec) — wired to
      `HomeViewModel.removeFromWatchlist()` (new, delegates to `WatchlistRepository.remove`).
- [x] Tests: dimmed-state rendering logic, remove action.
      `WatchlistRepositoryTest` (3 new tests on `observeActiveItemsWithAvailability` — flags a
      lost-availability item and leaves an available one alone, all-available case, ACTIVE-only
      filtering); `MyListViewModelTest` (1 new test: the `isAvailable` flag reaches `MyListRow`
      correctly through the full ViewModel, plus the pre-existing "removing a title drops it from
      the list" test already covered remove); `HomeViewModelTest` (new file, 2 tests: the same
      dimmed-flagging behaviour through `HomeViewModel.myList`, and `removeFromWatchlist` actually
      flipping the entry to REMOVED); `SearchViewModelTest` (2 new tests: `hasSubscribedServices`
      false with nothing subscribed before any search runs, flips true once a provider is
      subscribed).
- [x] `./gradlew test assembleDebug` green — 163 tests, 32 classes, 0 failures/errors.
- [x] Live verification on-device (emulator booted with `-gpu swangle`, renderer confirmed via
      `dumpsys SurfaceFlinger | grep GLES:` before trusting the session — see report). Forced an
      already-listed title (Spider-Man: No Way Home, tmdbId 634649 — the same title from M2e,
      genuinely still on the list from a prior session) into an unavailable state by deleting its
      cached `provider_availability` rows directly via `run-as sqlite3` (fresh `fetchedAt` means
      `ensureFresh` doesn't refetch and clobber the edit) — confirmed dimmed + "Not on your
      services" on both Home's My List carousel and the full My List screen, confirmed the Home
      carousel's new ✕ control actually flips the entry to REMOVED in the on-device DB. Separately
      forced zero subscribed providers (via DB, with the app fully force-stopped first to avoid a
      write race against in-flight Settings-screen toggles — see report) and confirmed Search's
      new "No services selected" state, that its "Choose your services" button lands on Settings,
      and that re-subscribing + a real query goes back to the ordinary
      matched-nothing/has-results messaging.

## M3 — Recommender ✅

Done means: scoring engine (incl. watchlist signal) + fixture-based unit tests, Home
shortlists, family scope, weekly WorkManager job + Monday notification, **plus the four
tunable sliders confirmed by Kev on 2026-08-20 (PLAN.md §4a) — all in v1, not a follow-up.**

- [x] Affinity vectors: rating + recency weights, watchlist signal at +0.6
      (`data/recommend/AffinityEngine.kt`)
- [x] IDF damping + per-attribute-type L2 normalisation (`AffinityEngine.applyIdfDamping`/
      `l2NormalisePerType`)
- [x] Candidate pool from `/discover` (GB, subscribed providers) ∪ `/recommendations`
      (`RecommendationRepository.gatherCandidatePool` — 6 pages/type from `/discover`,
      `/recommendations` for the top-5 UP-rated titles; excludes watched, shared-active-watchlist,
      this-cycle-DISMISSED, and over-age-cap candidates)
- [x] Scoring 0.70 affinity / 0.15 quality / 0.15 freshness (`data/recommend/Scorer.kt`,
      `RecommenderSpec`) — `tmdbQuality`'s "min 20 votes" floor needed `TitleEntity.voteCount`,
      which the app never persisted; added via schema v2→v3 (`MIGRATION_2_3`) and threaded
      through the DTOs/mappers
- [x] Shortlist assembly: ~8 per scope, max 2 per genre, 1 wildcard
      (`data/recommend/ShortlistAssembler.kt`)
- [x] Family scope `0.5×mean + 0.5×min` + strictest age cap (`data/recommend/FamilyBlend.kt`,
      orchestrated by `RecommendationRepository.refreshFamilyShortlist`). **Not wired to Home**:
      the who's-watching chip row / "Family Night" carousel UI was deliberately not built this
      milestone — a scope decision, flagged in the build report, not an oversight. The backend
      it would call is fully built and tested: `refreshFamilyShortlist(persist=false)` computes
      an ad-hoc blend for any profile subset without touching Room, ready for that UI to call
      when it lands.
- [x] Cold start (<5 events) → "Popular on your services" — `HomeViewModel`'s "For You" row
      shows a combined movies+TV popularity carousel labelled accordingly for cold-start
      profiles (`HomeUiState.isColdStartForYou`), real scored picks otherwise
- [x] WorkManager weekly Monday 06:00 + notification deep-link; POST_NOTIFICATIONS request.
      `work/RecommendationScheduler.kt` (pure delay-to-next-Monday-06:00 math, unit tested),
      `work/RecommendationWorker.kt` (CoroutineWorker: `refreshAll` + discover-cache invalidation
      + notify), `work/ShortlistNotifier.kt` (channel + "Your family shortlist is ready 🍿",
      relaunches `MainActivity` — Home is its only reachable destination once a profile is
      active, so that already satisfies "deep-links to Home"). Scheduled idempotently
      (`ExistingPeriodicWorkPolicy.KEEP`) from `FamilyWatchListApp.onCreate()`. `POST_NOTIFICATIONS`
      requested once, the first time Home is reached (`ui/AppRoot.kt`'s launcher +
      `UserPreferencesRepository.notificationPermissionRequested` one-shot flag) —
      "unmetered-preferred" is approximated as `NetworkType.CONNECTED` (WorkManager's Constraints
      API has no soft-preference knob, only hard requirements; flagged as a judgment call)
- [x] Home hero sources from the profile's top-scored pick, not raw popularity (PLAN.md §4's
      2026-08-19 design note — retires the old `discover.movies.firstOrNull()` approach).
      `HomeViewModel.uiState`'s `hero` is now `forYouTitles.firstOrNull()` (the score-sorted
      shortlist's top entry) falling back to the popular pick only for cold-start profiles or
      before the first shortlist exists — proven by a dedicated test seeding a higher-scored,
      *less popular* title and confirming it wins the hero slot over a lower-scored, more
      popular one
- [x] Four sliders (PLAN.md §4a) — math + per-profile storage. `data/recommend/SliderSettings.kt`
      (discovery -> wildcard count + diversity cap, recency -> half-life, personalMatch ->
      affinity/quality weight split) plus `FamilyBlendSlider` (mean/min blend). Per-profile
      storage: new `profile_sliders` table (schema v3→v4, `MIGRATION_3_4`,
      `ProfileSlidersEntity`/`Dao`/`ProfileSlidersRepository`) — a profile with no row reads as
      `SliderSettings.DEFAULT`. The family-blend slider is a **shared app-level** DataStore
      preference (`UserPreferencesRepository.familyBlendSlider`), not per-profile — see that
      property's kdoc for the full reasoning (also in this checkpoint's build report); gated to
      2+ profiles per Kev's mid-build resolution
- [x] "Tune my picks" screen, reachable from profile picker or Settings — `ui/tune/TunePicksScreen.kt`,
      reached via a new "RECOMMENDATIONS" section in Settings (chosen entry point — "profile
      picker or Settings" is satisfied either way; Settings is already profile-scoped and needs
      no new per-profile UI surface on the picker itself). The family-blend slider only renders
      when `TunePicksViewModel.familyBlendVisible` is true (2+ profiles)
- [x] Slider changes recompute that profile's shortlist immediately, debounced ~300–500ms
      (mirror `SearchViewModel`'s existing debounce pattern) — `TunePicksViewModel`
      (`RECOMPUTE_DEBOUNCE_MS = 400`), each slider updates its exposed state instantly and
      debounces the persist-to-Room + `RecommendationRepository.refreshProfileShortlist` call
- [x] **Acceptance bar, not optional:** fixture tests proving all sliders at s=0 reproduce the
      exact same shortlist a build with no sliders at all would produce
      (`SliderAcceptanceBarTest` — runs the full affinity→scoring→shortlist pipeline and the
      family blend twice, once wired to `RecommenderSpec` constants directly and once through
      `SliderSettings.DEFAULT`/`FamilyBlendSlider.DEFAULT`, and asserts bit-identical output)
- [x] Deterministic fixture unit tests for the scorer — base algorithm + slider variations +
      orchestration (`RecommendationRepositoryTest`, `HomeViewModelTest` additions,
      `TunePicksViewModelTest`, `RecommendationSchedulerTest`) — 260 tests total, 0 failures
- [x] `./gradlew test assembleDebug` green — 260 tests, 0 failures, `assembleDebug` clean
- [x] Live verification / screenshots: base recommendations, at least one slider visibly
      changing Home's output — done on-device (emulator, `-gpu swangle`, renderer confirmed via
      `dumpsys SurfaceFlinger`), see the checkpoint 4 note below and `docs/m3-live-*.png`

**M3 progress note (checkpoint 2 of 4, this pass — `feature-builder`):** the four sliders'
math and storage, layered on the checkpoint-1 engine. `SliderSettings`/`FamilyBlendSlider` are
pure derivations (each `s ∈ [-1,1]`, default 0) that convert to the engine's `ScoringWeights`/
`ShortlistConfig`/`FamilyBlendWeights`/half-life; the acceptance bar
(`SliderAcceptanceBarTest`) proves s=0 reproduces the no-slider pipeline exactly, both by
running the full pipeline twice and by asserting the derived config objects are `equals()`.
Family-blend-slider UI-home judgment call: a shared app-level DataStore preference (option 1
of two considered — full reasoning on `UserPreferencesRepository.familyBlendSlider`'s kdoc and
in the build report), not per-session or per-profile. **Visibility gating RESOLVED by Kev,
2026-08-20** (mid-build, after this checkpoint's storage landed): the control is only shown
with 2+ profiles on the account — see PLAN.md §4a's updated slider-4 note. Implemented in the
next checkpoint (`RecommendationRepository.observeFamilyBlendSliderVisible` for the UI signal,
plus a defense-in-depth pin to `FamilyBlendSlider.DEFAULT` inside `refreshFamilyShortlist`
itself whenever the whole account has fewer than 2 profiles, regardless of what value is
passed in — so a stale non-zero preference from a since-deleted second profile can never leak
into a solo-profile household's scoring). Not yet built this checkpoint: candidate-pool
fetching, WorkManager, the "Tune my picks" screen, and Home wiring — that's the remaining
checkpoints of this milestone. `./gradlew test assembleDebug` is green at 236 tests / 0
failures as of this checkpoint.

**M3 progress note (checkpoint 3 of 4, this pass — `feature-builder`):** candidate-pool fetching
and full orchestration landed — `RecommendationRepository` (`data/repository/`) wires the
checkpoint-1/2 pure engine to real Room/TMDB data: builds each profile's affinity vector from
their watch/rating/watchlist history, gathers candidates from `/discover` ∪ `/recommendations`,
excludes watched/listed/dismissed/over-age-cap titles, scores and assembles the shortlist, and
persists it (or, for an ad-hoc who's-watching subset, computes without persisting). Cold-start
profiles are left alone rather than writing a misleading personalised shortlist. Also lands the
family-blend-slider visibility gate Kev resolved mid-build (see above), the WorkManager weekly
job + notification, the "Tune my picks" screen with slider-triggered debounced recompute, and
the Home hero/For You rework sourcing from the real shortlist instead of raw popularity.

**Deliberately not built this checkpoint (scope decision, not an oversight — flagged in the
build report):** the who's-watching chip row / "Family Night" carousel UI on Home. The backend
it needs (`RecommendationRepository.refreshFamilyShortlist(persist=false)`, an ad-hoc blend for
any profile subset) is fully built and tested; only Home's UI call site is missing. Also not
wired: `TitleDetailScreen`'s "Because you liked …" reason line (PLAN.md §5) — the data exists
now (`ShortlistEntryEntity.reasons`), the route parameter to carry it through from a shortlist
card tap does not. Everything else in this checkpoint's brief (candidate-pool fetching,
WorkManager + notification, "Tune my picks", Home hero rework, slider-triggered recompute) is
done.

**M3 progress note (checkpoint 4 of 4, final pass — `feature-builder`):** live emulator
verification (`-gpu swangle`, renderer confirmed via `dumpsys SurfaceFlinger` before trusting
the session) against 3 pre-existing profiles from earlier milestones' testing. Seeded profile
"Kev" past the cold-start threshold (6 tagged watch events across Toy Story/Toy Story 2/
Paddington/Paddington 2/Spider-Man: No Way Home, two UP ratings) directly via `run-as sqlite3`
— the same established technique M2d/M2e/M2g used — then drove the real UI:
- **POST_NOTIFICATIONS fired correctly** on first reaching Home (`docs/m3-live-03-permission.png`).
- **Base recommendations, real and live**: tapping refresh triggered a genuine
  `refreshProfileShortlist` — real `/discover` + `/recommendations` + detail-fetch network calls
  against TMDB, no mocks. Result: hero flipped from the old raw-popularity Spider-Man pick to
  **Toy Story 2** (top-scored, 0.949), with reasons `["John Lasseter","Animation","Comedy"]`
  correctly citing the director/genre overlap with Kev's rated titles
  (`docs/m3-live-05-home.png` before -> `docs/m3-live-07-foryou.png` after).
- **"Tune my picks" screen** renders all four sliders including "Family Night blend" (visible —
  the account has 3 profiles) at their s=0 defaults (`docs/m3-live-09-tune-my-picks.png`).
- **A slider visibly changing Home's output**: moved the Discovery slider to ~+0.96 ("Surprise
  me") — persisted to `profile_sliders`, debounced recompute fired, and the "For You" row's
  third card visibly changed from *Spider-Man: Homecoming* to *Project Hail Mary* as the
  loosened diversity cap pulled in an unexplored genre (`docs/m3-live-13-discovery-slider2.png`,
  `docs/m3-live-16-home-final.png`).
- **WorkManager genuinely scheduled on-device**: `adb shell dumpsys jobscheduler` shows the real
  `androidx.work.systemjobscheduler` job for this package with `Minimum latency: +3d9h...` —
  correctly the distance from Thursday to next Monday 06:00.

**Real bug found and fixed during this verification, not simulated:** the second slider move
(Personal-match, then Discovery) grew `shortlist_entries` from 8 rows to 9 instead of replacing
the set — `ShortlistDao.upsertAll` only ever adds/updates rows for tmdbIds present in the new
assembled list, so a candidate that scored well on a previous recompute but didn't make this
one's cut lingered forever. Root-caused live via `sqlite3` row counts, fixed with a new
`ShortlistDao.deleteSuggestedForScope` (clears this cycle's still-SUGGESTED rows before the new
set is written; DISMISSED/WATCHED rows are deliberately preserved), called from
`RecommendationRepository.persistShortlist` before `upsertAll`. Verified live afterwards: two
further recomputes both held steady at exactly 8 rows. Regression test added
(`RecommendationRepositoryTest`, seeds a stale SUGGESTED row directly to sidestep
`DiscoverRepository`'s legitimate 24h `/recommendations` cache, which would otherwise mask the
bug on a second live call within the same day).

**M3 — Recommender: done.** 260 tests, 0 failures. `./gradlew test assembleDebug` green.

## M3b — Configurable suggestion count

Kev's follow-up, 2026-08-20: default shortlist size already bumped 8→30 directly by the
orchestrator (commit `3c4fff1`) after he flagged 8 as too thin for a "remove the scrolling"
app. He then asked for the count itself to be user-configurable. **Design corrected same
day**: the orchestrator initially proposed a fixed max=50 UI ceiling without confirming it
with Kev first — Kev pushed back on the process (a real design call made and implemented
without checking), and then proposed a better mechanism than either original option: the
slider's max should be the profile's *real* eligible-candidate count, already computed by
the recommender itself, not a guessed number. Full corrected spec in PLAN.md §4a, slider 5.

- [x] Per-profile integer preference for the user's *requested* count, default 30 — overrides
      `RecommenderSpec.SHORTLIST_TARGET_SIZE` per-profile, doesn't replace the constant
      (`ProfileSlidersEntity.suggestionCount`, `ProfileSlidersRepository.getSuggestionCount`/
      `setSuggestionCount`)
- [x] `refreshProfileShortlist`'s existing `scored` pool count (post dedup/watched/listed/
      dismissed/age-cap filtering — the real eligible count, since scoring never drops a
      candidate) is persisted on every refresh (`ProfileSlidersEntity.eligibleCandidateCount`,
      `ProfileSlidersRepository.getEligibleCandidateCount`/`setEligibleCandidateCount`) — the
      real, current ceiling, not an invented one. Schema v4→v5 (`AppDatabase.MIGRATION_4_5`,
      `app/schemas/.../5.json`), two `DEFAULT 30` columns on the existing `profile_sliders` table
- [x] "Tune my picks" screen reads the **last-known persisted** eligible count for the
      slider's max — loaded once at ViewModel init (no live fetch on screen-open); refreshed
      again after each slider-triggered recompute so it doesn't go stale mid-session either
- [x] At refresh time, actual target = `min(userRequestedCount, currentEligibleCount)`
      (`RecommendationRepository.refreshProfileShortlist`) — a shrunk pool this week doesn't
      erase what the user originally asked for (`getSuggestionCount` is never overwritten by a
      thin week); it recovers automatically if the pool grows back — proven by a dedicated test
      running two sequential refreshes (8 eligible, then 35) for the same stored request of 30
- [x] Edge case handled gracefully: `data/recommend/RecommenderSpec.kt`'s top-level
      `suggestionCountRange(eligibleCandidateCount)` returns `min(4, eligible)..eligible`
      (slider min adapts down), or `null` at eligible=0 — "Tune my picks" renders a disabled
      explanatory message for `null`, and a fixed (non-slider) text callout for the degenerate
      single-value range case (eligible 1-3), avoiding a zero-width Compose `Slider` range
- [x] UI: slider on the existing "Tune my picks" screen alongside the four taste sliders,
      same card chrome for visual consistency — but *not* the same signed/labelled-endpoints
      styling as those four (a plain linear range slider showing "Suggestions: N" with real
      "N titles" min/max endpoint labels instead, since this is a literal count, not a taste
      axis). No numeric-field alternative was needed — Kev's corrected spec settled on a slider.
- [x] Family-scope shortlists stay at the fixed default (30) — deliberately out of scope, not an
      oversight (`refreshFamilyShortlist` untouched, still uses `ShortlistConfig.SPEC_DEFAULT`)
- [x] Tests: preference default/round-trip (`ProfileSlidersRepositoryTest`, `RecommenderSpecTest`,
      `TunePicksViewModelTest`), the `min(requested, eligible)` clamping logic specifically
      including the ceiling-recovers-later case (`RecommendationRepositoryTest`), edge cases at
      eligible<4 and eligible=0 (`RecommenderSpecTest`'s `suggestionCountRange` coverage) — 289
      tests total, 44 classes, 0 failures
- [x] `./gradlew test assembleDebug` green
- [x] Live verification on-device (emulator, `-gpu swangle`, renderer confirmed via
      `dumpsys SurfaceFlinger` first): "Tune my picks" showed real live endpoint labels
      "4 titles ↔ 273 titles" (Kev's actual eligible pool that week, not a hardcoded number).
      Dragged the slider to 10 → on-device `sqlite3` confirmed `shortlist_entries` for Kev's
      personal scope shrank from 30 to exactly 10 rows. Dragged to 127 → grew to exactly 127
      rows. Reset → back to 30, taste sliders back to 0, eligible-count label untouched at 273
      (correctly independent of the request). Family-scope-unaffected is proven by the automated
      `RecommendationRepositoryTest` case, not a live device screenshot — M3's own Family Night
      chip-row UI was deliberately not built yet (see M3's checkpoint-3 note), so there is no
      in-app path to trigger `refreshFamilyShortlist` live; the backend it would call is
      unchanged and fully covered.

**Pre-existing test flakiness found and mitigated along the way (not this pass's bug, but made
worse by it — see below):** `./gradlew test` intermittently failed with
`kotlinx.coroutines.test.UncompletedCoroutinesError`/`UncaughtExceptionsBeforeTest` on ViewModel
tests unrelated to this pass (confirmed via `git stash` to reproduce identically on the
pre-M3b baseline, e.g. `TunePicksViewModelTest`'s pre-existing "a slider change is eventually
persisted" test hanging for a full 60s). Root cause: `ViewModel`s built directly in tests (not
through the Android framework) never have `viewModelScope` cancelled, so a still-in-flight
Room-backed init/recompute coroutine can resume on a real background thread *after* a test's
`tearDown()` closes its database, throwing on the closed connection — an exception
kotlinx-coroutines-test then attributes to whichever *other* test happens to be starting up,
observed as flaky failures on tests that were never touched. Adding more real Room round-trips
per refresh (this pass's `eligibleCandidateCount` persistence) widened the window and made it
fire more often. Mitigated two ways, both defensive/additive, not a full fix for the underlying
project-wide pattern (out of scope for M3b): `TunePicksViewModelTest` now routes every
constructed ViewModel through a `ViewModelStore` so `tearDown()` can call `.clear()` on all of
them (cancels `viewModelScope` cleanly); `testutil/InMemoryDb.kt` now gives every in-memory Room
database a larger, shared, process-lifetime executor pool (`newFixedThreadPool(16)`, was Room's
un-configured default) for more headroom under this suite's concurrent-DAO-call volume. A few of
this file's own tests were also hardened against a related race (a `.first()` with no predicate
doesn't actually wait for an async init write when the unseeded persisted default happens to
equal the constructed default — fixed by seeding a distinguishing sentinel value and waiting on
a real predicate instead). Four consecutive full-suite green runs after these changes; not a
guaranteed permanent fix for the wider pattern (`ProfileViewModelTest`, unrelated and untouched
by this pass, showed the same category of flake at least once during this investigation) — worth
a dedicated pass if it recurs.

## M3c — Family Night chips + "Because you liked…" reason line ✅

Kev's request, 2026-08-21: build the two pieces M3 deliberately deferred, as a standalone
review before M4 rather than folded silently into the polish pass. **No new backend work
needed** — both already exist and are tested from M3/M3b, this is UI wiring only:

- [x] **Family Night chip row** (Home): multi-select chips for "who's watching tonight?".
      When 2+ profiles are selected, call `RecommendationRepository.refreshFamilyShortlist`
      with `persist = false` (already built for exactly this ad-hoc case — see its kdoc) and
      show the blended results in a row on Home. Only relevant with 2+ profiles on the
      account at all — same visibility gating already established for the family-blend
      slider (PLAN.md §4a slider 4). `HomeViewModel` gained `profileRepository` (new
      constructor param), a debounced (`FAMILY_NIGHT_DEBOUNCE_MS = 400`, same order of
      magnitude as `TunePicksViewModel`'s slider debounce) trigger collecting the selected
      profile IDs, and reads the already-stored `UserPreferencesRepository.familyBlendSlider`
      preference — no second mechanism invented. `HomeScreen.kt`'s new `FamilyNightChipRow`
      reuses `AvatarBadge`'s existing `selected` styling (the same accent-border language the
      profile picker already uses) rather than inventing a new selection idiom; the "Family
      Night" results carousel uses the exact same `PosterCarousel`/`PosterCard` components as
      every other Home row
- [x] **"Because you liked…" reason line** (title details, PLAN.md §5 screen 4): when a title
      details screen is reached for a title that has a current `SUGGESTED` shortlist entry
      for the active profile's scope, look it up, parse its `reasons` JSON (already persisted
      by `RecommendationRepository.reasonsFor` — top 3 attribute names, e.g.
      `["John Lasseter","Animation","Comedy"]`) and render "Because you liked …". Only shown
      when such an entry exists — not for titles reached via Search/My List/History directly,
      matching the plan's "when reached from a shortlist" wording exactly. Implemented as a
      live lookup on screen-open (`RecommendationRepository.reasonsForShortlistEntry`, backed
      by new `ShortlistDao.getSuggestedEntry`) rather than a route parameter threaded from a
      shortlist-card tap — equivalent in practice (a title is only ever reachable from a
      shortlist card while it's genuinely still on that shortlist) and correct regardless of
      navigation path, with no extra plumbing through every `onOpenTitle` call site. Rendered
      as its own line in [Accent] directly under the genres line on `TitleDetailScreen`
- [x] Tests: chip-row selection → correct ad-hoc family blend call
      (`HomeViewModelTest`, new test proves the *exact* selected profile-ID set reaches
      `RecommendationRepository` — by giving only one candidate profile the UP rating that
      drives a specific `/recommendations` candidate and showing it only ever surfaces once
      that profile is genuinely part of the selection — and that the FAMILY scope stays empty
      in Room throughout, proving `persist = false`); reason-line lookup present/absent
      correctly depending on shortlist-entry existence, and that it renders the real parsed
      attribute names (new `TitleDetailViewModelTest`, 4 tests: no entry at all, a real
      SUGGESTED entry's attribute names round-tripping through the JSON parse, a DISMISSED
      entry for the same title staying silent, and a different profile's own-scope entry not
      leaking across profiles)
- [x] `./gradlew test assembleDebug` green — 294 tests, 45 classes, 0 failures;
      `assembleDebug` clean
- [x] Live verification / screenshots: both features working on-device against 3 real
      pre-existing profiles (Kev/Sam/Ellie) and live TMDB data (emulator, `-gpu swangle`,
      renderer confirmed via `dumpsys SurfaceFlinger` first) — see the build report for the
      full screenshot set (`docs/m3c-*.png`): the chip row rendering all 3 profiles, Kev+Sam
      selected (accent border) with a real "Family Night" carousel appearing underneath
      (Project Hail Mary / Hoppers / Toy Story 5 / …), on-device `sqlite3` confirming
      `shortlist_entries` held only Kev's own persisted scope throughout (no `FAMILY` row ever
      written by the ad-hoc call), the "Because you liked John Lasseter, Animation, Comedy"
      line on Toy Story 2's details screen (a real `SUGGESTED` entry from Kev's own
      shortlist), and its correct *absence* on Batman Begins' details screen reached via
      Search

## M3d — Family profile (first-class, persistent) ✅

Kev's request, 2026-08-21: creating/selecting the same 4 people every visit via the ad-hoc
chip row is tedious for a combination the household uses constantly. Confirmed design (all
three questions answered with the recommended option): a real, first-class profile — selectable
at the profile picker exactly like an individual, with its own persistent Home — not a chip-row
preset; just one (not multiple named groups); logging a watch while it's active auto-tags
every member. Full spec in PLAN.md §4 (new subsection right after the existing family-scope
paragraph). The ad-hoc "who's watching tonight?" row (M3c) is NOT replaced — it stays for
one-off combinations different from the saved Family profile.

- [x] `FamilyProfile` + `FamilyProfileMember` entities (PLAN.md §2) — one row, 2+ members
      required, editable membership, cascades cleanly on a member's deletion
- [x] "+ Create family profile" reachable from the profile picker, alongside adding an
      individual — pick members from existing profiles (2+), name (default "Family"), avatar
- [x] Selectable as the active profile exactly like a real person — sentinel-based
      `activeProfileId` distinction (or equivalent) recommended in the plan; build agent's
      call on exact mechanism as long as every read site handles both cases unambiguously
- [x] Home, when Family is active, sources hero/For You from the **persisted** family
      shortlist (`refreshFamilyShortlist` with `persist=true`, curated membership — NOT the
      ad-hoc `persist=false` path M3c built, and NOT hardcoded to "every profile on the
      account" the way the weekly job's current default is) — reuse the existing mean/min
      blend + slider-4 + strictest-age-cap logic unchanged
- [x] Log-watch while Family is active writes real `WatchEventProfile` rows against every
      member's own `Profile.id` (never against the Family profile's own id) — identical
      effect to today's manual multi-select, not new recommender logic
- [x] **RESOLVED by Kev, 2026-08-21** (was an open question — now locked, don't re-litigate):
      `refreshAll` refreshes every individual profile AND the Family profile (if one exists),
      each getting its own weekly shortlist exactly like any other profile — using its real
      curated membership, not a hardcoded "blend everyone" default. No separate legacy
      "everyone" fallback needs to coexist alongside the curated Family profile — if no Family
      profile has been created yet, there's simply nothing family-scoped to refresh that week
- [x] Tests: family-profile creation/membership CRUD, active-profile sentinel branching,
      log-watch auto-tag-all-members, persisted family shortlist generation via the curated
      (not "everyone") membership
- [x] `./gradlew test assembleDebug` green
- [x] Live verification: create a family profile from 2+ real profiles, select it as active,
      confirm Home shows a real persisted blended shortlist, log a watch under it and confirm
      every member got tagged — done on-device (emulator, `-gpu swangle`, renderer confirmed via
      `dumpsys SurfaceFlinger` first) against 3 real pre-existing profiles (Kev/Sam/Ellie).
      Created "The Family" (panda avatar) with members Kev+Sam via the new picker dialog;
      on-device `sqlite3` confirmed `family_profile`/`family_profile_members` rows. Selected it
      active — Home genuinely re-rendered as the Family profile (panda badge, "Building your
      picks" briefly, then a real hero "Project Hail Mary" + "For You" carousel from a freshly
      persisted `FAMILY`-scope shortlist: `sqlite3` showed exactly 30 rows under `scopeKey=
      "FAMILY"` with real reasons, e.g. `["Science Fiction","Adventure","friendship"]`). Logged a
      watch of Project Hail Mary from the details screen reached off that shortlist — the sheet
      opened with **Kev and Sam already ticked, Ellie not** (auto-tag, zero manual selection);
      saved, and `sqlite3` confirmed `watch_event_profiles` held exactly `(eventId, 1)` and
      `(eventId, 2)` — Kev and Sam's real `profiles.id`, never Ellie (id 3) and never the -1
      sentinel. Also confirmed Settings' "Tune my picks" row renders disabled with "Switch to a
      person profile to tune personal picks — this doesn't apply to Family" while Family is
      active. Screenshots and full command transcript in the build report.

## M3e — Per-profile notification control (queued, launch after M3d)

Kev's request, 2026-08-21, confirmed already-built-in before scoping (`ShortlistNotifier`/
`RecommendationWorker` exist from M3) — deliberately queued rather than launched alongside
M3d since both would touch `RecommendationWorker`/`refreshAll`/Settings concurrently. Full
spec in PLAN.md §4 "Per-profile notification control".

- [ ] Settings: master notifications on/off toggle, layered on top of (not replacing) the
      existing `POST_NOTIFICATIONS` OS permission check — both must allow it to fire
- [ ] Settings: per-profile checkboxes (every individual + the Family profile, once M3d
      exists) for which profiles' completed refreshes actually notify
- [ ] Default **on** for every profile — preserves current behaviour, not an opt-in reset
- [ ] `RecommendationWorker`/`refreshAll`'s per-profile loop (M3d) checks each profile's
      notification preference as it finishes refreshing
- [ ] Notification mechanism (one per enabled profile vs. a single batched notification) —
      implementation call, document whichever is chosen
- [ ] Tests: preference default/round-trip, notification fires/doesn't fire per the
      master-toggle × per-profile-toggle combination, correctly scoped to whichever
      profile(s) actually finished refreshing
- [ ] `./gradlew test assembleDebug` green
- [ ] Live verification: disable notifications for one profile, confirm it doesn't fire while
      others still do; disable the master toggle, confirm nothing fires at all

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
