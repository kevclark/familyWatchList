# PROGRESS.md — Family Watchlist

Living checklist mirroring PLAN.md §7. Update it as work lands so a cold session can resume.
Every milestone ends with `./gradlew test assembleDebug` green.

Last updated: 2026-08-16 (M0 complete, by `toolchain-setup`).

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
- [x] Single-module Kotlin + Compose app: minSdk 26, target/compileSdk 35, Material 3
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

**Open decision for Kev:** PLAN.md §1 pins compileSdk 35, but as of Aug 2026 the current
AndroidX/Compose generation requires compileSdk 37 + AGP 9.1. The build is pinned to the newest
libraries that still accept 35 (see comments in `libs.versions.toml`). Moving to
compileSdk 36/37 later is a one-line change plus a dependency bump — it needs Kev's sign-off.

---

## M1 — Data layer

Done means: Room schema + DAOs, TMDB client with throttling/caching, repositories;
JVM unit tests (MockWebServer, in-memory Room) pass.

- [ ] Room entities per PLAN.md §2: `Profile`, `Title`, `TitleAttribute`, `WatchEvent`,
      `WatchEventProfile`, `Rating`, `WatchlistEntry`, `Provider`, `ProviderAvailability`,
      `ShortlistEntry`
- [ ] DAOs + `AppDatabase` (KSP room-compiler), exported schemas checked in
- [ ] TMDB Retrofit/OkHttp client: Bearer auth interceptor from `BuildConfig.TMDB_ACCESS_TOKEN`
- [ ] Throttle to 4 req/s + 429 retry-after handling (PLAN.md §3)
- [ ] `append_to_response=credits,keywords,videos,watch/providers,release_dates` detail call
- [ ] TTL cache policy: titles 30d, providers 7d, discover pages 24h
- [ ] Repositories reconciling Room ⇄ TMDB (Room is the UI's source of truth)
- [ ] `AppContainer` manual DI wired into `FamilyWatchListApp`
- [ ] Unit tests: MockWebServer for the API client, in-memory Room for DAOs
- [ ] `./gradlew test assembleDebug` green

## M2 — Core flows

Done means: profiles, search, title details, Want-to-Watch list, log-watch sheet, history —
usable end-to-end.

- [ ] Onboarding: attribution, subscribed-services picker (GB defaults), first profile
- [ ] Profile picker: avatar grid, add/edit/delete, max 10 enforced in the repository,
      optional age-rating cap
- [ ] Home shell + navigation
- [ ] Search (`/search/multi`) with movie/TV filter chips and quick add-to-list
- [ ] Title details: hero, cast chips, availability badges + JustWatch attribution
- [ ] Want-to-Watch list (shared family list, added-by tag, My List row)
- [ ] Log-watch sheet: date, profile multi-select, per-profile thumbs; auto-flips list state
- [ ] History: reverse-chronological, filter by profile, edit/delete
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
- [ ] Compose UI test for the log-watch flow
- [ ] `./gradlew test assembleDebug` green

## M5 — Ship

- [ ] Installed on Kev's phone via wireless ADB (`docs/PREVIEW.md` §3)
- [ ] `docs/PREVIEW.md` verified end-to-end from the laptop (scrcpy)
- [ ] Final `./gradlew test assembleDebug` green
