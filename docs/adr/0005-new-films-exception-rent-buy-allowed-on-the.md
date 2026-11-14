# 0005. New films exception: rent/buy allowed on the New films row

- **Status:** Accepted
- **Date decided:** 2026-11-14
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Kev (2026-11-14) wanted a latest-releases line on Home, noting it is especially useful for Amazon and paid services: "what's just come out?". That conflicts with ADR 0004, because new releases are often rent/buy-only first.

## Decision

Two Home rows below the popular rows (alpha.20, `c205f9e`). TMDB has no "date added to provider", so "new" means recently released and on subscribed providers in the user's region. Films use `discover/movie` with `flatrate|free|rent|buy`; series use `flatrate|free`. Both sort by popularity with `vote_count.gte=10` (chosen from a live probe on 2026-11-14), and the window is 2 months (alpha.21, Kev 2026-11-14). **This is Kev's single sign-off exception to ADR 0004**: only `DiscoverRepository.newMovies` asks for rent/buy, and `DiscoverRepositoryTest` pins that every other discover call is unchanged. Paid-only films carry the existing RENT/BUY poster tag. To know a film is paid-only without a detail fetch per card, `newMovies` also fetches and caches the same query with `flatrate|free` only: a film in the wide page but not the narrow one is paid-only. One extra request per refresh.

## Alternatives considered

- Make rent/buy generally allowed in browsing: not requested.
- Fetch details per card to classify: too many throttled calls.
- A "new on service" feed: impossible, TMDB lacks the date.

## Consequences

- Home gains a + (watchlist) button on New cards that popular rows do not have.
- Series stay flatrate/free; returning seasons are out of scope.
- The cold-start For You row and the hero still draw only from popular rows.
- Cache keys carry the window (`discover_new_movie_2m`) so earlier windows are never mixed in.

## Source

[PLAN.md section 5i](../../PLAN.md); alpha.20 and alpha.21 in [PROGRESS.md](../../PROGRESS.md); commits `64c811e` (spec), `c205f9e`, `e309cb9`, `1b59b49`. Decided by Kev, 2026-11-14; transcript 2026-11-14 ("latest releases").
