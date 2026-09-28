# Beeline 0.4.0 task state — slice 2B1

## Objective

Characterize notification launch contracts before the 2B2 ownership move. This slice changes tests
and engineering records only. Production files remain unchanged.

## Status sets

- Source status: unchanged.
- Test status: The implementer reports 20 focused tests passed: 13 router, 5 host, and 2 presentation tests.
- Instrumented intent status: unavailable in this environment. No device intent test ran.
- Audit status: The implementer reports 612 findings and zero regression lines. This matches the 2A baseline.
- Lint status: The implementer reports that lint passed.
- Full gate status: unresolved. Release assembly passed, but the full test task timed out after
  reporting 16 unattributed failures. The XML files are unavailable, so this slice does not claim
  that they predate the slice.
- Device and live-server status: unverified.

## Characterized contracts

- Tap intents use `ACTION_VIEW`, the `palustris://notification/open/<key>` URI, and four extras.
- Misskey and Mastodon protocol enum values serialize and parse.
- Blank, missing, invalid, foreign, malformed, and mismatched values reject without persistence.
- Stored keys remain `origin`, `account_local_id`, `protocol`, and `notification_id`.
- SharedPreferences recreation, partial data, corrupt data, clear, and dismiss parsing are covered.
- The host waits during startup, switches to a non-active account, retains rejected launches, routes
  missing accounts to unavailable, and clears only an accepted launch.
- Tap and dismiss pending intents remain distinct and use the notification Android ID as request code.
- Pending-intent flags remain `UPDATE_CURRENT | IMMUTABLE`.

## Changed files

- `app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouterTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHostTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/notifications/NotificationPresentationTest.kt`
- `docs/wiki/notifications-and-direct-messages.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

## Verification

- Focused Gradle notification tests: The implementer reports that they passed.
- `git diff --check -- <slice pathspec>` is clean. Full `git diff --check` reports unrelated trailing whitespace in
  `.opencode/agents/orchestrator.md`, `.opencode/agents/targeted_fixer.md`, and staged
  `docs/classic_navigation.md`. These files are excluded from the slice and remain untouched.
- Architecture audit command: `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
  The implementer reports that it exited 0 with 612 findings and zero `regression:` lines.
- `:app:lintDebug`: The implementer reports that it passed.
- `test assembleRelease`: The implementer reports that it timed out after `assembleRelease` passed. The separate `test` retry also
  timed out after reporting the same 16 failures. The implementer reports that no focused notification test failed.
- The 16 names remain unresolved and unattributed. They were recorded in the 2A4 log by category: DraftActions (2), CapabilityCache (2),
  MisskeyThreadContinuation (5), NotificationSyncOrchestrator (5), and Navigation (2).
  Test-result XML files remain unavailable. The names are unattributed and text-compared only with
  `logs/BUGS.txt` and the 2A4 record; this is not proof that they predate 2B1.
- The implementer reports that notification presentation tests remain on Robolectric SDK 32 because
  SDK 35 requires runtime notification permission that this test does not grant. This reason is not
  independently verified here.

Ignored records:

- `logs/260928-2b1-notification-launch.txt`
- `logs/architecture-audit-2b1.txt`
- `logs/260928-2a4.txt` (comparison record for the 16 names)

## Staging pathspec

Do not stage, commit, or push in this continuation. If a parent stages this slice, use only these
paths:

```text
app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouterTest.kt
app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHostTest.kt
app/src/test/java/me/foxtails/palustris/data/notifications/NotificationPresentationTest.kt
docs/wiki/notifications-and-direct-messages.md
docs/agents/tasks/beeline-0.4.0.md
docs/agents/handoff.md
```

Exclude all pre-existing dirty files, including staged `docs/classic_navigation.md`. Keep ignored
logs outside staging.

## Limits

Live push, physical device, API 29, RTL, TalkBack, font scale, and signed-release checks remain
unverified.
