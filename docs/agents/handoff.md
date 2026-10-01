# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-01

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active recovery task.
The recovery plan is C:\Users\julie\.opencode\plan\beeline-0.4.0-phase4-recovery.md.
Configuration history stays in [tasks/orchestrator-shell-access.md](tasks/orchestrator-shell-access.md) and [tasks/agent-role-consolidation.md](tasks/agent-role-consolidation.md).
Start with AGENTS.md, agent control, workflow, and operation rules.

## Current position and next action

R10 is complete. `ShellNavigator` binds restored navigation state to the saved session owner (origin, protocol, local account ID, durable revision) synchronously before display. Matching restoration preserves the Search query, category, safe local page, and remembered panels. A mismatched owner clears the account-bound fields before display. The composer overlay is never restored because reply/quote targets do not survive process recreation.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R11. Stream multipart uploads with explicit one-shot ownership.

## Last safe commit

6d9b69d is the safe commit after R09.
R10 slice commit subject: `Preserve restored Search state for its matching session`.
The next session resolves the new R10 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R10 changed the ShellNavigator saver/binding, its session-revision wiring, restoration tests, and records only. No source, adapter, or editor-state change.
- ShellNavigatorTest reports 28 passing tests. ShellNavigatorRestorationTest reports 5 passing tests. SearchPanelRestorationTest reports 3 passing tests. NavigationTest reports 31 passing tests. ShellCharacterizationTest, WideNavigationTest, and AppShellStateTest report no regression.
- Post-slice full gate reports 1521 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete; all R01–R10 failures are absent. The recorded cancellation-timing flake did not fire in this run.
- `:app:lintDebug` reports BUILD SUCCESSFUL. ktlint reports repo-wide pre-existing findings; the new test file is clean.
- Live-server capability refresh, API 29, live-server push delivery, and signing checks remain unverified.
- Full NavigationTest reports 30 passing tests. ComposerOwnerTest, ShellCharacterizationTest, and SignInScreenTest report no regression.
- ktlintTestSourceSetCheck reports only pre-existing NavigationTest findings. AppShellFixtures has none.
- Post-repair test assembleRelease from R02 reports 1466 tests and 13 failures with release assembly complete.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- No Android device, API 29, live-server, or signing check comes from R04.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
