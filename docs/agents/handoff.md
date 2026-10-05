# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4C plan and implementation state.
Read AGENTS.md and its required linked pages before continuing.

## Current position and next action

Phase 4C-4 Notifications clearance is complete. The `NotificationsPanel.Notifications` branch of
`AppNotificationsDestinationContent` forwards the shell-supplied physical-right and bottom clearances to
`NotificationsScreen`. Wide layout keeps its full-size `Column`, `notification_refresh_surface`, and
`notifications_content` viewport. The top filter/query chip row, the notification rows and their controls, the empty
state, the error/storage retry controls, the sync-delayed banner, the paging/load-older control, and the paging error
text clear physical right in LTR and RTL.

A notification row is not a single opaque target. It always carries a dismiss control and can carry follow-request
controls, so the row card keeps its full width for visual underlay and its interactive content clears physical right
through an absolute right inset inside the row. This matches the DM inbox row precedent. It does not use a list-wide
content inset.

Wide Notifications has no bottom dock; its chip row sits at the top. Bottom clearance extends the wide list end
spacing only. Compact Notifications ignores both wide inputs and keeps its floating bottom chip row, contextual-control
placement, and scroll clearance. Chip travel and selection keep their own owners. Production clearances remain zero.
Floating navigation remains inactive. The DM, Home, Search, and Photo Grid slices remain unchanged.

Focused `NotificationsClearanceTest` passes: 8 tests. Existing suites pass unchanged: `NotificationsScreenTest` 14,
`NotificationRouteResolverTest` 2, `NotificationLaunchHostTest` 5, `NavigationTest` 36.
Full `test assembleRelease` passes with 157 suites, 1,624 tests, zero failures/errors/skips, and successful release
assembly. No external Gradle build was active before the runs.
Tests verify full viewport and list width, chip-row bounds, row-surface underlay, row action bounds, dismiss and
follow-request controls and callbacks, load-older and retry callbacks, empty/error/sync-banner content bounds,
final-item reach above bottom obstruction, branch forwarding, retained filter selection and scroll position, and
compact compatibility. Direct review found no unresolved required findings. Document links and slice-only whitespace
pass. No `problem_solver`, ADB, or live-server check ran.

Next: Profile is the last destination-clearance gate. Record a Profile-specific contract before editing; do not copy
the Notifications row decision without checking Profile row geometry and click targets. After Profile, complete 4C-5
activation. Do not activate vertical navigation or wire the wide DM action in a clearance slice.
The orchestrator remains implementation owner and Git operator. The maintainer prohibits `problem_solver` for this task.

## Last safe commit

`Add Notifications obstruction clearance`, based on `4763311` — `Add Photo Grid obstruction clearance`.
Resolve this checkpoint's hash from Git. Nothing was pushed.

## Limits and worktree caution

- Physical-device IME, physical foldable, API 29, live-server, signing, and TalkBack behavior remain unverified.
- No production floating-navigation geometry is approved. Do not invent clearance or fit values.
- The recipient finder is implemented. Its wide New conversation callback remains for the activation slice.
- A separate external `gradlew.bat clean installDebug` process ran in this worktree during an earlier slice and removed
  shared test results and build outputs. It was not stopped and finished on its own. Recheck shared-output
  activity before later builds. That external install's device state remains unknown.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve the deleted `PhotoGridFeedViewModelTest.kt` and PNGs, untracked captures, scripts, and caches.
  The Photo Grid clearance test uses a different name and must not restore that deletion.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
