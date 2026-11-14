# 0027. Trailers play in-app through a YouTube IFrame wrapper

- **Status:** Accepted
- **Date decided:** 2026-08-22
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The original plan opened trailers with an Intent to the YouTube app. Kev objected on 2026-08-22: jumping between apps while flicking through suggestions would be annoying, and asked for a lightweight in-app option. The first embed failed with YouTube Error 153.

## Decision

M4a-2 (`2f8daf3`) embeds the YouTube IFrame Player in a WebView. Error 153 was first blamed on missing Widevine, then correctly diagnosed on Kev's real phone as YouTube's Referer enforcement for embeds: the fix (`8939e11`) wraps the embed in a local HTML page with a real `<iframe>` loaded through `loadDataWithBaseURL` with an `https://` base origin. Later fixes handled fullscreen (own manual control, landscape lock, `fs=0`) across M8 and M9.

## Alternatives considered

- Intent to the YouTube app: original plan, rejected by Kev.
- A local media player (mp4, VLC): trailers are YouTube keys, not files.
- A third-party YouTube player library: kept as a stretch only.

## Consequences

- Playback cannot be verified on the headless emulator, and the real phone is the acid test.
- Fullscreen behaviour needed several real-device iterations (alpha.3 to alpha.7).

## Source

[PLAN.md section 1](../../PLAN.md) and section 8 (Error 153 diagnosis); M4a-2, M5, M8, M9 in [PROGRESS.md](../../PROGRESS.md); commits `0c7fa59`, `2f8daf3`, `caa6114`, `8939e11`. Decided by Kev, 2026-08-22; diagnosis by feature-builder.
