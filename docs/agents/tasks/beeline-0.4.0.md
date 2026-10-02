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
- R14 — measure notification correctness-state retention without eviction. New NotificationRetentionMeasurementTest reports 9 passing tests: 50,000 ingested events leave exactly 500 visible records; 1,000 and 10,000 dismissals grow tombstones exactly with empty items and deliveries; 200 stream deliveries claim 50, finish 50 as Presented, and release 100 on dismissal; 5 stable query keys hold 5 checkpoints across repeat baselines; removal cleans one account while the sibling keeps 12 items, 3 tombstones, and 5 delivery records; same-ID re-addition under a strictly newer generation starts with empty tombstones; the Room store holds one state row per account with zero sibling rows. No production, deletion-query, cap, migration, or schema change. Growth is acceptable at measured workloads, so no retention-policy slice opens. Heap and database/WAL bytes remain unmeasured: no device run, no WAL checkpointing, no deterministic byte assertions.
- R15a — split the favourite-artwork contract per protocol. The shared `SocialSourceContractTest` no longer asserts `Heart` for every adapter. It declares an abstract `expectedFavouriteArtworkStyle`, and each protocol test supplies its own value: `MisskeySourceContractTest` declares `Heart`, `MastodonSourceContractTest` declares `Star`. The renamed `favouriteArtworkStyleMatchesProtocolContract` passes for both adapters. `MisskeyIntegrationTest`, which extends `MisskeySourceContractTest`, keeps the Misskey `Heart` expectation. No production, adapter, or presentation change. MastodonSourceContractTest 7/7, MisskeySourceContractTest 7/7, MisskeyIntegrationTest 49/49 with --rerun-tasks. This removes the sole full-gate failure.
- R15 — restore the full gate and hand back to Phase 4A. Full `test assembleRelease --rerun-tasks` reports BUILD SUCCESSFUL with 151 suites, 1561 tests, 0 failures, 0 errors, 0 skipped; release assembly complete. The prior sole failure (the artwork contract) is resolved by R15a. The architecture audit exits 0 with 616 findings and no regression findings. lintDebug passes. ktlintCheck reports repo-wide pre-existing style findings against the committed baseline; the recovery hunks add no finding and no baseline changed. API 29 smoke and the restoration device check are unavailable locally (the only connected device is emulator-5554, API 36, with Beeline not installed). No production change.

# Current slice

R15 is complete. The full `test assembleRelease` gate is green: 151 suites, 1561 tests, 0 failures, 0 errors, 0 skipped, with release assembly complete. The R00 through R15a repair slices resolved every reproduced failure. The architecture audit exits 0 with no regression findings, and lintDebug passes. ktlintCheck reports repo-wide pre-existing style findings against the committed baseline; the recovery hunks add no finding and no baseline changed. API 29 smoke and the restoration device check remain unavailable locally. The orchestrator owns the slice, ran the gate, and operates Git.

# Files involved

- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task and names Phase 4A next.
- docs/agents/documentation-inventory.md holds the R15 review date.
- docs/wiki/ui-and-navigation.md documents the per-protocol post favourite artwork.
- docs/beeline_0.4.0.md holds the recovery status note (git-ignored, local only).
- logs/BUGS.txt holds the R15 result (git-ignored, local only).
- logs/261002-035505.txt holds R15 evidence and exact checks (git-ignored, local only).

# Verification

R15 verification: `test assembleRelease --rerun-tasks` reports BUILD SUCCESSFUL with 151 suites, 1561 tests, 0 failures, 0 errors, 0 skipped; release assembly complete. The architecture audit exits 0 with 616 findings and no regression findings. lintDebug passes. ktlintCheck reports repo-wide pre-existing style findings; no baseline changed and no recovery hunk added a finding. The API 29 smoke job and the restoration device check remain unverified. Full evidence lives in logs/261002-035505.txt.

# Next

Phase 4A — back priority and session-bound modal behavior. Use the recovered navigator as the only navigation authority. Reread `ShellBackPolicy.kt`, `ShellOverlayPresenter.kt`, `ShellOverlayHost.kt`, and their current tests before editing. Do not repeat R10 or claim Phase 4A complete because Search restoration is fixed.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The docs/beeline_0.4.0.md status note, the timestamped logs, and BUGS.txt edits are git-ignored and stay local; force-add needs explicit user approval. ktlint reports repo-wide pre-existing style findings against a stale committed baseline; resolving them is not part of this recovery. Heap and database/WAL bytes remain unmeasured: no device run occurred. API 29 smoke, live-server push delivery, live-server capability refresh, live-server media upload, signing, and the restoration device check remain unverified. No Android-only behavior changed in the recovery, so no new instrumented test ran.

# Last safe commit

ce75bcc is the safe commit after R15a.
R15 slice commit subject: `Record the green recovery gate and resume Phase 4A`.
The next session resolves the new R15 hash from Git without a second record-only commit.
