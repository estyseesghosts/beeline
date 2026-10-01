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

# Current slice

R06 is complete. `SocialSourceFactory` requires an explicit `SessionStore`. The fail-open null path is gone. Misskey sources receive the store-backed current-session check, and both adapters persist refreshed capabilities through revision-guarded `updateCapabilities`. New `SourceFactoryTest` covers routing and authority for both protocols.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt holds the R06 production repair.
- app/src/test/java/me/foxtails/palustris/data/SourceFactoryTest.kt holds the new R06 authority tests.
- app/src/test/java/me/foxtails/palustris/data/auth/SessionLifecycleTest.kt, ui/session/AccountManagerFixtures.kt, and data/notifications/push/PushCancellationTest.kt pass their owned stores to the factory.
- docs/agents/protocol-and-session-ownership.md holds the R06 factory authority contract.
- docs/wiki/accounts-and-sessions.md holds the R06 session identity section.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-061418.txt holds R06 evidence and exact checks.

# Verification

R06 verification: new SourceFactoryTest reports 6 tests and 0 failures. SessionLifecycle, session ViewModel, connected context, auth gateway, push cancellation, and Misskey integration suites pass; the Mastodon source contract run retains the pre-existing R15a artwork failure. ktlint reports only pre-existing findings; the new test file is clean. `:app:lintDebug` reports BUILD SUCCESSFUL. Post-repair test assembleRelease reports 1476 tests and 1 failure (R15a Mastodon artwork only) with release assembly complete. Review repair: the two store-level callback tests are removed. They called updateCapabilities directly and could not fail on factory wiring. That guard stays covered by CrossCuttingTest at the store level. Callback firing needs a live probe and stays unverified in unit tests. Full evidence lives in logs/261001-061418.txt.

# Next

Execute R07. Keep push source selection bound to its notification token.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, live-server capability refresh, signing, and environmental checks remain unverified. No Android-only behavior changed in R06, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

4145f4c is the safe commit after R05.
R06 slice commit subject: `Require the session authority in SocialSourceFactory`.
