# Architecture Decision Records

Decisions for Family Watchlist, reconstructed from `PLAN.md`, `PROGRESS.md`, code comments, `git log` and the project's session transcripts. Kev is the product decision-maker; Claude agents built the app under his direction.

- **SOURCED**: the decision text was found in a written source, linked in each ADR's Source section, with who decided it.
- **INFERRED**: reconstructed from code only, with no written decision found. Treat as unconfirmed until Kev agrees.

Where a decision changed later, the older ADR is marked Superseded and its Consequences say what the code does now. The code is the cross-check for what is true today.

| No. | Title | Status | Provenance | Date |
|---|---|---|---|---|
| [0001](0001-offline-first-the-ui-reads-room-only-tmdb.md) | Offline-first: the UI reads Room only, TMDB fills the cache | Accepted | SOURCED | 2026-08-16 |
| [0002](0002-recommender-deterministic-content-based-fully-local.md) | Recommender: deterministic, content-based, fully local | Accepted | SOURCED | 2026-08-16 |
| [0003](0003-age-cap-shared-rule-and-confirmed-certification-for.md) | Age cap: shared rule, and confirmed certification for capped Home rows | Accepted | SOURCED | 2026-08-21 |
| [0004](0004-no-rent-buy-in-browsing-paid-titles-only.md) | No rent/buy in browsing; paid titles only on explicit title searches | Accepted | SOURCED | 2026-08-22 |
| [0005](0005-new-films-exception-rent-buy-allowed-on-the.md) | New films exception: rent/buy allowed on the New films row | Accepted | SOURCED | 2026-11-14 |
| [0006](0006-search-is-search-then-check-gated-to-subscribed.md) | Search is search-then-check, gated to subscribed-service availability | Accepted | SOURCED | 2026-08-19 |
| [0007](0007-descriptive-search-no-inference-a-phrase-table-keyword.md) | Descriptive search: no inference, a phrase table, keyword OR groups | Accepted | SOURCED | 2026-11-07 |
| [0008](0008-themes-table-hard-coded-live-verified-tmdb-keyword.md) | Themes table: hard-coded, live-verified TMDB keyword ids | Accepted | SOURCED | 2026-11-07 |
| [0009](0009-more-like-this-uses-tmdb-recommendations-not-similar.md) | More like this uses TMDB /recommendations, not /similar | Accepted | SOURCED | 2026-11-07 |
| [0010](0010-weekly-refresh-re-anchored-one-time-unique-work.md) | Weekly refresh: re-anchored one-time unique work, not periodic work | Accepted | SOURCED | 2026-10-09 |
| [0011](0011-dismiss-is-a-lasting-negative-signal.md) | Dismiss is a lasting negative signal | Accepted | SOURCED | 2026-09-11 |
| [0012](0012-show-30-more-on-for-you-and-family.md) | Show 30 more on For You and Family Night | Accepted | SOURCED | 2026-10-09 |
| [0013](0013-search-load-more-fills-to-20-titles-within.md) | Search load-more fills to 20 titles within 3 pages | Accepted | SOURCED | 2026-11-07 |
| [0014](0014-show-more-on-new-rows-exact-paid-only.md) | Show more on New rows: exact paid-only detection and auto-advance | Accepted | SOURCED | 2026-11-14 |
| [0015](0015-emulator-must-boot-with-gpu-swangle.md) | Emulator must boot with -gpu swangle | Accepted | SOURCED | 2026-08-19 |
| [0016](0016-pinned-subagent-models-and-delegation.md) | Pinned subagent models and delegation | Accepted | SOURCED | 2026-10-09 |
| [0017](0017-commit-trailer-names-the-committing-agent-s-own.md) | Commit trailer names the committing agent's own model | Accepted | SOURCED | 2026-11-07 |
| [0018](0018-maximum-of-10-real-profiles-enforced-in-the.md) | Maximum of 10 real profiles, enforced in the repository | Accepted | SOURCED | 2026-08-16 |
| [0019](0019-region-defaults-to-gb-uk-but-is-a.md) | Region defaults to GB (UK) but is a preference, not a hardcode | Accepted | SOURCED | 2026-08-19 |
| [0020](0020-tmdb-requests-throttled-to-4-per-second.md) | TMDB requests throttled to 4 per second | Accepted | SOURCED | 2026-08-16 |
| [0021](0021-family-profile-persistent-profile-alongside-the-ad-hoc.md) | Family profile: persistent profile alongside the ad-hoc Family Night blend | Superseded by 0022 (in part) | SOURCED | 2026-08-21 |
| [0022](0022-family-becomes-a-fully-independent-profile.md) | Family becomes a fully independent profile | Accepted | SOURCED | 2026-08-22 |
| [0023](0023-backup-and-restore-one-versioned-user-data-json.md) | Backup and restore: one versioned user-data JSON via the Storage Access Framework | Accepted | SOURCED | 2026-08-16 |
| [0024](0024-tech-stack-kotlin-compose-room-manual-di-single.md) | Tech stack: Kotlin, Compose, Room, manual DI, single module | Accepted | SOURCED | 2026-08-16 |
| [0025](0025-no-runtime-ai-or-llm-the-tmdb-cache.md) | No runtime AI or LLM; the TMDB cache is never exported | Accepted | SOURCED | 2026-08-16 |
| [0026](0026-ratings-stay-a-3-point-up-neutral-down.md) | Ratings stay a 3-point UP / NEUTRAL / DOWN scale | Accepted | SOURCED | 2026-08-22 |
| [0027](0027-trailers-play-in-app-through-a-youtube-iframe.md) | Trailers play in-app through a YouTube IFrame wrapper | Accepted | SOURCED | 2026-08-22 |
| [0028](0028-explicit-room-migrations-schemas-exported-no-destructive-fallback.md) | Explicit Room migrations, schemas exported, no destructive fallback | Accepted | INFERRED | 2026-08-22 |
