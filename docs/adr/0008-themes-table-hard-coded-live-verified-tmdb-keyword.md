# 0008. Themes table: hard-coded, live-verified TMDB keyword ids

- **Status:** Accepted
- **Date decided:** 2026-11-07
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The generic keyword lookup finds near-empty tags for everyday words: "london" is 14 films while "london, england" is 1,115; "dystopian" is 6 films while "dystopia" is 1,032. Kev also asked what TMDB tags match a gritty London spy feel.

## Decision

A themes section in `PhraseTable` (alpha.18, `1991ea0`): a `ThemeTerm` maps one or more everyday phrases to a fixed list of TMDB keyword ids forming one OR group. Separate themes, genres, people and generic keywords AND together. Ids are hard-coded and each was verified on 2026-11-07 against live TMDB (`search/keyword`, then `discover` `total_results`); counts sit in a comment beside each entry. Themes with under about 30 films plus shows were dropped; 77 themes shipped. Examples: london maps to "london, england" (212), not the near-empty "london" (382039); gritty/dark/bleak maps to gritty | neo-noir | british noir; spy maps to five spy-related tags. Multi-word phrases match first ("cold war" is a theme, not the War genre). A phrase never maps to both a genre and a theme (pinned in `PhraseTableTest`). Themes bypass the generic keyword lookup, so they cost no network call. Judgement calls were flagged to Kev in PLAN 5g (gangster moved from Crime to a theme; "football" means soccer; musical stays a genre).

## Alternatives considered

- Smarter generic lookup (fuzzy or inferred): rejected, no inference.
- Looking ids up at runtime: slower and returns the near-empty tags.

## Consequences

- Ids can drift as TMDB changes; the comment counts date the verification.
- Kev maintains the table by reporting misses.
- Tests that used heist, zombie and dragons as unplaced words had to use non-theme words.

## Source

[PLAN.md section 5g](../../PLAN.md); "Queued for alpha.18" in [PROGRESS.md](../../PROGRESS.md); commits `4b1715b`, `ff3f748`, `1991ea0`. Decided by Kev, 2026-11-07, verified live by the orchestrator.
