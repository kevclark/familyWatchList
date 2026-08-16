# PLAN.md — Family Streaming Watchlist & Recommendations (Android)

Status: **awaiting Kev's approval**. No implementation starts until this is approved.
Decisions locked with Kev on 2026-08-16: multi-tag watch events, thumbs up/neutral/down ratings,
subscribed services configurable in-app, toolchain agent on Opus 4.8, feature agent on Sonnet 5,
**Want-to-Watch list is a core feature**, TV tracked at series level, weekly shortlist announced
via local notification, preview = emulator on agent101 + scrcpy viewer (EndeavourOS) on the
laptop, README.md added to M4 scope. KVM access confirmed on agent101 (kvm group) →
**no sudo needed anywhere in this project**. compileSdk bumped 35→37 (approved, applied in M1).

M1 spec questions resolved (2026-08-16): trailerKey persisted on Title now (free, same API
call); DiscoverCacheEntity blessed as part of the data model (§2); compileSdk 37 confirmed as
the intended target; Robolectric DAO tests pin `@Config(sdk=[34])` because Robolectric's SDK 36
shadows need JDK 21 (this box has JDK 17) — a test-harness ceiling only, not an app gap; revisit
if JDK 21 ever gets installed alongside 17.

---

## 0. Pre-flight (Kev, before implementation kicks off)

1. **TMDB API key** — register free at https://www.themoviedb.org → Settings → API.
   Copy the **API Read Access Token (v4)**. It goes in `local.properties` as
   `TMDB_ACCESS_TOKEN=...` (git-ignored, injected via BuildConfig). Not committed, ever.
2. **Switch this session to Sonnet** — run `/model sonnet` after approving this plan.
   The orchestrating session bills at its own model; subagent pins only cover the subagents.
   Sonnet 5 orchestrates day-to-day, delegating to:
   - `toolchain-setup` (pinned `claude-opus-4-8`) for environment work
   - `feature-builder` (pinned `claude-sonnet-5`) for app code
   (Kev has okayed occasional Fable use for this project when judgment calls warrant it —
   Sonnet remains the default orchestrator.)
3. **Sudo: none required.** Kev's user is already in the `kvm` group on agent101, and
   everything else installs to `~/` without root.

---

## 1. Tech stack (decided, with rationale)

| Choice | What | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose (Material 3) | The modern default; best model familiarity; streaming-app-quality UI is idiomatic in Compose |
| SDK levels | minSdk 26, target/compileSdk 36–37 | Covers every realistic family device. Kev approved bumping from 35 (post-M0, 2026-08-16) with AGP 9.x to unlock current Compose/Material 3; feature-builder picks the exact level the current stable toolchain supports |
| Local DB | Room (SQLite) + KSP | Offline-first cache + all user data lives here |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Standard, well-trodden TMDB pairing |
| Images | Coil 3 (Compose) | Disk/memory caching of posters for free |
| Background | WorkManager | Weekly shortlist regeneration + provider refresh |
| Settings | Jetpack DataStore (Preferences) | Subscribed services, active profile, flags |
| DI | Manual (single `AppContainer`) | Avoids Hilt's KSP overhead → faster Gradle builds on a 16GB box; app is small enough |
| Architecture | MVVM + repository, unidirectional data flow | One ViewModel per screen, repositories own Room+TMDB reconciliation |
| Trailers | TMDB `/videos` → YouTube key → `Intent` to YouTube app/browser | Zero-dependency v1. **Stretch:** embedded playback via FOSS `android-youtube-player` |

Single Gradle module (`app`). No multi-module ceremony for an app this size — build speed matters more.

---

## 2. Data model (Room)

```
Profile           id PK, name, avatarKey (preset emoji+colour), ageRatingCap TEXT?  -- e.g. "12" (UK certs U/PG/12/15/18), NULL = no cap
                  createdAt. Hard cap: 10 profiles (enforced in repository).

Title             tmdbId+mediaType composite PK. mediaType MOVIE|TV. title, year,
                  posterPath, backdropPath, overview, runtimeMin, certification (UK),
                  voteAverage, popularity, trailerKey TEXT? (YouTube key from the same
                  append_to_response=videos call; persisted at M1 even though trailer UI is
                  M4 — it's free on a call we're already making, avoids a re-fetch), fetchedAt.

TitleAttribute    (tmdbId, mediaType, attrType, attrId) PK. attrType GENRE|CAST|CREW|KEYWORD.
                  name, ord (cast billing order; crew: director/creator only).
                  One normalized table serves both display (cast chips) and the recommender.

WatchEvent        id PK, tmdbId, mediaType, watchedAt (date), note TEXT?.
WatchEventProfile (watchEventId, profileId) PK.        -- multi-tag: family night = one event, N profiles

Rating            (profileId, tmdbId, mediaType) PK. value UP|NEUTRAL|DOWN, ratedAt.
                  Latest write wins; per person, not per event.

WatchlistEntry    (tmdbId, mediaType) PK. addedByProfileId, addedAt,
                  state ACTIVE|WATCHED|REMOVED.
                  One shared family list, tagged with who added each title; Home's "My List"
                  row filters to the active profile with a "whole family" toggle. Logging a
                  watch of a listed title auto-flips its state to WATCHED.

Provider          providerId PK, name, logoPath, subscribed BOOL, displayPriority.
                  Seeded from TMDB GB provider list; "subscribed" toggled in Settings.
                  Default-on at onboarding: Netflix, Disney+, Amazon Prime Video, BBC iPlayer,
                  Channel 4, ITVX — user confirms/edits (brief's "Three" = BBC Three, which
                  lives inside iPlayer on TMDB's data).

ProviderAvailability (tmdbId, mediaType, providerId) PK. kind FLATRATE|FREE, fetchedAt.
                  GB region only.

ShortlistEntry    (weekStart, scopeKey, tmdbId, mediaType) PK. scopeKey = profileId or "FAMILY".
                  score REAL, reasons TEXT (JSON: top contributing attributes → "Because you
                  liked …"), state SUGGESTED|DISMISSED|WATCHED.

DiscoverCache     (queryHash) PK. resultTmdbIds TEXT (JSON array), fetchedAt.
                  Added in M1, not in the original table list: the concrete mechanism behind
                  §3's "discover/candidate pages cached 24h by query hash." TMDB cache, so it's
                  excluded from backup/restore like the rest of the fetched data below.
```

Backup/restore: Settings → export/import a single JSON of Profiles, WatchEvents, Ratings,
WatchlistEntries, Provider.subscribed (user data only; TMDB cache is refetchable) via
Storage Access Framework.

---

## 3. TMDB integration

**Auth:** v4 Read Access Token as `Authorization: Bearer` header via OkHttp interceptor.
From `local.properties` → `BuildConfig.TMDB_ACCESS_TOKEN`.

**Endpoints used:**

| Purpose | Endpoint | Notes |
|---|---|---|
| Search | `/search/multi` | Filter results to movie/tv client-side |
| Full detail | `/movie/{id}`, `/tv/{id}` | **`append_to_response=credits,keywords,videos,watch/providers,release_dates`** (`content_ratings` for TV) — one round-trip fills Title, TitleAttribute, ProviderAvailability, trailer key, and UK certification |
| Candidates | `/discover/movie`, `/discover/tv` | `watch_region=GB`, `with_watch_providers=<subscribed ids>`, `with_watch_monetization_types=flatrate\|free`, sorted by popularity; plus `/{type}/{id}/recommendations` for each top-liked title |
| Provider seed | `/watch/providers/movie?watch_region=GB` (+tv) | Populates Provider table |
| Images | `/configuration` once; poster `w342`, backdrop `w780` | Coil handles caching |

**Rate limits & caching:** TMDB's stated ceiling is ~50 req/s (legacy guidance 40 req/10s) —
generous, but we behave as if it's tight:
- OkHttp interceptor throttles to **4 req/s** with retry-after handling on 429.
- Room is the source of truth for the UI; network only fills/refreshes it.
  TTLs: title metadata **30 days**, watch-provider rows **7 days**, discover/candidate pages
  cached **24 h** per query hash. Stale rows refresh lazily on view + in the weekly job.
- The `append_to_response` pattern means logging a watched title costs **one** API call.

**Attribution (required by TMDB terms):**
- "Streaming data by JustWatch" text wherever availability badges render.
- TMDB logo + the exact notice their terms (§3) require: "This application uses TMDB and
  the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB" — in
  Settings → About and on onboarding.

---

## 4. Recommendation algorithm (content-based, fully local)

Per profile, build an **affinity vector** over attributes (genres, keywords, top-10 cast,
director/creator):

```
for each WatchEvent tagged with profile P, for each attribute a of that title:
    affinity_P[a] += ratingWeight × recencyWeight
ratingWeight:  UP = +1.0   unrated/NEUTRAL = +0.4   DOWN = −0.8
recencyWeight: exp(−ln2 × daysSince(watchedAt) / 180)      # 180-day half-life
```

**Watchlist signal:** an ACTIVE WatchlistEntry contributes its title's attributes to the
adder's vector at weight +0.6 × recencyWeight(addedAt) — adding something to the list is a
strong statement of taste even before it's watched. Listed titles are excluded from
shortlist *candidates* (you already know about them) but the "My List" Home row surfaces
which of them are streaming on your services right now.

Then dampen ubiquitous attributes (everything is "Drama") with an IDF-style factor computed
over the watched corpus, and L2-normalise per attribute type.

**Candidate pool:** `/discover` on subscribed GB providers (top ~120 by popularity per media
type) ∪ TMDB `/recommendations` for the profile's top-5 UP-rated titles. Exclude: already
watched, DISMISSED this cycle, and anything over the profile's `ageRatingCap`.

**Scoring:**
```
score = 0.70 × affinityMatch        # dot product vs candidate attrs, per-type weights:
                                    #   keyword 1.2, genre 1.0, crew 1.0, cast 0.8,
                                    #   normalised by attr count per type
      + 0.15 × tmdbQuality          # voteAverage/10, min 20 votes
      + 0.15 × freshness            # newer release + recently-added-to-provider boost
```

**Shortlist assembly (weekly, ~8 titles per scope):** greedy by score with a diversity cap
(max 2 per primary genre) + **1 wildcard slot** (high-quality title from an unexplored genre)
so taste doesn't tunnel.

**Family scope:** aggregate the vectors of selected profiles as
`0.5 × mean + 0.5 × min` (least-misery blend — nothing anyone hates), and apply the
**strictest** ageRatingCap among them. Home has a "who's watching tonight?" chip row that
recomputes this on the fly for any subset.

**Cold start:** < 5 watch events for a profile → popular-on-your-services (age-filtered)
labelled "Popular on your services" instead of "For you".

**Refresh & notification:** WorkManager weekly (Monday 06:00, unmetered-preferred)
regenerates shortlists + refreshes provider TTLs, then posts a local notification
("Your family shortlist is ready 🍿") that deep-links to Home. `POST_NOTIFICATIONS`
runtime permission is requested during onboarding (Android 13+); declining just means
silent refresh. Manual pull-to-refresh on Home does the same on demand.

This is all deterministic Kotlin — unit-testable with fixture data, no runtime LLM/API cost.

---

## 5. Screens

1. **Onboarding** — TMDB/JustWatch attribution, pick subscribed services (pre-ticked GB
   defaults), create first profile. One-time.
2. **Profile picker** — avatar grid (preset emoji + colour combos), add/edit/delete, max 10,
   optional age-rating cap per profile. Sets "active profile".
3. **Home** — streaming-app look: edge-to-edge, dark-first, horizontal poster carousels:
   *My List* (active profile ↔ whole-family toggle, availability badges) · *For {profile}* ·
   *Family night* (with who's-watching chips) · *Popular on your services*.
   Each card shows provider badge; long-press → dismiss ("not interested").
4. **Title details** — backdrop hero, poster, year/runtime/cert, overview, cast chips,
   availability badges ("Streaming data by JustWatch"), **▶ Trailer** (YouTube intent),
   **＋ My List** toggle, **Log watch**, thumbs rating, "Because you liked …" reason line
   when reached from a shortlist.
5. **Search** — TMDB multi-search with movie/TV filter chips, poster-grid results with
   quick add-to-list on each card.
6. **Log-watch sheet** — date (default today), profile multi-select chips, optional
   per-profile thumbs right in the sheet. One tap for the common case.
7. **History** — reverse-chronological, filter by profile, tap to edit/delete an event or
   change ratings.
8. **Settings** — services toggle list, profile management, JSON backup/restore, About &
   attributions.

Polish bar: Coil crossfade placeholders, shared-element-style transition into details,
Material 3 dynamic colour with dark default, predictive back. Feels like a streamer, not a form.

**Design tone note (Kev, after reviewing M2a screenshots, 2026-08-16):** onboarding/profile
screens read as "a bit primary school" — the bright saturated avatar palette and emoji-forward
layout skew more playful-kids-app than premium streamer. Course-correct for M2b and the M4
polish pass: pull the accent palette in line with actual streaming-app restraint (Netflix/
Disney+/Prime all lean muted dark surfaces + one confident accent colour, not a rainbow of
avatar swatches), let poster/backdrop imagery carry the visual interest rather than iconography,
and keep emoji avatars as one *option* among more neutral choices (initials, solid colour tiles)
rather than the only style. Applies most directly to M2b's poster-grid/detail screens (where
photography should dominate) and any M4 revisit of the M2a onboarding/profile screens.

---

## 6. Build environment & preview (agent101)

Everything user-space under `~/android-dev/` unless noted:

- **JDK:** Temurin 17 tarball → `~/android-dev/jdk17`
- **Android SDK:** `cmdline-tools` zip → `sdkmanager` installs `platform-tools`,
  `platforms;android-35`, `build-tools;35.x`, `emulator`,
  `system-images;android-35;google_apis;x86_64`
- **Gradle:** via wrapper (project-pinned, nothing global)
- **Env:** `ANDROID_HOME`, `JAVA_HOME` exported from `~/.config/fish/conf.d/android.fish`
  (Kev's shell is fish) and a `.env.sh` for bash-based tooling
- **Emulator (headless):** AVD `family_test`, run
  `emulator -avd family_test -no-window -no-audio -grpc 8554`. `/dev/kvm` access confirmed
  (Kev is in the `kvm` group on agent101).
- **Laptop viewing:** `adb -a nodaemon server` on agent101 (or `adb tcpip`), laptop runs
  `scrcpy --tcpip=agent101:5555` → live interactive screen. Toolchain agent writes exact
  steps to `docs/PREVIEW.md`.
- **Real device:** wireless ADB pairing (`adb pair`) documented in the same file;
  `./gradlew installDebug` targets whichever device is connected.

---

## 7. Milestones (feature-builder works in this order; each ends green)

| M | Deliverable | Done means |
|---|---|---|
| M0 | Toolchain + scaffolded Compose app | `./gradlew assembleDebug` green; APK boots on emulator (toolchain-setup agent) |
| M1 | Data layer | Room schema + DAOs, TMDB client with throttling/caching, repositories; JVM unit tests (MockWebServer, in-memory Room) pass |
| M2 | Core flows | Profiles, search, title details, Want-to-Watch list, log-watch sheet, history — usable end-to-end |
| M3 | Recommender | Scoring engine (incl. watchlist signal) + fixture-based unit tests, Home shortlists, family scope, weekly WorkManager job + Monday notification |
| M4 | Polish | Trailers, transitions/placeholders, dismiss flow, settings, backup/restore, attribution pass, decent repo README.md (what/why, screenshots, build & preview instructions, TMDB/JustWatch attribution) |
| M5 | Ship | Install on Kev's phone via wireless ADB; `docs/PREVIEW.md` verified from laptop |

Orchestrator keeps `PROGRESS.md` (checkbox per milestone item) so any session can resume cold.

**Testing bar:** recommender and caching logic get real JVM unit tests (deterministic
fixtures); DAOs tested against in-memory Room; API client against MockWebServer. Compose UI
tests only for the log-watch flow (highest-value). Every milestone ends with
`./gradlew test assembleDebug` green — no exceptions.

---

## 8. Risks & open items

- **TMDB provider data quality** for UK broadcaster catch-up (iPlayer/C4/ITVX) is decent but
  imperfect — availability badges are "best effort", worth saying so in About.
- **Non-commercial TMDB use** — fine (personal family app, no distribution).
- **TMDB AI/ML clause (terms §1.C/§2.A)** — reviewed 2026-08-16: it targets training/validating
  ML or AI systems and harvesting datasets for that purpose. Our recommender is a
  deterministic hand-written scoring function (fixed weights, no training, no runtime AI) and
  the backup export excludes cached TMDB content — so the app stays clearly outside the
  clause. Guardrails to preserve: never add runtime LLM features fed by TMDB data, and never
  export/share the TMDB cache.
- **Emulator on 16GB alongside Gradle** — workable, but don't run full builds and emulator
  boot simultaneously; toolchain agent sets `org.gradle.jvmargs=-Xmx4g` and emulator RAM 2GB.
- **Stretch (post-M5, only if credit remains):** embedded trailer player, episode-level TV
  tracking (v1 tracks TV at series level), Play-Feature-style "leaving soon" via provider-TTL
  diffing.
