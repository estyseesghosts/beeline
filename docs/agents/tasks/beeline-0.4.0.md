# Objective

Restore a trustworthy test gate. Repair the confirmed restoration, validation, session-selection, and upload defects. Then resume Phase 4A.

# Invariants

Preserve unrelated worktree and index content. Do not weaken tests. Do not implement Phase 4B through 4E. Do not change the approved navigation design. Do not add speculative DM pagination or notification eviction.

# Decisions

The recovery plan at C:\Users\julie\.opencode\plan\beeline-0.4.0-phase4-recovery.md controls slice order. R00 through R15 run serially with one reviewed commit per slice. Phase 4A stays blocked until R15 return criteria pass. The deleted Photo Grid test needs owner approval before committed-suite comparison. New slice R15a splits the Mastodon favourite artwork contract per protocol before R15.

# Completed

- R00 — record the current recovery baseline and failure attribution. Fresh grouped, isolated, and full-gate evidence replaces the historical count.

# Current slice

R00 is complete. No application source changed in this slice.

# Files involved

- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-010000.txt holds fresh attribution and exact checks.
- logs/BUGS.txt holds the current failure list.
- docs/wiki/build-test-and-release.md holds the verified local test procedure.

# Verification

Grouped historical classes with --rerun-tasks report 88 tests and 16 failures. Each class alone reports the same failures. The pre-repair test assembleRelease gate reports 1458 tests and 17 failures. Release assembly completes. The extra failure is MastodonSourceContractTest.defaultFavouriteArtworkStyleUsesHeart. Isolated rerun reports 7 tests and 1 failure. R15a owns that contract repair. Source revision is ecd0d6b on a dirty worktree with no uncommitted main source changes. References 464b2d1 and 219504c resolve. Intermediate XML reports were overwritten by later focused runs; console summaries and logs/261001-010000.txt preserve the sanitized attribution. Full evidence lives in logs/261001-010000.txt.

# Next

Execute R01. Activate draft writers in account-bound unit fixtures.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. Intermediate XML reports were overwritten; sanitized attribution survives in logs/261001-010000.txt. The timestamped log and BUGS.txt edit need explicit force-add approval because /logs/*.txt is Git-ignored. Device, API 29, live-server, signing, and environmental checks remain unverified.

# Last safe commit

ecd0d6b Allow general orchestrator shell commands.
