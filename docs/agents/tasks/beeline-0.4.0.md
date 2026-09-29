# Beeline 0.4.0 task state — Phase 3A-first

## Status

Phase 2E is committed at `8e1c046`. This worktree contains the first Phase 3A behavior.
`PillAction` now keeps an effective 48 dp by 48 dp target under tight parent constraints.

The previous task state was stale. It incorrectly claimed that Phase 2E remained uncommitted.

## Ownership and scope

`PillAction.kt` owns the shared action target, semantics, loading suppression, and click behavior.
`BubbleGeometry.kt` owns the shared 24 dp pill radius and 48 dp minimum height values.
Callers include hashtag actions, post interaction presentation, post sharing, emoji pinning, and
profile actions. No caller changes are required.

The slice changes only target measurement and regression coverage. It does not change labels,
colors, shape, padding, icons, navigation, post policy, media, editor, or emoji behavior.

## Tests

`PillActionTest` covers enabled button semantics and click behavior, constrained 48 dp bounds,
disabled semantics, and loading click suppression.

The focused suite passed 4 tests. Caller suites passed 77 tests: HomeFeed 37, ProfileScreen 22,
EmojiPicker 17, and PostShareSheet 1. `lintDebug` passed. The architecture audit passed with 612
findings and zero regressions.

## Documentation review

`docs/wiki/ui-and-navigation.md` does not define the `PillAction` or `BubbleGeometry` boundary.
It remains unchanged. `docs/agents/app-shell-ownership.md` also does not define this boundary.

## Device evidence

The adb check used one device: `emulator-5554`. The signed-in account was `@ctr@mstdn.ca`, and
the session remained preserved. The foreground activity was `me.foxtails.palustris/MainActivity`, visible and on-screen. The IME was not
visible (`isVisible=false`, `mInputShown=false`). The package was Beeline version `0.2.8` (`2008`),
with first install at 2026-09-28 15:54:45 and last update at 2026-09-28 17:23:44. No reinstall or
build ran during the check.

The device used Android 16 (SDK 36), font scale 1.0, light theme, and an unlocked display. The
display was 1848 by 2448 at density 480 with a 616 override. Home showed fully rendered Home,
Local, and Federated pills. The `#travel` chip was visible. Five post-action icons were evenly
spaced. No clipped hit areas appeared in the screenshot geometry. Back navigation returned to Home.

The search screenshot was taken after a mis-tap opened the image viewer. It is not evidence for
Search or Photo Grid. The Compose hierarchy exposed only `android.view.View` nodes with empty text
and content descriptions, so numeric 48 dp proof remains code and test evidence.

Search, Photo Grid, Notifications, direct messages, and Profile were not captured. Dark and
pure-black themes, compact and wide or foldable layouts, 200 percent font scale, keyboard, and
TalkBack were not run. UI dump numeric bounds were not measurable. The screenshots
`.tmp-pill-search.png` and `.tmp-pill-home2.png` remain untracked. These checks remain unverified.

## Preservation

The staged classic navigation document, `.opencode` changes, images, helpers, caches, logs, and the
committed Photo Grid test deletion remain unrelated and untouched. The temporary inspection files
and screenshots remain untracked and untouched.

This subagent does not stage or commit. The parent session must preserve those paths.
