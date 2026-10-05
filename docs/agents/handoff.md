# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-04

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4C slice plan and
current implementation state. Read AGENTS.md and its required linked pages before continuing.

## Current position and next action

Phase 4A and all Phase 4B slices are complete. 4C-1 is committed as
`1dc1d39` — `Characterize wide pane coordinate behavior`, preceded by `88044c3`. It adds a direction-aware
window-to-pane coordinate transform and LTR/RTL hinge tests. Navigation presentation, pane-selection
policy, and destination behavior remain unchanged. The orchestrator owns implementation, records,
validation, review, and Git. The maintainer prohibits further `problem_solver` use for this task.

The full test/build gate passes with 153 suites, 1,578 tests, zero failures/errors/skips, and
successful release assembly. A second wrapper run also succeeded with Gradle tasks up to date.
4C-2 is committed as `a0136e9` — `Add direct message recipient finder`, preceded by `1dc1d39`.
The session-scoped `DirectMessageViewModel` searches through its injected source. `DirectMessagesHost`
renders the cancelable sheet and exposes its open action through `DirectMessagesContract`. Focused
tests pass: 30 ViewModel and 15 Compose tests. The full test/build gate passes with 153 suites,
1,584 tests, zero failures/errors/skips, and successful release assembly.

4C-3 is committed as `1d2050b` — `Prepare shared vertical navigation presentation`, preceded by
`a0136e9`. It prepares a stateless six-target vertical presenter and a reusable contextual-action button.
The compact bar delegates to the shared button; the production wide rail remains unchanged. Focused
navigation checks pass: 52 tests across presentation, compact selection, shell navigation, and wide
navigation. The full gate passes with 153 suites, 1,585 tests, zero failures/errors/skips, and
successful release assembly.

4C-4 DM inbox clearance is complete. `DirectMessageInboxScreen` accepts shell-supplied physical-right
and bottom `Dp` values. Wide header and row interactions clear physical right; the viewport and row
surfaces remain full width; bottom clearance extends only the list range. Compact layout ignores both
wide-only inputs. Focused `DirectMessageScreenTest` passes with 18 tests. The full `test assembleRelease`
gate passed with 153 suites, 1,588 tests, zero failures/errors/skips, and successful release assembly.

Later gate retries overlapped a separate `gradlew.bat clean installDebug` process in the same
worktree. The combined run reported missing test classes and R8 inputs. A one-worker forced retry
could not delete a locked `R.jar`. Do not run another Gradle task until that external build releases
the shared outputs. The earlier full gate passed before these retries. No device check ran.

The DM conversation/editor clearance contract is now recorded in
`tasks/beeline-0.4.0.md`. It keeps the full viewport, clears transcript and editor content from
physical right, adds bottom clearance only to transcript scrolling, and preserves wide and compact
IME policies. No conversation-screen code changed in the inbox slice.

Next: implement that contract and test right-side bounds with the IME open and closed. The wide DM
action remains unwired. Do not activate vertical navigation until geometry and all destination-
clearance gates pass.

Source inspection confirms that `LargeLayoutMode.kt` uses width-only 600/840 dp boundaries.
`LargeScreenShell.kt` reserves an 80 dp rail and subtracts its physical offset from hinge coordinates.
`PalustrisApp.kt` and `ShellContent.kt` reuse `largePresentation` for navigation, content, system
bars, and back policy. `ShellNavigator` already remembers direct Photo Grid and DM selection.
`DirectMessagesContract` now exposes recipient-finder opening under the existing DM owner.
`LargeNavigationRail` remains the current production presentation. `WideNavigationPresentation`
tests the shared six-target vertical composition without activating it. `LargeLayoutModeTest`
verifies the content origin in LTR and RTL. Production safe-region fit, gesture/cutout/taskbar insets,
and device hinge behavior remain unverified. `DirectMessageConversationScreen` keeps its wide
`imePadding()` and navigation-bar positioning and its compact contextual-control insets. Existing
Compose tests cover editor submission but not obstruction clearance or real IME geometry.

## Last safe commit

`Define DM conversation clearance contract`, based on `1fcd03f` — Add DM inbox obstruction clearance.
Resolve this checkpoint's hash from Git.
Nothing was pushed.

## Limits and worktree caution

- No production vertical-capsule or contextual-action dimensions are approved. Reuse dimensions
  from tested components and existing contracts. Ask the maintainer if fit policy needs a new value.
- Device acceptance is not yet run. The available emulator is intended for compact-wide, not
  narrow-phone acceptance.
- API 29, physical foldable behavior, live-server behavior, signing, and TalkBack remain unverified.
- A separate `gradlew.bat clean installDebug` process was observed using this worktree during failed
  gate retries. It was not started or stopped by this task. Device state was not checked.
- Confirm that no external Gradle build is using shared outputs before running the next implementation
  gate.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve deleted Photo Grid test/PNGs, untracked captures, ADB scripts, and caches.
- The supplied `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
