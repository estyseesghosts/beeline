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
- R12 — cover streaming Mastodon uploads through the adapter. MastodonIntegrationTest reports 67 tests and 0 failures (63 existing plus 4 new adapter upload tests). MastodonSourceContractTest reports 7 tests with only the pre-existing R15a artwork failure. Full gate reports 1545 tests with only the pre-existing R15a artwork failure; release assembly complete. lintDebug passes. ktlint reports only pre-existing findings; the R12 hunks are clean. No production change. No v2 media, polling, limits, or processing change.

# Current slice

R12 is complete. `MastodonSource.uploadMedia` is proven to use the R11 streaming transport path with no adapter-side buffering: generated close-tracking streams through the real adapter verify POST /api/v1/media, multipart name `file` with default filename `upload`, caller MIME type, exact bytes, bearer ownership, attachment mapping, and close-once ownership on success, HTTP failure, mid-body disconnect, and cancellation. HTTP 422 maps to ServerError with its detail; a mid-body disconnect maps to NetworkUnavailable; both yield no attachment. A manual retry with a newly opened stream succeeds. The mid-body disconnect phase runs on an isolated fault server because MockWebServer never dispatches (and never dequeues) a request whose chunked body is cut mid-read. Documentation was a one-section wiki addition by one targeted_fixer session under an explicit no-commit contract; the orchestrator owns the slice, ran all gates, and operates Git. The full gate has run (1545 tests, R15a artwork failure only) and problem_solver re-review approved with no blocking findings.

# Files involved

- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt holds the R12 adapter upload tests and the AdapterUploadStream helper.
- docs/wiki/server-compatibility.md holds the R12 media upload section (fixer implementation).
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-183043.txt holds R12 evidence and exact checks.

# Verification

R12 verification: MastodonIntegrationTest reports 67 tests and 0 failures with --rerun-tasks (63 existing plus 4 new adapter upload tests). MastodonIntegrationTest plus MastodonSourceContractTest grouped reports 74 tests and 1 failure: the pre-existing R15a artwork failure only. lintDebug reports BUILD SUCCESSFUL. ktlintTestSourceSetCheck reports only pre-existing findings; the MastodonIntegrationTest findings (unused import, spacing, double blank line) were verified present at HEAD e91e193, and the R12 hunks introduce no new finding. problem_solver review returned approve-after-required-repairs: the retry half first reused the consumed helper stream instead of a genuine network fault. Repairs: genuine mid-body disconnect plus fresh-stream retry, with the disconnect phase isolated on its own fault server after investigation showed MockWebServer never dequeues an undispatched cut-off request. problem_solver re-review approved the repairs with no blocking findings: genuine mid-body disconnect plus fresh-stream retry on an isolated fault server, 422 mapping intact, cancellation bounded. Post-repair full gate reports 1545 tests and 1 failure (pre-existing R15a artwork only) with release assembly complete. No test was weakened. Full evidence lives in logs/261001-183043.txt.

# Next

Execute R13. Characterize DM anchors and context limits with no speculative protocol change.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server push delivery, live-server capability refresh, live-server media upload, signing, and environmental checks remain unverified. No Android-only behavior changed in R12, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

e91e193 is the safe commit after R11.
R12 slice commit subject: `Cover streaming Mastodon uploads through the adapter`.
