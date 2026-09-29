# Beeline 0.4.0 task state — Phase 3A-2

## Status

Phase 3A-2 centralizes semantic typography roles that have consumers. The roles delegate to
existing Material 3 styles. This slice does not change typography metrics, colors, spacing,
navigation, or storage.

## Ownership and scope

`AppTypography.kt` owns the semantic role names and the existing Material style mappings. Post
body text, author names, post metadata, and Home timeline tabs use these roles. Unused count and
heading roles were removed. The compact and large navigation files remain unchanged.

The role boundary remains protocol-neutral. It does not own font preferences, color schemes, post
policy, media, editors, emoji, or navigation state.

## Tests and verification

`AppTypographyTest` passed 1 test across all font families and text-size preferences.
`HomeFeedTest`, `NavigationTest`, `SinglePostScreenTest`, and `PostShareSheetTest` ran 111 tests;
109 passed and two baseline `NavigationTest` draft failures remained. The failures were
`closingComposerAutosavesUnsavedText` at line 820 and
`draftsSurviveActivityRecreationAndCanBeDeleted` at line 803. `logs/BUGS.txt` entry
`20260923-0B` records both failures against identical app sources with no `app/src` diff. This
slice does not touch draft, composer, or navigation state code.

The focused `AppTypographyTest` command passed. The affected-suite command exited 1 because of
the two baseline failures. `lintDebug` passed. The architecture audit passed with 616 findings
and zero regressions. `diff --check` passed for the allowed documentation paths.

The full gate remains known red because Phase 10 owns the existing failures. It was not reclassified
as a Phase 3A-2 regression.

## Device evidence — 3A-2

The adb handler tested `emulator-5554`, Android 16 SDK 36, at 1848x2448 with density 480 and
override 616. `MainActivity` was focused, the keyguard was false, the IME was hidden, and light
status and navigation bars were active. The installed debug package was version 0.2.8 (2008),
debuggable, with first install and last update on 2026-09-28; no reinstall was used. Font scale
was 1.0.

The `screen.png`, `screen_top.png`, and `screen_restored.png` captures showed fully rendered
authors, bodies, and timestamps without overlap. Home, Local, and Federated tabs were legible,
with the selected tab visible. Floating chrome covered images as designed, while text remained
readable. Compact portrait light geometry passed. A 500,600 to 500,1800 swipe scrolled only.

Back switched to Firefox at the `mstdn.ca/oauth/authorize` page. This is a behavior note, not a
typography failure. Starting `MainActivity` again recovered Home with state preserved. No sign-out
was performed. The `@ctr@mstdn.ca` account identity was preserved in the evidence and is not a
secret.

Unverified: Search and Profile because of mis-tap risk; dark or pure-black theme; wide or foldable
layouts because no fold display was available; 200% font scale; keyboard; and TalkBack. The
captures provide geometry evidence only. Temporary screenshots remain untracked and are excluded
from the documentation change.

## Documentation review

`docs/wiki/ui-and-navigation.md` documents navigation and feature boundaries, not theme
typography. `docs/agents/app-shell-ownership.md` documents shell ownership, not theme typography.
Both remain unchanged.

## Preservation and continuation

Only this task-state file and `docs/agents/handoff.md` are updated. Production and test source,
the staged classic navigation document, `.opencode` changes, images, helpers, caches, logs, and
the committed Photo Grid test deletion remain unrelated and untouched. This subagent does not
stage, commit, or push.

Prior history `8e1c046` and `5b8d98d` remains accurate. Last safe commit: `5b8d98d`. Next slice:
Phase 3A-3 or Phase 3B.
