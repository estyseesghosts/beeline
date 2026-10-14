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
- Slice 2 fixes two live Pleroma 2.7.2 failures: echoed local=false on Home/Federated Links, and unread_count 400 demotion.
- Slice 3 unifies the local rule after Pleroma repeats echoed local=false twice on tag Links.
- Regression tests pin every relaxed shape and every kept rejection.
- No wiki update: bug fixes only, no documented behavior change.

# Completed

- Slice 1 — per-item tolerance in MastodonTimelineService plus MastodonTimelineToleranceTest (3 tests). Commit 155fa32b.
- Slice 2 — live Pleroma 2.7.2 fixes on social.paladyn.org: accept echoed local=false on Home/Federated Link continuations (MastodonPageClient) + demote unread_count 400 as absent route (MastodonSource). Tests: local=false parses+replays and local=true still fails (MastodonPagingRelaxTest), unread 400 demotes (MastodonRuntimeDemotionTest). Commit 093d2e93. Records 441604f2. Live-verified on SM-G986W.

# Current slice

- Slice 3 — hashtag search: Pleroma repeats echoed local=false twice on tag Links, which the validator rejected. Unified local rule (absent or all-false passes everywhere except Local, which keeps exact true). Tests: dup-local hashtag + Home parse cases. Commit pending review.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonPageClient.kt
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonPagingRelaxTest.kt
- docs/agents/tasks/pleroma-home-timeline.md
- docs/agents/handoff.md
- logs/261014-120000-pleroma-home.txt

# Verification

- Slice 3: full :app:testDebugUnitTest green (7/7 paging incl. 2 new dup-local tests).
- :app:lintDebug, :app:ktlintCheck, :app:assembleDebug, :app:assembleRelease green.
- problem_solver review pending.
- Live #news check pending new testsigned APK.

# Next

- Commit slice 3, cut testsigned APK, verify #news live. Follow-ups (separate): list/bookmark/profile strictness, search offset/resolve gaps, byte-identical live-shape + Home replay test strengthening.

# Blockers

- Live Pleroma server check needs user test on the APK.

# Last safe commit

- 093d2e93 Fix Pleroma home timeline Link and unread_count demotion (slice 2; preceding safe: 155fa32b).
- Testsigned APK: app/build/outputs/apk/release/app-release-testsigned.apk (release build, debug-key signed, apksigner verified). Not pushed.
