# UI and Navigation

Status: current, partial coverage
Owner: UI maintainers
Last reviewed: 2026-10-05
Stale when: A destination, layout policy, restoration rule, or accessibility requirement changes.

Sources: `AGENTS.md`, `ui/`, Compose tests, and instrumented tests.

## Shell route and state ownership

`ShellContent` owns compact and large presentation. `ShellDestinationContent` routes destinations
to the feature shell adapters without owning feature state. Saveable shell holders pass through both presentations.
Search, Photo Grid, direct messages, and profile state remain with their connected feature owners.
The notifications shell adapter consumes the existing notification and DM contracts without unpacking them at the router boundary.
The Home shell adapter forwards the feed contract and shell-owned chip state to `HomeFeed` without owning Home state.
The Search shell adapter forwards the search and Photo Grid contracts to their screens without owning either state.
The Profile shell adapter forwards the Profile contract to `ProfileScreen` without owning Profile state.
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

The vertical New conversation action now opens this recipient finder through the existing DM contract.
It does not open the post composer or toggle back to Notifications.

Sources: `ui/directmessages/DirectMessageViewModel.kt`,
`ui/directmessages/DirectMessagesHost.kt`, `ui/directmessages/DirectMessageRecipientFinder.kt`,
`ui/shell/DirectMessagesContract.kt`, `DirectMessageViewModelTest`, `DirectMessageScreenTest`.
Verification: JVM tests cover cancellation, stale results, account filtering, selection, and lookup
failure. Compose tests cover search, selection, and dismissal. Live-server behavior remains unverified.

## Wide direct-message inbox clearance

`ShellContent` forwards physical-left, physical-right, and bottom obstruction clearances through the existing
destination branch. `DirectMessageInboxScreen` applies them only when the wide layout is active.
Header text, refresh, conversation text, and row click targets stay left of physical-right clearance.
The inbox viewport and row backgrounds keep their full width, so they can continue under floating
chrome. Bottom clearance extends the inbox scroll range instead of padding its viewport. Compact
layout ignores wide-only values and keeps its current system-bar and IME clearance.

Production navigation supplies real clearance to the pane that holds visible controls. Tests
also inject synthetic values in LTR and RTL. They verify the viewport, row underlay, interactive bounds,
the load-more action, final-item reach, shell-branch forwarding, and unchanged compact behavior.
Compose tests verify geometry and both physical edges. Device DM rendering remains unverified.

Sources: `ui/shell/ShellContent.kt`, `ui/shell/ShellDestinationContent.kt`,
`ui/shell/AppNotificationsDestinationContent.kt`, `ui/directmessages/DirectMessageInboxScreen.kt`,
`DirectMessageScreenTest`.

## Wide direct-message conversation clearance

`AppNotificationsDestinationContent` also forwards the shell clearances to `DirectMessageConversationScreen`.
The screen keeps its full-size viewport. Header, notice/error text, bubbles, retry/continue controls,
editor input, and Send clear physical right in both LTR and RTL. Bottom clearance extends only
the transcript scroll range. It does not move the editor or add an editor positioning inset.
Wide `imePadding()` and navigation-bar positioning remain unchanged without fallback. Compact layout ignores
wide-only inputs and keeps its contextual-control positioning policy.

`DirectMessageScreenTest` verifies content bounds, final transcript reach, branch forwarding, retained
editor text, and synthetic IME open/close transitions. It also compares compact bounds with and
without wide clearances. Production inputs are active. The wide compact fallback also supplies separate
`bottomNavigationClearance` to place the editor above the grouped bar. Physical-device IME behavior remains unverified.

Sources: `ui/directmessages/DirectMessageConversationScreen.kt`,
`ui/shell/AppNotificationsDestinationContent.kt`, `DirectMessageScreenTest`.

## Wide Home clearance

Home keeps its full-size pull-to-refresh and list viewports. Post controls, interactive media,
error/sign-in controls, and footer content clear shell-supplied physical right in LTR and RTL.
Transparent outer rows, error surfaces, and dividers keep their existing width beneath floating chrome.
Bottom clearance extends the existing scroll range. The wide timeline dock moves above that obstruction
and clears physical right. Home without a feed contract also clears its dock and empty-state text.

The compact and wide Home rows use one chip renderer. A circular caret uses the unselected chip surface, outline, and icon colors.
It stays in the first logical position outside the scrollable chips. It hides or shows the chips without moving or hiding itself.
The Home timeline selection stays with `ShellNavigator`.
`ShellContent` owns saveable visibility and one `LazyListState` above the layout branches.
Resizing preserves both values.
Changing the timeline scrolls the selected entry into view. Reduced motion skips that animation.
Clearance sets where chips rest, not where they travel (see Chip travel). Compact Home ignores wide inputs and keeps its IME-aware final spacing and shell-owned navigation.
Production clearance is active. The folded outer-screen Home rendering is emulator verified.

Sources: `ui/feed/HomeFeed.kt`, `ui/shell/ShellDestinationContent.kt`, `CategoryChipsGeometryTest`, `HomeClearanceTest`, `HomeFeedTest`, `NavigationTest`.
Focused Compose tests verify bounds, final-content reach, timeline callbacks, retained scroll position,
and compact compatibility. The shared-row test verifies collapse and retained chip position.
Outer-screen Home is emulator verified; hardware tablet and device RTL rendering remain unverified.

## Wide Search clearance

Search keeps its full-size content viewport and both result lists. Hashtag post rows, account result rows,
the continuation control, category chips, and the search field clear shell-supplied physical right in LTR
and RTL. Outer row extents, list dividers, empty states, and loading indicators keep their existing width
beneath floating chrome. Account rows clear through the list inset because the account row is one click target.

Bottom clearance extends the wide result scroll range only. The wide search dock keeps its bottom-start
placement, its spacing, and its measured height. The search field therefore keeps its current position
and its current wide IME behavior, which applies no field inset without compact fallback.
Compact layout ignores wide inputs and keeps its IME-aware control placement and scroll clearance.
The shared chip row includes a hide/show caret.
`SearchScreen` owns saveable visibility and chip scroll state across compact and wide placement.
Search category selection stays with the shell owner. Its circular caret uses the unselected chip surface, outline, and icon colors.
It stays in the first logical position outside the scrollable chips. Clearance sets resting insets only (see Chip travel).
Production clearance is active. Wide compact fallback uses separate positioning clearance to keep the field above navigation.

Sources: `ui/search/SearchScreen.kt`, `ui/shell/ShellDestinationContent.kt`, `SearchClearanceTest`.
Focused Compose tests verify viewport and divider bounds, interaction and dock clearance, final-content reach,
chip selection, retained list position, synthetic IME open and close, branch forwarding, and compact compatibility.
The shared-row test verifies collapse and retained chip position. Device Search rendering remains unverified.

## Wide Photo Grid clearance

Photo Grid keeps its full-size grid viewport. Tiles, the load-older control, the paging-error surface,
the up-to-date label, and the empty state clear shell-supplied physical right in LTR and RTL.
A tile is opaque media and one click target, so the clearance goes into the grid's own content inset
instead of inside each tile. A per-tile inset would leave an untappable strip in every lane.
Adaptive lanes recalculate for the narrower content area. Tile media no longer passes under floating chrome.
The full-screen error state keeps its full-size viewport and clears only its retry content.

Bottom clearance extends the wide grid end spacing. The wide filter-chip dock clears physical right and sits
above that obstruction, the same way the Home timeline dock does. The shared row keeps a circular caret in its first logical position.
The caret stays separate from the scrollable chips.
`PhotoGridScreen` owns saveable visibility and chip scroll state across compact and wide placement.
Feed selection and saved hashtags keep their existing owners. Clearance sets resting insets only (see Chip travel).
Compact Photo Grid ignores wide inputs and keeps contextual-control placement and scroll clearance.
Production clearance is active.

Sources: `ui/photogrid/PhotoGridScreen.kt`, `ui/shell/ShellDestinationContent.kt`, `PhotoGridClearanceTest`.
Focused Compose tests verify tile and reveal bounds, continuation and retry callbacks, final-tile reach, dock
position and clearance, chip and hashtag selection, branch forwarding, retained grid position, and compact compatibility.
The shared-row test verifies collapse and retained chip position. Device Photo Grid rendering remains unverified.

## Wide Notifications clearance

Notifications keeps its full-size wide content container, refresh surface, and list viewport.
The filter/query row moves to the bottom dock. Its circular caret stays in the first logical position,
separate from the scrollable chips.
`NotificationsScreen` owns account-keyed saveable visibility and chip scroll state.
The dock clears physical edges and bottom obstruction. The list end clears the dock and obstruction.
The notification query remains with its existing owner.
The compact filter row stays above navigation and keeps its current IME behavior.
Wide notification cards and controls, the empty state, storage and error retries, the sync-delayed
banner, the paging/load-older control, and the paging error text clear shell-supplied physical right in LTR and RTL.
A notification row always carries a dismiss control and can carry follow-request controls, so it is not a single
opaque target. The row card keeps its full width for visual underlay; its interactive content clears physical right
through an absolute right inset inside the row.

Bottom clearance extends the wide list end spacing past the dock.
Compact Notifications ignores wide inputs and keeps its floating bottom chip row, contextual-control placement, and scroll clearance.
Clearance sets resting insets only (see Chip travel). Production clearance is active.

Sources: `ui/notifications/NotificationsScreen.kt`, `ui/notifications/NotificationRow.kt`,
`ui/shell/AppNotificationsDestinationContent.kt`, `NotificationsClearanceTest`.
Focused Compose tests verify viewport and row bounds, row-surface underlay, dismiss and follow-request controls,
load-older and retry callbacks, final-item reach, retained filter selection and notification-list position, branch forwarding, and compact compatibility.
The shared-row test verifies collapse and retained chip position.
The folded emulator verifies the bottom dock and loaded-row clearance.
Hardware tablet and device RTL rendering remain unverified.

## Wide Profile clearance

Profile keeps its full-size `profile_content` and `profile_timeline_list` viewports. Interactive list content
clears shell-supplied physical right in LTR and RTL. That content is the post rows, the pinned rows, the Featured
title, the inline category chip row, the details item, the loading, empty, error, and inline-error surfaces, the
loading-more indicator, and the load-older or up-to-date footer. Item dividers keep the full width beneath floating
chrome. A profile row is a transparent post column, not an opaque card, so the clearance goes into the row content
instead of a list-wide inset.

The wide category dock keeps its bottom-start placement and its own spacing. It clears physical right and sits above
supplied bottom obstruction. Wide end-of-list clearance adds that obstruction to `LargeBottomDockClearance`.

The wide summary layout has two mirrored columns. LTR places the summary left and timeline right; RTL swaps them.
Each column takes only the physical-edge clearance that it reaches. Without the summary, the timeline takes both edges.
The summary scroll range ends past the category dock, so its final details stay reachable.

Compact Profile ignores wide inputs and keeps its measured end clearance and floating chip row.
The shared chip row includes a circular caret in its first logical position, separate from the scrollable chips.
`ProfileScreen` owns profile-keyed saveable visibility and chip scroll state across compact and wide placement.
Category selection and profile actions keep their existing owners. Clearance sets resting insets only (see Chip travel).
Production clearance is active.

Compact-wide uses the same one-column header and timeline hierarchy as mobile Profile through
`ProfileTimelinePresentation`. It keeps a wide bottom category dock, while the shell keeps the
contextual action beside floating navigation. This button placement is the only permitted difference
from mobile Profile. Expanded tablet Profile keeps `ProfileLargePresentation` and its two-column
summary. `WideNavigationTest` verifies the compact-wide shell, content bounds, dock
interaction, and shell-owned Edit profile action at 445 × 704 dp.

Sources: `ui/profile/ProfileScreen.kt`, `ui/profile/ProfileTimelinePresentation.kt`,
`ui/profile/ProfileLargePresentation.kt`, `ui/profile/ProfileTimelineList.kt`,
`ui/shell/ShellContent.kt`, `ui/shell/ShellDestinationContent.kt`, `ProfileClearanceTest`,
`WideNavigationTest`.
Focused Compose tests verify viewport and divider bounds, mirrored wide columns, header and row control bounds,
dock clearance, final-row and footer reach, load-older and retry callbacks, branch forwarding, retained category
selection and profile-list position, and compact compatibility. The shared-row test verifies collapse and chip-position retention.
Focused compact-wide Profile Compose coverage passes. Device Profile rendering remains unverified.

All seven wide surfaces also clear shell-supplied physical left through the same content owners described above.
Physical edges never reverse with layout direction. The viewport remains full size; clearance stays inside interactive or scroll content.

## Forced layout direction

The Display settings page has one switch that forces the layout direction. The label names the
direction that turning the switch on produces, so it reads `Force RTL Layout` in a left-to-right
device and `Force LTR Layout` in a right-to-left device. The label changes after the user switches.
The switch is on when a direction is stored. Turning it off returns to the device direction.

The Display page scrolls vertically, so every item stays reachable on a short screen. Its scroll
position resets each time the page opens.

The switch changes layout only. It does not change the application language, and every user-visible
string stays as the language provides it. Beeline has no right-to-left translations yet, so the
setting changes the layout without changing the text.

A user without the setting, or with a value from a future version, keeps the device direction.

Sources: `ui/layout/LayoutDirectionPolicy.kt`, `ui/settings/DisplaySettingsScreen.kt`,
`ui/ConnectedApp.kt`, `domain/AppPreferences.kt`.

Limits: device RTL, TalkBack, and physical hinge coordinates remain unverified.
Compose tests verify physical top-left alignment and absolute offsets for panes and floating navigation in both directions.

## Adaptive navigation anchors

The Display page stores independent physical-edge choices for tablet and compact-wide navigation.
Tablet navigation defaults to physical left. Compact-wide navigation defaults to physical right.
Missing or unknown stored values keep those defaults. The choices do not follow forced layout direction.

`PalustrisApp` uses the tablet choice in expanded mode. It uses the compact-wide choice in compact
and single-pane modes. The selected edge controls both the floating stack placement and safe-region
selection. Compact-narrow navigation keeps its existing placement.

On a split tablet, `LargeScreenShell` clears whichever pane the floating stack overlaps. Detail
content keeps its full viewport. `SinglePostScreen` adds the physical clearance to lazy-list content,
so the final viewport does not shrink.

Sources: `domain/AppPreferences.kt`, `data/preferences/FileAppPreferencesRepository.kt`,
`ui/settings/DisplaySettingsScreen.kt`, `ui/PalustrisApp.kt`, `ui/large/LargeScreenShell.kt`,
`ui/posts/SinglePostScreen.kt`.

Verification: preference, settings, adaptive-navigation, and detail-screen Compose tests cover
defaults, persistence, independent choices, LTR/RTL placement, pane clearance, and viewport bounds.
Physical tablet rendering, device RTL, and TalkBack remain unverified.

## Large panes and folding coordinates

`LargeLayoutMode` chooses compact, single-pane, or expanded-pane behavior from 600/840 dp window-width cutoffs.
`LargeScreenShell` applies system-bar insets around one destination host. Floating navigation does not subtract a rail from pane geometry.

Material 3 Adaptive supplies folding-feature bounds in window coordinates. The shell translates
those bounds into the pane-content pixel space. Content starts after the physical left/top system inset in both directions.
Separating and occluding features split safe regions; non-separating creases do not.

Sources: `ui/large/LargeLayoutMode.kt`, `ui/large/LargeScreenShell.kt`,
`LargeLayoutModeTest`.
Verification: JVM tests characterize LTR/RTL coordinate translation and pane behavior. Device
hinge coordinates remain unverified.

`calculateNavigationFit` owns permanent navigation fit. `NavigationFitWindowInsets` reads physical system-bar,
display-cutout, mandatory-gesture, and hinge geometry, then converts window pixels to dp.
Overlapping edge insets merge by their maximum. Nonmandatory back-gesture strips do not exclude visible navigation.
Vertical navigation requires the full controls (64 dp reserved width, 360 dp height) plus 360 dp useful content width.
Compact-wide reserves the taller 424 dp stack (capsule, composer action, tab caret, and both gaps) plus the same content width.
Otherwise it keeps compact navigation. IME height does not affect permanent presentation. Pane and detail selection remain independent.
`LargeLayoutModeTest` and `AdaptiveNavigationTest` cover fit, safe placement, hinge coordinates, density conversion, and RTL physical bounds.

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
The production vertical capsule is 56 × 296 dp, with six 48 dp slots and 4 dp padding at each end.
Its separate 56 dp action sits 8 dp below it. The stack remains fixed when Profile has no contextual action.
`LargeFloatingNavigation` replaces the rail and uses physical placement independent of RTL.
Outer-screen Home rendering is emulator verified. TalkBack and hardware tablet rendering remain unverified.

### Compact-wide tab caret

Compact-wide hides the inline chip caret and shows a 56 dp contextual caret below the composer action.
The vertical order is navigation capsule, composer action, then tab caret. The stack bottom-anchors
from the bottom safe edge and always reserves the caret slot, so hiding the caret never moves the
capsule or the action. The caret shares the feature-owned chip visibility state; it introduces no
second expanded/collapsed state. Screens without tab chips show no caret. The tab-chip dock keeps its
physical-edge clearance as resting insets: the last chip rests clear of the caret and capsule, and the path continues beneath them.

Planned polish: a future pass adds an 8 dp wide gradient blur fadeout between the tab-chip area and
the contextual caret. This task intentionally does not implement that transition.

Sources: `ui/large/CompactWideTabCaret.kt`, `ui/large/LargeFloatingNavigation.kt`,
`ui/large/LargeScreenShell.kt`, `ui/components/CategoryChips.kt`, `CompactWideTabCaretTest`.
Verification: Compose tests cover contextual toggle, caret absence without navigation shift,
bottom anchoring, chip clearance, and retained inline presentation. Device rendering remains unverified.

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
This behavior is active. Fit selects navigation independently of width-based panes and back precedence.
Expanded layouts default to physical left; other fitting windows default to physical right. The independent Display preferences described in [Adaptive navigation anchors](#adaptive-navigation-anchors) override those defaults.
Home, Search, Photo Grid, and Notifications offer Compose; Direct Messages offers New conversation; Profile shares edit/follow/unfollow policy.
When fit fails inside a wide layout, the grouped compact bar remains available and content clears it.
Search and the DM editor also receive separate fallback positioning clearance, including the compact IME base.
The folded emulator shows vertical navigation at 445 × 704 dp. A 900 × 900 dp tablet fixture passes Compose tests.
Narrow-phone, hardware tablet, device RTL, and physical hinge acceptance remain separate verification limits.

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

## Chip travel

`DestinationChipRow` fills its container. Its chip viewport reaches the far physical display edge and passes
beneath floating navigation there. `leftInset` and `rightInset` are physical resting insets (navigation
clearance plus an edge gap; compact rows pass 16 dp). The first and last chips rest clear of them, and the
inline caret sits inside the logical-start inset. The caret occupies the first logical position, so it moves
to the physical right in RTL by decision. Chips never scroll beneath the caret, so it cannot steal a tap.
With the caret hidden (compact-wide), the path starts at the display edge. `LargeBottomDock` spans its
container and provides the wide insets through `LocalLargeDockEdgeInsets`. The Search field keeps its 520 dp
bound and clears the same insets. Tab keys are stable IDs for every destination.
On Home, Search, Photo Grid, Notifications, and Profile, `LargeScreenShell(chipEdgeBleed = true)` widens the primary
pane by the outer margin (`LARGE_OUTER_MARGIN_DP`, 16 dp) on each side that touches the display, so the row reaches
the physical edge. `LocalPaneEdgeBleed` carries the amount. Non-chip content follows the margin again: the shell
pads other destinations, and chip destinations restore it through their clearance. A detail-only pane gets no bleed.
Tests: `CategoryChipsGeometryTest`, the destination `*ClearanceTest` suites, `NavigationTest`, `WideNavigationTest`.
Chips scrolled under floating chrome by a semantic scroll action are not held clear; only resting chips are.
Device rendering, TalkBack focus scrolling, and RTL locale behavior remain unverified.

## IME and large text (Phase 4E1)

Wide Search lifts its dock, and the end of its results, by the IME height that the pane's system-bar inset does not already clear (`max(bottomNavigationClearance, ime - systemBars)`). The viewport keeps its size; compact fallback clearance already includes the IME. Search empty states take the dock's end clearance. Device (emulator, API 37, compact-wide): at 100% and 200% font the field and chips sit above the keyboard. Tests: `SearchClearanceTest` (IME lift, 200% font scale). The compact capsule, other docks, and physical devices remain unmeasured.

## Haptic events

`ui/motion/HapticEvents.kt` is the only haptic authority. `HapticEvent` names five moments: `Selection`, `Commit`,
`Threshold`, `LongPress`, and `DestructiveConfirm`. `platformConstant` maps each to a platform constant that exists on
the running API (API 29 uses `KEYBOARD_TAP` and `LONG_PRESS` where `CONFIRM` and `REJECT` need API 30).
`PalustrisTheme` provides `LocalPalustrisHaptics`; the default is silent. The platform call honors the system
touch-feedback setting, so Beeline adds no haptic preference. Fire an event once per discrete moment, never per drag frame.
Sources: `ui/motion/HapticEvents.kt`, `HapticEventsTest`. Device haptic feel is unverified.
