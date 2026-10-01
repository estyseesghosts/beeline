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

# Current slice

R07 is complete. `UnifiedPushRegistrationManager` binds push source selection to its notification token. A failed token lookup ends as a no-op. It never escalates to account lookup or a transient factory source. Token-null removal cleanup uses a validated stored snapshot. Token, session, registry, and endpoint ownership is rechecked before each remote subscription mutation. Stale failures publish nothing against the replacement session.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/notifications/push/UnifiedPushRegistrationManager.kt holds the R07 production repair.
- app/src/test/java/me/foxtails/palustris/data/notifications/push/UnifiedPushRegistrationManagerTest.kt holds the new R07 token-binding tests.
- docs/agents/protocol-and-session-ownership.md holds the R07 push source binding contract.
- docs/wiki/notifications-and-direct-messages.md and docs/wiki/accounts-and-sessions.md hold the R07 push binding sections.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-062500.txt holds R07 evidence and exact checks.

# Verification

R07 verification: new UnifiedPushRegistrationManagerTest reports 15 tests and 0 failures. PushCancellationTest, PushRegistrationRepositoryTest, NotificationSyncOrchestratorTest, NotificationRepositoryTest, and SessionLifecycleTest pass; `:app:lintDebug` reports BUILD SUCCESSFUL. Post-repair test assembleRelease reports 1489 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete. The stale-token tests assert zero source calls and no failure state, which the old account-plus-factory fallback chain cannot satisfy. The endpoint, creation-gate, registry-identity, late-failure, and cleanup-race tests assert zero post-supersede mutations, which the pre-repair rechecks cannot satisfy. No test was weakened. Review repair added endpoint and registry rechecks before each remote mutation, stale guards in both failure paths, and revision-guarded capability writes. Full evidence lives in logs/261001-062500.txt.

# Next

Execute R08. Validate Mastodon report identities before sending.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, live-server capability refresh, signing, and environmental checks remain unverified. No Android-only behavior changed in R07, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

09db376 is the safe commit after R06.
R07 slice commit subject: `Keep push source selection bound to its notification token`.
