# 0014. Show more on New rows: exact paid-only detection and auto-advance

- **Status:** Accepted
- **Date decided:** 2026-11-14
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The New films and New series rows (ADR 0005) needed paging, and a capped profile can have every page-1 title filtered out (15, 18 or unrated). An empty row hides together with its More card, which made later pages unreachable (found in the alpha.21 emulator check).

## Decision

alpha.21 (`1b59b49`, `ef3f1e2`):
- The last card is the M16 `ShowMoreCard` look ("Show more" over "Page 2 of 5"). Each tap loads the next TMDB page of the same query, appends, and hides when TMDB has no more. Pages are cached 24 h in `discover_cache`; `total_pages` is kept in a one-row `<queryHash>#pages` entry of the same table, so no schema change. Dedupe by TMDB id, age cap and dismissals apply to every page; a refresh drops any in-flight page.
- **Exact paid-only past page 1:** the narrow (`flatrate|free`) result is the wide one minus paid-only titles in the same order, so a title at wide rank r has narrow rank at most r. `newMovies(page = p)` reads narrow pages 1..p (earlier ones are cache hits) and flags a wide title paid-only only if it is on none of them. Cost: one wide and one narrow request per tap. Chosen over fetching narrow page p only, which would wrongly flag included films.
- **Auto-advance:** when a load yields no visible new title while TMDB has more pages, the next page is loaded (and certification-enriched) automatically until something is visible, TMDB runs out, or 3 pages were consumed. A row with visible titles never auto-advances, so uncapped profiles are unaffected.
- For capped profiles only, up to 20 cert-less candidates per row (and per More page) get `ensureFresh` after refresh (`88170dc`).

## Alternatives considered

- Narrow page p only: rejected, wrongly flags included films.
- A per-card detail fetch for paid detection: too costly.
- Show an empty row with its More card: rejected.

## Consequences

- Reads and merges stay inside the throttle and the 24 h cache.
- Capped profiles can still see sparse rows (the confirmed-cert rule is unchanged).

## Source

[PLAN.md section 5i](../../PLAN.md); alpha.21 in [PROGRESS.md](../../PROGRESS.md); commits `1b59b49`, `ef3f1e2`, `88170dc`, `755341c`. Decided by Kev (More on the rows), engineering calls by the builder and orchestrator (exact detection, auto-advance), 2026-11-14.
