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

Phase 4A — back priority and session-bound modal behavior — is in progress. The task is larger
than one safe slice and is split into 4A-1 (back priority for picker and popup), 4A-2 (inactive
group icons from saved memory), and 4A-3 (shared navigation-button contract and navigation item
model). 4A-1 is implemented and focused-verified: `ShellBackPolicyTest` reports 11 tests and 0
failures, and the navigation and shell suites pass. `assembleDebug` and `installDebug` succeed on
emulator-5554 (API 36). The next slice is 4A-2. The full `test assembleRelease` gate and
`:app:lintDebug` run before Phase 4A completion.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.

## Last safe commit

4a66382 is the safe commit after R15. Phase 4A commits follow from it.
R15 slice commit subject: `Record the green recovery gate and resume Phase 4A`.
The next session resolves the new hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R15 is a gate-and-records slice. No production, adapter, presentation, transport,
  route, or header change. It updates the task state, handoff, the 0.4.0 plan status,
  the documentation inventory, the UI wiki artwork section, and BUGS.txt.
- Full gate: `test assembleRelease --rerun-tasks` is BUILD SUCCESSFUL with 151 suites,
  1561 tests, 0 failures, 0 errors, 0 skipped; release assembly complete. The count
  matches R14 and the R15a artwork split removed the prior sole failure.
- The architecture audit exits 0 with 616 findings and no regression findings.
- `:app:lintDebug` passes. `ktlintCheck` fails on repo-wide pre-existing style
  findings against the stale committed baseline; no baseline changed and no recovery
  hunk added a finding. Resolving that style debt is a separate slice.
- No fixer session was dispatched. The orchestrator owns the slice, ran the gate, and
  operates Git.
- API 29 smoke and the restoration device check are unavailable: the only connected
  device is emulator-5554 (API 36) and Beeline is not installed. Heap and database/WAL
  bytes, live-server push delivery, live-server capability refresh, live-server media
  upload, and signing remain unverified.
- No Android-only behavior changed in the recovery, so no new instrumented test ran.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The docs/beeline_0.4.0.md status note, the timestamped logs, and BUGS.txt edits are git-ignored and stay local; force-add needs explicit user approval.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the modified importantdocs/writing_style.md, modified agent definitions, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
