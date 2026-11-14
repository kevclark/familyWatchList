# 0013. Search load-more fills to 20 titles within 3 pages

- **Status:** Accepted
- **Date decided:** 2026-11-07
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Kev (2026-11-07, slapstick search on his phone) found More returned only 10 or 12 titles. One tap fetched one TMDB discover page per media type and the local checks (rent/buy-only, availability, age and unrated, TV keyword) dropped about half.

## Decision

alpha.18 (`1991ea0`): one load (first search or one More tap) keeps fetching TMDB pages until at least `TARGET_TITLES` = 20 titles pass the checks, `MAX_PAGES_PER_LOAD` = 3 pages are fetched, or TMDB has no more pages. A media type that runs out stops being asked. `ResultPage.pagesConsumed` lets the ViewModel advance `nextPage`. Titles on screen (`exclude` keys) and seen earlier in the load are dropped before any availability check. `hasMore` is true only while some media type has a further page. Emission stays progressive. TV with a person pages locally over the person's credits in chunks of 20 under the same rule.

## Alternatives considered

- Just raise the page size: TMDB fixes it at 20.
- Unbounded fill: would blow the request budget; the cap bounds it at 3 discover calls per type.

## Consequences

- A sparse query can still return fewer than 20 after 3 pages.
- The fill-until-visible idea was reused for New rows (ADR 0014).

## Source

[PLAN.md section 5g](../../PLAN.md), problem 1; "Queued for alpha.18" in [PROGRESS.md](../../PROGRESS.md); commits `27c3952`, `1991ea0`; constants in `DescriptiveSearchRepository.kt`. Decided by Kev, 2026-11-07.
