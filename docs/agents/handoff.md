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

R01 is complete. The orchestrator owns recovery delivery and ran all R01 checks directly.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R02. Give shell draft fixtures a valid account writer.

## Last safe commit

e3f74b0 is the safe commit before R01.
Resolve the new slice commit by subject: `Activate draft writers in account-bound unit fixtures`.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R01 changed test fixtures and records only. Production draft revocation rules remain unchanged.
- DraftActionsTest reports 13 passing tests. DraftWriteAuthorityTest reports no regression.
- ktlintCheck reports only pre-existing violations in other test files.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- No Android device, API 29, live-server, or signing check comes from R01.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
