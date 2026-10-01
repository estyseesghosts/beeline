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
- R06 — require the session authority in source construction. All 6 SourceFactoryTest tests pass. SessionLifecycle, session ViewModel, connected context, auth gateway, push cancellation, and Misskey integration suites pass. The Mastodon contract run retains the pre-existing R15a artwork failure. No adapter, store, or wiring change.
- R07 — bind push source selection to its notification token. All 15 UnifiedPushRegistrationManagerTest tests pass. PushCancellationTest, PushRegistrationRepositoryTest, NotificationSyncOrchestratorTest, NotificationRepositoryTest, and SessionLifecycleTest pass with no regression. No registry, adapter, store, or wiring change.
- R08 — validate Mastodon report identities before sending. All 15 ModerationServiceTest tests pass (13 existing plus 2 new report validation regressions). MastodonIntegrationTest, MastodonSourceContractTest, and MisskeyIntegrationTest pass except the pre-existing R15a artwork failure. No cursor, encoding, or comment-behavior change.
- R09 — bind Mastodon moderation cursors to the source session. All 17 ModerationServiceTest tests pass. MastodonIntegrationTest reports 63 passing tests. ModerationViewModelTest reports 13 passing tests. ModerationListScreenTest reports 3 passing tests. Adapter contracts pass except the pre-existing R15a artwork failure. A rejected continuation keeps existing rows visible. No generic cursor, endpoint, or route change.

# Current slice

R09 is complete. `MastodonModerationService` takes required session revision and source instance dependencies from the source UUID. The moderation cursor payload binds both values at version 2. Legacy version-1 and raw-URL cursors fail safely. The first-page self-Link uses the exact initial request URL.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonModerationService.kt holds the R09 production repair.
- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt holds the R09 source wiring.
- app/src/test/java/me/foxtails/palustris/ModerationServiceTest.kt holds the extended R09 cursor tests.
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt holds the R09 source-level cursor tests.
- app/src/test/java/me/foxtails/palustris/ui/settings/ModerationViewModelTest.kt holds the R09 row-preservation test.
- app/src/main/java/me/foxtails/palustris/ui/settings/ModerationListScreen.kt holds the R09 unsupported-with-rows presentation repair.
- app/src/test/java/me/foxtails/palustris/ui/settings/ModerationListScreenTest.kt holds the new R09 presentation tests.
- docs/agents/protocol-and-session-ownership.md holds the R09 cursor binding contract.
- docs/wiki/server-compatibility.md holds the R09 moderation paging section.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-123500.txt holds R08 evidence and exact checks.
- logs/261001-062500.txt holds R07 evidence and exact checks.

# Verification

R09 verification: ModerationServiceTest reports 17 tests and 0 failures. MastodonIntegrationTest reports 63 tests and 0 failures. ModerationViewModelTest reports 13 tests and 0 failures. ModerationListScreenTest reports 3 tests and 0 failures. Adapter contracts report 68 tests with only the pre-existing R15a artwork failure; `:app:lintDebug` reports BUILD SUCCESSFUL. Post-slice test assembleRelease reports 1502 tests and 1 failure (R15a artwork only) with release assembly complete. The version-2 payload makes old code reject new cursors and new code reject legacy cursors. No test was weakened. Full evidence lives in logs/261001-130000.txt.

# Next

Execute R10. Preserve restored Search state for its matching session.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, live-server capability refresh, signing, and environmental checks remain unverified. No Android-only behavior changed in R07, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

34e2a19 is the safe commit after R08.
R09 slice commit subject: `Bind Mastodon moderation cursors to the source session`.
