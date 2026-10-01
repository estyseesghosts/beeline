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
- R05 — repair the notification generation lifecycle. All 27 NotificationSyncOrchestratorTest tests pass (25 historical plus 2 gated removal-revocation regressions). No repository, registry, or store change.

# Current slice

R05 is complete. `NotificationSyncOrchestrator.unregister` now revokes synchronously with the exact generation through `NotificationRepository.deactivate`, so the legitimate replacement stays strictly newer and an in-flight write still fails after revocation. `removeAccount` keeps its unregister plus repository removal plus trailing-check composition. `accept` requires an active controller entry matching the token. Two gated tests prove removal revokes an in-flight write and a cancelled removal still revokes.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/notifications/NotificationSyncOrchestrator.kt holds the R05 production repair.
- app/src/test/java/me/foxtails/palustris/data/notifications/NotificationSyncOrchestratorTest.kt holds the two R05 gated regression tests.
- docs/agents/protocol-and-session-ownership.md holds the R05 notification generation contract.
- docs/wiki/notifications-and-direct-messages.md holds the R05 lifecycle section.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-170000.txt holds R05 evidence and exact checks.

# Verification

R05 verification: focused NotificationSyncOrchestratorTest reports 27 tests and 0 failures across consecutive --rerun-tasks runs. Repository, synchronizer, state-ownership, write-failure, and SessionLifecycle suites report BUILD SUCCESSFUL. ktlint reports only pre-existing findings; the changed test file is clean. `:app:lintDebug` reports BUILD SUCCESSFUL. RoomNotificationStoreInstrumentedTest reports 1 passing test on emulator-5554 (API 36). Post-repair test assembleRelease reports 1468 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete. Full evidence lives in logs/261001-170000.txt.

# Next

Execute R06. Require SessionStore in source construction.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, signing, and environmental checks remain unverified. Emulator-5554 (API 36) covers only the Room store round-trip and delete path. R05 leaves API 29, live-server push delivery, and signing unverified; ktlint shows repo-wide pre-existing findings.

# Last safe commit

9445dec is the safe commit after R04.
R05 slice commit subject: `Repair notification generation lifecycle regressions`.
