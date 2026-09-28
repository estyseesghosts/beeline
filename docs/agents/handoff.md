# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current position

Slice 2A3 now uses `AuthenticatedHttpClient` for neutral authenticated HTTP execution. Mastodon
production services no longer construct that client through `MisskeyApi`. The test suite uses a
test-only neutral fixture. No staging, commit, or push occurred.

## Verification

The new transport test passes 6 tests. The requested focused adapter and transport set ran 260
tests, with 258 passing and two cancellation failures.

The fully qualified Mastodon failure was
`me.foxtails.palustris.data.mastodon.MastodonIntegrationTest.cancelingTimelinePageCancelsRequestAndAllowsRetry`.
The focused run failed during teardown with
`java.io.IOException: Gave up waiting for queue to shut down` from `MockWebServer.shutdown`.
The test passed when rerun alone.

The fully qualified Misskey failure was
`me.foxtails.palustris.data.misskey.MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure`.
The focused run failed at `MisskeyApiTest.kt:140` with `java.lang.AssertionError` from
`assertTrue(canceledCall.get())`. The test passed when rerun alone.

These failures are isolation or timing flakes. They are not a transport baseline failure.
They need a test-isolation and timing stabilization owner. Do not weaken the tests.

`AuthenticatedHttpClientTest.cancellationCancelsInFlightCallAndRemainsCancellationException`
passed all 6 tests. The architecture audit exits 0 with 613 findings and zero regression lines.
Its ignored record is `logs/architecture-audit-2a3.txt`.

`:app:lintDebug` and `assembleRelease` pass. The combined `test assembleRelease` command timed out
after unrelated full-suite failures. Full device and live-server checks remain unavailable. Earlier
suites do not prove the new transport client without its new test.

## Next slice

Run the remaining gates. Inspect the diff. Use the explicit file-by-file pathspec in the task state
if a parent agent later stages this slice. Keep `MastodonAuth` compatibility for 2A4 only.

## Staging boundary

The exact staging pathspec is listed in `docs/agents/tasks/beeline-0.4.0.md`.
It contains two new transport production files, 17 migrated production files, 15 test files,
and three documentation files.

Exclude staged `docs/classic_navigation.md`.
Exclude the seven modified `.opencode/agents` files and the two untracked `.opencode/agents` helpers.
Exclude modified `importantdocs/writing_style.md`.
Exclude deleted `currentbehaviour.png` and `intendedbehaviour.png`.
Exclude the four untracked helper scripts, both Python cache directories, and `screen.png`.
Keep ignored `logs/*` outside staging, including the audit record.
