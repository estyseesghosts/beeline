# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-04

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4C plan and implementation state.
Read AGENTS.md and its required linked pages before continuing.

## Current position and next action

Phase 4C-4 Search clearance is complete. The existing Search branch forwards shell-supplied physical-right
and bottom clearances. Search keeps its full-size content viewport, both list viewports, outer row extents,
dividers, empty states, and loading indicators. Hashtag post rows, account rows, the continuation item,
category chips, and the search field clear physical right in LTR and RTL. Account rows clear through the
list's absolute content inset because the account row is one click target.
Bottom clearance adds to the wide result scroll range only. The wide dock keeps its bottom-start placement,
its spacing, and its measured height, so the dock and the search field do not move.
The maintainer requires that bottom clearance must not move the dock or the field. Wide Search applies no
IME field inset. Chip scrolling and selection remain unchanged. Compact Search ignores both wide inputs and
keeps its IME-aware control placement and scroll clearance. The DM and Home slices remain unchanged.

Focused `SearchClearanceTest` passes: 8 tests. Existing suites pass unchanged: `HomeClearanceTest` 7,
`DirectMessageScreenTest` 22, `HomeFeedTest` 37, `NavigationTest` 36, `SearchOwnerTest` 8,
`SearchPanelRestorationTest` 3.
Full `test assembleRelease` passes with 155 suites, 1,607 tests, zero failures/errors/skips,
and successful release assembly.
Tests verify viewport and divider bounds, interaction and dock clearance, final-content reach,
synthetic IME open and close, chip selection, branch forwarding, retained list position, and compact
compatibility. Direct review found no unresolved required findings. Document links and slice-only
whitespace pass. No `problem_solver`, ADB, or live-server check ran.
One full run also reported an unrelated pre-existing flake in `MastodonIntegrationTest`
(`IOException: Gave up waiting for queue to shut down`). That suite passes 67 tests in isolation, and the
recorded rerun gate is green.

Next: investigate Photo Grid under its own bounded destination-clearance contract. Notifications and Profile
follow as separate gates. Do not activate vertical navigation or wire the wide DM action in a clearance slice.
The orchestrator remains implementation owner and Git operator. The maintainer prohibits `problem_solver` for this task.

## Last safe commit

`Add Search obstruction clearance`, based on `9717c49` — `Add Home obstruction clearance`.
Resolve this checkpoint's hash from Git. Nothing was pushed.

## Limits and worktree caution

- Physical-device IME, physical foldable, API 29, live-server, signing, and TalkBack behavior remain unverified.
- No production floating-navigation geometry is approved. Do not invent clearance or fit values.
- The recipient finder is implemented. Its wide New conversation callback remains for the activation slice.
- Process checks found no external Java/Gradle build before this slice's gates. No external process was stopped.
- Recheck shared-output activity before later builds. The prior external install's device state remains unknown.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve deleted Photo Grid test/PNGs, untracked captures, scripts, and caches.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
