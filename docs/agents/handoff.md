# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-04

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4C plan and implementation state.
Read AGENTS.md and its required linked pages before continuing.

## Current position and next action

Phase 4C-4 Home clearance is complete. The existing Home branch forwards shell-supplied physical-right
and bottom clearances. Home keeps full-size pull-to-refresh and list viewports, transparent outer rows,
full-width dividers, and existing error surfaces. Interactive post content, error/sign-in controls,
and footer content clear physical right in LTR/RTL. Bottom clearance extends the existing scroll range.
The wide timeline dock clears physical right and moves above bottom obstruction. The null-Home branch
also clears its dock and empty-state text. Chip scrolling and selection travel remain unchanged.
Compact Home ignores both wide inputs. Its IME-aware spacing and shell-owned tabs/navigation remain unchanged.
The DM slices remain unchanged. Floating navigation remains inactive with zero production clearances.

Focused `HomeClearanceTest` passes: 7 tests. Existing `HomeFeedTest` and `NavigationTest` pass: 73 tests.
Full `test assembleRelease` passes with 154 suites, 1,599 tests, zero failures/errors/skips,
and successful release assembly. The unchanged `DirectMessageScreenTest` passes with 22 tests.
Tests verify viewport/underlay bounds, interaction clearance, final post/footer reach, branch forwarding,
timeline callbacks, retained scroll position, and compact compatibility. Direct review found no unresolved required findings.
Document links and slice-only whitespace pass. No `problem_solver`, ADB, or live-server check ran.

Next: investigate Search and record its separate bounded contract before implementation.
Search, Photo Grid, Notifications, and Profile clearance gates remain before activation.
Do not activate vertical navigation or wire the wide DM action in a clearance slice.
The orchestrator remains implementation owner and Git operator. The maintainer prohibits `problem_solver` for this task.

## Last safe commit

`Add Home obstruction clearance`, based on `f8f1056` — `Add DM conversation obstruction clearance`.
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
