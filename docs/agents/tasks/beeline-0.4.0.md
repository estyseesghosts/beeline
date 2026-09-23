# Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices: 0A, 0B, 1A, 1B1-1B5, 1C, 1D1-1D3, 1E1, 1E2, 2A1-2A4, 2B1-2B3, 2C1-2C3, and 2D1-2D3. Phase 2E is conditional.

# Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Keep Photo Grid state independent of Home.
- Do not expose hidden quote or media content before reveal.
- Do not claim measured retention without heap, disk, and Room measurements.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

# Decisions

- `docs/260923_current_state.md` is the active review input. `docs/260926_current_state.md` does not exist. Confirm if a newer review appears.
- Slice 2E is conditional on a later UI change that needs it. Phases 0-2 do not trigger it. Record it as not triggered and confirm before running.
- No device is reachable. Device, screenshot, foldable, RTL-device, TalkBack, live-server, and signed-release checks are unverified.
- Architecture audit exit 0 means no new regressions against the baseline. It does not mean architecture completion.
- Research for 0A and 0B can run in parallel. Keep commits ordered 0A, then 0B.

# Completed

- Slice 0A is complete at `ba3fe53`. Verification: 51 Python tests passed; architecture audit `--check` exited 0 with 605 findings and no new regressions against baseline; all 120 relative links across 11 touched Markdown files resolve. No source changes occurred.
- Slice 0B is complete (this record ships with the slice commit). Its commit hash is recorded at the 1A boundary. The focused Gradle baseline set passed 79 tests across seven classes. Python unittest passed 51 tests. Architecture audit exited 0 with 605 findings and no new regressions. Eight baseline-file link targets resolve. No source changes occurred.
- Slice L0 is complete at `cbf8698`. Slice 1A is complete at `af1f983`.
- Slice 1B1 is complete at `5207a96`. Timeline cursors use opaque, identity-bound route tokens.

# Current slice

Slice 1B4 is complete and ships with the current commit. `unfavorite` now encodes its ID with the existing path-segment helper. 1B3 commit and last safe commit are `d21280e`. Current slice 1B5 is PAUSED; next is 1C, and the 1B4 hash is recorded at that next boundary.

# Files involved

0B documentation: `docs/agents/beeline-0.4.0-ui-baseline.md`, `docs/agents/documentation-inventory.md`, `docs/agents/README.md`, this task state, `docs/agents/handoff.md`, and `logs/260923-035132.txt`. Later source files and tests appear in the owner map below.

Forward owner/caller/test map for phases 1-2. Paths are relative to `app/src/main/java/me/foxtails/palustris/` unless noted. Tests are under `app/src/test/java/me/foxtails/palustris/`. These are planned checks, not 0A results.

| Slice | Owner files | Key symbols / lines | Callers and boundary | Tests to run in that slice |
| --- | --- | --- | --- | --- |
| 1A | data/mastodon/MastodonModerationService.kt; data/misskey/MisskeyApi.kt (GET helper prefixes /api/) | relationship :74, profileRelationship :85, validateTarget :104; MisskeyApi.get :172 | MastodonSource.setBlocked/setMuted :156-162, removeBlockedAccount/removeMutedAccount :392-394 | ModerationServiceTest, data/mastodon/MastodonIntegrationTest; both adapters' moderation contracts + lint |
| 1B1 | data/mastodon/MastodonPageCursor.kt, MastodonPageClient.kt, MastodonTimelineService.kt; MastodonSource source-instance wiring | timeline route cursor binds timeline kind, account/session, instance, and variant | MastodonIntegrationTest; source contract regression set; cursor failures are rejected before another request |
| 1B2 | data/mastodon/MastodonSource.kt, MastodonPageClient.kt, MastodonIntegrationTest.kt | savedPosts and searchHashtag use route-bound opaque cursors; legacy raw replay removed | saved-post and hashtag UI through SocialSource; route, query, account, path, and allowlist validation |
| 1B3 | data/mastodon/MastodonModerationService.kt | list :55-59, decodeCursor :115-128, ModerationCursor in domain/ModerationModels.kt :13-18 | blocked/muted list pagination; retain ModerationCursor account/kind/variant fields | ModerationServiceTest :79-101; wrong-path and altered-filter Link cases |
| 1B4 | data/mastodon/MastodonSource.kt | unfavorite :271-275 (raw id) vs favorite :263-267 (encodePathSegment :537-538) | SocialSource.unfavorite | MastodonSourceContractTest, MastodonIntegrationTest :778-786 pattern; reserved-character IDs |
| 1B5 | MastodonSource.kt first, then MisskeySource.kt — separate commit per adapter | request wrapper :474-488 (ResponseLimitExceeded -> ResourceLimit("thread") :479-480); Misskey :616-637 (:632-633) | adapter error normalization for shared UI | each adapter's integration/contract tests; characterize a capped non-thread operation first |
| 1C | ui/posts/PostRow.kt, ui/posts/SinglePostScreen.kt; inspect ui/photogrid/PhotoPagerSizing.kt | row quote card :163-186, warningDecision :89-91; detail PhotoGrid branch :190-313, unconditional detail quote :295-311 | Home/Search/Profile rows and Photo Grid detail share the visibility decision; photo geometry stays distinct; grid filtering PhotoGridScreen.kt :99-114 | ui/SinglePostScreenTest, ui/PostTextPresentationTest, domain/ContentWarningPolicyTest; hidden quotes, CW, muted tags, placeholder/omit, no semantics leak |
| 1D1 | data/misskey/MisskeyDirectMessageService.kt | conversations :28-49, cursor :48, conversationThread :51-66, markConversationRead :76-78 no-op, 404 fallback :80-84, encode/decodeCursor :110-115 | MisskeySource.conversations :384-391; repository owns local read state; compare MastodonDirectMessageService separately | DirectMessageSourceTest, data/directmessages/DirectMessageRepositoryTest, ui/directmessages/DirectMessageViewModelTest |
| 1D2 | data/misskey/MisskeyDirectMessageService.kt | single untilId fan-out :31-34, min-by-time cursor :48 | composite adapter-owned cursor binding both endpoint continuations per account | DirectMessageSourceTest; asymmetric pages, duplicates, malformed/foreign cursor, 404 fallback |
| 1D3 | MisskeyDirectMessageService.kt conversationThread; DM repository/ViewModel if contract expands | children single fetch :62 (limit 30, no continuation) | long reply-rooted conversations need bounded continuation + visible partial/retry | DirectMessageSourceTest, DirectMessageRepositoryTest, DirectMessageViewModelTest; long thread, failure, account replacement |
| 1E1 | data/misskey/MisskeySource.kt, data/mastodon/MastodonSource.kt | Misskey :690 DIRECT_PAGE_LIMIT, :704-708 SECURE_CREDENTIAL_FAILURE_CODES, :709 MISSING_PUSH_REGISTRATION_CODES (all declaration-only; live values in MisskeyPushService :34/:46/:49); Mastodon :491 DEFAULT_NOTIFICATION_LIMIT, :535 DIRECT_CONVERSATION_LIMIT (live in MastodonNotificationService :146/:180, MastodonDirectMessageService :116/:129) | facade residue only; compare live service values before deleting | both adapter integration/contract suites; compilation + lint |
| 1E2 | data/mastodon/MastodonMapper.kt | editableProfile :88 and legacyEditableProfile :117 take unused origin param | callers MastodonSelfProfileService.kt :31,:69,:33,:71; output must be unchanged | MastodonIntegrationTest self-profile tests; mapper fixtures; adapter contracts |
| 2A1 | data/misskey/MisskeyApi.kt, HttpClientPool.kt, HttpResponse.kt; Mastodon/auth/DI consumers | ServerAddress.normalize :33-43, execute :211-231, readBody :233-248, HttpLayerConfig :8, clientFor :23-49, linkHeaderCursor :10-18 | importers: SourceFactory.kt :4-5, auth/MisskeyAuth :4-5, auth/MastodonAuth :4-6, 11 Mastodon services, di/AppModule :31,:89, tests (HttpClientPoolTest, WebSocketTransportTest, MisskeyIntegrationTest, ModerationServiceTest, DirectMessageSourceTest, ProfileSourceContractTest, NotificationAdapterContractTest, SocialSourceContractTest, CrossCuttingTest, SessionLifecycleTest, PushCancellationTest, AccountManagerFixtures) | HttpClientPoolTest, WebSocketTransportTest, Api29CompatibilityTest, both adapter integration suites |
| 2A2 | move HttpClientPool + HttpResponse + neutral primitives to new data/transport/ | no behavior change | update imports across Mastodon/auth/DI/tests | 2A1 characterization set + both adapter contracts + auth tests |
| 2A3 | data/transport/ neutral authenticated request client; Misskey JSON and /api/ prefix stay in data/misskey/MisskeyApi.kt | get :172-182 and post :57-66 are the Misskey-prefixing helpers; postForm/patch/put/delete/postMultipart/getUrl/webSocket are already neutral; WebSocket :191-209 | migrate Mastodon callers; preserve exact URL shapes, bearer, redirect-off, timeout, size-limit, cancellation | MastodonIntegrationTest, MastodonSourceContractTest, WebSocketTransportTest, lint |
| 2A4 | data/auth/*, di/AppModule.kt, data/SourceFactory.kt; remove old generic transport placement | MisskeyAuth :31-34, MastodonAuth :30-44, AppModule :89,:101-125,:221-230, SourceFactory :16,:25,:44 | authentication, source creation, capabilities, remaining shared callers | AuthGateway tests, both adapter suites, transport tests, lint + architecture audit; update docs/agents/protocol-and-session-ownership.md |
| 2B1 | ui/notifications/NotificationLaunchRouter.kt; data/notifications/AndroidNotificationPresenter.kt, AndroidNotificationDismissReceiver.kt; MainActivity.kt | NotificationLaunch :18-21, prefs notification_launch keys origin/account_local_id/protocol/notification_id :23-63, parse :109-129, intentFor/launchKey :131-154; presenter prepare :52-86, present :129-168 (PendingIntent identity = AndroidNotificationIds requestCode + data URI + UPDATE_CURRENT\|IMMUTABLE); receiver :12-29; MainActivity :41,:74,:162-174 | tap/dismiss intents -> router -> NotificationLaunchHost :23-54 acceptance + acknowledgement | ui/notifications/NotificationLaunchRouterTest, NotificationLaunchHostTest, data/notifications/NotificationPresentationTest |
| 2B2 | move NotificationLaunch value, codec, store below UI into data/notifications/; host keeps navigation acceptance/ack | data imports of ui.notifications today: AndroidNotificationPresenter.kt :23-25, AndroidNotificationDismissReceiver.kt :12 | preserve prefs file name/keys, intent extras, pending-intent identity, process-recreation survival | 2B1 tests + stale/foreign intent + upgrade-survival cases; lint |
| 2B3 | data/notifications/AndroidNotificationPresenter.kt formatting split | NotificationPresentationFactory uses R.string; ui/notifications/NotificationLabelText.kt :35 text extension imported by data | data must import no ui.notifications symbol; pass prepared text instead | NotificationPresentationTest, NotificationLaunchRouterTest; notification repository tests + lint + audit |
| 2C1 | move AccountSearchState from ui/feed/FeedState.kt :34-43 to ui/search/ | FeedState :15 field, FeedViewModel :91 controller, imports in SearchContract/SearchScreen | move type only; test imports updated | FeedViewModelRequestTest, SearchPanelRestorationTest; compile |
| 2C2 | ui/search/SearchController.kt lifetime; ui/feed/FeedViewModel.kt, FeedHost.kt, ui/session/ConnectedEntryStore.kt | controller ctor :17-28, feed search delegation :289-299, stop :350-361, FeedHost VM key feed-$accountId-$sessionGeneration :53-72, entry store :24-58 | explicit connected lifetime, release, projection subscription; mirror-state hazard noted (controller _state + FeedState.accountSearch) | FeedViewModelRequestTest, SearchPanelRestorationTest, PostProjectionCoordinatorTest, ConnectedEntryStoreTest |
| 2C3 | ui/photogrid/PhotoGridController.kt, PhotoGridFeedState.kt lifetime | controller ctor :27-43, preferences FilePhotoGridPreferencesRepository :31-60 (photo-grid-preferences.json), stop :190-197 | independent Photo Grid feed/timeline/hashtags/prefs/pager; do not reuse Home state | PhotoGridFeedViewModelTest, PhotoGridScreenTest, projection + session tests |
| 2D1 | ui/thread/PostThreadViewModel.kt | default private InMemoryPostPreferencesRepository() :50, import :16, assisted factory :668-671, binding AppModule :160-162 | ThreadHost + 16 test constructions must pass an explicit repository | ui/thread/PostThreadViewModelTest; session replacement + prefs refresh |
| 2D2 | ui/profile/ProfileViewModel.kt; domain/ServerCapabilities.kt + both capability probes if correct owner | likedAvailable rule :486-496 (self: capabilities.likedPosts != Unsupported :491-493; other: protocol == MISSKEY :494-495), import :26; probes set likedPosts Supported (MisskeyCapabilityProbe :61, MastodonCapabilityProbe :198) with no self/other signal | adapter/source-owned eligibility; keep signed-in Liked on both protocols, other-Misskey yes, other-Mastodon no; unknown/denied/unsupported distinct | ProfileViewModelTest :100-143, ProfileScreenTest :262, adapter contract tests + lint |
| 2D3 | ui/posts/PostRow.kt favouriteIconFor :238-245, import Protocol :42 | PostRowPresentation (PostRowCallSurface.kt :30-38) has no style field; capabilities.primaryFavourite mode is mutation semantics, not artwork | supplied presentation rule replaces Protocol.MASTODON branch; favourites stay distinct from emoji reactions | new focused tests (favouriteIconFor has zero coverage today), SinglePostScreenTest, adapter contracts |
| 2E | conditional: ui/shell/ShellContent.kt (295 lines), ShellDestinationContent.kt (358), DestinationCallbacks.kt bundles :16-44 | three callback bundles already exist; parameter count is the residual surface | shell stays composition root; extract only if a later UI change needs it | ShellCharacterizationTest, HomeFeedTest, ProfileScreenTest — only if triggered |

# Phases 3-10 map

This slice did not derive a detailed owner/caller/test map for phases 3-10. See `docs/beeline_0.4.0.md`, section "File-to-slice locator". Recheck source and tests before each later slice.

# Verification

- The 1B4 slice consists of `MastodonSource.kt`, `MastodonIntegrationTest.kt`, and these two records; the whole-worktree diff also lists unrelated pre-existing changes that are never staged or committed with a slice.
- 0A verification is recorded above: 51 Python tests; architecture audit exit 0 with 605 findings and no new regressions; 120 relative links resolved.
- The initial focused Gradle run exited 1 with two `NavigationTest` failures. `NavigationTest` then failed standalone at base commit `ba3fe53` with an empty `app/src` diff; this pre-existing issue is logged in `logs/BUGS.txt` and the task-state blockers. The final 0B gate excluded `NavigationTest` and passed the seven-class set: exit 0, 79 tests across `MotionTokensTest`, `SpringyInteractionsTest`, `LargeLayoutModeTest`, `WideNavigationTest`, `HomeFeedTest`, `AppShellStateTest`, and `SettingsDisplayTest`.
- `NavigationTest.closingComposerAutosavesUnsavedText` and `NavigationTest.draftsSurviveActivityRecreationAndCanBeDeleted` fail when run standalone and in the combined set. Both failures reproduce at base commit `ba3fe53` with an empty `app/src` diff. This is a pre-existing open defect; its cause is not established. The failures are excluded from the 0B gate for that reason only and are tracked in `logs/BUGS.txt`.
- 0B `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- 0B architecture audit `--check`: exit 0; 605 findings, no new regressions against baseline.
- 1A review rerun `ModerationServiceTest`: exit 0; Gradle reported `BUILD SUCCESSFUL`.
- 1A review rerun adapter integration and contract tests: exit 0; Gradle reported `BUILD SUCCESSFUL`.
- 1A Mastodon integration/source contract and Misskey integration tests: exit 0; Gradle reported `BUILD SUCCESSFUL`. The test task did not print a test count.
- 1A initial `:app:lintDebug`: exit 1; 1 error, 92 warnings, and 2 hints. The `UnusedBoxWithConstraintsScope` finding was outside the 1A diff and is resolved by L0 below.
- L0 `:app:lintDebug`: exit 0; Gradle reported `BUILD SUCCESSFUL` after replacing the unused `BoxWithConstraints` with `Box`.
- L0 shell regression tests (`ShellCharacterizationTest`, `AppShellStateTest`): exit 0; Gradle reported `BUILD SUCCESSFUL`.
- L0 `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- L0 architecture audit `--check`: exit 0; 605 findings, with no new regressions against the baseline.
- 1A `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- 1A architecture audit `--check`: exit 0; 605 findings, with no new regressions against the baseline.
- Review rerun `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- Review rerun architecture audit `--check`: exit 0; 605 findings, with no new regressions against the baseline.
- `lintDebug` was not rerun. L0 lint passed, and the only main-source change was a comment.
- Final `git status --short`: exit 0; staged `docs/classic_navigation.md` and unrelated dirty files remain unchanged. `git rev-parse --short HEAD`: exit 0; `cbf8698`.
- 0B Markdown links: eight baseline-file targets were checked against direct file reads and glob; all eight targets resolve. Source/test directory links resolve by glob.
- Register: 68 rows — 29 binding-specification rows, 26 proposal rows, and 13 polish research/reference rows. Binding clauses restated in proposal rows remain part of those proposal rows and do not add separate register rows or change the binding status of the original specification clauses. Several proposal rows also flag unverified device measures.
- No source changes occurred. Device and live-server checks remain unverified.

# Next

Completed += `Slice 1B4 — complete (ships with this commit)`; 1B3 commit = `d21280e`.
Current slice = `1B5 — PAUSED (gate unmet, awaiting decision)`; Next = `1C` (shared quote/CW presentation rule). Resume 1B5 only if the recorded decision approves a conservative response cap per adapter.
Last safe commit = `d21280e`. The 1B4 hash is recorded at the next slice boundary (1C, since 1B5 is paused).

# Blockers

- No device is reachable; device baselines and checks remain unverified.
- Live-server and signed-release behavior remain unverified.
- The Android 15 system-bar instrumentation failure remains recorded in `logs/BUGS.txt`.
- No empirical retention measurements exist.
- 0B device measures remain pending.
- Confirm `docs/260926_current_state.md` if a newer review input appears.
- Tighten `AppLocaleControllerTest.everyLocaleResolvesATranslatedValueOrFallback` and `LocalizationResourceTest.localeCatalogMatchesResourcesEnumAndAndroidConfig` when catalogs return.
- The residual 03-G ordering risk remains in the Plan 03 task state.
- No full green gate has passed. One full run executed during 1B1 and is red with 16 failures (above).
- NavigationTest has two failures. Their pre-existing status is separately verified at `ba3fe53` with an empty `app/src` diff. The cause is not established. Investigate in a dedicated slice before phase 10.
- The 14 failures are DraftActionsTest (2), CapabilityCacheTest (2), MisskeyThreadContinuationTest (5), and NotificationSyncOrchestratorTest (5). Their executed test and production sources are byte-identical to `af1f983`; `git diff af1f983 --name-only` lists none of them. Direct grep finds no reference from those classes and subjects to symbols changed by L0, 1A, or 1B1. Focused runs reproduce all 14 failures. Transitive closure was not exhaustively proven. Introduction commits were not bisected because `git worktree add` was blocked by permission. The baseline was not executed. Product-versus-environment cause is not established. These failures are not attributable to the 0.4.0 slices by available evidence. Do not weaken or skip tests. A dedicated investigation slice owns these failures.
- Full unit suite is RED (16 failures in `test assembleRelease`). A dedicated investigation slice must restore full-suite green before the phase-10 release gate.
- 1B5 gate unmet: source verification shows the only response caps routed through request{} are thread reads (Mastodon: MastodonThreadService; Misskey: MisskeySource thread paths). No non-thread capped path exists, so the plan's required non-thread failure test cannot be written without adding a new conservative response cap per adapter (a behavior change). 1B5 is paused pending an explicit decision. 1E1/1E2 do not depend on 1B5 and proceed.

# 1B1 verification

- `:app:testDebugUnitTest --tests "me.foxtails.palustris.data.mastodon.MastodonIntegrationTest"`: exit 0; `BUILD SUCCESSFUL` after the final loop-guard fix.
- `:app:testDebugUnitTest --tests "me.foxtails.palustris.data.mastodon.MastodonSourceContractTest" --tests "me.foxtails.palustris.ModerationServiceTest" --tests "me.foxtails.palustris.data.misskey.MisskeyIntegrationTest"`: exit 0; `BUILD SUCCESSFUL`.
- `:app:lintDebug`: exit 0; `BUILD SUCCESSFUL`.
- `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- Architecture audit `--check`: exit 0; 605 findings. No new regressions were reported.
- Full `test assembleRelease`: exit 1. Release assembly completed, but 16 of 1,265 JVM tests failed. Failures: `DraftActionsTest.saveFailureReportsError`, `DraftActionsTest.deleteFailureStillCompletesAndReports`, `CapabilityCacheTest.oldSourceReadAfterReplacementMissesAndPublishesNothing`, `CapabilityCacheTest.lateProbeAfterRemoveAndReAddWithSameRevisionCannotPublish`, `MisskeyThreadContinuationTest.sourceRejectsForeignAccountAndFocalWithoutNetworkOrConsumption`, `MisskeyThreadContinuationTest.sourceContinuationTokenIsOpaqueUuidAndSingleUse`, `MisskeyThreadContinuationTest.sourceReleasesContinuationStoreBeforeNetworkWork`, `MisskeyThreadContinuationTest.sourcePreservesTransportOrderAcrossContinuations`, `MisskeyThreadContinuationTest.sourceValidatesSessionBeforeConsumingContinuation`, five `NotificationSyncOrchestratorTest` cases (`reAddRejectsOldTokenAndAcceptsReplacement`, `productionRemovalRejectsLateEventsAndDropsGenerationEntry`, `generationsStayMonotonicAcrossRepeatedRemoval`, `unregisterRejectsLateStreamEventsAndRemovesActiveEntry`, `removalRightAfterPublishCancelsPollWithoutOrphan`), and the two documented `NavigationTest` failures. The cause and pre-existing status of failures beyond the two documented NavigationTest cases are unverified.
- Triage rerun of the four classes reproduced the same 14 failures in the focused run. The baseline identity, no-reference evidence, and conclusion are recorded in Blockers and `logs/BUGS.txt`; product-versus-environment cause and exact introduction commit remain unknown.
- Added cursor regression tests: `timelineCursorIsBoundToAccountSessionAndSourceInstance` checks different account, session revision, and source instance each reject as `Unsupported("pagination.cursor")` with unchanged request count. `timelineCursorRejectsUnsafeDecodedUrlsAndHardenedQueries` checks hostile port, userinfo, fragment, scheme, path, unknown key, duplicate pagination key, empty pagination value, and `local=false` on Local before requests. `timelineRejectsMalformedCursorsAndStopsWhenLinkIsMissing` checks no-Link returns null and malformed Base64, truncated JSON, and unknown version reject with unchanged request count.
- Final `git status --short`: exit 0; 1B1 changed source, test, handoff, and task-state files plus the new codec and ignored task logs. Existing unrelated changes and the staged `docs/classic_navigation.md` remain present. `git diff --stat` reports unrelated pre-existing dirty changes too. `git rev-parse --short HEAD`: exit 0; `af1f983`. Nothing was staged by this task.
- Cancellation coverage was not added. Cancellation remains propagated by `MisskeyApi.execute`; this contract was not independently exercised here.
- The first compile attempt exited 1 because the JSON library does not expose `keySet()`. The codec now reads keys through the iterator.
- The tests assert an opaque timeline cursor, Local/Federated second-page wire routes, rejection of raw and cross-route cursors before another request, and rejection of an invalid Local Link before returning a cursor.
- Raw URL cursors are rejected because a server-supplied URL must not choose an authenticated route.
- Cancellation coverage was not added. Cancellation remains propagated by `MisskeyApi.execute`; it was not test-verified here.
- Timeline response order remains server order. Opaque status IDs are not compared.
- `NavigationTest` was excluded from the 0B focused gate; its two failures appear in the full-suite run. Device and live-server behavior remain unverified.
- Review repair added `invalidCursorOnUnprimedSourceIsRejectedBeforeCapabilityProbe` (unsupported cursor and zero requests), `cursorEqualToCurrentRequestUrlIsRejectedBeforeRequest` (unsupported cursor and zero requests), and `cursorPayloadTamperingAndInvalidQueryShapesAreRejectedWithoutRequests` (changed query, variant, string version, multiple pagination keys, duplicate/empty local, all unsupported with unchanged request count). Local and Federated second-page cases assert the bearer header. These repair tests pass in the focused integration run.

# Last safe commit

`d21280e` (1B3) is the last safe commit. The 1B4 hash is recorded at the next slice boundary (1C, since 1B5 is paused).

# 1B4 verification

- Changed only the `unfavorite` path to call the existing `encodePathSegment()` helper. `setPrimaryFavourite` already uses that same helper for both selected and unselected mutations.
- `MastodonSource.kt` raw-ID interpolation grep found a single raw ID in a request path: `unfavorite` at its prior line 277. Other matched path interpolations encode the ID. Query parameters use builders and are not part of this path finding.
- `MastodonIntegrationTest.unfavoriteEncodesReservedIdCharactersAndPreservesPlainIdPath` checks POST, bearer authorization, exact `/api/v1/statuses/plain-id/unfavourite` path, and exact `/api/v1/statuses/slash%2F%3F%20space%25%23/unfavourite` path. No unfavourite alias exists. No raw-path assertion needed updating.
- Response error mapping is unchanged. SourceError behavior was not modified.
- Integration gate: exit 0; `MastodonIntegrationTest` passed, including the new unfavorite wire-path case.
- Source-contract/moderation/Misskey integration gate: exit 0; Gradle reported `BUILD SUCCESSFUL`.
- `:app:lintDebug`: exit 0; Gradle reported `BUILD SUCCESSFUL`.
- `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- Architecture audit `--check`: exit 0; 605 findings and no new baseline regressions.
- No other test asserts unfavorite wire bytes, and there is no unfavourite alias. No prior raw-path assertion needed updating.
- The helper is `URLEncoder.encode(value, UTF_8).replace("+", "%20")`. Plain IDs retain identical path bytes. Reserved characters `/`, `?`, space, `%`, and `#` serialize as `%2F`, `%3F`, `%20`, `%25`, and `%23`.
- `git status --short`: exit 0; `docs/classic_navigation.md` remains staged, and unrelated dirty paths remain untouched. `git rev-parse --short HEAD`: exit 0; `d21280e`. No staging or commit occurred.
- Device and live-server behavior remain unverified. Full-suite red, NavigationTest, 14-failure investigation, and paused 1B5 blockers remain unchanged.

# 1B2 verification

- `MastodonIntegrationTest` focused gate: exit 0; Gradle reported `BUILD SUCCESSFUL` (53 tests).
- Source-contract/moderation/Misskey integration gate: exit 0; Gradle reported `BUILD SUCCESSFUL`.
- `:app:lintDebug`: exit 0; Gradle reported `BUILD SUCCESSFUL`.
- Python unittest: exit 0; 51 tests passed. Architecture audit `--check`: exit 0; 605 findings with no new regressions.
- Grep for `getPageLegacyRawReplay`, `Temporary: raw Link replay`, `raw Link replay`, and `replay raw server Links` in Mastodon source returned no matches.
- Test search found only `MastodonIntegrationTest` assertions for `timelines/tag`; no test asserted `v1/bookmarks` before this slice.
- `savedPosts` and `searchHashtag` perform no capability or network work before `pageClient.currentUrl` validates a non-null cursor and `pageClient.getPage` revalidates it. No pre-page contrast test is needed.
- The existing hashtag second-page test changes only cursor representation expectations; request paths, bearer headers, and response order remain unchanged. New cases cover bookmark paging, normalized `#cats`/`cats` identity, account/session/source-instance and route mismatch, altered route/query values, malformed and raw values, and invalid Link rejection.
- Final status preserves staged `docs/classic_navigation.md`; HEAD is `5207a96`. No staging or commit occurred. Device and live-server checks remain unverified.
- An early test attempt failed because the normalized hashtag replay lacked a queued response. The corrected test queues that response and passes. An initial loop test used a valid continuation URL; the final test uses the route's current first-page URL and confirms rejection before request.

# 1B3 verification

- Routes: `blocked` pins `/api/v1/accounts/blocked`; `muted` pins `/api/v1/accounts/muted`. Both allow one nonblank `max_id`, `since_id`, or `min_id`; `limit` is optional but must equal `40`. Unknown, duplicate, empty, or extra keys fail. Neither route has another first-page filter.
- Cursor payload fields are `version`, `variant`, `route`, `kind`, `account`, and `url`. The cursor retains the existing `ModerationCursor` account, query-kind, and protocol-variant bindings. Base64url is unpadded. The cursor is passed through moderation UI state only; no persistence path was found, so no migration is needed.
- `MastodonSource.blockedAccounts` and `mutedAccounts` call the moderation service directly inside `request`; they do not refresh capabilities or perform other I/O before cursor decoding. Invalid cursors therefore fail before any request.
- `ModerationServiceTest.mastodonBlockedAndMutedPagesUseOpaqueRouteBoundCursors` checks GET method and bearer authorization on both pages, both route paths, opaque `max_id` wire bytes, and exact returned item order.
- `ModerationServiceTest.mastodonModerationRejectsTamperedAndLegacyCursorsBeforeRequest` checks wrong path, changed limit, unknown and duplicate query keys, empty pagination values, valueless/empty/duplicate `limit`, combined pagination keys, raw and relative cursor values, wrong route/kind/variant, cross-account replay, and payload tampering (unknown or string version, missing field, wrong field type). Each rejection checks unchanged request count. A missing `limit` remains valid; only a present value other than exactly `40` is rejected.
- `ModerationServiceTest.mastodonModerationRejectsInvalidLinksAndCurrentUrlLoop` checks invalid response Link yields `Unsupported("pagination.link")`, and a Link equal to the current request URL is rejected.
- The first moderation test run exited 1 because `since_id` was incorrectly treated as an invalid pagination key. The test was corrected: the contract allows exactly one of `max_id`, `since_id`, or `min_id`.
- Cursor failures retain the existing `moderation.cursor` feature string. Invalid response Links use `pagination.link`.
- `ModerationServiceTest`: latest focused run exited 0; runner reported `BUILD SUCCESSFUL` but did not print a test count; the file contains 13 `@Test` methods. The initial run failed because `since_id` is a valid continuation key; the corrected assertions passed.
- Mastodon integration/source contract and Misskey integration command: exit 0; `BUILD SUCCESSFUL`. Grep found no other tests asserting Mastodon moderation-list paging or `/v1/lists` routes.
- `:app:lintDebug`: exit 0. Python unittest: exit 0; 51 tests. Architecture audit `--check`: exit 0; 605 findings, no baseline regression.
- Invalid cursor failures retain `moderation.cursor`, as asserted by the existing test. Invalid server Links use `pagination.link`.
- At the 1B2 boundary, HEAD was `9e29ef4`; no staging or commit occurred. Device and live-server behavior remain unverified. The full-suite, NavigationTest, 14-failure, and paused 1B5 blockers remain unchanged.
- Review repair adds `hashtagCursorRejectsAnotherQueryWithoutChangingTheCursor`: it obtains a cats cursor from a real cats Link response, passes that unchanged cursor to `searchHashtag("dogs", cursor)`, and asserts `Unsupported("pagination.cursor")` with no additional request.
- Review repair adds `bookmarkCursorRejectsTimelineRouteBeforeCapabilityProbe`: it obtains a bookmark cursor from a real bookmarks Link response, passes it to `timeline(Home, cursor)` on a source with the real capability probe, and asserts `Unsupported("pagination.cursor")` with no additional request.
- Renamed the earlier mixed tampering case to `hashtagCursorPayloadTamperingAndOtherRoutesAreRejected`. Its changed query payload has a cats path, so that case proves payload tampering/path validation, not unchanged-cursor cross-query rejection.
- Both required Gradle focused gates, Python unittest, and architecture audit exited 0. `lintDebug` was not rerun because this repair changes tests and records only; no main source changed.
- At the 1B2 boundary, HEAD was `5207a96`; staged `docs/classic_navigation.md` and unrelated worktree changes remained untouched. No staging or commit occurred. Existing blockers remain unchanged.

# 1B1 review repair verification

- The capability-probe contrast uses the real `MastodonCapabilityProbe` with a stale schema snapshot. Invalid input returns `Unsupported("pagination.cursor")` with zero requests. Valid pagination requests the metadata endpoint, the first timeline page, and the second page. It checks second-page URL and bearer authorization.
- At the 1B1 boundary, the ownership text still described temporary bookmark and hashtag raw-Link replay. Slice 1B2 resolved that transitional behavior; the current ownership contract covers all page routes.
- Required Mastodon integration, adapter contract/moderation/Misskey integration, lint, Python, and architecture audit gates exited 0.
- Full-suite red status remains tracked as its own blocker. The 14 unexplained failures are not reclassified. No full suite was run for this repair.
- At the 1B1 review boundary, HEAD was `af1f983`; the staged `docs/classic_navigation.md` remained untouched. Review changes were not committed. The 1B1 hash is recorded at the 1B2 boundary.

# 1B3 review repair

- Root cause: OkHttp `HttpUrl.queryParameter("limit")` returns null for a valueless key. `queryParameterValues("limit")` distinguishes valueless (`null`) from assigned-empty (`""`) values. Validation now checks presence in `queryParameterNames`, then requires the complete values list to equal exactly `["40"]`; this also rejects duplicates. Link and replay use the same validator and preserve `pagination.link` and `moderation.cursor` errors.
- `mastodonBlockedAndMutedPagesUseOpaqueRouteBoundCursors` now checks each page method, bearer token, route, and exact mapped item order.
- `mastodonModerationRejectsTamperedAndLegacyCursorsBeforeRequest` now checks valueless, empty, and duplicate `limit`; multiple pagination keys; a genuinely different port; unknown-version value, string version, missing field, and wrong field type. Rejections assert unchanged request count.
- `mastodonModerationRejectsValuelessLimitLinksAndAllowsMissingLimit` checks those three invalid Link query forms return `Unsupported("pagination.link")`, and confirms missing `limit` is accepted.
- `mastodonModerationRejectsInvalidLinksAndCurrentUrlLoop` retains the invalid-Link and current-URL loop assertions.
- `:app:testDebugUnitTest --tests "me.foxtails.palustris.ModerationServiceTest"`: exit 0; `BUILD SUCCESSFUL`.
- Mastodon integration/source contract/Misskey integration gate: exit 0; `BUILD SUCCESSFUL`.
- `:app:lintDebug`: exit 0; `BUILD SUCCESSFUL`.
- `python -m unittest discover -s tools/tests`: exit 0; 51 tests passed.
- Architecture audit `--check`: exit 0; 605 findings and no new baseline regressions.
- The moderation cursor binds origin/account/kind/variant/route/query, but not `sessionRevision` or `sourceInstance`. ViewModel memory lifetime and `AccountSourceRegistry.isCurrent` guard normal paging. A direct same-account `SocialSource` replay after source replacement remains possible. Stronger binding was consciously deferred; no code change was made for this NIT.
- The 1B3 review repair recorded `9e29ef4` as its last safe commit at that time. The current 1B3 hash and last safe commit are `d21280e`; 1B4 is complete and ships with this commit. The current slice is 1B5 PAUSED and the next slice is 1C; record the 1B4 hash at that next boundary. No staging or commit occurred. Existing blockers remain unchanged.
