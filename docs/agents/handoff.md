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

R08 is complete. `MastodonModerationService.report` validates its target with `validateTarget` before constructing fields. A same-origin Misskey target or a blank target ID fails as unsupported without a request. A foreign target keeps the foreign-origin error. New `ModerationServiceTest` report tests cover invalid targets, account-only bodies, and reserved-character encoding.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R09. Bind Mastodon moderation cursors to the source session.

## Last safe commit

459c67b is the safe commit after R07.
R08 slice commit subject: `Validate Mastodon report identities before sending`.
The next session resolves the new R08 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R08 changed the Mastodon report target validation and records only. The cursor codec, other moderation mutations, form encoding, and comment behavior remain unchanged.
- ModerationServiceTest reports 15 passing tests. MastodonIntegrationTest, MastodonSourceContractTest, and MisskeyIntegrationTest report 115 tests with only the pre-existing R15a artwork failure.
- Post-slice test assembleRelease reports 1491 tests and 2 failures with release assembly complete: the pre-existing R15a Mastodon artwork failure plus an isolation-dependent MastodonIntegrationTest cancellation-timing flake that passes alone and never touches the changed report path; all R01–R08 failures are absent.
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
