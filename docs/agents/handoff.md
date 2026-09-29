# Handoff

**Status:** Phase 2E is committed at `8e1c046`. Phase 3A-first is implemented in the worktree and
remains uncommitted.

The task-state file is `docs/agents/tasks/beeline-0.4.0.md`.

## Current position

`PillAction` enforces an effective 48 dp target under tight parent constraints. Its label, color,
shape, padding, icon size, loading state, and click condition remain unchanged.

Regression tests cover enabled semantics and bounds, disabled semantics, and loading suppression.

## Verification

The focused `PillActionTest` suite passed 4 tests.

The caller suites passed 77 tests: `HomeFeedTest` 37, `ProfileScreenTest` 22,
`EmojiPickerTest` 17, and `PostShareSheetTest` 1.

`lintDebug` passed. The architecture audit passed with 612 findings and zero regressions.

## Device evidence

The adb check used `emulator-5554` with signed-in `@ctr@mstdn.ca`. The session remained preserved.
The Beeline package was version `0.2.8` (`2008`), with no reinstall or build during the check.
The foreground activity was `me.foxtails.palustris/MainActivity`, visible and on-screen. The IME was
not visible (`isVisible=false`, `mInputShown=false`). The device ran Android 16 (SDK 36), with font
scale 1.0, light theme, light status bar, and an unlocked display. The display was 1848 by 2448 at
density 480 with a 616 override. The package first installed at 2026-09-28 15:54:45 and last
updated at 2026-09-28 17:23:44.

Home, Local, and Federated pills rendered fully. The `#travel` chip was visible. Five post-action
icons were evenly spaced. No clipped hit areas appeared in screenshot geometry. Back navigation
returned to Home. A mis-tap opened the image viewer, so the search screenshot does not verify Search
or Photo Grid. The Compose hierarchy exposed only `android.view.View` nodes with empty text and
content descriptions. Numeric 48 dp proof therefore remains code and test evidence.

Commands used:

`gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "*PillActionTest"`

`gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "*HomeFeedTest" --tests "*ProfileScreenTest" --tests "*PostShareSheetTest" --tests "*EmojiPickerTest"`

`gradlew.bat --no-daemon --console=plain :app:lintDebug`

`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`

The full gate is known red in `logs/BUGS.txt`; Phase 10 owns a green full gate.

## Next slice

Phase 3A-2: theme roles.

Last safe commit: `8e1c046` (Phase 2E). Next commit: pending for Phase 3A-first.

## Limits

Search, Photo Grid, Notifications, direct messages, and Profile were not captured. Dark and
pure-black themes, compact and wide or foldable layouts, font scale 200 percent, keyboard,
TalkBack, RTL, API 29, and UI dump numeric bounds remain unverified. Live-server behavior also
remains unverified. The screenshots `.tmp-pill-search.png` and `.tmp-pill-home2.png` remain
untracked. The adb handler and reviewer own these checks.

The staged classic navigation document, `.opencode` changes, images, helpers, caches, logs, and the
committed Photo Grid test deletion remain unrelated. The temporary inspection files and screenshots
remain untracked. This subagent did not stage or commit.
