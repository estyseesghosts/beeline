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
- Slice L0 is complete at `cbf8698`. Slice 1A is complete (ships with this commit). Record the 1A hash at the 1B1 boundary.

# Current slice

Slice 1B1.

# Files involved

0B documentation: `docs/agents/beeline-0.4.0-ui-baseline.md`, `docs/agents/documentation-inventory.md`, `docs/agents/README.md`, this task state, `docs/agents/handoff.md`, and `logs/260923-035132.txt`. Later source files and tests appear in the owner map below.

Forward owner/caller/test map for phases 1-2. Paths are relative to `app/src/main/java/me/foxtails/palustris/` unless noted. Tests are under `app/src/test/java/me/foxtails/palustris/`. These are planned checks, not 0A results.

| Slice | Owner files | Key symbols / lines | Callers and boundary | Tests to run in that slice |
| --- | --- | --- | --- | --- |
| 1A | data/mastodon/MastodonModerationService.kt; data/misskey/MisskeyApi.kt (GET helper prefixes /api/) | relationship :74, profileRelationship :85, validateTarget :104; MisskeyApi.get :172 | MastodonSource.setBlocked/setMuted :156-162, removeBlockedAccount/removeMutedAccount :392-394 | ModerationServiceTest, data/mastodon/MastodonIntegrationTest; both adapters' moderation contracts + lint |
| 1B1 | data/mastodon/MastodonPageClient.kt, MastodonTimelineService.kt; pattern sources MastodonProfileService.kt, MastodonNotificationCursorCodec.kt :17-59 | getPage :14-20, validatePaginationUrl :22-32; timeline :14-31 | MastodonSource.timeline :113-116; opaque adapter-owned cursor binds timeline kind, hashtag, account/session, variant | MastodonIntegrationTest :227-244, :822-844; MastodonSourceContractTest; wrong-path, relative, foreign-origin, cancellation cases |
| 1B2 | data/mastodon/MastodonSource.kt | savedPosts :340-347, searchHashtag :427-436 | saved-post and hashtag UI through SocialSource; cursors stay opaque in UI | MastodonIntegrationTest :247-265; cross-account and cross-query reuse |
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

Slice 1A is complete (ships with this commit). Current slice is 1B1. Next, bind Mastodon timeline cursors per plan. Record the 1A hash at the 1B1 boundary. L0 hash is `cbf8698`. Last safe commit is `cbf8698` (L0). The 1A hash does not exist yet.

# Blockers

- No device is reachable; device baselines and checks remain unverified.
- Live-server and signed-release behavior remain unverified.
- The Android 15 system-bar instrumentation failure remains recorded in `logs/BUGS.txt`.
- No empirical retention measurements exist.
- 0B device measures remain pending.
- Confirm `docs/260926_current_state.md` if a newer review input appears.
- Tighten `AppLocaleControllerTest.everyLocaleResolvesATranslatedValueOrFallback` and `LocalizationResourceTest.localeCatalogMatchesResourcesEnumAndAndroidConfig` when catalogs return.
- The residual 03-G ordering risk remains in the Plan 03 task state.
- The full Gradle gate remains pending for the final release gate. No full gate has run for 0.4.0.
- NavigationTest has two failures reproducible at base commit ba3fe53 with no source diff. The cause is not established. Investigate in a dedicated slice before phase 7 composer work.

# Last safe commit

`cbf8698` (L0) is the last safe commit. The 1A hash does not exist yet. Record its hash at the 1B1 boundary.
