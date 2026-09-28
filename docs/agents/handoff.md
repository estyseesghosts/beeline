# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current position

Slice 2B1 adds notification launch characterization tests. No production file changed. No staging,
commit, or push occurred.

## Verification

The implementer reports that 20 focused router, host, and presentation tests passed. The implementer
reports that lint passed. The required architecture audit command was
`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
The implementer reports that it exited 0 with 612 findings and zero `regression:` lines. The count
matches the 2A baseline. This review did not independently rerun the focused tests, lint, or audit.
The ignored audit output record is `logs/architecture-audit-2b1.txt`.

The fully qualified Mastodon failure was
`me.foxtails.palustris.data.mastodon.MastodonIntegrationTest.cancelingTimelinePageCancelsRequestAndAllowsRetry`.
The focused run failed during teardown with
`java.io.IOException: Gave up waiting for queue to shut down` from `MockWebServer.shutdown`.
The test passed when rerun alone.

The fully qualified Misskey failure was
`me.foxtails.palustris.data.misskey.MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure`.
The focused run failed at `MisskeyApiTest.kt:140` with `java.lang.AssertionError` from
`assertTrue(canceledCall.get())`. The test passed when rerun alone.

The previous slice reported two isolated cancellation failures. This slice does not weaken those
tests. The full gate remains unresolved. The implementer reports that the combined `test
assembleRelease` command completed release assembly but reported 16 failures. Test-result XML files
are unavailable, so the names remain unresolved and unattributed. The 2A4 record lists them by
category: DraftActions (2), CapabilityCache (2), MisskeyThreadContinuation (5),
NotificationSyncOrchestrator (5), and Navigation (2). The names were text-compared with
`logs/BUGS.txt` and `logs/260928-2a4.txt`. This does not prove that they predate 2B1.
No failure delta is attributed to this slice.

The implementer reports that the presentation test remains on Robolectric SDK 32 because SDK 35
requires runtime notification permission that this test does not grant. This reason is not
independently verified here.

## Next slice

Review the 16 full-suite failures against the previous baseline. Then begin 2B2 using the
characterization tests and the explicit pathspec in the task state.

## Staging boundary

The exact staging pathspec is listed in `docs/agents/tasks/beeline-0.4.0.md`. The three test files
and three documentation files are intended for this slice. The index still contains unrelated staged
`docs/classic_navigation.md`. Do not reset the index.

Use the pathspec in the task state.

Exclude staged `docs/classic_navigation.md`.
Exclude all pre-existing dirty files, including the modified `.opencode/agents` files and helpers.
Exclude modified `importantdocs/writing_style.md`.
Exclude deleted `currentbehaviour.png` and `intendedbehaviour.png`.
Exclude the four untracked helper scripts, both Python cache directories, and `screen.png`.
Keep ignored `logs/*` outside staging, including both 2B1 records and the 2A4 comparison record.

`git diff --check -- <slice pathspec>` is clean. Full `git diff --check` reports unrelated trailing
whitespace in `.opencode/agents/orchestrator.md`, `.opencode/agents/targeted_fixer.md`, and staged
`docs/classic_navigation.md`. These files are excluded from the slice and remain untouched. Do not
claim that the full check is clean.
