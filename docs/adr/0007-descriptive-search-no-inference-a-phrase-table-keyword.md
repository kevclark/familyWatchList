# 0007. Descriptive search: no inference, a phrase table, keyword OR groups

- **Status:** Accepted
- **Date decided:** 2026-11-07
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

After weeks of use Kev said finding something for the family to watch was still hard: Search only did a title lookup, so "slapstick comedies", "action comedies" or "movies with Ryan Reynolds" returned nothing useful.

## Decision

Hard constraint from Kev: no inference or AI in the app; interpretation is deterministic word matching, and the only network calls are TMDB lookups. A pure, offline Kotlin parser (`QueryParser`, `PhraseTable`, `AgeFilter`) produces a structured query: media type words ("movies", "shows"), genres from a phrase table kept separately for movie and TV ids (action is 28 for movies and 10759 for TV), people via `search/person` ("with X", "starring X"), descriptive words via `search/keyword`, and ignored stop words. Genres combine with AND. Execution is TMDB discover with the region and subscribed providers, popularity sort and a vote floor, paged. If nothing structured is found it falls back to title search, and a title search also runs beside every structured one. Removable interpretation chips show what was understood and an "Ignored" line shows what was not. An age-filter chip row (U, PG, 12, 15, 18, Any) defaults to the profile's cap.
Follow-up (alpha.17, `8386d36`): TMDB's exact "slapstick" keyword is nearly unused, so a keyword word resolves to an OR group (`|`) of the exact name plus "<word> ..." variants, maximum 6. Exact-match picking alone returned 0 results in the emulator check.

## Alternatives considered

- LLM or embedding interpretation: forbidden by Kev's constraint and by the TMDB AI clause (ADR 0025).
- Title search only (status quo).
- TV with a person: TV discover has no cast filter, so TV uses the person's credits.

## Consequences

- Coverage is exactly the phrase table; Kev adds misses as found (ADR 0008).
- Structured search is flatrate/free only (ADR 0004).
- The live emulator check found three bugs that unit tests missed.

## Source

[PLAN.md section 5f](../../PLAN.md); M17 and its follow-ups in [PROGRESS.md](../../PROGRESS.md); commits `c203225`, `ecff3ec`, `b667014`, `4e3d42c`, `8386d36`, `239add7`. Decided by Kev, 2026-11-07 (transcript, first message of that day).
