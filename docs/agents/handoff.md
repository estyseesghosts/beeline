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

R15a implementation is complete and the focused gates have run. The favourite
artwork contract is split per protocol: the shared `SocialSourceContractTest`
declares an abstract `expectedFavouriteArtworkStyle`; `MisskeySourceContractTest`
supplies `Heart` and `MastodonSourceContractTest` supplies `Star`. The renamed
`favouriteArtworkStyleMatchesProtocolContract` passes for both adapters and for
`MisskeyIntegrationTest`, which extends the Misskey contract base. This removes
the sole full-gate failure. No production, adapter, or presentation change.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R15. Run the full integration gate and hand back to Phase 4A.

## Last safe commit

ba41628 is the safe commit after R14.
R15a slice commit subject: `Split the favourite-artwork contract per protocol`.
The next session resolves the new R15a hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R15a changes only two contract-test files plus the protocol-ownership note and
  records. No production, adapter, presentation, transport, route, or header change.
- `MastodonSourceContractTest` reports 7 passing tests, `MisskeySourceContractTest`
  7, and `MisskeyIntegrationTest` 49 with --rerun-tasks. The artwork test is renamed
  and parameterized per protocol; no test was weakened or removed.
- The R15a hunks add no new ktlint finding. `ktlintTestSourceSetCheck` reports
  repo-wide pre-existing findings against a stale committed baseline; the only
  finding in a touched file is the pre-existing unused `MisskeyApi` import at
  `MastodonSourceContractTest.kt:5`, unused at HEAD `ba41628`. `:app:lintDebug`
  reports BUILD SUCCESSFUL.
- No fixer session was dispatched. The orchestrator owns the slice, implemented the
  contract split directly, ran the focused checks, made the doc edits, and operates Git.
- R15a removes the sole full-gate failure, so R15 can run its integration gate.
- Heap and database/WAL bytes, live-server push delivery, API 29, live-server
  capability refresh, and signing checks remain unverified.
- No Android-only behavior changed in R15a, so no new instrumented test ran.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the modified importantdocs/writing_style.md, modified agent definitions, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
