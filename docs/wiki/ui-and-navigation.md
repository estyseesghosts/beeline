# UI and Navigation

Status: current, partial coverage
Owner: UI maintainers
Last reviewed: 2026-10-05
Stale when: A destination, layout policy, restoration rule, or accessibility requirement changes.

Sources: `AGENTS.md`, `ui/`, Compose tests, and instrumented tests.

## Shell route and state ownership

`ShellContent` owns compact and large presentation. `ShellDestinationContent` renders destination
branches without owning feature state. Saveable shell holders pass through both presentations.
Search, Photo Grid, direct messages, and profile state remain with their connected feature owners.
The connected session host owns post projection and validates account and session revision before
delivery. Characterization tests cover compact and large Search routes and profile editor and pager
continuity.

Sources: `ui/shell/ShellContent.kt`, `ui/shell/ShellDestinationContent.kt`,
`ui/session/ConnectedSessionHost.kt`, `ShellCharacterizationTest`, `ProfileViewModelTest`.

## Search lifetime

Search uses one `SearchOwner` for each connected account session. `SearchHost` registers the owner
with `ConnectedEntryStore` and releases it when the session retires. Route changes and recomposition
do not create another owner. Search state does not depend on Home or Photo Grid state.

The owner validates account and session revision before applying post projections. Its sink joins
`PostProjectionCoordinator` while the owner is active. The coordinator forwards external and accepted
publication updates without changing route or saved-state restoration.

Sources: `ui/search/SearchOwner.kt`, `ui/search/SearchHost.kt`, `ui/shell/PostProjectionCoordinator.kt`,
`SearchOwnerTest`, `ConnectedEntryStoreTest`.

## Navigation restoration

`ShellNavigator` owns shell navigation and selection behind one boundary. Its versioned saver
binds restored state to the origin, protocol, local account ID, and durable session revision.
Matching restoration preserves the Search query, category, safe local page, and remembered
Search/Photo Grid and Notification/DM panels. A mismatched owner clears the account-bound
query, category, prefill, and page synchronously before display; destination, timeline,
panels, non-composer overlays, sheets, and visibility stay. The composer overlay is never
restored because reply/quote targets do not survive process recreation. Old or malformed payloads restore safe
navigation memory without account-bound text.

Sources: `ui/navigation/ShellNavigator.kt`, `ui/PalustrisApp.kt`,
`ShellNavigatorTest`, `ShellNavigatorRestorationTest`, `SearchPanelRestorationTest`,
`NavigationTest`.

## Direct-message recipient selection

`DirectMessagesHost` keeps the inbox, conversation, editor, and recipient finder with the
session-keyed `DirectMessageViewModel`. The host presents recipient selection in a cancelable modal
sheet. Search uses the connected session's `SocialSource.searchAccounts` request. The finder excludes
the signed-in account and results with a foreign connection ID. Selecting a current result calls the
existing `startConversation(Account)` action. Canceling the finder preserves the selected
conversation and editor. Request generations reject results after a query change, dismissal, or
session retirement.

The wide navigation action is not connected to this contract yet. Phase 4C activates that callback
after navigation and destination-clearance gates pass.

Sources: `ui/directmessages/DirectMessageViewModel.kt`,
`ui/directmessages/DirectMessagesHost.kt`, `ui/directmessages/DirectMessageRecipientFinder.kt`,
`ui/shell/DirectMessagesContract.kt`, `DirectMessageViewModelTest`, `DirectMessageScreenTest`.
Verification: JVM tests cover cancellation, stale results, account filtering, selection, and lookup
failure. Compose tests cover search, selection, and dismissal. Live-server behavior remains unverified.

## Wide direct-message inbox clearance

`ShellContent` forwards physical-right and bottom obstruction clearances through the existing
destination branch. `DirectMessageInboxScreen` applies them only when the wide layout is active.
Header text, refresh, conversation text, and row click targets stay left of physical-right clearance.
The inbox viewport and row backgrounds keep their full width, so they can continue under floating
chrome. Bottom clearance extends the inbox scroll range instead of padding its viewport. Compact
layout ignores both wide-only values and keeps its current system-bar and IME clearance.

The application still supplies zero clearance because floating navigation remains inactive. Tests
inject synthetic values in LTR and RTL. They verify the viewport, row underlay, interactive bounds,
the load-more action, final-item reach, shell-branch forwarding, and unchanged compact behavior.
These tests do not verify production geometry or physical rendering.

Sources: `ui/shell/ShellContent.kt`, `ui/shell/ShellDestinationContent.kt`,
`ui/shell/AppNotificationsDestinationContent.kt`, `ui/directmessages/DirectMessageInboxScreen.kt`,
`DirectMessageScreenTest`.

## Wide direct-message conversation clearance

`AppNotificationsDestinationContent` also forwards the shell clearances to `DirectMessageConversationScreen`.
The screen keeps its full-size viewport. Header, notice/error text, bubbles, retry/continue controls,
editor input, and Send clear physical right in both LTR and RTL. Bottom clearance extends only
the transcript scroll range. It does not move the editor or add an editor positioning inset.
Wide `imePadding()` and navigation-bar positioning remain unchanged. Compact layout ignores both
wide-only inputs and keeps its contextual-control positioning policy.

`DirectMessageScreenTest` verifies content bounds, final transcript reach, branch forwarding, retained
editor text, and synthetic IME open/close transitions. It also compares compact bounds with and
without wide clearances. Production inputs remain zero because floating navigation is inactive.
Physical-device IME behavior and production obstruction geometry remain unverified.

Sources: `ui/directmessages/DirectMessageConversationScreen.kt`,
`ui/shell/AppNotificationsDestinationContent.kt`, `DirectMessageScreenTest`.

## Wide Home clearance

Home keeps its full-size pull-to-refresh and list viewports. Post controls, interactive media,
error/sign-in controls, and footer content clear shell-supplied physical right in LTR and RTL.
Transparent outer rows, error surfaces, and dividers keep their existing width beneath floating chrome.
Bottom clearance extends the existing scroll range. The wide timeline dock moves above that obstruction
and clears physical right. Home without a feed contract also clears its dock and empty-state text.

Timeline chips keep their existing scroll and selection behavior. Clearance does not set chip travel.
Compact Home ignores both wide inputs. Its shell-owned tabs, navigation, and IME-aware final spacing remain unchanged.
The application still supplies zero values because floating navigation is inactive.

Sources: `ui/feed/HomeFeed.kt`, `ui/shell/ShellDestinationContent.kt`, `HomeClearanceTest`, `HomeFeedTest`, `NavigationTest`.
Focused Compose tests verify bounds, final-content reach, timeline callbacks, retained scroll position,
and compact compatibility. Device rendering and production obstruction geometry remain unverified.

## Wide Search clearance

Search keeps its full-size content viewport and both result lists. Hashtag post rows, account result rows,
the continuation control, category chips, and the search field clear shell-supplied physical right in LTR
and RTL. Outer row extents, list dividers, empty states, and loading indicators keep their existing width
beneath floating chrome. Account rows clear through the list inset because the account row is one click target.

Bottom clearance extends the wide result scroll range only. The wide search dock keeps its bottom-start
placement, its spacing, and its measured height. The search field therefore keeps its current position
and its current wide IME behavior, which applies no field inset.
Compact layout ignores both wide inputs and keeps its IME-aware control placement and scroll clearance.
Category chips keep their own scrolling and selection behavior. Clearance does not set chip travel.
The application still supplies zero values because floating navigation is inactive.

Sources: `ui/search/SearchScreen.kt`, `ui/shell/ShellDestinationContent.kt`, `SearchClearanceTest`.
Focused Compose tests verify viewport and divider bounds, interaction and dock clearance, final-content reach,
chip selection, retained list position, synthetic IME open and close, branch forwarding, and compact
compatibility. Device rendering and production obstruction geometry remain unverified.

## Wide Photo Grid clearance

Photo Grid keeps its full-size grid viewport. Tiles, the load-older control, the paging-error surface,
the up-to-date label, and the empty state clear shell-supplied physical right in LTR and RTL.
A tile is opaque media and one click target, so the clearance goes into the grid's own content inset
instead of inside each tile. A per-tile inset would leave an untappable strip in every lane.
Adaptive lanes recalculate for the narrower content area. Tile media no longer passes under floating chrome.
The full-screen error state keeps its full-size viewport and clears only its retry content.

Bottom clearance extends the wide grid end spacing. The wide filter-chip dock clears physical right and sits
above that obstruction, the same way the Home timeline dock does. Chips keep their own scrolling and selection
behavior, and clearance does not set chip travel. Compact Photo Grid ignores both wide inputs and keeps its
contextual-control placement and scroll clearance. The application still supplies zero values because floating
navigation is inactive.

Sources: `ui/photogrid/PhotoGridScreen.kt`, `ui/shell/ShellDestinationContent.kt`, `PhotoGridClearanceTest`.
Focused Compose tests verify tile and reveal bounds, continuation and retry callbacks, final-tile reach, dock
position and clearance, chip and hashtag selection, branch forwarding, retained grid position, and compact
compatibility. Device rendering and production obstruction geometry remain unverified.

## Wide Notifications clearance

Notifications keeps its full-size wide `Column`, refresh surface, and list viewport. The top filter/query chip row,
notification row cards and their controls, the empty state, the storage/error retry controls, the sync-delayed
banner, the paging/load-older control, and the paging error text clear shell-supplied physical right in LTR and RTL.
A notification row always carries a dismiss control and can carry follow-request controls, so it is not a single
opaque target. The row card keeps its full width for visual underlay; its interactive content clears physical right
through an absolute right inset inside the row.

Wide layout has no bottom dock because its chip row sits at the top. Bottom clearance extends the wide list end
spacing only. Compact Notifications ignores both wide inputs and keeps its floating bottom chip row, contextual-control
placement, and scroll clearance. Chips keep their own scrolling and selection behavior, and clearance does not set
chip travel. The application still supplies zero values because floating navigation is inactive.

Sources: `ui/notifications/NotificationsScreen.kt`, `ui/notifications/NotificationRow.kt`,
`ui/shell/AppNotificationsDestinationContent.kt`, `NotificationsClearanceTest`.
Focused Compose tests verify viewport and row bounds, row-surface underlay, dismiss and follow-request controls and
callbacks, load-older and retry callbacks, final-item reach, retained filter selection and scroll position, branch
forwarding, and compact compatibility. Device rendering and production obstruction geometry remain unverified.

## Large panes and folding coordinates

`LargeLayoutMode` currently chooses compact, single-pane, or expanded-pane behavior from
600/840 dp window-width cutoffs. `LargeScreenShell` applies system-bar insets, then places the
80 dp rail beside one destination host. Pane geometry uses the remaining content bounds.

Material 3 Adaptive supplies folding-feature bounds in window coordinates. The shell translates
those bounds into the pane-content pixel space. LTR content starts after the physical left inset
and rail. RTL content starts after the physical left inset because the `Row` places the rail on
the right. Separating and occluding features split safe regions; non-separating creases do not.

Sources: `ui/large/LargeLayoutMode.kt`, `ui/large/LargeScreenShell.kt`,
`LargeLayoutModeTest`.
Verification: JVM tests characterize LTR/RTL coordinate translation and pane behavior. Device
hinge coordinates remain unverified. Cutout, gesture, taskbar, stable-height, and production
navigation-fit policy are not implemented yet.

## Back navigation

`topSurfaceForBack` in `ui/navigation/ShellBackPolicy.kt` owns the dismissal order. The media
viewer owns back while it is open. A profile image, the emoji picker, and the post action
bubble dismiss first. In a wide layout, the notification settings, composer, and edit-profile
overlays dismiss before the selected post. In a compact layout, the selected post dismisses
before those overlays. A notification route and a local page dismiss before the shell returns
Home. Back does not navigate because an animation completes.

Sources: `ui/navigation/ShellBackPolicy.kt`, `ui/PalustrisApp.kt`, `ShellBackPolicyTest`,
`ShellNavigatorTest`, `NavigationTest`.

## Compact floating selection

The compact capsule keeps four fixed grouped positions and a separate contextual action.
Search and Notifications show their remembered child icons while inactive.
`NavigationButton` shares icon tint, scale, press treatment, and tab semantics.
`NavigationCapsule` shares material and one traveling selection indicator across horizontal and vertical presentation.
`WideNavigationPresentation` arranges the six direct targets vertically with the same buttons and capsule.
It receives selection, bounds, and callbacks from its caller. The Profile target shows the account avatar;
tap selects Profile and long press requests account switching. `ContextualNavigationActionButton` renders
the shared contextual action. The compact bar supplies grouped items, profile content, and callbacks.
Selection changes immediately; the animation does not navigate. Reduced motion moves the indicator
without a spatial transition.
The approved capsule stays 212 × 56 dp, with four 48 dp targets.
RTL reverses logical slot placement without changing the selected destination.

Sources: `ui/navigation/CompactAppNavigation.kt`, `ui/navigation/NavigationPresentation.kt`,
`ui/navigation/NavigationItem.kt`, `CompactNavigationSelectionTest`, `NavigationPresentationTest`, `NavigationTest`.
Verification: Compose tests cover stable bounds, interrupted selection, RTL, reduced motion, and compact 200% text.
A six-target vertical test uses the shared presenter. Its dimensions are test inputs, not approved production geometry.
The production wide rail remains unchanged. Adaptive activation and rail replacement remain planned.
Physical rendering and TalkBack remain unverified.

### Compact IME placement and content clearance

Compact navigation uses the greater of the IME and system-bar bottom insets.
Search, Photo Grid, notification filters, and profile categories stay above navigation through the same geometry owner.
Home tabs move with navigation. Home still hides its controls during forward scrolling.
Destination scroll content carries final-item clearance. The shell does not pad the full viewport for floating controls.
The DM editor reserves navigation and IME space in its existing column.
The thread keeps normal content spacing because the editor already occupies space below it.
The query, editor text, navigation state, and callbacks keep their existing owners.

Sources: `ui/layout/CompactOverlayMetrics.kt`, `ui/shell/ShellContent.kt`,
`ui/directmessages/DirectMessageConversationScreen.kt`, `NavigationTest`, `DirectMessageScreenTest`.
Verification: Compose tests cover Search placement through IME dismissal and final-content clearance with synthetic IME insets.
Tests cover Home, Search, Photo Grid, Notifications, Profile, and the DM conversation.
Measured high-font dock clearance remains Phase 4E work. Device rendering remains unverified.

### Required adaptive end state

Maintainer clarification, 2026-10-04: compact-narrow, compact-wide, and large/tablet must share underlying navigation components.
Compact-narrow retains the existing four-button grouped bar and existing narrow layout.
Compact-wide and tablet use the same vertical six-button presentation: Home, Search, Photo Grid,
Notifications, Direct Messages, and Profile. Tablet detail panes remain independent of navigation presentation.
This is required behavior, not an implementation claim. The current width-only policy does not establish compact-wide support.
The available emulator simulates compact-wide; narrow-phone acceptance needs separate evidence.

## Draft restoration

Closing a dirty composer saves the draft and closes the overlay. The saved text
appears under Profile drafts. A clean close saves nothing. A failed save keeps
the editor text and shows the save error. A revoked writer saves nothing. A saved
draft survives activity recreation and supports deletion. Recomposition keeps the
same writer and creates one draft.

Sources: `ui/composer/ComposerOwner.kt`, `ui/PalustrisApp.kt`,
`app/src/test/java/me/foxtails/palustris/ui/shell/AppShellFixtures.kt`,
`app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt`.

## Purpose

<!-- Explain the visible application structure for contributors. -->

## Entries

<!-- Add compact and wide layouts, destinations, Photo Grid, profiles, threads, restoration, and accessibility. -->

## Profiles

A profile shows a category row. The row order is Featured, Posts, Replies,
Media, Reposts, Liked, and Show more. The Posts, Replies, Media, and Reposts
feeds are protocol-neutral profile timelines.

The Featured tab leads only when the profile has more than one pinned post. A
single pinned post appears at the top of the Posts feed with no Featured tab.
Pinned posts do not appear in the other profile feeds. Featured renders the
pinned posts the profile already loaded, so it makes no new request.

The Liked tab shows Mastodon favourites or Misskey reactions. It appears for
the signed-in account on either protocol and for another Misskey account. It
does not appear for another Mastodon account, because Mastodon exposes
favourites only to the signed-in account.

Sources: `ui/profile/ProfileCategory.kt`, `ui/profile/ProfileTimelineList.kt`,
`ui/profile/ProfileViewModel.kt`, `data/mastodon/MastodonProfileService.kt`,
`data/misskey/MisskeyProfileService.kt`, `ProfileScreenTest`.

## Photo Grid Detail Media

Photo Grid detail sizes the photo pager from known attachment dimensions
in compact and wide layouts. The pager keeps full width. The pager keeps
`ContentScale.Fit`. The post body stays below the media.

The height follows the active photo aspect. Square through 4:5 uses the
natural height. Media at or wider than 16:9 uses the natural short height.
The short height prevents vertical letterboxing. Media taller than 4:5
stays capped at the 4:5 viewport. A short viewport clamps the height to
the available space. The calculation reserves 64 dp for the header and
200 dp for post content.

Unknown or invalid dimensions use the square to 5:4 viewport fallback.
Multi-photo posts share one stable height across all pages. The shared
height is the smallest aspect-aware height. The shared height prevents
vertical letterboxing for the active photo. Horizontal letterboxing on
taller pages is the accepted tradeoff.

Sources: `ui/posts/SinglePostScreen.kt`, `ui/photogrid/PhotoPagerSizing.kt`,
`SinglePostScreenTest`.

## Post Quote Previews

Rows and Photo Grid detail use one quote preview presentation. The precedence is parent content-warning rules, server-hidden content, a muted-tag warning, the quote content warning, then the quote body. Server-hidden quotes keep a non-revealable placeholder. A quote with an account-local muted hashtag shows a revealable `muted word: #tag` warning before its text or media is composed. After reveal, a quote content warning still hides its body. Muted words remain deferred because no client-side matched-word data exists; re-entry requires approved adapter and domain work with Mastodon and Misskey semantics verified independently. The hidden-content Remove preference applies to parent posts and Photo Grid filtering, not quote cards.

Sources: `ui/posts/QuotePreviewCard.kt`, `ui/posts/PostRow.kt`,
`ui/posts/SinglePostScreen.kt`, `SinglePostScreenTest`.

## Post favourite artwork

The primary favourite action uses a per-protocol artwork policy. Misskey presents
a heart; Mastodon presents a star. `SocialSource.favouriteArtworkStyle` carries the
policy from the adapter through the feed presentation. `FeedViewModel` reads it
from the source, `FeedHost` puts it on `HomeFeedUiState`, `HomeFeed` passes it in
`PostRowPresentation`, and `PostRow`/`InteractionRow` select the icon with
`favouriteIconFor`. It is not a feature capability, and the post row does not
branch on `Protocol`. `SocialSourceContractTest` declares the expected style in each
protocol's test, so the shared contract base never asserts one protocol's artwork
as the default.

Sources: `domain/SocialSource.kt`, `domain/FavouriteArtworkStyle.kt`,
`ui/feed/FeedViewModel.kt`, `ui/feed/FeedHost.kt`, `ui/feed/HomeFeed.kt`,
`ui/posts/PostRow.kt`, `ui/posts/PostInteractionPresentation.kt`,
`data/misskey/MisskeySource.kt`, `data/mastodon/MastodonSource.kt`,
`SocialSourceContractTest`, `MastodonSourceContractTest`,
`PostRowFavouriteArtworkTest`.
