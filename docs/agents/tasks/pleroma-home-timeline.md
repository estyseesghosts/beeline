# Objective

Fix Pleroma home timeline load failure. The timeline page must return good statuses when one entry is malformed.

# Invariants

- Protocol differences stay behind adapter boundaries.
- Cursors stay opaque above adapters.
- Unknown capability stays separate from unsupported.
- Pagination validation stays strict.
- Unrelated worktree changes stay intact.

# Decisions

- Slice 1 changes only the timeline page map to per-item tolerance. List, bookmark, hashtag, and profile paths stay unchanged and become follow-ups.
- Paging validator, auth scopes, capability probe, and UI stay unchanged.
- Regression test feeds a Pleroma-shaped page with one poison entry and asserts good items return.

# Completed

- Slice 1 — per-item tolerance in MastodonTimelineService plus MastodonTimelineToleranceTest (3 tests). Commit 155fa32b.
- Slice 2 — live Pleroma 2.7.2 fixes on social.paladyn.org: accept echoed local=false on Home/Federated Link continuations (MastodonPageClient) + demote unread_count 400 as absent route (MastodonSource). Tests: local=false parses+replays and local=true still fails (MastodonPagingRelaxTest), unread 400 demotes (MastodonRuntimeDemotionTest). Commit hash in handoff entry.

# Current slice

- None. Slice 2 committed. Next: user live test on Pleroma server with testsigned APK.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonPageClient.kt
- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonPagingRelaxTest.kt
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonRuntimeDemotionTest.kt
- docs/agents/tasks/pleroma-home-timeline.md
- logs/261014-120000-pleroma-home.txt

# Files involved

- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonTimelineService.kt
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonPagingRelaxTest.kt or new timeline tolerance test
- docs/agents/tasks/pleroma-home-timeline.md
- logs/261014-120000-pleroma-home.txt

# Verification

- Slice 2: full :app:testDebugUnitTest green (incl. pleromaHomeLinkWithEchoedLocalFalseParses, homeLinkWithLocalTrueStillFails, unreadCount400DemotesUnreadForTheSession).
- :app:lintDebug, :app:ktlintCheck, :app:assembleDebug, :app:assembleRelease green.
- tools/tests 52 pass (slice 1; unchanged since).
- Arch audit exits 1 on pre-existing findings (baseline untouched).
- problem_solver review: no blocking findings; R1 comment + R2 records repaired by owner. O1 skipped (shared branch already covered); O2 bare-?local presence check recorded as follow-up.
- Docs: no wiki update (bug fixes only, no documented behavior change).
- Live verification on social.paladyn.org pending new testsigned APK.

# Next

- User live test on Pleroma server with testsigned APK. Follow-ups (separate): list/bookmark/hashtag/profile strictness, search offset/resolve gaps, bare-?local presence hardening.

# Blockers

- Live Pleroma server check needs user test on the APK.
- Pleroma source copy was sandbox-blocked for one subagent.

# Last safe commit

- 155fa32b Skip malformed entries in Mastodon home timeline pages (slice commit; preceding safe: c45d62e4).
- Testsigned APK: app/build/outputs/apk/release/app-release-testsigned.apk (release build, debug-key signed, apksigner verified). Not pushed.
