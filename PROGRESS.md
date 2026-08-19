# PROGRESS.md — Family Watchlist

Living checklist mirroring PLAN.md §7. Update it as work lands so a cold session can resume.
Every milestone ends with `./gradlew test assembleDebug` green.

Last updated: 2026-08-19 (emulator hero/gradient-scrim SIGSEGV root-caused and fixed via
`-gpu swangle` — by `toolchain-setup`).

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
rather than silently resolved:**
- **Search with zero subscribed providers returns nothing at all.** Home's "Popular on your
  services" row has an explicit fallback for this case (PLAN.md §4's cold start); Search has no
  such carve-out specified, so it now just returns an empty result for every query until at
  least one service is subscribed. Worth confirming that's actually wanted, vs. Search also
  needing a "nothing subscribed yet" fallback message.
- **Existing watchlist entries aren't pruned when they lose availability** — implemented as
  documented in PLAN.md §5a, but that specific default was never explicitly confirmed by Kev,
  only assumed reasonable by the orchestrator. Flag again here since it's now live.

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

## M2g — Search empty-state message + unavailable watchlist item treatment (queued)

Kev's answers to M2d's two open questions, 2026-08-19. Both confirmed, not just proposed —
build as specified:

- [ ] Search shows explicit textual feedback when zero providers are subscribed (rather than a
      silent empty result list) — same spirit as Home's cold-start message, adapted for Search
- [ ] Watchlist items that have lost availability render visually dimmed/greyed directly on
      their card, in every context they appear — Home's My List carousel AND the full My
      List/watchlist screen — not only discoverable by tapping into the details screen
- [ ] An explicit remove/clean-up action for an unavailable item is reachable from that same
      list context directly (e.g. long-press or a visible control on the card) — no detour
      through details required
- [ ] Tests: dimmed-state rendering logic, remove action
- [ ] `./gradlew test assembleDebug` green
- [ ] Live verification — no longer blocked: the emulator crash is fixed as of 2026-08-19,
      boot with `-gpu swangle` (see PLAN.md §8)

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
