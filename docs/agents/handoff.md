# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4C plan and implementation state.
Read AGENTS.md and its required linked pages before continuing.

## Current position and next action

Phase 4C-4 Photo Grid clearance is complete. The `SearchPanel.PhotoGrid` branch forwards the shell-supplied
physical-right and bottom clearances. `PhotoGridScreen` keeps its full-size `photo_grid_content` viewport and
clears physical right through the staggered grid's own absolute content inset. Tiles, the load-older control,
the paging-error surface, the up-to-date label, and the empty state clear physical right in LTR and RTL.
The full-screen error state keeps its full-size viewport and clears only its retry content.
The maintainer chose the content inset on 2026-10-05 after a per-tile click-bound inset was rejected: a tile is
opaque media and one click target, so a per-tile inset would leave an untappable strip inside every lane.
Tile media therefore no longer passes under floating chrome. This is the only Photo Grid deviation from the
row-underlay rule used by the DM, Home, and Search slices.
Bottom clearance extends the wide grid end spacing. The wide filter-chip dock clears physical right and sits
above supplied bottom obstruction, matching the Home timeline dock. Photo Grid has no measured dock height.
Chip scrolling, chip selection, and the add-hashtag entry keep their own owners. Clearance sets no chip travel.
Compact Photo Grid ignores both wide inputs and keeps its contextual-control placement and scroll clearance.
The DM, Home, and Search slices remain unchanged.

Focused `PhotoGridClearanceTest` passes: 9 tests. Existing suites pass unchanged: `PhotoGridScreenTest` 8,
`PhotoGridOwnerTest` 10, `SearchClearanceTest` 8, `HomeClearanceTest` 7, `DirectMessageScreenTest` 22,
`NavigationTest` 36.
Full `test assembleRelease` passes with 156 suites, 1,616 tests, zero failures/errors/skips, and successful
release assembly. A separate external `gradlew.bat clean installDebug` process ran in this worktree during the
first full gate and wiped the test results, so that run was repeated after the external build finished.
The external process was not stopped.
Tests verify viewport and tile bounds, sensitive-tile reveal bounds, continuation and retry callbacks, final-tile
reach above the dock and above bottom obstruction, dock size and clearance, chip and hashtag selection, branch
forwarding, retained grid position, and compact compatibility. Direct review found no unresolved required
findings. Document links and slice-only whitespace pass. No `problem_solver`, ADB, or live-server check ran.
One earlier full run reported an unrelated pre-existing flake in `MastodonIntegrationTest`
(`IOException: Gave up waiting for queue to shut down`). That suite passes 67 tests in isolation.

Next: Notifications and Profile follow as separate gates, each with its own recorded contract.
Do not activate vertical navigation or wire the wide DM action in a clearance slice.
The orchestrator remains implementation owner and Git operator. The maintainer prohibits `problem_solver` for this task.

## Last safe commit

`Add Photo Grid obstruction clearance`, based on `5579742` — `Add Search obstruction clearance`.
Resolve this checkpoint's hash from Git. Nothing was pushed.

## Limits and worktree caution

- Physical-device IME, physical foldable, API 29, live-server, signing, and TalkBack behavior remain unverified.
- No production floating-navigation geometry is approved. Do not invent clearance or fit values.
- The recipient finder is implemented. Its wide New conversation callback remains for the activation slice.
- A separate external `gradlew.bat clean installDebug` process ran in this worktree during this slice and removed
  shared test results and build outputs. It was not stopped and finished on its own. Recheck shared-output
  activity before later builds. That external install's device state remains unknown.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve the deleted `PhotoGridFeedViewModelTest.kt` and PNGs, untracked captures, scripts, and caches.
  The new Photo Grid clearance test uses a different name and must not restore that deletion.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
