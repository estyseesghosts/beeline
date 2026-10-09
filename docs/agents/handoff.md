# Handoff

Phase 3B, Phase 5, Phase 6, and Phase 7 are pushed (`origin/main` at `cfbd6ff3`). Phase 8 is in progress in `docs/beeline_0.4.0.md`. Packets 8A ("Extract one emoji tile with pending state and failed-write handling (8A)") and 8B ("Scope picker transient state to one owner and share recents across sizes (8B)") are committed. Next is 8C. The last safe commit before Phase 8 is `cfbd6ff3`.

Read `docs/agents/tasks/beeline-0.4.0.md` for decisions and open items, and `docs/wiki/ui-and-navigation.md` ("Emoji picker tile (Phase 8A)", "Emoji picker identity and scope (Phase 8B)") for the owning section.

Gradle test tasks that leave a gated call pending inside `runTest` hang until the timeout, and the daemon then reports "disappeared unexpectedly". Complete every gate before the test ends.

Push at the end of Phase 8 because the user asked for it.
