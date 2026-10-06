# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for Phase 4C state and the next slice contract.
Read AGENTS.md and its required pages before continuing.
The Display layout-direction task has its separate record at [tasks/force-layout-direction.md](tasks/force-layout-direction.md).

## Current position

4C-5b activates safe adaptive floating navigation and absorbs the former 4C-5d activation gates.
The production rail is removed. Compact-wide and tablet reuse the shared six-target capsule and action button.
Fit uses mandatory system gestures, system bars, cutouts, and structural hinges, with physical dp geometry.
IME height does not select permanent presentation. Pane and back policy remain independent.
All seven destination surfaces receive physical clearance. Wide compact fallback clears Search and the DM editor too.
The DM New conversation action opens the existing recipient finder.

4C-5c anchor preferences are committed at `b419be1`. The shared chip slice now uses one renderer and a leading circular caret.
Wide Notifications filters use the bottom dock. The slice is verified and is ready to commit.
After that commit, investigate compact-wide Profile as a separate slice. The required result is the mobile Profile presentation with button placement as the only difference.

## Last safe commit

Preceding safe commit: `b419be1` — `Persist physical navigation anchors`.
The chip-slice commit subject is `Unify destination chip presentation`. Nothing was pushed.

## Evidence and limits

- Focused chip and destination suites pass. `:app:lintDebug` passes.
- The full gate passes: 1,685 tests, zero failures, errors, or skips; release assembly succeeds.
- `:app:installDebug` succeeded on `emulator-5554`. The folded 445 × 704 dp screen showed Home caret styling and hide/show.
- The folded emulator showed the Notifications bottom dock above the system bar and loaded rows above the dock.
  The emulator was restored to OPENED. Captures are in `C:\Users\julie\AppData\Local\Temp\opencode\`.
- The 900 × 900 dp tablet and LTR/RTL geometry have Compose evidence only. Hardware tablet, device RTL, physical hinge coordinates,
  TalkBack, physical-device IME, API 29, release signing, and live-server behavior remain unverified.
- The unchanged Mastodon cancellation flake did not occur in the final gate. Record future occurrences separately; do not weaken it.

## Worktree caution

- No delegation occurred. The orchestrator owns implementation, direct review, records, validation, and Git.
  The maintainer prohibits `problem_solver` for this task. Do not push.
- Recheck external Java/Gradle activity before each build. Do not edit sources during a build.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve deleted `PhotoGridFeedViewModelTest.kt` and PNGs, unrelated captures, scripts, and caches.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it or ignored logs.
- Stage only explicit reviewed slice paths.
