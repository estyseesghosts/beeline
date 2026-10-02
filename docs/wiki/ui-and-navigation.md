# UI and Navigation

Status: current, partial coverage
Owner: UI maintainers
Last reviewed: 2026-10-02
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
