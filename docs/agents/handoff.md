# Handoff

**Status:** Phase 3 is complete through source commit `464b2d1`. This handoff records the fresh
build integration only. It changes no production or test source.

The task-state file is `docs/agents/tasks/beeline-0.4.0.md`.

## Fresh-build correction

Prior device runs were stale. They used Beeline 0.2.8 installed on 2026-09-28, before all Phase 3
commits. Do not use those runs as Phase 3 evidence.

The fresh APK was built from HEAD `464b2d1` at
`app/build/outputs/apk/debug/app-debug.apk`; the build exited 0. Plain
`adb -s emulator-5554 install -r` returned `Success` and preserved data. Package
`lastUpdateTime` was 2026-09-29 01:36:54, with `firstInstall` still 2026-09-28 and version
0.2.8/2008.

## Fresh emulator evidence

`MainActivity` was `RESUMED` in task 29. Firefox OAuth stayed background `STOPPED`. Font scale was
1.0 and the display was 1848x2448.

Home showed Home, Local, and Federated chips inside the screen, with chip-row heights near 185
pixels. Post metadata, timestamps, hashtag chips (`#travel +1` and `#photomonday +22`), media,
the five-action row, bottom navigation, and Compose content were inside the screen. The action
row was `[31,1696][1817,1881]`; each action was about 357 pixels wide and none was clipped.

Notifications showed three cards inside the screen. Each Dismiss control was inside its card with
no clipping. The five-filter row was present, and Likes at the right edge was scrollable. View-only
taps opened Notifications at `1228,2109` and returned Home at `839,2109`. The session stayed
preserved. This fresh run did not dismiss, acknowledge, filter, or mutate data. An earlier
accidental star is not part of this run.

`fresh-home.png` was written with plain `exec-out`. Image reading was blocked, so UI dumps provide
the geometry evidence.

## Limits

Profile, Search, Photo Grid, direct messages, dark mode, wide and foldable layouts, 200 percent
font scale, keyboard behavior, TalkBack, and live-server behavior remain unverified. A 200 percent
run needs a separate font-scale change and restore.

The full gate remains known red, and Phase 10 owns that failure. The task-state file contains the
per-slice test, lint, and audit record.

## Next slice

Phase 4A stabilizes back, modal, and memory behavior after Phase 3A and 3B.

Last safe source commit: `464b2d1`. This documentation update has no commit yet; its next hash is
pending.

Preserve the unrelated dirty worktree, including the staged `classic_navigation` document,
modified agent and writing-style files, deleted PNGs and Photo Grid test, and untracked captures,
helpers, caches, and temporary files. Do not stage, commit, or push this documentation work.
