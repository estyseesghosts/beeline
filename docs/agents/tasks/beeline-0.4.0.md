# Beeline 0.4.0 task state — Phase 3C2 repair

## Status

Phase 3C2 repairs notification row bounds at a 200% font scale. The repair adds reachability
coverage without changing shared, protocol-neutral notification presentation.

## Requirement and owner

Notification activity, summaries, timestamps, post text, follow-request actions, errors, and the
trailing dismiss action must remain inside the notification card at font scale 2.0.

`NotificationRow.kt` owns these row bounds. `NotificationsScreen.kt` remains the owner of list
filtering, acknowledgement, local-seen state, dismiss callbacks, and compact scroll clearance.

The row now gives weighted content a zero minimum width. Scaled activity, timestamp, hidden text,
warnings, follow-request labels, errors, and dismiss text use bounded lines. Follow-request
buttons share the available content width. Callbacks, semantic state, domain models, and protocol
behavior remain unchanged.

The notification screen test now clicks enabled Accept, Reject, and Dismiss actions. It checks
click semantics and callback delivery at 200% density in compact and wide layouts. It also checks
warning, hidden-content, and error text bounds at 200% density.

## Verification

* The focused `NotificationsScreenTest` suite passed 14 tests.
* The notification test package passed.
* `:app:lintDebug` passed with zero findings.
* The architecture audit reported 615 existing findings and zero regressions.
* The full unit test task ran 1,456 tests and reported 18 known failures owned by Phase 10.
* The reviewer did not rerun the architecture audit.

## Device evidence

The online emulator pass used `emulator-5554` with Android 16, SDK 36, and a 1,848 by 2,448 display.
The display density was 480 dpi with a 616 density value. The initial font scale was 1.0.
The main activity opened in the foreground without restoration. Firefox did not appear.

At font scale 1.0, the Home baseline used `screen_home10.png`. The Notifications destination used
`screen_notif10d.png`. Three cards appeared with Dismiss visible on every card. No card overflowed.
The return Home state used `screen_home10b.png`.

At font scale 2.0, the Home state used `screen_home20.png`. The Notifications state used
`screen_notif20.png`. TEST wrapped to two lines. Rows stayed inside their cards. The first two
Dismiss actions remained fully visible and reachable. No horizontal overflow appeared. The third
card remained partly behind the filter pills during scroll. This was scroll overlap, not row overflow.

The font scale returned to 1.0. The restored Home state used `screen_home_restored.png`.
One accidental star highlight occurred during coordinate probing. The highlight was reverted.
No OAuth flow or other data mutation occurred. The pass used no back action.

The screenshots provide geometry evidence only. Search, Photo Grid, DMs, and Profile at font scale
2.0 remain unverified because extra taps were blocked. Dark, pure-black, wide, foldable, keyboard,
and TalkBack behavior remain unverified. Callback, filtering, and acknowledgement behavior were
not tapped during the device pass.

## Documentation review

The notification boundary documentation remains accurate. No wiki update is required because
ownership, protocol behavior, persistence, navigation, and list-container ownership did not
change.

## Unverified

The emulator verified Notifications at font scales 1.0 and 2.0. Search, Photo Grid, DMs, and
Profile at font scale 2.0 remain unverified because extra taps were blocked. Dark, pure-black,
wide, foldable, keyboard, and TalkBack behavior remain unverified. Callback, filtering, and
acknowledgement behavior were not tapped. The screenshots provide geometry evidence only.

## Preservation and continuation

The unrelated dirty worktree remains untouched. This subagent does not stage, commit, or push.

Last safe commit: `ca058e9`. Next slice: Phase 3C3.
