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

R06 is complete. `SocialSourceFactory` requires an explicit `SessionStore`. The fail-open null path is gone. Misskey sources receive the store-backed current-session check, and both adapters persist refreshed capabilities through revision-guarded `updateCapabilities`. New `SourceFactoryTest` covers routing and authority for both protocols.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R07. Keep push source selection bound to its notification token.

## Last safe commit

4145f4c is the safe commit after R05.
R06 slice commit subject: `Require the session authority in SocialSourceFactory`.
The next session resolves the new R06 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R06 changed the source factory session dependency, its authority check, and records only. Adapters, store implementation, and DI wiring remain unchanged.
- SourceFactoryTest reports 6 passing tests. SessionLifecycle, session ViewModel, connected context, auth gateway, push cancellation, and Misskey integration suites report no regression. The Mastodon contract run retains the pre-existing R15a artwork failure.
- Post-repair test assembleRelease reports 1476 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete; all R01–R06 failures are absent.
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
