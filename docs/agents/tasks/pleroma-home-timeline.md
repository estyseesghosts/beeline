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

- Slice 1 — per-item tolerance in MastodonTimelineService plus MastodonTimelineToleranceTest (3 tests). Commit hash in handoff entry.

# Current slice

- None. Slice 1 committed. Next: user live test on Pleroma server with testsigned APK.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonTimelineService.kt
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonPagingRelaxTest.kt or new timeline tolerance test
- docs/agents/tasks/pleroma-home-timeline.md
- logs/261014-120000-pleroma-home.txt

# Verification

- Full :app:testDebugUnitTest green, including 3 new MastodonTimelineToleranceTest tests (0 failures).
- tools/tests 52 pass.
- Arch audit exits 1 on pre-existing findings (recorded, baseline untouched). One new finding on touched file: function-complexity-growth MastodonTimelineService.kt:timeline. Reviewer: dismiss, function stays cohesive.
- :app:lintDebug, :app:ktlintCheck, :app:assembleDebug, :app:assembleRelease green.
- problem_solver review: contract satisfied, scope clean, no code repairs. REQUIRED records item (confirm slice file isolation) satisfied by owner.
- Docs: no wiki update. No documented timeline error behavior changes; tolerance matches the documented mapper-skip precedent.

# Next

- User live test on Pleroma server with testsigned APK. Follow-up slices (separate): list/bookmark/hashtag/profile strictness, search offset/resolve gaps.

# Blockers

- Live Pleroma server check needs user test on the APK.
- Pleroma source copy was sandbox-blocked for one subagent.

# Last safe commit

- c45d62e4 Add-recommendation-feedback-D4
