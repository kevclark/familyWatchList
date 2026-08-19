# PROGRESS.md — Family Watchlist

Living checklist mirroring PLAN.md §7. Update it as work lands so a cold session can resume.
Every milestone ends with `./gradlew test assembleDebug` green.

Last updated: 2026-08-17 (M2b complete — search, details, watchlist, logging, history, plus the
M2a visual rework against PLAN.md §5a — by `feature-builder`).

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

- [ ] `AccentObsidian` (#8B5CF6) added to `ui/theme/Color.kt`; default preference is OBSIDIAN
- [ ] Accent colour persisted in `UserPreferencesRepository` (DataStore), same pattern as
      `onboardingComplete`/`activeProfileId`
- [ ] Settings: accent picker row (4 swatches, checkmark on active), live-updates the theme
- [ ] `Theme.kt`'s fixed `val Accent = AccentEmber` replaced with the stored preference
- [ ] Home: "For You" section always visible, pre-M3 "coming soon" copy (not §4's cold-start
      wording — see PLAN.md §5a for why), CTA into logging a watch
- [ ] Tests: preference default/round-trip, settings selection logic
- [ ] `./gradlew test assembleDebug` green
- [ ] Screenshot(s) confirming Obsidian applied app-wide + the new For You card

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
