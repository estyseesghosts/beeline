# App Shell Ownership

**Owner:** app-shell and feature-presentation maintainers.

**Status:** current. The shell decomposition is complete. Completion slices C-01 through
C-05, C-06a, C-06b, C-06c, C-07, C-08, C-09, C-10, C-11, C-12a through C-12d4, C-13,
C-14, and C-15 are implemented and test verified. Steps 13, 14, and 15 of the progress
report are complete. No dead scaffolding remains.

**Last reviewed:** 2026-10-05.

**Source baseline:** `b629a2c` (planning). Status refreshed against `c9e06c8`.
Phase 4C-4 clearance invariants below are recorded through the Profile clearance slice.

**Evidence:** source verified. R02 verifies the shell draft fixture with NavigationTest,
ComposerOwnerTest, and ShellCharacterizationTest. Device and live-server behavior remain unverified.

**Completion owner:** `docs/archive/agents/decomposition-01-02-completion.md`.

## Present Boundary

`ConnectedApp` is root composition. It collects session and account index, chooses the startup,
sign-in, or connected presentation, installs theme and application-wide content policy, and composes
the session host. It does not write settings or own post fan-out.

`AccountManager` publishes one `ConnectedSessionContext` under `ui/session/`. The context joins the
visible account, the durable session revision, the runtime presentation generation, and the
registered source. `ConnectedSessionHost` in `ui/session/` reads that context. It composes focused
feature hosts, and keeps the shared feed owner, saved-collection owner, draft owner, post-action
owner, and projection coordinator. It exposes no token and no `SocialSource` to presentation.

`ui/session/ConnectedEntryStore.kt` owns the terminal teardown callbacks for one connected entry.
The store is activity-scoped. It survives activity recreation and retires feature models when the
connected lifetime retires or the owner clears. A composition can leave and return with the same
connected lifetime without stopping a retained model.

Focused feature hosts own their model, state, actions, and projection registration:

| Host | Owner | Contract |
| --- | --- | --- |
| `ui/feed/FeedHost.kt` | Home `FeedViewModel` | `HomeContract`, `PostInteractions` |
| `ui/photogrid/PhotoGridHost.kt` | `PhotoGridOwner` | `PhotoGridContract` |
| `ui/search/SearchHost.kt` | connected-session `SearchOwner` | `SearchContract` |
| `ui/saved/SavedCollectionsHost.kt` | bookmark `SavedPostsViewModel` | `SavedCollections` |
| `ui/profile/ProfileHost.kt` | `ProfileViewModel` | `ProfileContract` |
| `ui/thread/ThreadHost.kt` | `PostThreadViewModel` | `ThreadContract` |
| `ui/notifications/NotificationsHost.kt` | `NotificationsViewModel` | `NotificationsContract` |
| `ui/notifications/NotificationSettingsHost.kt` | `NotificationSettingsViewModel` | `NotificationSettingsContract` |
| `ui/directmessages/DirectMessagesHost.kt` | `DirectMessageViewModel` | `DirectMessagesContract` |
| `ui/emoji/EmojiHost.kt` | `EmojiCatalogViewModel` | `EmojiPresentation` |

`ui/shell/ShellContent.kt` owns compact and large shell presentation. `ui/shell/DetailActionPolicy.kt`
resolves the origin-based post-action handlers. Compact detail uses its feed owner. Wide detail passes
the active thread owner for the full single-post lifetime, including initial thread acquisition,
because that owner renders the focal post. `ShellDetailCallbacks` is the shared detail callback bundle.

`ui/posts/PostRow.kt` owns the shared `InteractionRow` behavior. Its default repost confirmation
uses `PostRepostConfirmationState`, so detail callers do not implement a second forwarding path.

`SettingsOverlayHost` in `ui/settings/` owns the settings route, settings models, and settings
commands. It publishes the validated account set and delays account models until the restored
index is validated. `NotificationLaunchHost` in `ui/notifications/` owns launch delivery. It acknowledges
a launch only when the receiving shell accepts its route. A missing account routes to the
recoverable unavailable state. The inbox carries a request epoch, so rejected pages change no
state.

`PalustrisApp` is the composition root. It owns navigation and surface placement, but not adaptive
layout. `ui/shell/ShellContent.kt` owns compact and large shell presentation. It accepts narrow
feature contracts in `ui/shell/`. Back precedence is a pure policy in `ui/navigation/ShellBackPolicy.kt`.
The shell keeps the back state and the guarded dismissal, and routes dismissal plus the back and
edge-swipe conditions through the policy. Completion slice C-12c reduces the remaining assembly.
`ui/shell/ShellOverlayPresenter.kt` owns transient overlay state behind one boundary: the
post-action bubble, the emoji picker target, the media and profile-image requests, and the dialog
flags. Slice S1a. The shell reads the holder and keeps navigation, placement, and session-bound
validation. Only the profile dialog flag survives process recreation.
`ui/shell/ShellDestinationContent.kt` renders the destination scaffold with its animated
branches: notification detail, local pages, Home, search with Photo Grid, notifications, and
profile. Slice S1b. It owns no state. Saveable holders and scroll states stay with the shell
and pass through unchanged.
`ui/shell/ShellOverlayHost.kt` hosts bubbles, viewers, sheets, overlays, the emoji picker,
notification settings, and dialogs behind two composables. Slice S1c. `ShellBubbleHost`
preserves the z-order around the compact single-post surface. The host owns no state.

`ui/composer/ComposerOwner.kt` owns the composer editor for one connected account. It holds text,
warning, audience, the dirty snapshot, the drafts list, reply and quote restoration, and the publish
flow. It publishes `ComposerNavigation` requests. `PalustrisApp` applies a request by placing the
composer overlay. The shell keeps overlay placement and back precedence. The editor state uses a
saveable snapshot. A session replacement clears restored reply and quote targets.
`ui/composer/ComposerOverlayHost.kt` owns the composer sheet assembly beside the owner: the save
control, the editor bindings, publish with its confirmation message, and tracking cleanup. The
shell keeps the placement condition with open, guarded close, and emoji-picker target requests.

Test code binds test-only recorders in `app/src/test/java/me/foxtails/palustris/ui/shell/AppShellFixtures.kt`.

| Contract | Owner | State | Actions |
| --- | --- | --- | --- |
| `AccountSwitcher` | `AccountManager` | Account references | Switch, add, settings, sign out |
| `EmojiPresentation` | `EmojiCatalogViewModel` | Catalog and capabilities | Load, retry, group and pin preferences |
| `NotificationSettingsContract` | `NotificationSettingsViewModel` | One target account and settings | Eleven settings commands |
| `BookmarksContract` | bookmark `SavedPostsViewModel` | Bookmark collection | Refresh, paging, remove, permissions, react |
| `NotificationsContract` | `NotificationsViewModel` | Notification inbox | Refresh, paging, read, dismiss, follow, query |
| `DirectMessagesContract` | `DirectMessageViewModel` | Inbox, selection, send, composer editor | Refresh, paging, open, close, start, update editor, send |
| `ProfileContract` | `ProfileViewModel` | Target, categories, relationship, editor | Open, category, paging, follow, react, editor |
| `ThreadContract` | `PostThreadViewModel` | Selected thread | Activate, deactivate, paging, mutations |
| `PhotoGridContract` | `PhotoGridOwner` | Independent Photo Grid feed | Load, select, refresh, paging, hashtag, error |
| `SearchContract` | `ui/search/SearchOwner.kt` | Account and hashtag search state | Search, paging |
| `ComposerContract` | `ui/composer/ComposerOwner.kt` | Editor fields, dirty snapshot, drafts, reply, quote | Update editor, save, delete draft, publish |
| `DraftsContract` | account draft store | Saved drafts for the active account | Load, save, delete |

`ui/shell/PostProjectionCoordinator` is the single fan-out owner for normalized post updates and
accepted publications. It validates account and durable revision, excludes the origin sink, and
suppresses nested forwarding. It retires with its connected entry and rejects repeated publication
deliveries by created-post identity. `PostProjectionCoordinatorTest` covers origin exclusion, nested
suppression, foreign accounts, old revisions, publications, duplicate rejection, and retirement.

## Characterization Evidence

Slice 2E inspected `ShellContent`, `ShellDestinationContent`, `DestinationCallbacks`, `HomeFeed`,
`ProfileTimelineList`, `ProfileViewModel`, `ConnectedSessionHost`, and the Search, Photo Grid,
and direct-message contracts. The shell keeps saveable holders and pager state. Feature owners keep
editor state and session-bound models. The connected host keeps the explicit post authority.

The characterization tests cover foreign publication rejection, retired projection rejection,
compact and large Search routes, and profile editor and pager continuity. This slice makes no
extraction because the current owners already enforce one mutable owner per connected lifetime.

## Known Gaps

This table records each gap and its state. A row that names a completion slice is still open. The
acceptance matrix records the status.

| Gap | Source evidence | Completion slice |
| --- | --- | --- |
| Post-action ownership | Closed by C-07. The popup owner is built from the stable connected identity and reads the latest refresh callback without recreation. P-02 narrowed the leaf contract: `LocalPostPopupOwner` provides `PostPopupPresentation`. Post mutations use one `PostInteractionExecutionAuthority` per connected session. | — |
| Composer completion | Closed by C-06a. `ComposerOwner.publish` reserves the submission and rejects obsolete save callbacks. | — |
| Draft storage boundary | Closed by C-06b. `data/auth/DraftActions.kt` owns storage and binds to one account. `ui/shell/DraftsContract.kt` carries no storage type. | — |
| Draft removal coordination | Closed by C-06c. `AccountManager.removeAccount` revokes draft writers and deletes rows in one serialized boundary. A revoked `DraftActions` writer writes nothing and reports no success. | — |
| Projection retirement | Closed by C-07. The coordinator retires with its connected entry and rejects repeated publication deliveries by created-post identity. | — |
| Home paging demand | Closed by C-08. `HomePagingDemand` tracks filter identity and the request epoch beside the row count. Only accepted pages consume the budget. | — |
| Notification launch | Closed by C-09. `NotificationLaunchHost` acknowledges a launch only when the receiving shell accepts its route. Rejected pages change no state. | — |
| Dead scaffolding | Closed by C-15. No caller remained for any removed symbol. `FeedViewModel` and `AccountManager` use the `data.notifications` names directly. | — |
| Shell assembly | Closed by C-12d3. `PalustrisApp.kt` owns navigation and placement. Composer sheet assembly moved to `ui/composer/ComposerOverlayHost.kt` in C-12a. Back precedence moved to `ui/navigation/ShellBackPolicy.kt` in C-12b. Preview-only placement moved to `ui/PalustrisAppPreview.kt` in C-12d1. Navigation state and transitions moved to `ui/navigation/ShellNavigator.kt` in C-12d2 and C-12d3. Session-bound guards stay in the shell. | — |
| Test isolation | Closed by C-12d4. `ReplyComposerTest` composes at feature level through `ComposerFeatureFixtures` since C-12c. `HomeFeedTest` and `SignInScreenTest` compose presenter behavior through `HomeFeatureFixtures` since C-12d4. Scroll clearance, detail navigation, composer, account switching, confirmation, and popup-host assertions stay on `AppShellFixtures.app`. | — |
| Cancellation | Closed by C-13. Every touched suspending path rethrows `CancellationException`. `PostInteractionMutationOwner.handleFailure` rethrows before its guarded fallback. | — |

## Removed In The Migration

- The dead outer `onOpenReactionPicker` parameter. Live `expandReactionPicker` forwarding remains.
- The duplicate shell `ownedPosts` input. Home rows come from `HomeFeedUiState.ownedPosts` only.
- `actionSource`, `draftStore`, and `postPreferences` as generic shell parameters.
- The test-only `onReply` seam. Reply behavior is asserted through composer state.
- The obsolete `moved...` import aliases.
- The pass-through wrappers `AppHomeDestinationContent`, `AppSearchDestinationContent`,
  `AppPhotoGridDestinationContent`, and `AppProfileDestinationContent`.
- The unregistered `sourceFactory.create(session)` fallback in `ConnectedSessionHost`.
- The separate `activeSession` and `session` inputs to the connected shell.

## Removed Cleanup (C-15)

C-15 removed the dead scaffolding with no caller: `LegacyLargeProfilePresentation` and
`LegacyProfileHeader` (replaced by `ProfileLargePresentation.kt` and `ProfileHeader.kt`),
`LegacyMediaTransitionImage` and `LegacyMediaTransitionImageCanvas` (replaced by the media
transition layer), `MarkdownPostText` (replaced by `InlineEmojiText`), `AppBackHandler` and
`BackNavigationState` (replaced by the root back policy), `SectionTabs` (replaced by inline
tab rows), and the `AccountSyncCoordinator` aliases (replaced by the `data.notifications`
names in `FeedViewModel`, `AccountManager`, and the feed tests).

## Invariants

- `CompactOverlayMetrics` owns compact bottom geometry for the composition lifetime.
  Navigation uses `max(system navigation inset, IME inset)`. Contextual docks add navigation clearance above that base.
  `compactScrollEndClearance` uses the same contextual inset plus the destination control-stack height.
  Home clearance includes its tabs and navigation. Scrolling content carries clearance; the shell viewport stays full size.
  `ShellContent` places navigation and Home tabs. Search, Photo Grid, Notifications, and Profile own their floating docks.
  `DirectMessageConversationScreen` places the editor in its column above compact navigation and the IME.
  Its weighted thread needs normal spacing, not a second copy of editor or navigation clearance.
  The wide editor retains its prior IME/system-bar policy. Feature state and protocol contracts do not change.
  `NavigationTest` covers IME dismissal coordination, final rows/tiles, and DM editor separation.
  `DirectMessageScreenTest` and `WideNavigationTest` cover existing feature and wide behavior.
  Synthetic insets and Robolectric screenshots do not verify device rendering. Measured high-font clearance remains Phase 4E work.

- Required adaptive end state (maintainer clarification, 2026-10-04): compact-narrow, compact-wide,
  and tablet reuse the underlying navigation components and one navigator. Compact-wide and tablet
  share the vertical six-button presentation. Compact-narrow keeps the existing four grouped positions
  and narrow layout. Independent detail-pane geometry must not create a separate navigation owner.
  This remains planned; current `LargeLayoutMode` uses width-only 600/840 dp cutoffs.
  The available emulator is intended for compact-wide testing, not narrow-phone acceptance.

- `ui/LayoutDirectionPolicy.kt` owns the effective layout direction rule.
  `deviceLayoutDirection()` reads `LocalConfiguration.current.layoutDirection`, which is the platform
  authority for the device direction. A Compose override never changes that configuration value.
  `ConnectedApp` reads it before publishing, resolves `AppLayoutDirection` against it, and provides
  `LocalLayoutDirection` in its existing `CompositionLocalProvider`. Callers below the root must
  resolve against `deviceLayoutDirection()` and never against `LocalLayoutDirection.current`, because
  the published value is the forced direction there.
  Android has no public API that sets layout direction independently of the locale, so the forcing is
  Compose-level. `LocaleManager.applicationLocales` stays with `AppLocaleController` and is unchanged.
  `System` is the default for an absent or unknown stored key, so an upgrade never flips an existing
  user. The Display item carries a headline only; a supporting summary would need a third string.
  Physical `absolutePadding` clearance sites keep following the physical right edge and must not be
  converted. System bar and window insets stay physical.
  Unverified: device rendering, physical foldable behavior, TalkBack, and real right-to-left
  language support, which is a separate task. Known gap: `LargeScreenShell` converts a physical
  `bounds.left` from `LargeLayoutMode` and applies it with direction-relative `Modifier.offset`, so
  forced right-to-left can move a pane away from the physical hinge. That needs a physical-anchor
  decision from the maintainer and is not fixed here.

- `LargeLayoutMode.kt` owns current wide pane and folding-feature geometry. `HingeInfo.bounds` is
  translated from window pixels into the pane-content coordinate space by `LargeContentOriginPx`.
  The current LTR content origin includes the left system inset and 80 dp rail; RTL includes the
  physical left inset but not the rail, because the `Row` places that rail at physical right.
  `calculateLargePaneLayout` removes separating or occluding features from safe pane regions and
  ignores non-separating creases. Its 600/840 dp mode selection remains independent of the new
  navigation-fit policy, which Phase 4C has not activated. System gesture, cutout, taskbar-safe
  placement, stable IME-independent fit, and production capsule geometry remain unverified.
  `LargeLayoutModeTest` characterizes the pixel transform and existing pane behavior; it does not
  verify physical folding-device coordinates.

- `NavigationButton` owns interaction presentation for its composition lifetime, not destination state.
  It shares selected tint, icon scale, press treatment, and tab semantics. Callers supply profile
  content and account-switch callbacks. `NavigationCapsule` owns material and one traveling indicator.
  `CompactContextualNavigationBar` supplies grouped items, horizontal geometry, and callbacks.
- `WideNavigationPresentation` composes the six `WideNavigationItem` values vertically with shared
  buttons and capsule. The caller supplies selection, callbacks, and bounds. The Profile target uses
  `AccountAvatar`; a long press requests account switching. The presenter does not choose production
  dimensions or replace `LargeNavigationRail`.
- `ContextualNavigationAction` carries the icon, accessibility label, enabled state, and callback.
  `ContextualNavigationActionButton` owns its shared Material 3 rendering and motion. The compact
  bar delegates to it; the callback remains with the shell or feature contract.
- `ShellNavigator` remains the selection authority. The indicator uses the existing motion scheme
  and logical offsets matching evenly spaced slots. Reduced motion reads the selected index directly.
  Neither shared component dispatches navigation from animation completion.
  `CompactNavigationSelectionTest` covers interruption, reduced motion, RTL, and fixed 48 dp bounds
  at 200% text. `NavigationTest` covers selected-icon alignment and grouped compact behavior.
  `NavigationPresentationTest` covers six-target order, vertical alignment, 48 dp bounds, focus,
  LTR/RTL, reduced motion, profile account switching, and contextual-action bounds and callbacks.

- `ShellContent` carries named physical-right and bottom obstruction clearances through the existing
  destination host. `AppNotificationsDestinationContent` forwards them to the DM inbox and conversation.
  `DirectMessageInboxScreen` uses physical-right clearance for header and row interaction content,
  while its viewport and row surfaces remain full width. Bottom clearance extends only the list
  scroll range. Compact layout ignores both wide-only inputs and keeps its existing IME-aware policy.
  The application currently supplies the default zero values because floating navigation is inactive.
  `DirectMessageScreenTest` covers synthetic LTR/RTL clearance, full-width underlay, row and header
  bounds, final-content reach, branch forwarding, and compact compatibility.
  `DirectMessageConversationScreen` applies physical-right clearance to its header, notice/error text,
  transcript content, and editor content. Its viewport and transcript viewport remain full size.
  Bottom clearance extends only the transcript scroll range. Wide `imePadding()` and navigation-bar
  positioning remain unchanged. Compact layout ignores both inputs and retains contextual-control insets.
  Tests cover LTR/RTL bubble and control bounds, transcript reach, and synthetic IME open/close transitions.
  Changing bottom clearance does not move the editor or Send action. Editor text remains feature-owned.
  Physical-device IME behavior and production obstruction geometry remain unverified.

- `ShellDestinationContent` forwards both shell clearances to Home without changing its feature or scroll-state owners.
  `HomeFeed` clears physical right inside post interaction content, error/sign-in content, and list status/footer content.
  Its pull-to-refresh and list viewports, transparent outer post extents, error surfaces, and dividers retain their width.
  Interactive media and quotes stay inside the cleared post content. Bottom clearance extends the existing list end spacing.
  Only the wide timeline dock moves above bottom obstruction and clears physical right. The null-Home branch clears its dock and text.
  `HomeTimelineTabs` retains chip scrolling, selection travel, and timeline callbacks. Obstruction clearance does not calculate chip travel.
  Compact Home ignores both inputs and retains its IME-aware end spacing and shell-owned tabs/navigation.
  `HomeClearanceTest` verifies synthetic LTR/RTL bounds, row/divider underlay, error/sign-in callbacks, final post/footer reach,
  branch forwarding, timeline selection, scroll-state retention, and narrow compact compatibility.
  Production clearances remain zero. Device rendering and production obstruction geometry remain unverified.

- `ShellDestinationContent` forwards both shell clearances to Search without changing query, category, or list-state owners.
  `SearchScreen` clears physical right inside hashtag post rows, account result rows, the continuation item, and the wide dock.
  Its content viewport, both list viewports, outer row extents, dividers, empty states, and loading indicators keep their width.
  Account rows clear through the list's absolute content inset, because the account row surface is its own click target.
  Bottom clearance adds to the wide result end spacing only. The wide dock keeps its bottom-start placement,
  `LargeBottomDock` spacing, and measured height, so obstruction clearance never moves the dock or the search field.
  Wide Search applies no IME field inset and keeps its current dock geometry.
  `CategoryChips` retains chip scrolling and selection. Compact Search ignores both inputs and keeps
  `compactContextualControlsPositioningInsets` and `compactScrollEndClearance`.
  `SearchClearanceTest` verifies synthetic LTR/RTL bounds, row and divider underlay, account row and continuation callbacks,
  final result reach, dock stability, chip selection, branch forwarding, retained list position, and compact IME compatibility.
  Production clearances remain zero. Device rendering and production obstruction geometry remain unverified.

- `ShellDestinationContent` forwards both shell clearances to Photo Grid without changing its feed, preference,
  or grid-state owners.
  `PhotoGridScreen` clears physical right through the staggered grid's own absolute content inset. A tile is opaque
  media and one click target, so a per-tile inset would leave a dead strip inside every lane. Its
  `photo_grid_content` viewport keeps the full size, and the adaptive lanes stay adaptive.
  Tiles, the load-older control, the paging-progress item, the paging-error surface, the up-to-date label, and the
  empty state therefore clear physical right in LTR and RTL. Tile media no longer passes under floating chrome.
  The full-screen error state keeps its full-size viewport and clears only its retry content.
  Bottom clearance adds to the wide grid end spacing. The wide filter-chip dock clears physical right and sits above
  supplied bottom obstruction, matching `HomeFeed`. Photo Grid has no measured dock height.
  `FilterChipRow` retains chip travel, chip selection, and the add-hashtag entry. Obstruction clearance sets no chip travel.
  Compact Photo Grid ignores both inputs and keeps `compactContextualControlsPositioningInsets`,
  `compactScrollEndClearance`, and `CompactFilterDockHeight`.
  `PhotoGridClearanceTest` verifies synthetic LTR/RTL bounds, tile and reveal control bounds, continuation and retry
  callbacks, final-tile reach, dock position and clearance, chip and hashtag selection, branch forwarding, retained
  grid position, and compact compatibility.
  Production clearances remain zero. Device rendering and production obstruction geometry remain unverified.

- `AppNotificationsDestinationContent` forwards both shell clearances to the notification inbox.
  `NotificationsScreen` clears physical right in the wide top chip row and in the notification list content.
  A notification row always carries a dismiss control and can carry follow-request controls, so it is not a single
  opaque target. The row card keeps its full width for visual underlay; its interactive content (row open target,
  dismiss, accept/reject) clears physical right through an absolute right inset inside the row.
  The empty state, error/storage retry controls, sync-delayed banner, paging/load-older control, and paging error
  text clear physical right. Bottom clearance adds to the wide list end spacing only. The wide `Column`,
  `notification_refresh_surface`, and `notifications_content` keep their full size. Wide layout has no bottom dock;
  its chip row sits at the top. Compact Notifications ignores both inputs and keeps its floating bottom chip row,
  `compactContextualControlsPositioningInsets`, `compactScrollEndClearance`, and `CompactFilterDockHeight`.
  `NotificationsClearanceTest` verifies synthetic LTR/RTL bounds, row-surface underlay, dismiss and follow-request
  controls and callbacks, the load-older and retry callbacks, final-item reach, retained filter selection and
  scroll position, branch forwarding, and compact compatibility.
  Production clearances remain zero. Device rendering and production obstruction geometry remain unverified.

- `ShellDestinationContent` forwards both shell clearances to Profile without changing its feature, paging,
  category, or scroll-state owners.
  `ProfileScreen` passes physical-right clearance to its wide owners and adds bottom obstruction to
  `LargeBottomDockClearance` for the wide end of list. Compact ignores both inputs.
  `ProfileLargePresentation` clears the only wide column that reaches the pane physical right edge: the timeline
  column in LTR and the summary column that holds the header and details in RTL. The wide category dock keeps its
  bottom-start placement and `LargeBottomDock` spacing, clears physical right, and sits above the bottom obstruction.
  `ProfileTimelineList` clears physical right on the content it owns: post rows, pinned rows, the Featured title,
  the inline category chip row, the details item, the loading, empty, error, and inline-error surfaces, the
  loading-more indicator, and the load-older or up-to-date footer.
  A profile row is `PostRow`, a transparent column, so its content takes an absolute right padding while the item
  divider keeps the full width for visual underlay. That matches `HomeFeed`. It is not the Photo Grid tile inset.
  `ProfileCategoryChips` takes a modifier so the inline row clears through the same value.
  `ProfileClearanceTest` verifies synthetic LTR/RTL bounds, viewport and divider underlay, the mirrored wide
  columns, header and row controls, dock clearance, final-row and footer reach, load-older and retry callbacks,
  branch forwarding, retained category selection and scroll position, and compact compatibility.
  Production clearances remain zero. Device rendering and production obstruction geometry remain unverified.

- A contract carries no session secret, access token, source, repository, or ViewModel.
- `sessionGeneration` and durable `sessionRevision` stay distinct.
- The connected session uses one registered source per session. Recomposition does not create
  replacement sources.
- The shell consumes one accepted connected context. It never joins separate session flows.
- `AccountManager` owns the source factory. The shell does not create a source.
- A feature model retires with its connected entry, not with a composition disposal.
- The account lifecycle issues the direct-message writer generation. A repository captures it. A
  revoked writer cannot mutate current DM storage. Network requests stay outside the lock.
- The account lifecycle issues the draft writer generation. `DraftActions` captures it. A revoked
  draft writer cannot recreate removed drafts. Removal revokes writers before deleting rows.
- The direct-message composer text stays with the direct-message feature owner. A screen does not
  hold it and does not clear it on Send.
- `DirectMessageViewModel` owns the temporary recipient-finder query, results, loading, and error
  state for one connected session. `DirectMessagesHost` renders the finder as a Material 3 modal
  sheet and exposes only `openRecipientFinder` through `DirectMessagesContract`. Search uses the
  session's injected `SocialSource`; it creates no search owner or cache. The model excludes the
  signed-in account and foreign connection IDs, accepts selection only from current results, and
  routes the selected account through `startConversation(Account)`. Cancel and session retirement
  invalidate lookup generations without clearing the active conversation or editor. The wide
  navigation action remains unwired until the later activation slice. ViewModel and Compose tests
  cover cancellation, result races, account filtering, selection, lookup errors, and host UI.
- The composer editor stays with the composer feature owner. The shell requests transitions and
  places the overlay. It does not hold editor fields.
- Post-action family slots are typed with operation tokens. Only the owning token releases a
  slot. A busy family rejects its second caller without waiting.
- The popup owner and the projection coordinator retire with the connected entry. A retired
  popup has no authority. A retired coordinator delivers nothing.
- Photo Grid keeps independent feed state, selection, preferences, and projection sink from Home.
- Search keeps account and session-revision state outside `FeedViewModel`. Its connected owner and
  projection sink release through `ConnectedEntryStore`, and repeated registration uses one key.
- Home paging demand resets on filter identity and request epoch changes. Only accepted pages
  consume the no-progress budget. The demand blocks while sign-in is required.
- Post commands bind to the validated account set. A removed target reports unavailable at call
  time and revokes queued writes. Failed commands are retained for explicit retry. Command errors
  resolve to resources in the shell.
- The locale owner applies first-upgrade precedence once. A moved repository exports the in-app
  choice to the platform. A moved platform imports the external choice into the repository.
  `MainActivity` serializes each locale decision with its side effect.
- Active-account and selected-account notification settings stay distinct.
- Every source-backed feature receives values from one accepted connected lifetime.
- Shell draft tests use one active writer for each account. `ShellDrafts.forAccount`
  activates the authority and builds the contract before composition. The test holds
  the contract stable across recomposition. A recreation test renews the owner with
  the same store, authority, and generation. The test retires each session scope at
  teardown. Account-null previews use `drafts()` and carry no writer.
- Closing a dirty composer reaches storage, closes the overlay, and permits Profile
  navigation. A clean close creates no draft. A failed save keeps the editor text
  and reports no success. A revoked writer keeps the editor text and stores nothing.
  Recomposition keeps the same writer and stores one draft.
- `ShellNavigator` binds restored navigation state to the origin, protocol, local account ID,
  and durable session revision synchronously before display. Matching restoration preserves
  the Search query, category, safe local page, and remembered panels. A mismatched owner
  clears the account-bound query, category, prefill, page, viewed profile, and selected post.
  The composer overlay is never restored because reply/quote targets do not survive process
  recreation. Old payloads keep safe navigation memory without account-bound text. Malformed payloads
  restore a safe navigator.

## Limits

- Contract verification is JVM and Robolectric only.
- Live-server and physical-device behavior are unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
