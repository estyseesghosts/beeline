# Beeline 0.4.0 task state — Phase 3 complete

## Status

Phase 3 is complete. The seven Phase 3 slices improve high-font readability, shared typography,
chip geometry, and motion characterization without changing the product boundary.

Phase 4A is next. It will stabilize back behavior, modal behavior, and memory after the Phase 3A
and 3B work. The last safe source commit is `464b2d1`. This documentation update is pending its
own commit; its next hash is not known yet.

## Phase 3 history

The Phase 3 commits are:

1. `5b8d98d` — enforce the `PillAction` 48 dp effective bounds.
2. `e23972e` — centralize post and tab typography roles.
3. `2c3a1a8` — reuse shared bubble geometry in `CategoryChips`.
4. `7e7f774` — characterize shared motion transitions without visual change.
5. `ca058e9` — keep post metadata and actions readable at 200 percent text.
6. `eaeff11` — repair notification row bounds at 200 percent text.
7. `287b02c` — stack profile statistics at narrow widths for large text.
8. `464b2d1` — record the Phase 3 visual system as complete.

Per-slice tests, lint, and architecture audits passed or reported no changed-scope regression.
The full gate remains known red, and Phase 10 owns that failure. The whole-worktree
`diff --check` failure is unrelated `.opencode` whitespace; changed-file checks passed.

## Fresh-build emulator evidence

Earlier device runs were stale. They used Beeline 0.2.8 installed on 2026-09-28, before all
Phase 3 commits. Those runs are not evidence for the Phase 3 implementation.

Fresh evidence used plain `adb -s emulator-5554` commands without helpers or command chaining:

- The APK at `app/build/outputs/apk/debug/app-debug.apk` was built from HEAD `464b2d1`; the build
  exited 0.
- `adb -s emulator-5554 install -r` returned `Success` and preserved data.
- Package data showed `lastUpdateTime` 2026-09-29 01:36:54. `firstInstall` remained 2026-09-28.
  The package was version 0.2.8/2008.
- `MainActivity` was foreground at task 29 in `RESUMED` state. Firefox OAuth remained in the
  background and `STOPPED`.
- `font_scale` was 1.0 and the display was 1848x2448.
- Home showed Home, Local, and Federated chips inside the screen. Chip-row heights were about
  185 pixels. Post metadata, timestamps, hashtag chips (`#travel +1` and `#photomonday +22`),
  and media were inside the screen.
- The Home action row was `[31,1696][1817,1881]`. It had five actions, each about 357 pixels
  wide, with no clipping. Bottom navigation and Compose content were inside the screen.
- Notifications showed three cards inside the screen. Each Dismiss control was inside its card
  with no clipping. The filter row showed five filters; Likes at the right edge was scrollable.
- A view-only tap at `1228,2109` opened Notifications. A view-only tap at `839,2109` returned
  Home. The session stayed preserved. This run did not dismiss, acknowledge, filter, or mutate
  data. An accidental star from an earlier run is not part of this fresh run.
- `fresh-home.png` was written with plain `exec-out`. Image reading was blocked, so the geometry
  evidence comes from UI dumps.

## Remaining verification limits

Profile, Search, Photo Grid, and direct messages remain unverified on this fresh build. Dark mode,
wide and foldable layouts, 200 percent font scale, keyboard behavior, TalkBack, and live-server
behavior also remain unverified. A 200 percent run needs a separate font-scale change and restore.

## Documentation review

The Phase 3 changes affect characterization coverage, minimum-height behavior, and narrow
profile-stat stacking. They do not change screen ownership, navigation ownership, persistence,
protocol behavior, or the shell boundary. No wiki update is required.

## Preservation and continuation

This integration record changes documentation only. It does not change production or test source.
The unrelated staged `classic_navigation` document, modified agent files and writing-style file,
deleted PNGs and Photo Grid test, untracked images and helpers, caches, and temporary files remain
untouched. No secrets entered the work. No staging, commit, or push occurs here.

Next slice: Phase 4A, stabilize back, modal, and memory behavior after Phase 3A and 3B.
