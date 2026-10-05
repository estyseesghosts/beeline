# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4C plan and implementation state.
Read AGENTS.md and its required linked pages before continuing.

## Current position and next action

Phase 4C-4 Profile clearance is complete. That closes every destination-clearance gate: DM inbox, DM conversation
and editor, Home, Search, Photo Grid, Notifications, and Profile. The `Destination.Profile` branch of
`ShellDestinationContent` forwards the shell-supplied physical-right and bottom clearances to `ProfileScreen`.
Compact Profile ignores both inputs and keeps its measured end clearance and its floating bottom chip row. In wide
layout the end-of-list clearance is `LargeBottomDockClearance` plus the supplied bottom obstruction.

A profile row is `PostRow`, a transparent column with separate state and footer items. Its content therefore takes
an absolute right padding while the item divider keeps the full width for visual underlay. This matches the Home row
precedent. It does not use the Photo Grid tile inset or the Notifications card decision.

The wide summary layout has two columns, and only one of them reaches the pane physical right edge. That column
changes with the layout direction, because the summary `Row` mirrors in RTL. Clearance applies to the timeline column
in LTR and to the summary column that holds the header and details in RTL. The other column keeps its usable width.
Without the summary the timeline fills the pane and always clears. The wide category dock keeps its bottom-start
placement and `LargeBottomDock` spacing, clears physical right, and sits above the supplied bottom obstruction. Chip
travel and selection keep their own owners. Production clearances remain zero. Floating navigation remains inactive.

Focused `ProfileClearanceTest` passes: 11 tests. Existing suites pass unchanged: `ProfileScreenTest` 24,
`ProfileViewModelTest` 28, `ProfileTimelinePagerTest` 10, `WideNavigationTest` 9, `NavigationTest` 36.
Full `test assembleRelease` passes with 158 suites, 1,635 tests, zero failures/errors/skips, and successful release
assembly. No external Gradle build was active before the runs.
Tests verify full viewports and item-divider underlay, row and interaction bounds, the mirrored wide columns, header
message and follow controls, dock start placement with right and bottom clearance, final-row and footer reach,
load-older and retry callbacks, the error, empty, and null-account states, the Featured title, pinned rows, details
fields, the inline category row, branch forwarding, retained category selection and scroll position, and compact
compatibility in both layout directions. Direct review found no unresolved required findings. Document links and
slice-only whitespace pass. No `problem_solver`, ADB, or live-server check ran.

Next: record the 4C-5 activation contract, then integrate safe placement and activate adaptive navigation. Two
decisions need maintainer input there: the useful-content minimum and any placement gap, and the physical-right
anchor against the RTL rail placement. Also verify one pre-existing gap: the wide Profile summary column has no dock
clearance, so its final details rows can end under the existing category dock. Do not invent production geometry.
The orchestrator remains implementation owner and Git operator. The maintainer prohibits `problem_solver` for this task.

## Last safe commit

`Add Profile obstruction clearance`, based on `29c38f5` — `Add Notifications obstruction clearance`.
Resolve this checkpoint's hash from Git. Nothing was pushed.

## Limits and worktree caution

- Physical-device IME, physical foldable, API 29, live-server, signing, and TalkBack behavior remain unverified.
- No production floating-navigation geometry is approved. Do not invent clearance or fit values.
- The recipient finder is implemented. Its wide New conversation callback remains for the activation slice.
- One pre-existing flake exists: `MastodonIntegrationTest cancelingTimelinePageCancelsRequestAndAllowsRetry`
  can fail with `IOException: Gave up waiting for queue to shut down`. It passes in isolation and is unrelated to
  clearance work. Record it separately; do not weaken it.
- A separate external `gradlew.bat clean installDebug` process ran in this worktree during an earlier slice and removed
  shared test results and build outputs. It was not stopped and finished on its own. Recheck shared-output
  activity before later builds. That external install's device state remains unknown.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve the deleted `PhotoGridFeedViewModelTest.kt` and PNGs, untracked captures, scripts, and caches.
  The Photo Grid clearance test uses a different name and must not restore that deletion.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
