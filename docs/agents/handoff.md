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

R12 implementation is complete and the full gate has run. `MastodonSource.uploadMedia` is proven to use the R11 streaming transport path with no adapter-side buffering. Four adapter tests through the real adapter verify POST /api/v1/media, multipart name `file` with default filename `upload`, caller MIME type, exact bytes, bearer ownership, attachment mapping, and close-once ownership on success, HTTP 422 failure, genuine mid-body disconnect, and cancellation. A fresh stream retries successfully after the disconnect. The disconnect phase runs on an isolated fault server because MockWebServer never dequeues a request cut off mid-body.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R13. Characterize DM anchors and context limits with no speculative protocol change.

## Last safe commit

e91e193 is the safe commit after R11.
R12 slice commit subject: `Cover streaming Mastodon uploads through the adapter`.
The next session resolves the new R12 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R12 changed the Mastodon integration test, the server-compatibility wiki, and records only. No production, transport, route, header, or editor-state change.
- MastodonIntegrationTest reports 67 passing tests (63 existing plus 4 new). Grouped with MastodonSourceContractTest: 74 tests with only the pre-existing R15a artwork failure.
- Post-repair full gate reports 1545 tests and 1 failure (pre-existing R15a artwork only) with release assembly complete.
- `:app:lintDebug` reports BUILD SUCCESSFUL. ktlint reports repo-wide pre-existing findings; the R12 hunks introduce no new finding.
- Test and wiki implementation by one targeted_fixer session under an explicit no-commit contract (kept through review repairs in the same session). The orchestrator owns the slice, ran all gates, and operates Git.
- Live-server media upload, API 29, live-server push delivery, live-server capability refresh, and signing checks remain unverified.
- No Android-only behavior changed in R12, so no new instrumented test ran.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. R12 needs no BUGS.txt entry (coverage only, no defect repaired).
- A clean-snapshot comparison remains pending because git worktree access is denied.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the modified importantdocs/writing_style.md, modified agent definitions, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
