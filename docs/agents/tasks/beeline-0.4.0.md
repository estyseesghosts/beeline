# Beeline 0.4.0 task state — Mastodon callback repair

## Objective

Repair Mastodon OAuth verification and callback handling without changing Misskey behavior.

## Status sets

- Source status: Mastodon verify uses `/api/v1/accounts/verify_credentials`.
  Token exchange uses root `/oauth/token`. Registration uses `/api/v1/apps`.
  The capability probe uses `/api/v2/instance`. Authentication 404 errors keep their stage.
  Generic capability 404 errors remain unsupported. Unknown errors remain unknown.
- Callback status: PKCE, form encoding, redirect validation, origin validation, and strict state
  matching remain intact. Duplicate valid callbacks start one exchange and preserve the pending code.
- Test status: 16 session tests, 3 auth gateway tests, 59 Mastodon integration tests, and 48
  Misskey integration tests pass. Live-server exchange proof remains unverified.
- Audit status: `python tools/scripts/architecture_audit.py . --baseline
  tools/architecture-baseline.json --check` exited 0 and reported 611 findings with zero baseline
  regressions. Historical slice counts of 610, 611, 612, and 613 occurred across worktree states.
  Treat these counts as unstable count-only measurements, not regressions or proof. The related
  logs are ignored by `/logs/*.txt`: `logs/260928-oauth-callback.txt` and
  `logs/260928-mastodon-callback-repair.txt`.
- Device status: the debug APK installed successfully. Welcome layout and dummy callback dispatch
  passed. No exchange occurred because the device had no pending request.
- Live-server status: a fresh user approval remains required to prove the exchange and verify flow.

## Exact Mastodon staging pathspec

Stage only the Mastodon repair files and its focused tests:

```text
git add -- app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonErrorMapper.kt app/src/main/java/me/foxtails/palustris/ui/session/AccountManager.kt app/src/test/java/me/foxtails/palustris/data/auth/AuthGatewayTest.kt app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyIntegrationTest.kt app/src/test/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClientTest.kt app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt docs/agents/tasks/beeline-0.4.0.md docs/agents/handoff.md
```

Exclude welcome UI files, the 2C3 PhotoGrid and Search files, and staged `docs/classic_navigation.md`.
Exclude unrelated `.opencode`, writing, image, helper, cache, and log files.

## Verification record

- Focused auth, Mastodon integration, capability, transport, and session tests passed.
- `:app:lintDebug` passed after the duplicate-callback test change.
- The full gate remains unresolved because the known baseline suite reports unrelated failures.
- The audit command exited 0 and reported 611 findings with zero baseline regressions. Historical
  counts of 610, 611, 612, and 613 are unstable count-only measurements across worktree states.

## Boundaries

The repair changes Mastodon authentication and its session callback guard only.
The implementation paths are `MastodonAuth.kt`, `MastodonErrorMapper.kt`, and `AccountManager.kt`.
The focused test paths are `AuthGatewayTest.kt`, `MastodonIntegrationTest.kt`,
`MisskeyIntegrationTest.kt`, `AuthenticatedHttpClientTest.kt`, and `SessionViewModelTest.kt`.
No resource files changed. Misskey transport, the welcome UI, PhotoGrid, Search, and navigation
remain outside this slice.

## Records

- Implementation log: `logs/260928-mastodon-callback-repair.txt`.
- Audit source: `logs/260928-oauth-callback.txt`.
- No secrets, tokens, callback values, or response bodies belong in these records.
