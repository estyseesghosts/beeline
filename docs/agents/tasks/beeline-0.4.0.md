# Objective

Restore a trustworthy test gate. Repair the confirmed restoration, validation, session-selection, and upload defects. Then resume Phase 4A.

# Invariants

Preserve unrelated worktree and index content. Do not weaken tests. Do not implement Phase 4B through 4E. Do not change the approved navigation design. Do not add speculative DM pagination or notification eviction.

# Decisions

The recovery plan at C:\Users\julie\.opencode\plan\beeline-0.4.0-phase4-recovery.md controls slice order. R00 through R15 run serially with one reviewed commit per slice. Phase 4A stays blocked until R15 return criteria pass. The deleted Photo Grid test needs owner approval before committed-suite comparison. New slice R15a splits the Mastodon favourite artwork contract per protocol before R15.

# Completed

- R00 — record the current recovery baseline and failure attribution. Fresh grouped, isolated, and full-gate evidence replaces the historical count.
- R01 — activate draft writers in account-bound unit fixtures. All 13 DraftActionsTest tests pass. DraftWriteAuthorityTest passes with no regression.
- R02 — bind shell draft fixtures to an active writer. Full NavigationTest passes with 30 tests. ComposerOwnerTest, ShellCharacterizationTest, and SignInScreenTest pass with no regression.
- R03 — repair the Misskey continuation test runtime and one exposed fixture ordering defect. All 12 MisskeyThreadContinuationTest tests pass. No production change.
- R04 — repair the capability-cache probe fixture. All 16 CapabilityCacheTest tests pass. No production change.

# Current slice

R04 is complete. The capability-cache probe fake now carries Home timeline support matching the real probe, so both replacement-fencing tests reach their intended assertions. Production capability cache and Misskey source remain unchanged. No SourceFactory change and no production slice was needed.

# Files involved

- app/src/test/java/me/foxtails/palustris/data/misskey/CapabilityCacheTest.kt holds the probe fake fix.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-160000.txt holds R04 evidence and exact checks.

# Verification

Pre-repair rerun of the capability-cache class reports 16 tests and 2 failures: both SourceError Unsupported(timeline:Home) at MisskeyTimelineService.kt:25 via MisskeySource.kt:123, thrown from each replacement source's first timeline call because the fake probe carried empty timelines. Post-repair rerun reports BUILD SUCCESSFUL with 16 tests and 0 failures. MisskeyIntegrationTest, MisskeyThreadContinuationTest, and SessionLifecycleTest report BUILD SUCCESSFUL. ktlintTestSourceSetCheck reports only pre-existing violations in unrelated test files; CapabilityCacheTest.kt is clean. Post-repair test assembleRelease reports 1466 tests and 6 failures with release assembly complete; both R03 and R04 failures are absent and the remaining failures belong to R05 and R15a. Source revision is 1c90e9e plus the R04 worktree changes. Full evidence lives in logs/261001-160000.txt.

# Next

Execute R05. Attribute and repair the notification lifecycle failures.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. Device, API 29, live-server, signing, and environmental checks remain unverified.

# Last safe commit

1c90e9e Repair the Misskey continuation test runtime.
R04 slice commit subject: `Repair the capability-cache probe fixture`.
