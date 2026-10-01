# Objective

Restore a trustworthy test gate. Repair the confirmed restoration, validation, session-selection, and upload defects. Then resume Phase 4A.

# Invariants

Preserve unrelated worktree and index content. Do not weaken tests. Do not implement Phase 4B through 4E. Do not change the approved navigation design. Do not add speculative DM pagination or notification eviction.

# Decisions

The recovery plan at C:\Users\julie\.opencode\plan\beeline-0.4.0-phase4-recovery.md controls slice order. R00 through R15 run serially with one reviewed commit per slice. Phase 4A stays blocked until R15 return criteria pass. The deleted Photo Grid test needs owner approval before committed-suite comparison. New slice R15a splits the Mastodon favourite artwork contract per protocol before R15.

# Completed

- R00 — record the current recovery baseline and failure attribution. Fresh grouped, isolated, and full-gate evidence replaces the historical count.
- R01 — activate draft writers in account-bound unit fixtures. All 13 DraftActionsTest tests pass. DraftWriteAuthorityTest passes with no regression.

# Current slice

R01 is complete. Production draft revocation rules remain unchanged.

# Files involved

- app/src/test/java/me/foxtails/palustris/data/auth/DraftActionsTest.kt holds the fixture repair and new coverage.
- docs/agents/protocol-and-session-ownership.md holds the draft activation contract.
- docs/wiki/data-and-privacy.md holds the draft writer section.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-020000.txt holds R01 evidence and exact checks.

# Verification

DraftActionsTest with --rerun-tasks reports BUILD SUCCESSFUL with all 13 tests passing. DraftWriteAuthorityTest with --rerun-tasks reports BUILD SUCCESSFUL. ktlintCheck reports only pre-existing violations in other test files; DraftActionsTest has none. Post-repair test assembleRelease reports 1461 tests and 15 failures with release assembly complete; both historical draft failures are resolved and no new failure appears. Source revision is e3f74b0 plus the R01 worktree changes. Full evidence lives in logs/261001-020000.txt.

# Next

Execute R02. Give shell draft fixtures a valid account writer. Rerun both historical Navigation failures before and after the fixture change.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. Device, API 29, live-server, signing, and environmental checks remain unverified.

# Last safe commit

e3f74b0 Record the current recovery baseline and failure attribution.
