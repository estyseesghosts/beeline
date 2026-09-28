# Beeline 0.4.0 task state — slice 2A1

## Objective

Characterize the existing shared transport contracts after Phase 1. This slice changes tests
only. It does not move transport code or change request, response, origin, Link, WebSocket,
cancellation, or failure behavior.

## Boundary

Phase 1 remains closed at commit `a189ab5` (`Fix Photo Grid parity coverage`). Slice 2A1 is the
current uncommitted test-only boundary. Slice 2A2 remains the next planned change.

## Characterized owners

- `data/misskey/MisskeyApi.kt` owns request execution, headers, bodies, bearer placement,
  cancellation, failure mapping, response limits, and WebSocket construction.
- `data/misskey/HttpClientPool.kt` owns credential-free client reuse, timeouts, redirects, and
  bounded LRU retention.
- `data/misskey/HttpResponse.kt` owns adapter-facing body and header representation and current
  Link parsing.
- `ServerAddress.normalize` owns HTTPS origin normalization and rejection.
- Mastodon and Misskey adapters retain protocol-specific mapping and cursor policy.

## Slice changes

- Added `MisskeyApiTest` for JSON and non-JSON responses, request verbs and paths, query values,
  content types, caller headers, bearer placement, ApiFailure details, cancellation, and upload
  stream closure.
- Extended `HttpClientPoolTest` for the 4 MiB below/at/above boundary and origin normalization.
- Extended `MastodonIntegrationTest` with current Link relation, whitespace, malformed, case, and
  repeated-header characterization.
- Extended `WebSocketTransportTest` with invalid-origin rejection and adapter cancellation.
- `Api29CompatibilityTest` required no change because transport has no API-29-specific claim.

## Verification

- `MisskeyApiTest`: 5 passed.
- `HttpClientPoolTest`: 9 passed.
- `WebSocketTransportTest`: 6 passed.
- `Api29CompatibilityTest`: 3 passed.
- `MastodonIntegrationTest`: 59 passed.
- `MisskeyIntegrationTest`: 48 passed.
- `MastodonSourceContractTest`: 6 passed.
- `AuthGatewayTest`: 2 passed.
- `:app:lintDebug`: passed.
- The exact audit command was `python tools/scripts/architecture_audit.py . --baseline
  tools/architecture-baseline.json --check`.
- The audit exited 0 and reported 613 findings.
- The audit reported no `regression:` lines. The count is 4 above the prior 609 result.
- The stored 609 result has no row list, so the four additional file and symbol names cannot be
  reconstructed. The current output names production files and symbols only.
- The audit scans `app/src/main`; the 2A1 diff changes test files and agent records only. No extra
  finding can come from the 2A1 test-only diff.
- The full audit output is in ignored `logs/architecture-audit-2a1.txt`.
- `test assembleRelease` ran and failed at `:app:testDebugUnitTest`: 1,385 tests completed and 16
  known baseline tests failed. `:app:assembleRelease` completed successfully. `logs/BUGS.txt`
  records these baseline failures.

## Not verified

Live-server behavior, authenticated redirect wire behavior, physical-device rendering, API 29
physical behavior, RTL, TalkBack, font-scale behavior, and signed release behavior remain
unverified.

## Hygiene

Production files and `docs/agents/protocol-and-session-ownership.md` remain unchanged. No commit,
staging, or push was performed.

The separate `git status --short` command observed these sets:

- Staged: `docs/classic_navigation.md`.
- Modified: `.opencode/agents/code_reviewer_high.md`, `.opencode/agents/code_reviewer_low.md`,
  `.opencode/agents/git_handler.md`, `.opencode/agents/orchestrator.md`,
  `.opencode/agents/problem_solver_high.md`, `.opencode/agents/problem_solver_low.md`,
  `.opencode/agents/targeted_fixer.md`, `app/src/test/java/me/foxtails/palustris/WebSocketTransportTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/misskey/HttpClientPoolTest.kt`,
  `docs/agents/handoff.md`, `docs/agents/tasks/beeline-0.4.0.md`, `importantdocs/writing_style.md`.
- Deleted: `currentbehaviour.png`, `intendedbehaviour.png`.
- Untracked: `.opencode/agents/adb_handler.md`, `.opencode/agents/codebase_explorer_android.md`,
  `app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyApiTest.kt`, `screen.png`,
  `tools/scripts/__pycache__/`, `tools/scripts/adb_control.py`, `tools/scripts/adb_flow.py`,
  `tools/scripts/adb_inspect.py`, `tools/scripts/adb_screenshot.py`, `tools/tests/__pycache__/`.

Ignored and unstaged: `logs/260927-000000.txt` and `logs/architecture-audit-2a1.txt`.
The proposed staging set contains only the four 2A1 test files and these two docs files:
`docs/agents/tasks/beeline-0.4.0.md` and `docs/agents/handoff.md`.
The staged `docs/classic_navigation.md` and all other unrelated sets stay untouched.
