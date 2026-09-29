# Handoff

**Status:** Phase 3A-2 is implemented in the worktree and remains uncommitted.

The task-state file is `docs/agents/tasks/beeline-0.4.0.md`.

## Current position

`AppTypography.kt` exposes semantic roles for post body, post author, post metadata, and tabs.
Existing Material 3 styles remain the source of all role metrics. Post content and Home timeline
tabs use role access without changing colors, spacing, or layout ownership.

## Verification

`AppTypographyTest` passed 1 test. The affected suite ran 111 tests; 109 passed and two baseline
`NavigationTest` draft failures remained at lines 820 and 803. `logs/BUGS.txt` entry
`20260923-0B` records the same failures. `lintDebug` passed. The architecture audit passed with
616 findings and zero regressions. `diff --check` passed for the allowed documentation paths.

The full gate remains known red because Phase 10 owns the existing failures. Last safe commit is
`5b8d98d`; prior history `8e1c046` and `5b8d98d` is accurate.

## Device evidence — 3A-2

On `emulator-5554` (Android 16 SDK 36, 1848x2448, density 480, override 616), `MainActivity`
was focused with keyguard false, IME hidden, and light system bars. Font scale was 1.0. Package
version 0.2.8 (2008) was debuggable; first install and last update were 2026-09-28, with no
reinstall.

`screen.png`, `screen_top.png`, and `screen_restored.png` showed fully rendered authors, bodies,
and timestamps without overlap. Home, Local, and Federated tabs were legible and the selected tab
was visible. Floating chrome overlapped images by design, but text stayed readable. Compact
portrait light geometry passed. The swipe scrolled only. Back opened Firefox at
`mstdn.ca/oauth/authorize`; restarting `MainActivity` recovered Home with state preserved. This
is a behavior note, not a typography failure. No sign-out was performed. `@ctr@mstdn.ca` was
preserved as the tested account identity.

Unverified: Search/Profile, dark or pure-black theme, wide/foldable layouts, 200% font scale,
keyboard, and TalkBack. The screenshots are geometry evidence only. Temporary screenshots are
untracked and excluded.

## Next slice

Phase 3A-3 or Phase 3B.

## Preservation

Only the task-state file and this handoff were updated. Production and test source, the staged
classic navigation document, `.opencode` changes, images, helpers, caches, logs, and the committed
Photo Grid test deletion remain unrelated. This subagent does not stage, commit, or push.
