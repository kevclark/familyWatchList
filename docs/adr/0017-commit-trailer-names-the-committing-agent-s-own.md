# 0017. Commit trailer names the committing agent's own model

- **Status:** Accepted
- **Date decided:** 2026-11-07
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

M15 and M16 builder commits were labelled `Co-Authored-By: Claude Opus 5.5` because the orchestrator pasted its own trailer into the builder brief, but the builder is Sonnet 5.5.

## Decision

Kev (2026-11-07) wants accurate labels: a commit made by a subagent names that subagent's model (feature-builder Sonnet 5.5, toolchain-setup Opus 5.5, read from `.claude/agents/*.md`), not the orchestrator's. The already-pushed history was left as it is. Separately, Kev (2026-11-14) said his global commit-message rule omits Claude attribution, but this project keeps the `Co-Authored-By` line (plus `Claude-Session`) as an exception because it already uses it. The global rule's other parts still apply: subject at most 71 visible characters, body lines at most 71, no Jira prefix.

## Alternatives considered

- Rewrite history to correct the labels: declined, history left as is.
- Drop attribution: declined for this project.

## Consequences

- Commits `a1ad62b`, `ec239e2`, `8da8a2a`, `8155048` and `efbaa2f` carry the wrong "Opus 5.5" label. The About-screen stats do not depend on trailers.
- Every agent brief that asks for commits must carry the right trailer.

## Source

Kev's statements, 2026-11-07 (labels) and 2026-11-14 (attribution exception; session transcript, git-commit-message skill invoked that day), recorded in the orchestrator's memory notes (`feedback-subagent-commit-trailer`). Cross-checked against `git log` trailers for the five commits above. [CLAUDE.md](../../CLAUDE.md).
