# 0004. No rent/buy in browsing; paid titles only on explicit title searches

- **Status:** Accepted
- **Date decided:** 2026-08-22
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

TMDB's availability data covers rent and buy as well as subscription, but the app's premise is "what is on the services we already pay for". Kev found Central Intelligence missing from Search although it was rentable on Amazon Video and Apple TV.

## Decision

Kev's call, 2026-08-22 (first post-alpha decision, M6): Search and the watchlist add-gate count BUY/RENT on a subscribed provider as available, and such titles carry a "Rent/Buy" badge. Everything that browses or recommends stays `flatrate|free` only: Popular, For You, the recommender's candidate pool, and later structured search and More like this. The app never proactively nudges spending. `ProviderKind` gained BUY and RENT appended at the end. TMDB gives no prices, so the UI never implies one. Browsing code uses a separate path (`isPaidOnlyCached` and `passesBrowseGate`) so the widened check cannot leak into recommendations. M17 (`8386d36`, 2026-11-07) extended the rule: structured descriptive search is flatrate/free only and drops rent/buy-only titles, while a plain title search keeps rent/buy.

## Alternatives considered

- Keep flatrate/free everywhere (original): rejected, Kev wanted to find titles he knew existed.
- Rent/buy everywhere: rejected, it nudges spending.

## Consequences

- One exception exists, ADR 0005.
- Paid availability needs no new network call, since the same `/watch/providers` response carries it.
- M6 exposed a primary-key data-loss bug when a title is both FLATRATE and RENT (`d414035`) and needed a migration that reset `fetchedAt` to re-trip stale provider caches (`8203297`).

## Source

[PLAN.md section 5a](../../PLAN.md), "Paid (rent/buy) titles included in Search + watchlist, Kev's call, 2026-08-22"; M6 in [PROGRESS.md](../../PROGRESS.md) and M17 follow-ups; commits `768bd39`, `b75d62a`, `d414035`, `317bbc5`, `8386d36`. Decided by Kev.
