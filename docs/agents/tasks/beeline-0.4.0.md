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
- R13 — characterize direct-message anchor and context limits. Seven new characterization tests pass with no production change: five Mastodon anchor tests in DirectMessageSourceTest (403 mapping, 410 mapping, blank and foreign identities with zero requests, malformed anchor body, repeated-anchor dedup across context) and two repository tests in DirectMessageRepositoryTest (late thread write rejected after session replacement, thread source failure keeps cached rows with failure type plus message). Focused DM suites report 102 tests and 0 failures. Grouped adapter contracts report 245 tests with only the pre-existing R15a artwork failure. Full gate reports 1552 tests with only the pre-existing R15a artwork failure; release assembly complete. lintDebug passes. ktlint reports only pre-existing findings; the R13 hunks are clean. The wiki and protocol-ownership pages record the anchor and context limits and the explicit live-evidence blocker (no disposable account or approval; truncation beyond one context response unverified; no pagination invented). No BUGS.txt entry: characterization only, no defect repaired.

# Current slice

R13 is complete. The direct-message anchor and context contract is characterized with no production change: Mastodon loads one anchor plus one context response and reports Finished with no cursor; a non-null thread cursor fails before any request; 403, 404, and 410 anchor reads, blank or foreign identities, malformed bodies, and non-direct anchors fail truthfully as unsupported; repeated anchor and context rows merge without duplicates. The repository needs a stored conversation preview with the last-post anchor, merges remote posts with cached rows, keeps cached rows on failure, rejects late thread writes after session replacement, and reloads fresh context on retry with no cursor. Seven new tests cover the gaps; the focused, adapter, lint, and full gates pass with only the pre-existing R15a artwork failure. Documentation records the limits and the explicit live-evidence blocker. One targeted_fixer session implemented the tests under an explicit no-commit contract; the orchestrator owns the slice, repaired one test after a stop-condition investigation, ran all gates, and operates Git. The full gate has run (1552 tests, R15a artwork failure only) and problem_solver review found no blocking findings.

# Files involved

- app/src/test/java/me/foxtails/palustris/DirectMessageSourceTest.kt holds the 5 new Mastodon anchor tests.
- app/src/test/java/me/foxtails/palustris/data/directmessages/DirectMessageRepositoryTest.kt holds the 2 new repository tests.
- docs/wiki/notifications-and-direct-messages.md holds the R13 anchor and context limit section.
- docs/agents/protocol-and-session-ownership.md holds the R13 Mastodon adapter paragraph.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-212959.txt holds R13 evidence and exact checks.

# Verification

R13 verification: focused DM suites (DirectMessageSourceTest, DirectMessageRepositoryTest, DirectMessageViewModelTest) report 102 tests and 0 failures with --rerun-tasks. Grouped Mastodon plus Misskey adapter contracts report 245 tests and 1 failure: the pre-existing R15a artwork failure only. lintDebug reports BUILD SUCCESSFUL. ktlint reports only pre-existing findings; the R13 hunks introduce no new finding. The fixer stopped at its fail gate on the gated in-flight failure test; orchestrator investigation traced it to coroutine stacktrace recovery copying exceptions across suspend boundaries plus unnecessary in-flight gating, and rewrote the test with a directly throwing fake pinning failure type plus message. problem_solver review returned no blocking findings and two required doc-only repairs (review-date correction, Affected Tests listing); both applied by the owner. Post-repair full gate reports 1552 tests and 1 failure (pre-existing R15a artwork only) with release assembly complete. No test was weakened. Full evidence lives in logs/261001-212959.txt.

# Next

Execute R14. Measure notification retention without unsafe eviction.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. API 29, live-server DM truncation, live-server push delivery, live-server capability refresh, live-server media upload, signing, and environmental checks remain unverified. No Android-only behavior changed in R13, so no new instrumented test ran. ktlint shows repo-wide pre-existing findings.

# Last safe commit

3fb6973 is the safe commit after R12.
R13 slice commit subject: `Characterize direct-message anchor and context limits`.
