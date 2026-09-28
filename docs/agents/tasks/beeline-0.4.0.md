# Beeline 0.4.0 task state — slice 2A3

## Objective

Move authenticated HTTP execution behind the neutral transport boundary. Remove Misskey-owned
construction from Mastodon production services. Add direct transport regression coverage.

## Status

- Source status: complete for slice 2A3.
- Test status: the focused adapter and transport set ran 260 tests. The two cancellation failures
  pass when each test runs alone. They are isolation or timing flakes, not baseline failures.
- Audit status: source verified. The architecture audit exits 0 with 613 findings and zero regressions.
- Device status: unverified.
- Live-server status: unverified.
- Release status: not yet verified in this continuation.

## Production files

- Added `app/src/main/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClient.kt`.
- Added `app/src/main/java/me/foxtails/palustris/data/transport/TransportFailure.kt`.
- Updated `app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt`.
- Updated `app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt`.
- Updated `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonCapabilityProbe.kt`.
- Updated `MastodonDirectMessageService.kt`, `MastodonErrorMapper.kt`,
  `MastodonModerationService.kt`, `MastodonNotificationService.kt`, and `MastodonPageClient.kt`.
- Updated `MastodonProfileService.kt`, `MastodonPushService.kt`, `MastodonSelfProfileService.kt`,
  `MastodonSource.kt`, `MastodonStreamService.kt`, and `MastodonThreadService.kt`.
- Updated `MisskeyApi.kt`, `MisskeyDirectMessageService.kt`, and `MisskeySource.kt`.

`MastodonSource`, `MastodonCapabilityProbe`, `MastodonPageClient`, and
`MastodonModerationService` no longer accept `MisskeyApi`. `MisskeyApi` retains only its no-origin
client bridge for the `MastodonAuth` compatibility constructor. Slice 2A4 owns both removals and
the dependency redesign.

## Test files

- Added `app/src/test/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClientTest.kt`.
- Added the test-only neutral fixture `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonTestClient.kt`.
- Updated `CrossCuttingTest.kt`, `DirectMessageSourceTest.kt`, `ModerationServiceTest.kt`,
  `NotificationAdapterContractTest.kt`, `ProfileSourceContractTest.kt`, and
  `WebSocketTransportTest.kt`.
- Updated Mastodon capability, integration, node-info, notification-sync, and source-contract tests.
- Updated `HttpClientPoolTest.kt` and `MisskeyApiTest.kt`.

The new transport tests cover foreign authenticated URLs, cancellation, response limits, disabled
redirects, bearer isolation, WebSocket origin validation, and multipart stream closure. Earlier
transport suites do not count as coverage for the new client.

## Verification

- New `AuthenticatedHttpClientTest`: 6 tests passed.
- Requested focused set: 260 tests ran, with 258 passing and 2 cancellation failures.
- `me.foxtails.palustris.data.mastodon.MastodonIntegrationTest.cancelingTimelinePageCancelsRequestAndAllowsRetry`
  failed in the focused run during `@After` server shutdown. Its message was
  `java.io.IOException: Gave up waiting for queue to shut down` from
  `MockWebServer.shutdown` at `MastodonIntegrationTest.kt:83`. The test passed alone.
- `me.foxtails.palustris.data.misskey.MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure`
  failed the focused run at `MisskeyApiTest.kt:140`, where `assertTrue(canceledCall.get())`
  reported `java.lang.AssertionError`. The test passed alone.
- The Mastodon test passed alone with
  `:app:testDebugUnitTest --tests me.foxtails.palustris.data.mastodon.MastodonIntegrationTest.cancelingTimelinePageCancelsRequestAndAllowsRetry`.
- The Misskey test passed alone with
  `:app:testDebugUnitTest --tests me.foxtails.palustris.data.misskey.MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure`.
- `AuthenticatedHttpClientTest.cancellationCancelsInFlightCallAndRemainsCancellationException`
  passed 6/6 when the transport class ran alone.
- These flakes need a test-isolation and timing stabilization owner. Do not weaken the tests.
- `:app:compileDebugUnitTestKotlin`: passed.
- Architecture audit: exit 0, 613 findings, 0 regression lines. The ignored record is
  `logs/architecture-audit-2a3.txt`.
- `:app:lintDebug`: passed.
- `assembleRelease`: passed.
- Combined `test assembleRelease` timed out after unrelated full-suite failures. The separate release
  assembly passed. The full suite reported existing failures in draft actions, capability cache,
  thread continuation, and notification synchronization before timeout.

## Hygiene and staging

Do not stage, commit, or push in this continuation. Preserve unrelated worktree changes.
If a parent agent stages this slice, use an explicit file-by-file pathspec containing only these files:

### New production files

- `app/src/main/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClient.kt`
- `app/src/main/java/me/foxtails/palustris/data/transport/TransportFailure.kt`

### Migrated production files

- `app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt`
- `app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonCapabilityProbe.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonDirectMessageService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonErrorMapper.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonModerationService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonNotificationService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonPageClient.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonProfileService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonPushService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSelfProfileService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonStreamService.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonThreadService.kt`
- `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyApi.kt`
- `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyDirectMessageService.kt`
- `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeySource.kt`

### New and updated test files

- `app/src/test/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClientTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonTestClient.kt`
- `app/src/test/java/me/foxtails/palustris/CrossCuttingTest.kt`
- `app/src/test/java/me/foxtails/palustris/DirectMessageSourceTest.kt`
- `app/src/test/java/me/foxtails/palustris/ModerationServiceTest.kt`
- `app/src/test/java/me/foxtails/palustris/NotificationAdapterContractTest.kt`
- `app/src/test/java/me/foxtails/palustris/ProfileSourceContractTest.kt`
- `app/src/test/java/me/foxtails/palustris/WebSocketTransportTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonCapabilityProbeTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonNodeInfoDiscoveryTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonNotificationSyncTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonSourceContractTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/misskey/HttpClientPoolTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyApiTest.kt`

### Documentation files

- `docs/agents/protocol-and-session-ownership.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

The audit record `logs/architecture-audit-2a3.txt` remains ignored and is not a staging pathspec.
Exclude staged `docs/classic_navigation.md`.
Exclude modified `.opencode/agents/code_reviewer_high.md`.
Exclude modified `.opencode/agents/code_reviewer_low.md`.
Exclude modified `.opencode/agents/git_handler.md`.
Exclude modified `.opencode/agents/orchestrator.md`.
Exclude modified `.opencode/agents/problem_solver_high.md`.
Exclude modified `.opencode/agents/problem_solver_low.md`.
Exclude modified `.opencode/agents/targeted_fixer.md`.
Exclude modified `importantdocs/writing_style.md`.
Exclude deleted `currentbehaviour.png` and `intendedbehaviour.png`.
Exclude untracked `.opencode/agents/adb_handler.md` and `.opencode/agents/codebase_explorer_android.md`.
Exclude untracked `tools/scripts/adb_control.py`, `tools/scripts/adb_flow.py`,
`tools/scripts/adb_inspect.py`, and `tools/scripts/adb_screenshot.py`.
Exclude untracked `tools/scripts/__pycache__/` and `tools/tests/__pycache__/`.
Exclude untracked `screen.png`.

## Limits

Physical-device, API 29 physical, RTL, TalkBack, font-scale, signed-release, and live-server checks
remain unverified. The Mastodon authentication compatibility constructor remains deferred to 2A4.
