# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05

## Continuation pointer

Read [tasks/force-layout-direction.md](tasks/force-layout-direction.md) for the Display layout
direction switch task state. Read AGENTS.md and its required linked pages before continuing.
Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the separate Phase 4C plan.

## Current position and next action

Phase 4C-5 is in progress. 4C-5a (geometry and fit policy) is complete. Next: 4C-5b (capsule and
action placement + wide DM action wiring).

The navigation fit policy is implemented in `LargeLayoutMode.kt`. `calculateNavigationFit` builds
safe regions from window geometry, system bar insets, gesture insets, cutout bounds, and separating
or occluding folding features. It selects vertical navigation only when one safe region contains
the full controls (64 dp width, 360 dp height) and leaves 360 dp useful content width. Otherwise
it falls back to compact. IME height does not affect the fit. Pane and detail selection remain
independent.

Approved maintainer decisions: useful-content minimum = 360 dp, placement gap = 8 dp, fit
fallback = compact. The anchor is decoupled from LTR/RTL with two new display settings toggles
(tablet default left, compact-wide default right).

## Last safe commit

`Add navigation fit policy`, based on `8c5d879` — `Add Profile obstruction clearance`.
Resolve this checkpoint's hash from Git. Nothing was pushed.

## Limits and worktree caution

- Device rendering, real right-to-left language support, TalkBack, signing, and physical foldable
  behavior remain unverified. Beeline has no right-to-left translations today.
- The connected simulated device is the goalpost (Galaxy Z Fold 8). The simulator does not have a
  correctly sized internal screen. Internal-screen behavior is verified through JVM tests at tablet
  dimensions. Both form factors must be tested: outside screen (compact-wide phone) and inside
  screen (tablet square sized). The outer screen must show the new vertical navigation layout.
- Physical-device IME and API 29 behavior remain unverified.
- One pre-existing flake exists: `MastodonIntegrationTest cancelingTimelinePageCancelsRequestAndAllowsRetry`
  can fail with `IOException: Gave up waiting for queue to shut down`. It passes in isolation and is unrelated
  to this work. Record it separately; do not weaken it.
- A separate external `gradlew.bat clean installDebug` process ran in this worktree during an earlier slice and
  removed shared test results and build outputs. It was not stopped and finished on its own. Recheck shared-output
  activity before later builds. That external install's device state remains unknown.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve the deleted `PhotoGridFeedViewModelTest.kt` and PNGs, untracked captures, scripts, and caches.
  The Photo Grid clearance test uses a different name and must not restore that deletion.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
