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

R09 is complete. `MastodonModerationService` takes required session revision and source instance dependencies from the source UUID. The moderation cursor payload binds both values at version 2. Legacy version-1 and raw-URL cursors fail safely. The first-page self-Link uses the exact initial request URL. New cursor tests cover session and instance binding, cross-account and cross-kind replay, self-Link rejection, payload mutations, wire-order paging, validation before I/O, and failed-page row preservation.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R10. Preserve restored Search state for its matching session.

## Last safe commit

34e2a19 is the safe commit after R08.
R09 slice commit subject: `Bind Mastodon moderation cursors to the source session`.
The next session resolves the new R09 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R09 changed the Mastodon moderation cursor codec, its source wiring, the unsupported-with-rows presentation, and records only. The generic page cursor codec, request routes, form encoding, and moderation mutations remain unchanged.
- ModerationServiceTest reports 17 passing tests. MastodonIntegrationTest reports 63 passing tests. ModerationViewModelTest reports 13 passing tests. ModerationListScreenTest reports 3 passing tests. Adapter contracts report 68 tests with only the pre-existing R15a artwork failure.
- Post-slice test assembleRelease reports 1502 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete; all R01–R09 failures are absent. The recorded cancellation-timing flake did not fire in this run.
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
