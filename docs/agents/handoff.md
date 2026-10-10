Video (`docs/video.md`) is in progress. Read `docs/agents/tasks/video.md`. Slice 6 is `164621d6`; slice 2 follows it. Slice 3 (feed autoplay) is next. The emulator is x86_64, so FFmpeg and WebM need an arm64 device.

Related hashtags (`docs/related-tags.md`) is complete: slices 1 to 6 are committed. Read `docs/agents/tasks/related-hashtags.md` and `docs/agents/related-hashtags.md`. Slice 1 is `2b551c12`, slice 2 `c8771358`, slice 3 `0829f3d4`, slice 4 `3df65066`, slice 5 `3901c0f8`. Slice 6 (composer autocomplete) is the next commit on top of them.

Release gate: the owner must review the catalog before a release (the largest groups and every `amb` hashtag). Until then treat a build as a test build. Risks: fork behavior of `any[]`, the Mastodon limit of 3 extras, and `v2/suggestions` unverified with a token.

Release 0.2.13 is tagged `v0.2.13` and pushed with its changelog in `docs/wiki/changelog.md`. Its release commit follows `416dd72e`. The 0.2.12 tag failed its release build on a ktlint violation, fixed in `4a50bb14`. Phase 3B and Phases 5 through 8 are pushed. Phase 8 comprises 8A ("Extract one emoji tile with pending state and failed-write handling (8A)"), 8B ("Scope picker transient state to one owner and share recents across sizes (8B)"), and 8C ("Present the compact pop-out and full picker as one contract with a sheet fallback (8C)"). The last safe commit before Phase 8 is `cfbd6ff3`. Next is Phase 9 in `docs/beeline_0.4.0.md`: 9A notifications, 9B Direct Messages, 9C profile and settings utility surfaces, then 9D loading, offline, retry, and sensitive placeholders.

Read `docs/agents/tasks/beeline-0.4.0.md` for decisions and open items. Read `docs/wiki/ui-and-navigation.md` ("Emoji picker tile (Phase 8A)", "Emoji picker identity and scope (Phase 8B)", "Reaction picker sizes (Phase 8C)") for the owning sections.

Gradle test tasks that leave a gated call pending inside `runTest` hang until the timeout, and the daemon then reports "disappeared unexpectedly". Complete every gate before the test ends.

In Git Bash, `adb shell cat /sdcard/...` rewrites the path. Use `adb exec-out uiautomator dump /dev/tty` or set `MSYS_NO_PATHCONV=1`. Do not run two `uiautomator dump` commands at once.

The architecture audit exits 1 on regressions that predate 0.2.11. Do not edit the baseline to hide them. Do not push again unless asked.

The Photo Grid redesign (`docs/photo-grid-redesign-plan.md`, slices 1 to 4) follows `29b7a484`: cards with caption and favorite, rail underlap, and press-and-hold quick-view. Read `docs/agents/tasks/photo-grid-redesign.md`. Slice 5 (drag and release) follows `56fec6fc`.

The composer and media upload plan (`docs/composer-and-media-upload-plan.md`) is in progress. Read `docs/agents/tasks/composer-and-media.md`. Slices 1 to 8 are committed at `3ac01ce5` (slice 8 is the composer surface: full-screen on compact, floating card on expanded, Close/Drafts/Post top bar, in-composer drafts browser). Slice 9 (composer body) is `4881099e` plus `4d86a8f1`. Slice 10 (thread editing) is `1d802ae1`. Slice 11 (media in the composer) is `e6f66a74`. The plan stops at slice 11. The last safe commit before slice 6 is `feb26b2c`.
