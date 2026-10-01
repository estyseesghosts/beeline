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

R11 implementation is complete and focused gates pass. The full gate runs before commit. `UploadStreamOwner` owns one upload input stream for a single multipart call and releases it on every terminal path. Stream bodies report unknown length, are one-shot, and refuse a second write. OkHttp 4.12 does not propagate one-shot through the enclosing `MultipartBody`, so file-carrying bodies are wrapped in `OneShotRequestBody` while the consume-once guard stops silent truncated replays. A field-only PATCH body stays replayable. Retry behavior is unchanged for replayable requests. An upload retry needs a newly opened input.
Problem_solver provides read-only investigation and review. Targeted_fixer accepts only small explicit work packages.
Keep one implementation owner and one Git operator at a time.
The next slice is R12. Cover streaming Mastodon uploads through the adapter.

## Last safe commit

d6ef2a7 is the safe commit after R10.
R11 slice commit subject: `Stream multipart uploads with explicit one-shot ownership`.
The next session resolves the new R11 hash from Git without a second record-only commit.
The last safe application source remains 464b2d1. Simulator evidence reference 219504c resolves.

## Limits

- R11 changed the transport upload path, its tests, and records only. No adapter, route, header, or editor-state change.
- AuthenticatedHttpClientTest reports 25 passing tests. MisskeyApiTest reports 7 passing tests. HttpClientPoolTest reports 9 passing tests. MastodonIntegrationTest reports 63 passing tests alone.
- Grouped MastodonIntegrationTest plus MastodonSourceContractTest reports 70 tests with 2 failures: the pre-existing R15a artwork failure and the known isolation-dependent cancellation-timing flake, which passes alone and in class isolation.
- `:app:lintDebug` reports BUILD SUCCESSFUL. ktlint reports repo-wide pre-existing findings; all R11 files are clean.
- Post-repair full gate reports 1541 tests and 1 failure (pre-existing R15a artwork only) with release assembly complete. The grouped-run cancellation flake did not fire.
- Documentation updates were implemented by one targeted_fixer session under an explicit no-commit contract (27 insertions across 2 files, no other changes). The orchestrator owns the slice, ran the focused gates and lint, and operates Git. The full gate runs before commit.
- Live-server media upload, API 29, live-server push delivery, live-server capability refresh, and signing checks remain unverified.
- No Android-only behavior changed in R11, so no new instrumented test ran.
- The deleted Photo Grid test excludes that test from the available suite. Do not change it without owner approval.
- The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored.
- A clean-snapshot comparison remains pending because git worktree access is denied.
- Existing worktree changes remain intact. Do not push without a user request.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
Preserve local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
