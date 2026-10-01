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

R05 is complete. `NotificationSyncOrchestrator.unregister` revokes synchronously with the exact generation through `NotificationRepository.deactivate`; `removeAccount` keeps its unregister plus repository removal plus trailing-check composition; `accept` requires an active controller entry matching the token. Two gated tests prove removal and cancelled removal revoke an in-flight write.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R06. Require SessionStore in source construction.

## Last safe commit

9445dec is the safe commit after R04.
R05 slice commit subject: `Repair notification generation lifecycle regressions`.
The next session resolves the new R05 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R05 changed the notification orchestrator revocation path, its accept guard, and records only. Repository, registry, and store implementations remain unchanged.
- NotificationSyncOrchestratorTest reports 27 passing tests. Repository, synchronizer, state-ownership, write-failure, and SessionLifecycle suites report no regression.
- Post-repair test assembleRelease reports 1468 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete; all R03, R04, and R05 failures are absent.
- RoomNotificationStoreInstrumentedTest reports 1 passing test on emulator-5554 (API 36).
- ktlint reports repo-wide pre-existing findings; the changed test file is clean.
- API 29, live-server push delivery, and signing checks remain unverified.
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
