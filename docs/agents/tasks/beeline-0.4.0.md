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

# Current slice

R08 is complete. `MastodonModerationService.report` validates its target with `validateTarget` before constructing fields. A same-origin Misskey target or a blank target ID fails as unsupported without a request. A foreign target keeps the foreign-origin error. The optional status ID keeps same-origin validation and rejects a blank value.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonModerationService.kt holds the R08 production repair.
- app/src/test/java/me/foxtails/palustris/ModerationServiceTest.kt holds the extended R08 report validation tests.
- docs/agents/protocol-and-session-ownership.md holds the R08 report validation contract.
- docs/wiki/server-compatibility.md holds the R08 report validation section.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-123500.txt holds R08 evidence and exact checks.
- logs/261001-062500.txt holds R07 evidence and exact checks.

# Verification

R08 verification: ModerationServiceTest reports 15 tests and 0 failures. MastodonIntegrationTest, MastodonSourceContractTest, and MisskeyIntegrationTest report 115 tests with only the pre-existing R15a artwork failure; `:app:lintDebug` reports BUILD SUCCESSFUL. Post-slice test assembleRelease reports 1491 tests and 2 failures with release assembly complete. The invalid-target test asserts error types and an unchanged request count, which the old origin-only check cannot satisfy. No test was weakened. Full evidence lives in logs/261001-123500.txt.

# Next

Execute R09. Bind Mastodon moderation cursors to the source session.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, live-server capability refresh, signing, and environmental checks remain unverified. No Android-only behavior changed in R07, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

459c67b is the safe commit after R07.
R08 slice commit subject: `Validate Mastodon report identities before sending`.
