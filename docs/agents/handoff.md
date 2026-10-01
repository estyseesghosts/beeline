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

R00 is complete. The orchestrator owns recovery delivery and ran all R00 checks directly.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R01. Activate draft writers in account-bound unit fixtures.

## Last safe commit

ecd0d6b is the safe commit before R00.
Resolve the new slice commit by subject: `Record the current recovery baseline and failure attribution`.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R00 changed records only and left application source unchanged.
- All 16 historical failures reproduce on the dirty worktree. Narrow slice R15a owns the new Mastodon contract failure and the orchestrator owns R15a.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- Intermediate XML reports were overwritten; sanitized attribution survives in logs/261001-010000.txt.
- The timestamped log and BUGS.txt edit need explicit force-add approval because /logs/*.txt is Git-ignored.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- No Android device, API 29, live-server, or signing check comes from R00.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
