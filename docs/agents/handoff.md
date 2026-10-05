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

Next: define 4C-5c anchor preferences before editing. The 4C-5b checkpoint is directly reviewed.
Defaults currently anchor expanded layouts left and other fitting windows right. No anchor preference ships yet.

## Last safe commit

`6dcfac4` — `Add navigation fit policy` precedes the checkpoint subject `Activate safe adaptive floating navigation`.
Resolve the new checkpoint hash from Git. Nothing was pushed.

## Evidence and limits

- Production Kotlin, focused tests, and `:app:lintDebug` pass.
- `:app:installDebug` succeeded on `emulator-5554` before `test assembleRelease`.
- The full gate passes: 160 suites, 1,674 tests, zero failures, errors, or skips; release assembly succeeds.
- Folded outer-screen rendering shows vertical navigation at 1169 × 1848 px and 420 dpi.
  The local capture is `logs/4c5b-outer-home-ready.png`. Square-tablet and RTL placement have Compose evidence only.
- Physical hinge coordinates, hardware tablet rendering, device RTL, TalkBack, device IME, API 29, signing,
  and live-server recipient selection remain unverified in this slice.
- The unchanged Mastodon cancellation flake did not occur in the final gate. Record future occurrences separately; do not weaken it.

## Worktree caution

- No delegation occurred. The orchestrator owns implementation, direct review, records, validation, and Git.
  The maintainer prohibits `problem_solver` for this task. Do not push.
- Recheck external Java/Gradle activity before each build. Do not edit sources during a build.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve deleted `PhotoGridFeedViewModelTest.kt` and PNGs, unrelated captures, scripts, and caches.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it or ignored logs.
- Stage only explicit reviewed slice paths.
