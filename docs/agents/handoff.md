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

R13 implementation is complete and the full gate has run. The direct-message anchor and
context contract is characterized with no production change: Mastodon loads one anchor plus one
context response and reports Finished with no cursor; a non-null thread cursor fails before any
request; 403, 404, and 410 anchor reads, blank or foreign identities, malformed bodies, and
non-direct anchors fail truthfully as unsupported; repeated anchor and context rows merge without
duplicates. The repository needs a stored conversation preview with the last-post anchor, merges
remote posts with cached rows, keeps cached rows on failure, rejects late thread writes after
session replacement, and reloads fresh context on retry with no cursor. Live-server truncation
beyond one context response stays unverified: no disposable account or approval exists, and no
continuation is invented.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R14. Measure notification retention without unsafe eviction.

## Last safe commit

3fb6973 is the safe commit after R12.
R13 slice commit subject: `Characterize direct-message anchor and context limits`.
The next session resolves the new R13 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R13 changed two DM test files, the notifications-and-direct-messages wiki, the
  protocol-and-session-ownership page, and records only. No production, transport, route, header,
  or editor-state change.
- Focused DM suites report 102 passing tests. Grouped Mastodon plus Misskey adapter contracts:
  245 tests with only the pre-existing R15a artwork failure.
- Post-repair full gate reports 1552 tests and 1 failure (pre-existing R15a artwork only) with
  release assembly complete.
- `:app:lintDebug` reports BUILD SUCCESSFUL. ktlint reports repo-wide pre-existing findings; the
  R13 hunks introduce no new finding.
- Tests by one targeted_fixer session under an explicit no-commit contract (stopped at its fail
  gate on the gated failure test). The orchestrator owns the slice, repaired that test after a
  stop-condition investigation, made the doc edits, ran all gates, and operates Git.
- Live-server DM truncation, API 29, live-server push delivery, live-server capability refresh,
  and signing checks remain unverified.
- No Android-only behavior changed in R13, so no new instrumented test ran.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. R13 needs no BUGS.txt entry (coverage only, no defect repaired).
- A clean-snapshot comparison remains pending because git worktree access is denied.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the modified importantdocs/writing_style.md, modified agent definitions, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
