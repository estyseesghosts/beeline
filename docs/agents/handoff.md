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

R07 is complete. `UnifiedPushRegistrationManager` binds push source selection to its notification token. A failed token lookup ends as a no-op. It never escalates to account lookup or a transient factory source. Token-null removal cleanup uses a validated stored snapshot. Token, session, registry, and endpoint ownership is rechecked before each remote subscription mutation, and stale failures publish nothing against the replacement session. New `UnifiedPushRegistrationManagerTest` covers current selection, stale no-op behavior, cross-account rejection, replacement races, registry replacement, endpoint supersede, creation-gate replacement, late-failure capability preservation, token-null cleanup, cleanup failure, and the valid connection path.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R08. Validate Mastodon report identities before sending.

## Last safe commit

09db376 is the safe commit after R06.
R07 slice commit subject: `Keep push source selection bound to its notification token`.
The next session resolves the new R07 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R07 changed the push manager source selection, its ownership rechecks, and records only. The registry, adapters, store implementation, and DI wiring remain unchanged.
- UnifiedPushRegistrationManagerTest reports 15 passing tests. PushCancellationTest, PushRegistrationRepositoryTest, NotificationSyncOrchestratorTest, NotificationRepositoryTest, and SessionLifecycleTest report no regression.
- Post-repair test assembleRelease reports 1489 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete; all R01–R07 failures are absent.
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
