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
                  createdAt. Hard cap: 10 profiles (enforced in repository). Real people only —
                  the Family profile below is a separate concept, doesn't count against this cap.

FamilyProfile     id PK (effectively a singleton — "just one" per Kev, 2026-08-21), name TEXT
                  (default "Family", editable), avatarKey, createdAt.
FamilyProfileMember (memberProfileId) PK, FK -> Profile, cascades on member deletion (removes
                  them from the family, doesn't delete the family profile itself). Minimum 2
                  members to create one — a "family" of one person isn't a blend.

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

**Home hero banner — design note for M3 (Kev, 2026-08-19):** his original concern about the
hero (before M2e's investigation into whether Spider-Man was genuinely available) was really
"why does a single specific movie dominate Home by default," not data accuracy specifically.
The hero currently sources from `discover.movies.firstOrNull()` — "most popular on your
services," a generic, impersonal pick for something that visually prominent. Once this section's
scoring exists, M3 should make the hero source from the profile's top-scored personalised pick
instead of raw popularity — genuinely meaningful rather than generic. Revisit at that point.

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

**Shortlist assembly (weekly, ~30 titles per scope — bumped from an original 8 at Kev's
request, 2026-08-20: "I end up scrolling for hours [elsewhere], which is what I want this app
to remove... but the suggestion count should still be higher"):** greedy by score with a diversity cap
(max 2 per primary genre) + **1 wildcard slot** (high-quality title from an unexplored genre)
so taste doesn't tunnel.

**Family scope:** aggregate the vectors of selected profiles as
`0.5 × mean + 0.5 × min` (least-misery blend — nothing anyone hates), and apply the
**strictest** ageRatingCap among them. Home has a "who's watching tonight?" chip row that
recomputes this on the fly for any subset — this is the **ad-hoc** path, stays exactly as
built (M3c), unreplaced by the below. It's for "just these two, tonight," not the household's
standing combination.

**The Family profile (Kev, 2026-08-21) is the complementary *persistent* path** for "the same
people, basically every time" — reachable from the profile picker (a "+ Create family
profile" option alongside adding an individual), selectable as **the active profile** exactly
like Kev/Sam/Ellie, with its own real Home (hero, For You, the lot).

- **Membership**: picked once from existing profiles at creation (2+ required), editable
  later. Not necessarily "everyone" — could be a deliberate subset.
- **Mechanics reuse, not rebuild**: this already has real infrastructure. `refreshFamilyShortlist`
  (persisted variant) and the `FAMILY_SCOPE_KEY` path already exist — built for the weekly
  job's "everyone" default. This feature is that same mechanism, generalised to run against
  the Family profile's *curated* membership (not hardcoded "all profiles") and reachable from
  Home directly (not just the background weekly job), using the same mean/min blend + slider-4
  + strictest-age-cap logic already built, unchanged.
- **Selecting it as "active"**: needs a way to distinguish "Family is active" from a real
  `Profile.id` in whatever currently holds `activeProfileId` — a sentinel value (e.g. a
  reserved negative ID, since real Room auto-increment IDs are always positive) is a clean,
  low-blast-radius way to do this without touching the `Profile` table/cap logic at all. Build
  agent's call on exact mechanism as long as the branch ("is Family active, or a real person")
  is unambiguous everywhere `activeProfileId` currently gets read.
- **Logging a watch while Family is active auto-tags every member** (Kev, 2026-08-21) —
  identical effect to manually multi-selecting everyone on the log-watch sheet today, not new
  behaviour, just a shortcut. Writes real `WatchEventProfile` rows against each member's own
  `Profile.id` — nothing is ever logged against the Family profile's own id, so the recommender
  needs zero new logic to understand a "virtual" profile.
- **Cold start** doesn't apply to Family the same way (it's never itself the subject of a watch
  event) — its shortlist quality is only as good as its members' own logged history, same as
  the existing ad-hoc family blend already works today.
- Deleting a member profile removes them from the Family profile's membership (cascade), not
  the Family profile itself.

**Cold start:** < 5 watch events for a profile → popular-on-your-services (age-filtered)
labelled "Popular on your services" instead of "For you".

**Age-cap safety gap, found and confirmed 2026-08-21, fix is non-negotiable regardless of
anything else in this section:** the "(age-filtered)" promise above was never actually
implemented. `DiscoverRepository.discoverMovies`/`discoverTv` — which power both the Popular
row and the cold-start hero fallback — apply **zero** age-rating filtering; the only place
age-cap exclusion exists is inside `RecommendationRepository`'s warm-profile scoring path,
which a cold-start profile never reaches. Concretely: a child's profile could currently be
shown an 18-rated title as its "recommendation." **Fix**: reuse the existing cert-rank
check already built for the real recommender (`RecommendationRepository`/`FamilyBlend` —
don't write a second implementation of "is this title over this profile's cap") and apply it
everywhere a title is surfaced for a specific profile scope. Audit, don't assume just these
two call sites are affected — check Search, the ad-hoc Family Night blend (M3c), and the
watchlist add-gate (M2d) for the same gap while this is being fixed, and close any found using
the same shared check.

**Cold-start Home treatment (Kev, 2026-08-21).** The hero previously fell back to
`discover.movies.firstOrNull()` for a cold-start profile — a title masquerading as "your
pick" when it's just popularity, with no visual distinction from a real personalised hero
(only a small row label underneath told them apart). Replaced: for a cold-start profile,
**the hero area itself becomes an introductory/"getting started" panel** — no movie backdrop,
no implied recommendation, explanatory copy (something like "Log a few things you've watched
or add to your list, and we'll start finding what's next") with a CTA into Search. The
"Popular on your services" row **stays** below it — Kev confirmed genuinely useful browsing
content is worth keeping for a new profile, it just needed the age-cap fix above and to never
be presented as if it were personalised.

**Refresh & notification:** WorkManager weekly (**configurable day/time, default Friday
06:00** — see "Configurable schedule" below; was a hardcoded Monday 06:00 through M3e,
unmetered-preferred) regenerates shortlists + refreshes provider TTLs, then posts a local
notification ("Your family shortlist is ready 🍿") that deep-links to Home.
`POST_NOTIFICATIONS` runtime permission is requested during onboarding (Android 13+);
declining just means silent refresh. Manual pull-to-refresh on Home does the same on demand.

**Configurable schedule (Kev, 2026-08-21, queued as M3f).** `RecommendationScheduler`'s day
(`DayOfWeek.MONDAY`) and hour (`6`) are currently literal hardcoded values, not a preference —
confirmed by direct code read before scoping this. Kev wants a real Settings control (day-of-
week + hour-of-day), not just a different hardcoded default. **Default: Friday, 06:00**
("Friday morning" — he didn't specify an exact hour, so the existing 06:00 convention carries
over). **The subtle correctness requirement, easy to miss:** `scheduleWeekly` is currently
called with `ExistingPeriodicWorkPolicy.KEEP` on every app start, *deliberately* idempotent so
routine launches never reset an already-scheduled job's cadence. That's still correct for
normal app-start calls — but when the user actually *changes* this setting, the underlying
WorkManager job must be genuinely rescheduled (e.g. `ExistingPeriodicWorkPolicy.UPDATE`, or an
explicit cancel-then-reschedule triggered specifically by the settings-change event) or the
stored preference will silently do nothing — the old schedule keeps running underneath it.
Minute-level granularity isn't needed (hour-only, matching the existing `:00` convention) —
don't over-build a full time-of-day picker with minutes.

**Per-profile notification control (Kev, 2026-08-21, queued as M3e — built after M3d lands,
same files would otherwise conflict):** currently one fixed notification fires for the whole
weekly job, no granularity, no in-app off switch beyond the one-time OS permission prompt.
Kev wants: (1) an in-app master on/off toggle in Settings, layered on top of the OS
permission (both must allow it for a notification to actually fire — disabling either one
silences it); (2) per-profile selection of which profiles' completed refreshes actually
notify — e.g. "notify for my own picks and Family's, but not Sam's or Ellie's." Ties directly
into M3d's `refreshAll` becoming a per-profile loop (including the Family profile) — this is
the natural place to check each profile's notification preference as it finishes refreshing.
Default: **on** for every profile, preserving today's existing behaviour until someone
explicitly opts out — not an opt-in cold start for a feature that already exists. Exact
mechanism (one notification per enabled profile vs. a single batched notification listing
whoever finished) is an implementation call — document whichever is chosen.

This is all deterministic Kotlin — unit-testable with fixture data, no runtime LLM/API cost.

### 4a. Tunable sliders (Kev, 2026-08-20 — all four confirmed for v1, not a trim-down set)

Four per-profile sliders, each a signed value **s ∈ [-1, +1], default 0 exactly reproducing
the fixed spec above** — 0 is "the algorithm as already designed," not an arbitrary midpoint.
Live in a dedicated **"Tune my picks" screen**, reachable from the profile picker or Settings.
Changing a slider **recomputes that profile's shortlist immediately** (debounced ~300–500ms
after the user stops dragging, same pattern as `SearchViewModel`'s existing debounce — don't
recompute on every drag frame) rather than waiting for the weekly job; the weekly
WorkManager run still applies whatever the slider is currently set to.

1. **"Sticks to my taste" (s=−1) ↔ "Surprise me" (s=+1)** — shortlist-assembly stage only,
   doesn't touch the core scoring formula:
   - wildcard slot count: `round(1 + s)` → 0 at s=−1, **1 at s=0 (matches spec exactly)**, 2 at s=+1
   - diversity cap (max per genre): `round(2 − s)` → 3 at s=−1 (looser), **2 at s=0 (matches
     spec)**, 1 at s=+1 (tightest, forces most variety)
2. **"Recent favourites" (s=+1) ↔ "All-time favourites" (s=−1)** — adjusts the recency
   half-life on a log/exponential curve (half-life is multiplicative, not linear):
   `halfLifeDays = 180 × 4^(−s)` → **180 at s=0 (exact spec default)**, ~45 days at s=+1
   (recent-weighted), ~720 days at s=−1 (near all-time). Exact exponent base is an
   implementation choice — 4 is a suggestion, pick something that gives a noticeable but not
   absurd range; the s=0 default must land on exactly 180.
3. **"Personal match" (s=−1) ↔ "Popular & well-reviewed" (s=+1)** — shifts weight between
   `affinityMatch` and `tmdbQuality` in the scoring formula; `freshness`'s 0.15 stays fixed
   (it's not really part of this axis). **s=0 must reproduce affinityWeight=0.70,
   qualityWeight=0.15 exactly** (today's spec). These are relative ranking weights, not a
   probability distribution — they don't need to sum to 1, so pick a monotonic mapping with
   both weights staying positive across the full range (e.g. affinity roughly 0.85→0.40,
   quality roughly 0.05→0.35 as s goes −1→+1) — exact curve is an implementation choice as
   long as s=0 hits the spec values precisely and both extremes stay sane/positive.
4. **Family scope only: "Everyone's happy" (s=−1) ↔ "Average taste wins" (s=+1)** — replaces
   the fixed 0.5×mean + 0.5×min blend with `meanWeight = 0.5 + 0.5×s`, `minWeight = 1 −
   meanWeight` → pure least-misery at s=−1, **exact 0.5/0.5 spec default at s=0**, pure
   average-taste at s=+1.
   **UI-home decision, RESOLVED by Kev 2026-08-20:** this slider is only shown/relevant at
   all when the account has **2 or more profiles** — a single-profile account never has a
   family scope, so the slider would be meaningless clutter for most of this app's actual
   users (family size varies). Hide it entirely when `profileCount < 2`; when hidden, its
   effective value stays at the neutral default **s=0** (i.e. hiding it never silently
   changes scoring — if a second profile gets added later, the family blend behaves exactly
   as originally spec'd until someone actually touches the slider). Where it lives when
   visible (shared app-level setting vs. shown alongside the who's-watching chip row) is
   still the build agent's call — the *visibility gating* is now settled, the exact UI
   placement isn't.

5. **Suggestion count — not a taste slider, a literal count control (Kev, 2026-08-20, design
   corrected same day after Kev proposed a better mechanism — see below).**
   Separate from the four −1..+1 taste sliders above: a per-profile **integer**, default 30
   (already the fixed `RecommenderSpec.SHORTLIST_TARGET_SIZE` fallback — this control
   overrides it per-profile rather than replacing the constant). Directly sets
   `ShortlistConfig.targetSize` for that profile's personal shortlist.

   **The slider's max is the profile's real eligible-candidate count, not a guessed fixed
   number.** `RecommendationRepository.refreshProfileShortlist` already computes an `eligible`
   pool (post-dedup, post-watched/listed-exclusion, post-age-cap) before scoring/assembly —
   that count IS the true ceiling for this profile right now, no need to invent one. Persist
   it (e.g. alongside the profile's slider prefs) each time a refresh runs, and have the "Tune
   my picks" screen read that **last-known** count to set the slider's max — not a live fetch
   on screen-open (extra network calls just to view Settings would fight the app's
   offline-first design; the persisted number is already refreshed on every weekly job run
   and every slider-triggered recompute).

   This ceiling drifts day to day as availability changes — handle that by storing what the
   user *asked for* (their chosen slider value) separately from what they *get*: at refresh
   time, the actual target passed to `ShortlistAssembler` is
   `min(userRequestedCount, currentEligibleCount)`. A shrunk pool this week doesn't erase the
   user's original request — if the pool recovers next week, they're back to their full
   chosen count automatically, no re-entry needed.

   **Edge case, must be handled gracefully, not crash or show an inverted range:** if the
   eligible count is ever below a sane minimum (e.g. a very new profile, or heavily filtered
   services), the slider's min must adapt too — don't present a slider where min > max. A
   reasonable floor is `min(4, currentEligibleCount)`; if the eligible count is 0, disable the
   slider with a short explanatory message rather than rendering a broken control.

   UI: a slider on the same "Tune my picks" screen, for visual consistency with the four taste
   sliders already there (Kev offered either a numeric field or a slider; a slider fits the
   screen's existing paradigm better on mobile — flag if a numeric field is actually preferred
   instead).

   **Scope boundary, deliberate not an oversight:** this control only affects a profile's
   own personal shortlist. Family-scope shortlists stay at the fixed default (30) for now
   — tying "how many" to any one profile's personal preference doesn't make sense for a
   blended family list, and Kev didn't ask for a separate family-scope count control.
   Revisit only if asked.

**Storage:** per-profile slider values, new columns/table on `Profile` or a small companion
table — whichever fits the existing schema more cleanly, your call.

**Testing requirement, not optional:** fixture tests must prove **s=0 on all sliders
reproduces bit-for-bit the same shortlist a build with no sliders at all would have produced**
— this is the concrete acceptance bar for "sliders didn't silently change default behaviour."

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
   quick add-to-list on each card. **Results are restricted to titles currently available on
   a subscribed GB service** (see §5a "Search & watchlist availability gating", 2026-08-19) —
   not a general catalog finder.
6. **Log-watch sheet** — date (default today), profile multi-select chips, optional
   per-profile thumbs right in the sheet. One tap for the common case.
7. **History** — reverse-chronological, filter by profile, tap to edit/delete an event or
   change ratings.
8. **Settings** — services toggle list, profile management, JSON backup/restore, About &
   attributions.

Polish bar: Coil crossfade placeholders, shared-element-style transition into details,
Material 3 dynamic colour with dark default, predictive back. Feels like a streamer, not a form.

### 5a. Visual design system (locked in after Kev's M2a review, 2026-08-17)

M2a's first pass ("primary school", "a bit dull", "not on par with Netflix/Prime") was built
against prose ("polished", "feels like a streamer") with no concrete tokens — that's the root
cause, not the model. Replacing the earlier design-tone note with actual specification so the
next build pass has something unambiguous to execute against:

- **Colour:** near-black background (`#0B0B0D`–`#121214` range, not Material's default dark
  grey), one confident accent colour used sparingly (buttons, active states, badges/ticks) —
  not yet chosen; feature-builder proposes 2–3 candidate swatches (avoid Netflix red / Disney
  blue directly) and Kev picks. Surfaces gain depth from gradient scrims over imagery
  (dark-to-transparent behind titles on hero/backdrop images), not flat elevated cards.
- **Typography:** bold/condensed display type for titles and section headers, restrained body
  text; imagery-to-text ratio should visibly favour imagery — a screen with more text than
  poster art is wrong for this app.
- **Layout:** posters are 2:3 thumbnails in edge-to-edge `LazyRow` carousels with tight
  inter-card spacing, no Material card borders/shadows/boxed backgrounds around them. Minimal
  chrome — a transparent/scrim top bar over hero content, not a solid Material app bar wherever
  imagery is present.
- **Home structure:** one continuous scrollable feed of stacked horizontal carousels (My List /
  For You / Family Night / Popular), matching Netflix/Prime directly — not per-section tabs.
  (Disney+/Apple TV's tabbed-sections pattern is a legitimate alternative but adds real nav
  complexity; revisit only if the single feed becomes unwieldy once M3 adds more rows.)
- **Avatars:** keep emoji as one option, not the default aesthetic — add neutral choices
  (initials on a solid tile, single muted colour) and pull the 12-swatch palette in toward
  restraint, not primary-colour brightness.
- **Motion:** Coil crossfade on image load, subtle scale-on-press for poster cards, and pull
  the M4-planned shared-element-style transition into title details forward if it's cheap to
  do now rather than waiting — first-impression screens (Home → details) matter most for "does
  this feel dazzling" and are worth front-loading polish on.

### Known defects from M2a review (fix before/within M2b, 2026-08-17)

- **Services picker has no filter.** With the full GB provider list it's a long unsearchable
  list — add a search/filter field (substring match on provider name is enough; no need for
  true fuzzy matching).
- **Re-entering onboarding from Settings has no way back.** Settings → "Services & attribution
  setup" flips `onboardingComplete` false and drops the user into the full onboarding flow
  (starting at the attribution/"Welcome" screen) with no back/close affordance — if they didn't
  mean to redo the whole flow, they're stuck. Fix: onboarding entered from Settings needs a
  visible back/close action, and arguably should jump straight to the services step rather than
  replaying attribution + profile creation.
- **"Who's watching?" doesn't explain the model.** Unclear from the screen alone whether to
  create one profile per family member or a single shared "Family" profile. Fix: subtitle copy
  clarifying — one profile per person (up to 10); selecting several at once (for family movie
  night / the family shortlist scope) happens elsewhere (log-watch sheet, Home's who's-watching
  chips), not by creating a group profile.

### Post-M2b decisions (Kev's review, 2026-08-19)

- **Accent colour: `AccentObsidian` (`#8B5CF6`) is now the default**, not Ember. Deeper/richer
  violet than Orchid, same family, pulled toward saturation rather than Orchid's lighter lift.
  Clears WCAG AA against `Ink` (~4.6:1), consistent with the other three candidates.
- **Accent becomes a user preference, not a fixed build-time token.** Add it to
  `UserPreferencesRepository` (DataStore) alongside `onboardingComplete`/`activeProfileId`,
  default `OBSIDIAN`. Settings gets a new row: the four candidates (Ember/Aurora/Orchid/
  Obsidian) as tappable swatches with a checkmark on the active one; picking one updates the
  whole app's theme live via recomposition. `Theme.kt`'s old fixed `val Accent = AccentEmber`
  goes away in favour of reading the stored preference.
- **Home's missing personalised row, fixed properly.** M2b omitted the *For You*/*Family Night*
  rows entirely rather than showing §4's cold-start placeholder — a plan-instruction gap on
  Kev's orchestrator's part, not a bad call by the build agent given what it was told. Fix: a
  **"For You" section is always visible on Home**, never omitted. Two states apply depending on
  whether M3 has shipped yet:
  - **Pre-M3 (now):** M3's scoring doesn't exist yet regardless of how much a profile has
    logged, so the row shows an honest "coming soon" message — something like "We're still
    learning what you like — personalised picks arrive in a future update," with a CTA into
    logging a watch (Search or the log-watch sheet). Do **not** reuse §4's "not enough watched
    yet" cold-start copy here — that specific wording implies logging more will unlock it today,
    which isn't true pre-M3 even for a profile with 20 logged titles.
  - **Post-M3:** replaced by §4's real behaviour — under 5 logged events → "Popular on your
    services" labelled row (already built); 5+ → actual scored picks. M3's own milestone work
    should retire the pre-M3 placeholder copy above.

### Search & watchlist availability gating (Kev's review, 2026-08-19)

M2b's `SearchRepository` was built as a general, unfiltered TMDB catalog finder ("PLAN.md §5
screen 5 is a title finder" — its own kdoc) on the reasoning that you should be able to
log/add anything you've watched, streaming or not. **That reasoning was never actually
validated with Kev and turned out to be wrong** — his stated intent (and the app's whole
premise) is "only show me what's available on the services I actually pay for." Orchestrator
error: this was represented as a deliberate, settled decision in the M2b report/summary
without being flagged as an open question. Correcting it now:

- **Search results are filtered to GB availability on a subscribed provider.** TMDB's
  `/search/multi` has no provider-filter parameter (only `/discover` does, and it doesn't take
  a free-text query), so this can't be done in one API call — it's search-then-check:
  1. Run `/search/multi` as today.
  2. For each result, resolve its GB watch-provider availability — reuse cached data where a
     title's already been detail-fetched (search, discover, or a previous view all populate
     the same `Title`/`ProviderAvailability` rows); otherwise fetch it, throttled at the
     existing 4 req/s.
  3. Drop any result with no GB availability on a subscribed provider. Only show what survives.
  - **Expected UX consequence:** results settle progressively rather than appearing instantly
    — for an uncached page of ~20 results at 4 req/s that's a few seconds of results filling
    in/dropping out after the debounce fires. This is the accepted trade-off Kev chose over
    the two alternatives (cache-only filtering — inaccurate, hides genuinely-available titles
    that just haven't been checked yet; or an unfiltered search with a separate "on your
    services" filter chip — Kev wants the restriction as the default, not opt-in).
  - **Cancellation matters:** in-flight availability checks for a stale query must be
    cancelled when a new one starts (extend `SearchViewModel`'s existing dedupe/cancellation
    pattern), or a slow-resolving old batch can overwrite a newer query's results.
- **Adding to the Want-to-Watch list is gated the same way** — blocked (with a clear inline
  message) unless the title currently has GB availability on a subscribed provider. Applies at
  `WatchlistRepository.add()`/`toggle()`, reusing the same availability-resolution logic as
  search rather than duplicating it.
- **Existing watchlist entries are NOT auto-removed if they later lose availability — confirmed
  by Kev, 2026-08-19, with two refinements (queued as M2g, below).** A title added while
  available that later leaves a service stays on the list rather than silently disappearing —
  matches how Netflix/Prime's own saved lists behave. The gate only applies at add-time.
- **Search with zero subscribed providers (queued as M2g):** currently returns nothing, with
  no explanation. Confirmed by Kev: add textual feedback (same spirit as Home's cold-start
  message) explaining that no services are selected, rather than a silent empty screen.
- **Unavailable watchlist entries — visual treatment (queued as M2g):** Kev wants them
  **greyed out / visually dimmed** directly on the card wherever they appear (Home's My List
  carousel, the full My List/watchlist screen) — not just discoverable by tapping into
  details — plus an **explicit remove/clean-up action** reachable from that same list context,
  not requiring a detour through the details screen first.
- **Logging a watch and History are explicitly NOT gated.** These are a historical record —
  you watched something, possibly on a service that's since dropped it, a rental, a disc, a
  friend's account — availability at watch-time is irrelevant to whether you can log it.
  Do not extend this restriction there; it only applies to Search and the watchlist add path.

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
  `emulator -avd family_test -no-window -no-audio -no-boot-anim -no-snapshot -gpu swangle -memory 2048`.
  `/dev/kvm` access confirmed (Kev is in the `kvm` group on agent101). **`-gpu swangle` is
  required** — it is the fix for the hero/gradient-scrim SIGSEGV (§8). agent101 is itself a KVM
  guest with no GPU render node, so `-gpu host` is not an option; software rendering only.
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
- **Emulator SIGSEGV on hero/gradient-scrim rendering — ✅ ROOT-CAUSED AND FIXED 2026-08-19**
  (`toolchain-setup`). **The fix is to boot with `-gpu swangle` instead of
  `-gpu swiftshader_indirect`.** This is a real fix, not a mitigation: the crashing code is no
  longer loaded at all. `docs/PREVIEW.md` §1 carries the updated standard boot command.

  **Root cause.** With `-gpu swiftshader_indirect`, guest GLES is served by the emulator's
  bundled *SwiftShader GLES* driver
  (`~/android-dev/sdk/emulator/lib64/gles_swiftshader/libGLESv2.so`, reporting
  `OpenGL ES 3.0 SwiftShader 4.0.0.1`) — Google's long-deprecated SwiftShader GL backend, which
  upstream abandoned in favour of SwiftShader Vulkan. A core dump of a live crash
  (signal 11, `si_code=1 SEGV_MAPERR` — address not mapped; fault address `0x5af3a6ca0000`,
  exactly page-aligned, i.e. one page past the end of a heap allocation) shows `RIP` sitting in
  anonymous, non-file-backed executable memory — SwiftShader's Reactor/Subzero **JIT-generated**
  code — with the stack immediately beneath it returning into `libGLESv2.so` (`+0xd49fc`,
  `+0xd4b03`, `+0xd4def`, `+0x15c1ab`). Register state at the fault describes a strided surface
  walk: `R14 = 4` (bytes per pixel), `RBX = 0x77a` (1914 px), `RDX = RSI = 0x1de8` (7656 bytes =
  1914 × 4, the row stride), plus three heap surface pointers. So a JIT-compiled texture
  sampling/blend routine runs off the end of its buffer while compositing the large scaled
  backdrop image under the multi-stop alpha gradient in `ui/components/Scrims.kt`. It is an
  upstream out-of-bounds bug in dead-end code — **not an app bug**, and not memory pressure
  (the box had ~11GB free; it is a genuine SIGSEGV, not an OOM SIGKILL).

  **The fix.** `-gpu swangle` routes guest GLES through **ANGLE** (2.1.17841) onto **SwiftShader
  Vulkan 5.0.0**, the actively maintained backend; `libGLESv2.so` from `gles_swiftshader` is
  never loaded. Rendering is pixel-identical at native 1080x2400 and the guest gains GLES 3.1
  (was capped at 3.0). Boot time and RAM are unchanged.

  **Evidence (identical scripted soak both sides — relaunch app, scroll Home hero, open title
  details, scroll the backdrop, back):**
  | GPU mode | Result |
  |---|---|
  | `-gpu swiftshader_indirect` | crashed on cycle 1 and cycle 3 of two separate runs |
  | `-gpu swangle` | **60 cycles across 3 runs, zero crashes** |

  **⚠️ The flag must be passed on the command line.** Setting `hw.gpu.mode = swangle` in the
  AVD's `config.ini` is a false positive — the emulator logs `gles_mode_selected:swangle` but
  the guest still loads SwiftShader GLES 4.0.0.1 and crashed on the first cycle. `config.ini`
  has been left at its original value; always pass `-gpu swangle` explicitly.

  **Also ruled out.** `-gpu swiftshader` is not a separate code path — the emulator normalises
  both spellings to the same renderer (`gpu_mode_requested: swiftshader` is logged for
  `swiftshader_indirect` too). `-gpu host` is impossible on agent101: the box is itself a KVM
  guest with no GPU render node (`/dev/dri/renderD*` absent) and no X/Wayland session. A newer
  emulator is not available — `sdkmanager --list` reports 37.1.11 as the current stable release
  with no update pending, and the installed `system-images;android-35;google_apis;x86_64` is at
  the latest revision (9); since the bug is host-side, a different guest image would not help
  regardless. The old `-memory 3072 -skin 720x1600` reduced-resolution recipe is obsolete and
  should not be used — it never worked, and native resolution is now stable.

  **Untested alternative if `swangle` ever regresses:** `-gpu lavapipe` (Mesa lavapipe for
  Vulkan + auto-selected software GLES) is also bundled with this emulator build.

  Real Android hardware was never affected by any of this.
- **Stretch (post-M5, only if credit remains):** embedded trailer player, episode-level TV
  tracking (v1 tracks TV at series level), Play-Feature-style "leaving soon" via provider-TTL
  diffing.
- **Home hero banner showing an unavailable title — ✅ ROOT-CAUSED 2026-08-19 (`feature-builder`).**
  Kev found Spider-Man: No Way Home as Home's hero despite zero confirmed UK availability.
  Live on-device DB inspection (same `run-as sqlite3` technique as M2d) ruled out **both**
  candidate code-bug hypotheses: `subscribedProviderIds` was not empty at fetch time (the
  cached discover page's queryHash encoded the correct 6-provider set), and the 24h cache was
  not stale (~21.4h old, fetched with that same already-finalised provider set). A live call
  to TMDB's own `/movie/634649/watch/providers` confirmed TMDB's GB data genuinely lists the
  title free on BBC iPlayer right now, matching our cached `ProviderAvailability` row exactly
  — this is the TMDB provider-data-quality risk already named above ("best effort" UK
  catch-up data), not an app filtering defect; not fixable app-side. The latent
  `toProviderParam()`-drops-filter-on-empty-list bug was real regardless and got fixed
  unconditionally (`discoverMovies`/`discoverTv` now return empty rather than falling back to
  an unfiltered page), plus discover-cache invalidation on subscribed-provider change was
  added as defense-in-depth. Full writeup in PROGRESS.md M2e.
- **Region should be configurable, not hardcoded — ✅ RESOLVED 2026-08-19 (`feature-builder`).**
  Kev's request: TMDB doesn't do IP geolocation — `watch_region=GB` is an explicit parameter our
  own code sends, never inferred from server location (confirmed in §5a's region-gating work) —
  so travelling abroad would otherwise leave the app silently stuck on UK-only results with no
  way to check local availability. Region is now a `UserPreferencesRepository` preference
  (default GB) threaded call-time into every discover/detail/search/watchlist call, with a
  Settings picker sourced live from TMDB's `/watch/providers/regions`. **Open design question,
  resolved:** subscribed-provider IDs are region-specific, so switching region does not
  auto-clear the subscribed list — it flags a dismissible inline notice in Settings prompting a
  revisit of the services picker instead. Full writeup, including a real cache-correctness fix
  this surfaced (stale cross-region provider data) and a live GB→US→GB verification against real
  TMDB data, in PROGRESS.md M2f.
