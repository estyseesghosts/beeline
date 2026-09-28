# Beeline 0.4.0 task state — slice 2B3

## Objective

Separate prepared notification text from UI formatting.
Preserve notification content, launch behavior, and delivery state.

## Status sets

- Source status: `NotificationTextResolver` owns Android notification title and body resolution in `data.notifications`.
- Test status: presentation tests cover reaction, fallback, content-warning, and preview contracts.
- Instrumented intent status: unavailable in this environment. No device intent test ran.
- Audit status: 608 findings and zero regression lines. The 2B2 count was 609.
- Lint status: `:app:lintDebug` passed.
- Full gate status: not green. `test assembleRelease` reached `assembleRelease` but timed out after
  reporting known DraftActions, CapabilityCache, MisskeyThreadContinuation, and
  NotificationSyncOrchestrator failures. No failure is attributed to this slice.
- Device and live-server status: unverified.

Focused presentation tests passed. Repository and delivery planner tests passed.
The architecture audit passed with 608 findings and zero regression lines.
`test assembleRelease` reached `assembleRelease` but timed out at 120 seconds while the full test
task reported known DraftActions, CapabilityCache, MisskeyThreadContinuation, and
NotificationSyncOrchestrator failures. The full gate is not green.

## Prepared text boundary

`NotificationPresentationFactory` owns the resolver lifetime and passes its application context.
It resolves resources in the current locale and does not depend on Compose or `ui.notifications`.
The UI formatter remains the owner of Compose notification-row labels.

Content warnings take precedence over post text. Empty and disabled previews use actor text.
Empty actor text uses the actorless resource. Reaction fallback labels use the same resource or plain
value for both protocols. `BigTextStyle` receives the prepared body without truncation.

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
- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLabelText.kt`
- `docs/wiki/notifications-and-direct-messages.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

Deleted old paths:

- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouter.kt`
- `app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationLaunchRouterTest.kt`

## Changed files for 2B3

- `app/src/main/java/me/foxtails/palustris/data/notifications/NotificationTextResolver.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationPresenter.kt`
- `app/src/test/java/me/foxtails/palustris/data/notifications/NotificationPresentationTest.kt`
- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLabelText.kt`
- `docs/wiki/notifications-and-direct-messages.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

## Verification

- Focused notification presentation, repository, and delivery planner tests passed after the blank
  post-text fallback assertion was added.
- `git diff --check -- <slice pathspec>` is clean. Full `git diff --check` reports unrelated trailing whitespace in
  `.opencode/agents/orchestrator.md`, `.opencode/agents/targeted_fixer.md`, and staged
  `docs/classic_navigation.md`. These files are excluded from the slice and remain untouched.
- Architecture audit command: `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
  The command exited 0 with 608 findings and zero `regression:` lines. The ignored output is
  `logs/architecture-audit-2b3.txt`.
- `:app:lintDebug` passed.
- `test assembleRelease` timed out at 120 seconds. Its report listed DraftActions, CapabilityCache,
  MisskeyThreadContinuation, and NotificationSyncOrchestrator failures. Test-result XML files are
  unavailable, so the full-suite failure set remains unresolved and unattributed against the
  16-name BUGS baseline.
- The implementer reports that notification presentation tests remain on Robolectric SDK 32 because
  SDK 35 requires runtime notification permission that this test does not grant. This reason is not
  independently verified here.

Ignored records:

- `logs/260928-2b2-notification-launch.txt`
- `logs/architecture-audit-2b2.txt`
- `logs/260928-2a4.txt` (comparison record for the 16 names)

The audit records contain summaries only. `grep -nE 'rule|app/src/' logs/architecture-audit-2b1.txt`
and the matching 2B2 command each exited 1 with zero matching lines. The records have 5 and 6 total
summary lines. The 612-to-609 and 609-to-608 reductions are count-only and unexplained at rule level.
No regression line appeared in any record.

## Staging pathspec

For 2B3, stage only the following paths if the parent requests staging:

```text
app/src/main/java/me/foxtails/palustris/data/notifications/NotificationTextResolver.kt
app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationPresenter.kt
app/src/test/java/me/foxtails/palustris/data/notifications/NotificationPresentationTest.kt
docs/wiki/notifications-and-direct-messages.md
docs/agents/tasks/beeline-0.4.0.md
docs/agents/handoff.md
```

Do not stage, commit, or push in this continuation. Keep ignored verification logs outside staging.

The previous 2B2 pathspec follows for historical reference. Do not use it for 2B3.

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
