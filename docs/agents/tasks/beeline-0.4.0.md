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
- R10 — preserve restored Search state for its matching session. ShellNavigatorTest reports 28 tests and 0 failures. ShellNavigatorRestorationTest reports 5 tests and 0 failures. SearchPanelRestorationTest reports 3 tests and 0 failures. NavigationTest reports 31 tests and 0 failures. ShellCharacterizationTest, WideNavigationTest, and AppShellStateTest pass. The composer overlay is never restored. Full gate reports 1521 tests with only the pre-existing R15a artwork failure; release assembly complete.
- R11 — stream multipart uploads with explicit one-shot ownership. AuthenticatedHttpClientTest reports 25 tests and 0 failures. MisskeyApiTest reports 7 tests and 0 failures. HttpClientPoolTest reports 9 tests and 0 failures. MastodonIntegrationTest reports 63 tests and 0 failures alone. Grouped adapter run reports 70 tests with only the pre-existing R15a artwork failure and the known isolation-dependent cancellation flake, which passes alone. lintDebug passes. ktlint reports only pre-existing findings; all R11 files are clean. No whole-input buffering remains. Field name, filename, MIME type, bearer, path, User-Agent, and response cap are unchanged.

# Current slice

R11 is complete. `UploadStreamOwner` owns one upload input stream for a single multipart call and releases it on every terminal path. Stream bodies report unknown length with no pre-read, are one-shot, and refuse a second write because consumed input cannot be replayed. OkHttp 4.12 does not propagate one-shot through the enclosing `MultipartBody`, so the upload entry wraps file-carrying bodies in `OneShotRequestBody` while the consume-once guard stops silent truncated replays. A field-only PATCH body stays replayable. Retry behavior is unchanged for replayable requests. An upload retry needs a newly opened input. Documentation updates were implemented by one targeted_fixer session under an explicit no-commit contract; the orchestrator owns the slice, ran the focused gates and lint, and operates Git. The full gate runs before commit.

# Files involved

- app/src/main/java/me/foxtails/palustris/data/transport/UploadStreamOwner.kt holds the new per-upload owner and one-shot streaming body.
- app/src/main/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClient.kt holds the R11 streaming postMultipart, ownership-taking patchMultipart, and owner-backed MultipartFileBody.
- app/src/test/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClientTest.kt holds the R11 lifecycle matrix.
- app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyApiTest.kt holds the R11 shared-path regressions.
- docs/agents/protocol-and-session-ownership.md holds the R11 transport ownership entry (fixer implementation).
- docs/wiki/data-and-privacy.md holds the R11 upload lifetime section (fixer implementation).
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-160155.txt holds R11 evidence and exact checks.

# Verification

R11 verification: AuthenticatedHttpClientTest reports 25 tests and 0 failures with --rerun-tasks. MisskeyApiTest reports 7 tests and 0 failures. HttpClientPoolTest reports 9 tests and 0 failures. MastodonIntegrationTest reports 63 tests and 0 failures alone with --rerun-tasks. Grouped MastodonIntegrationTest plus MastodonSourceContractTest reports 70 tests and 2 failures: the pre-existing R15a artwork failure and the known isolation-dependent cancellation-timing flake, which passes alone and in class isolation. lintDebug reports BUILD SUCCESSFUL. ktlintTestSourceSetCheck reports only pre-existing findings; the MisskeyApiTest findings sit outside the R11 hunk, and all other R11 files are clean. problem_solver review returned approve-after-required-repairs: the effective request body now reports one-shot through OneShotRequestBody (field-only PATCH stays replayable), PATCH pre-write cleanup and part MIME pins are covered, and framing claims are protocol-accurate. problem_solver re-review approved the repairs with no blocking findings. Post-repair full gate reports 1541 tests and 1 failure (pre-existing R15a artwork only) with release assembly complete. No test was weakened. Full evidence lives in logs/261001-160155.txt.

# Next

Execute R12. Cover streaming Mastodon uploads through the adapter.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, live-server capability refresh, live-server media upload, signing, and environmental checks remain unverified. No Android-only behavior changed in R11, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

d6ef2a7 is the safe commit after R10.
R11 slice commit subject: `Stream multipart uploads with explicit one-shot ownership`.
