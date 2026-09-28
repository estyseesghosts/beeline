# Beeline 0.4.0 task state — slice 2A2

## Objective

Move the generic HTTP client pool and response from `data/misskey` to `data/transport`.

## Boundary

Slice 2A1 is committed as `5db0d1a`. Slice 2A2 changes transport ownership only. Slice 2A3
still owns the future `MisskeyApi` move. Slice 2A4 still owns authentication and dependency
injection redesign.

## Changes

- Moved `HttpClientPool` and `HttpLayerConfig` to `data/transport`.
- Moved `HttpResponse` and its generic Link cursor parser to `data/transport`.
- Preserved timeouts, disabled redirects, the maximum of 16 clients, connection keying, and
  singleton wiring.
- Updated Misskey, Mastodon, authentication, source, dependency injection, and test imports.
- Kept `MisskeyApi`, Misskey failures, response limits, JSON bodies, and prefixes in `data/misskey`.
- Exhaustive grep found no old fully qualified transport type references.

## Verification

- The post-edit focused command passed 142 tests: MisskeyApiTest (5), HttpClientPoolTest (9),
  WebSocketTransportTest (6), MastodonIntegrationTest (59), MisskeyIntegrationTest (48), and
  SessionLifecycleTest (15). It exited 0 with zero regressions.
- The complete focused evidence remains 23 transport tests, 113 adapter tests, and 17 authentication
  tests. The earlier adapter rerun passed after one queue-shutdown timeout.
- The cancellation test passed when rerun alone:
  `MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure`.
- `:app:lintDebug` passed after the cosmetic import edit.
- `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`
  exited 0 and reported 613 findings. It reported zero `regression:` lines. Output is in ignored
  `logs/architecture-audit-2a2.txt`. This matches the 2A1 count of 613.
- The full `test assembleRelease` gate ran past the 180-second limit. The test task reported 1,385
  tests and 17 failures before the command timed out. The 2A1 baseline records 16 failures in
  `logs/BUGS.txt`. The retained output does not identify the seventeenth test, failure message, or
  test XML. The cancellation test passed alone, so the seventeenth failure remains unexplained and
  unattributed. It is not documented as a baseline failure or a proven flake. A separate
  `:app:assembleRelease` run passed.

## Documentation

`docs/agents/protocol-and-session-ownership.md` now names `data/transport` as the owner for the
generic pool and response. It does not claim that slices 2A3 or 2A4 are complete.

## Hygiene

No slice files are staged, committed, or pushed by this slice. The separate status command observed:

- Staged: `docs/classic_navigation.md`.
- Modified slice files: the two moved source files, their updated callers and tests, and
  `docs/agents/protocol-and-session-ownership.md`, this task file, and `docs/agents/handoff.md`.
- Modified unrelated files: seven `.opencode/agents/*.md` files and `importantdocs/writing_style.md`.
- Deleted unrelated files: `currentbehaviour.png` and `intendedbehaviour.png`.
- Untracked unrelated files: two `.opencode/agents/*.md` files, `screen.png`, three helper scripts,
  and two Python cache directories.
- Ignored files: `logs/260928-000000.txt`, `logs/architecture-audit-2a2.txt`, and prior ignored logs.

The intended staging set contains exactly these files:

- Moved production files: `app/src/main/java/me/foxtails/palustris/data/misskey/HttpClientPool.kt`
  to `app/src/main/java/me/foxtails/palustris/data/transport/HttpClientPool.kt`, and
  `app/src/main/java/me/foxtails/palustris/data/misskey/HttpResponse.kt` to
  `app/src/main/java/me/foxtails/palustris/data/transport/HttpResponse.kt`.
- Updated production files: `app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt`,
  `app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt`,
  `app/src/main/java/me/foxtails/palustris/data/auth/MisskeyAuth.kt`,
  `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonNotificationService.kt`,
  `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonPageClient.kt`,
  `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt`,
  `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyApi.kt`,
  `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyPushService.kt`, and
  `app/src/main/java/me/foxtails/palustris/di/AppModule.kt`.
- Updated test files: `app/src/test/java/me/foxtails/palustris/CrossCuttingTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/auth/SessionLifecycleTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/misskey/HttpClientPoolTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyIntegrationTest.kt`,
  `app/src/test/java/me/foxtails/palustris/data/notifications/push/PushCancellationTest.kt`, and
  `app/src/test/java/me/foxtails/palustris/ui/session/AccountManagerFixtures.kt`.
- Documentation files: `docs/agents/protocol-and-session-ownership.md`,
  `docs/agents/tasks/beeline-0.4.0.md`, and `docs/agents/handoff.md`.

Use an explicit pathspec list for `git add` and `git commit`. The commit pathspec list must contain
only the files above, which excludes the already staged `docs/classic_navigation.md`.

Exclude these unrelated sets: staged `docs/classic_navigation.md`; seven modified `.opencode/agents`
files and modified `importantdocs/writing_style.md`; deleted `currentbehaviour.png` and
`intendedbehaviour.png`; untracked helper scripts, Python caches, and `screen.png`; and ignored
`logs/*` files.

## Limits

Live-server, physical-device, API 29 physical, RTL, TalkBack, font-scale, and signed-release
behavior remain unverified.
