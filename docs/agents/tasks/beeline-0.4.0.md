# Beeline 0.4.0 task state — slice 2B2

## Objective

Move notification launch value, codec, storage, and pending-state ownership from UI to data.
Preserve the 2B1 launch contract and storage format.

## Status sets

- Source status: moved to `data.notifications` without behavior changes.
- Test status: the implementer reports that focused router, host, and presentation tests passed.
- Instrumented intent status: unavailable in this environment. No device intent test ran.
- Audit status: 609 findings and zero regression lines. The 612-finding 2B1 count reduced after the move.
- Lint status: `:app:lintDebug` passed.
- Full gate status: unresolved and unattributed. `test assembleRelease` timed out after reporting
  only DraftActions (2), CapabilityCache (2), and MisskeyThreadContinuation (5). Sync (5) and
  Navigation (2) were not visible before the timeout. No test-result XML was available. The 16-name
  BUGS baseline therefore remains unresolved: 9 names were visible, and 7 names remain unknown.
  No notification launch failure appeared in the visible output, but this does not prove full coverage.
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

- `app/src/main/java/me/foxtails/palustris/data/notifications/NotificationLaunch.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/NotificationLaunchStore.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/NotificationLaunchRouter.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationPresenter.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationDismissReceiver.kt`
- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
- `app/src/main/java/me/foxtails/palustris/MainActivity.kt`
- `app/src/test/java/me/foxtails/palustris/data/notifications/NotificationLaunchRouterTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHostTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/notifications/NotificationPresentationTest.kt`
- `docs/wiki/notifications-and-direct-messages.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

Deleted old paths:

- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouter.kt`
- `app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouterTest.kt`

## Verification

- The implementer reports that focused Gradle notification tests passed.
- `git diff --check -- <slice pathspec>` is clean. Full `git diff --check` reports unrelated trailing whitespace in
  `.opencode/agents/orchestrator.md`, `.opencode/agents/targeted_fixer.md`, and staged
  `docs/classic_navigation.md`. These files are excluded from the slice and remain untouched.
- Architecture audit command: `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
  The command exited 0 with 609 findings and zero `regression:` lines.
- `:app:lintDebug` passed.
- `test assembleRelease` timed out at 120 seconds after reporting only three categories. Test-result
  XML files are unavailable. Sync and Navigation remain unknown after the timeout. The full-suite
  failure set remains unresolved and unattributed against the 16-name BUGS baseline.
- The implementer reports that notification presentation tests remain on Robolectric SDK 32 because
  SDK 35 requires runtime notification permission that this test does not grant. This reason is not
  independently verified here.

Ignored records:

- `logs/260928-2b2-notification-launch.txt`
- `logs/architecture-audit-2b2.txt`
- `logs/260928-2a4.txt` (comparison record for the 16 names)

The audit records contain summaries only. `grep -nE 'rule|app/src/' logs/architecture-audit-2b1.txt`
and the matching 2B2 command each exited 1 with zero matching lines. The records have 5 and 6 total
summary lines. The 612-to-609 reduction is count-only and unexplained at rule level. No regression
line appeared in either record.

## Staging pathspec

Do not stage, commit, or push in this continuation. If a parent stages this slice, use
`git add --all -- <paths>` so deleted old paths and new paths remain in the index. Use explicit path
arguments instead when the parent must avoid broad staging. The deleted old paths are listed after
the add and modify paths. Git may display matching pairs as renames. Use only these paths:

```text
app/src/main/java/me/foxtails/palustris/data/notifications/NotificationLaunch.kt
app/src/main/java/me/foxtails/palustris/data/notifications/NotificationLaunchStore.kt
app/src/main/java/me/foxtails/palustris/data/notifications/NotificationLaunchRouter.kt
app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationPresenter.kt
app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationDismissReceiver.kt
app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHost.kt
app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt
app/src/main/java/me/foxtails/palustris/MainActivity.kt
app/src/test/java/me/foxtails/palustris/data/notifications/NotificationLaunchRouterTest.kt
app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHostTest.kt
app/src/test/java/me/foxtails/palustris/data/notifications/NotificationPresentationTest.kt
docs/wiki/notifications-and-direct-messages.md
docs/agents/tasks/beeline-0.4.0.md
docs/agents/handoff.md
app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouter.kt
app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouterTest.kt
```

Deleted paths (`D` in the current worktree; Git may report `R` after staging):

```text
D app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouter.kt
D app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouterTest.kt
```

Exclude all pre-existing dirty files, including staged `docs/classic_navigation.md`. Keep ignored
logs outside staging.

## Limits

Live push, physical device, API 29, RTL, TalkBack, font scale, and signed-release checks remain
unverified.
