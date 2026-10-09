# Handoff

Release 0.2.11 is tagged `v0.2.11` and pushed with its changelog in `docs/wiki/changelog.md`. Its release commit follows `8d606671`. Phase 3B and Phases 5 through 8 are pushed. Phase 8 comprises 8A ("Extract one emoji tile with pending state and failed-write handling (8A)"), 8B ("Scope picker transient state to one owner and share recents across sizes (8B)"), and 8C ("Present the compact pop-out and full picker as one contract with a sheet fallback (8C)"). The last safe commit before Phase 8 is `cfbd6ff3`. Next is Phase 9 in `docs/beeline_0.4.0.md`: 9A notifications, 9B Direct Messages, 9C profile and settings utility surfaces, then 9D loading, offline, retry, and sensitive placeholders.

Read `docs/agents/tasks/beeline-0.4.0.md` for decisions and open items. Read `docs/wiki/ui-and-navigation.md` ("Emoji picker tile (Phase 8A)", "Emoji picker identity and scope (Phase 8B)", "Reaction picker sizes (Phase 8C)") for the owning sections.

Gradle test tasks that leave a gated call pending inside `runTest` hang until the timeout, and the daemon then reports "disappeared unexpectedly". Complete every gate before the test ends.

In Git Bash, `adb shell cat /sdcard/...` rewrites the path. Use `adb exec-out uiautomator dump /dev/tty` or set `MSYS_NO_PATHCONV=1`. Do not run two `uiautomator dump` commands at once.

The architecture audit exits 1 on regressions that predate 0.2.11. Do not edit the baseline to hide them. Do not push again unless asked.
