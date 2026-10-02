# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-02

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active recovery task.
The recovery plan is C:\Users\julie\.opencode\plan\beeline-0.4.0-phase4-recovery.md.
Configuration history stays in [tasks/orchestrator-shell-access.md](tasks/orchestrator-shell-access.md) and [tasks/agent-role-consolidation.md](tasks/agent-role-consolidation.md).
Start with AGENTS.md, agent control, workflow, and operation rules.

## Current position and next action

R14 implementation is complete and the focused gates have run. Notification
retention is measured with counted synthetic workloads and no eviction ships:
50,000 ingested events leave 500 visible records; 1,000 and 10,000 dismissals
grow tombstones exactly; 200 stream deliveries claim, finish, and release with
exact counts; 5 stable query keys hold 5 checkpoints; removal cleans one
account while the sibling account stays intact; the Room store holds one state
row per account with zero sibling rows. Growth is acceptable at measured
workloads, so no retention-policy slice opens. Heap and database/WAL bytes
remain unmeasured: no device run occurred. No production, deletion-query, cap,
migration, or schema change.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R15a. Split the Mastodon favourite artwork contract per protocol. Then execute R15.

## Last safe commit

bb52b65 is the safe commit after R13.
R14 slice commit subject: `Measure notification correctness-state retention`.
The next session resolves the new R14 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R14 adds one new measurement test file plus the measured wiki section, the
  retention-inventory review touch, and records only. No production, transport,
  route, header, or editor-state change.
- NotificationRetentionMeasurementTest reports 9 passing tests. The full
  notification package group reports BUILD SUCCESSFUL.
- Full gate reports 1561 tests and 1 failure: the pre-existing R15a artwork
  failure only (1552 prior tests plus 9 new R14 tests). Release assembly complete.
- `:app:lintDebug` reports BUILD SUCCESSFUL. ktlint reports repo-wide pre-existing findings; the
  R14 hunks introduce no new finding.
- No fixer session was dispatched (the slice adds a new file). The orchestrator
  owns the slice, repaired one Room main-thread test failure after
  stop-condition investigation, made the doc edits, ran all gates, and operates Git.
- R15a is not done and the artwork failure still stands, so the R15 gate stays
  blocked until R15a completes.
- Heap and database/WAL bytes, live-server push delivery, API 29, live-server
  capability refresh, and signing checks remain unverified.
- No Android-only behavior changed in R14, so no new instrumented test ran.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the modified importantdocs/writing_style.md, modified agent definitions, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
