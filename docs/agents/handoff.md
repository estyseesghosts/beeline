# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current position

Slice 2B2 moves notification launch value, codec, store, and pending-state authority to
`data.notifications`. No staging, commit, or push occurred.

## Verification

The implementer reports that focused router, host, and presentation tests passed. The moved router
test only removed redundant same-package imports. `:app:lintDebug` passed. The required architecture audit command was
`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
It exited 0 with 609 findings and zero `regression:` lines. The previous 2B1 count was 612.
The ignored audit output record is `logs/architecture-audit-2b2.txt`.

The fully qualified Mastodon failure was
`me.foxtails.palustris.data.mastodon.MastodonIntegrationTest.cancelingTimelinePageCancelsRequestAndAllowsRetry`.
The focused run failed during teardown with
`java.io.IOException: Gave up waiting for queue to shut down` from `MockWebServer.shutdown`.
The test passed when rerun alone.

The fully qualified Misskey failure was
`me.foxtails.palustris.data.misskey.MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure`.
The focused run failed at `MisskeyApiTest.kt:140` with `java.lang.AssertionError` from
`assertTrue(canceledCall.get())`. The test passed when rerun alone.

The full `test assembleRelease` attempt timed out at 120 seconds after reporting only DraftActions (2),
CapabilityCache (2), and MisskeyThreadContinuation (5). Sync (5) and Navigation (2) were not visible
before the timeout. Test-result XML files are unavailable. The 16-name BUGS baseline therefore stays
unresolved and unattributed: 9 names were visible, and 7 names remain unknown. No notification launch
failure appeared in the visible output, but this does not prove full coverage or rule out a launch failure.
No failure delta is attributed to this slice.

The implementer reports that the presentation test remains on Robolectric SDK 32 because SDK 35
requires runtime notification permission that this test does not grant. This reason is not
independently verified here.

## Audit count delta

The 2B1 and 2B2 ignored audit records contain summaries only. The commands
`grep -nE 'rule|app/src/' logs/architecture-audit-2b1.txt` and its 2B2 equivalent each exited 1
with zero matching lines. The 2B1 record has 5 total summary lines. The 2B2 record has 6. The
reduction from 612 to 609 is count-only and unexplained at rule level. Both records report zero
regression lines.

## Next slice

Review the unresolved full-suite failures. Then continue from the 2B2 pathspec in the task state.

## Staging boundary

The exact file-by-file staging pathspec is listed in `docs/agents/tasks/beeline-0.4.0.md`. Use
`git add --all -- <paths>` or the listed paths explicitly. This preserves deleted old UI paths and
new data paths; Git may display them as renames. The launch data files, wiring files, tests, and
documentation are intended for this slice. The index still contains unrelated staged
`docs/classic_navigation.md`. Do not reset the index.

Use the pathspec in the task state.

Exclude staged `docs/classic_navigation.md`.
Exclude all pre-existing dirty files, including the modified `.opencode/agents` files and helpers.
Exclude modified `importantdocs/writing_style.md`.
Exclude deleted `currentbehaviour.png` and `intendedbehaviour.png`.
Exclude the four untracked helper scripts, both Python cache directories, and `screen.png`.
Keep ignored `logs/*` outside staging, including both 2B2 records and earlier comparison records.

`git diff --check -- <slice pathspec>` is clean. Full `git diff --check` reports unrelated trailing
whitespace in `.opencode/agents/orchestrator.md`, `.opencode/agents/targeted_fixer.md`, and staged
`docs/classic_navigation.md`. These files are excluded from the slice and remain untouched. Do not
claim that the full check is clean.
