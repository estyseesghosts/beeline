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

# Current slice

R03 is complete. The continuation class runs under the repository RobolectricTestRunner and @Config(sdk = [35]) pattern; the lock-release test constructs its source directly under a custom dispatcher instead of enqueueing after it. Production Misskey source and continuation store remain unchanged. No split and no production slice was needed.

# Files involved

- app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyThreadContinuationTest.kt holds the runner annotations and the lock-release fixture construction.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-150000.txt holds R03 evidence and exact checks.

# Verification

Pre-repair rerun of the continuation class reports 12 tests and 5 failures: 4 RuntimeException Method put in org.json.JSONObject not mocked at MisskeyThreadContinuationTest.kt:337 during fixture setup, plus 1 ServerError at MisskeyErrorMapper.kt:21 wrapping the same not-mocked detail from the Dispatcher transport path. Post-repair rerun reports BUILD SUCCESSFUL with 12 tests and 0 failures. The Misskey package sweep reports 107 tests and 3 failures outside R03 by ownership (2 CapabilityCacheTest with R00 baseline support; 1 MisskeyApiTest cancellation with pre-existing status unverified). ktlintCheck reports only pre-existing main-source violations; the changed test file has no new finding. Source revision is 97e2499 plus the R02 and R03 worktree changes. Full evidence lives in logs/261001-150000.txt.

# Next

Execute R04. Attribute and repair the capability-cache failures. Identity fencing remains at read and publication boundaries.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. Device, API 29, live-server, signing, and environmental checks remain unverified.

# Last safe commit

97e2499 Activate draft writers in account-bound unit fixtures.
R03 slice commit subject: `Repair the Misskey continuation test runtime`.
