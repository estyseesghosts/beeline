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

R04 is complete. The capability-cache probe fake carries Home timeline support matching the real probe; both replacement-fencing tests reach their intended assertions. No production change came from R04.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R05. Attribute and repair the notification lifecycle failures.

## Last safe commit

1c90e9e is the safe commit after R03.
R04 slice commit subject: `Repair the capability-cache probe fixture`.
The next session resolves the new R03 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R04 changed the capability-cache probe fake and records only. Production capability cache and Misskey source remain unchanged.
- CapabilityCacheTest reports 16 passing tests. MisskeyIntegrationTest, MisskeyThreadContinuationTest, and SessionLifecycleTest report no regression.
- Post-repair test assembleRelease reports 1466 tests and 6 failures with release assembly complete; the remaining failures belong to R05 and R15a.
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
