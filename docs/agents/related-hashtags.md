# Related hashtags ownership

Status: current  
Owner: Maintainers  
Last reviewed: 2026-10-10  
Stale when: The catalog format, the expansion rules, or the discovery owners change.

Authority: [plan](../related-tags.md), [task state](tasks/related-hashtags.md), and the source below.

## Owners

| Responsibility | Owner | Lifetime |
|---|---|---|
| Immutable groups, identity index, prefix lookup | `domain/hashtags/HashtagCatalog` | Process |
| Reading the asset once | `data/hashtags/HashtagCatalogRepository` (Hilt singleton) | Process |
| Expansion, ranking, related hashtags | `domain/hashtags/HashtagExpander` | Stateless |
| Display language to allowed codes and scripts | `domain/hashtags/HashtagLanguagePolicy` | Stateless |
| The "Combine related hashtags" setting | `AppPreferences.combineRelatedHashtags` | Persisted, global |
| Composer hashtag chips | `ui/composer/ComposerHashtags` (state), `HashtagToken.kt` (token rule) | One composer body |
| Trending hashtags, popular accounts and typed suggestions for Search | `ui/search/SearchExploreController`, owned by `SearchOwner` | One `SearchOwner`; `release()` stops it and clears the suggestion cache |

## Invariants

- The asset is `app/src/main/assets/hashtag-catalog.json`, version 1. A missing or invalid file gives an empty catalog.
- Catalog names are identities: lowercase with `Locale.ROOT`. Accent variants are separate members.
- A hashtag in an `amb` list never expands, and never joins another merge.
- Only `h` and `s` members start a merge. `n` members join a merge. `r` members are only suggested.
- A group never merges members of the groups in its `x` list. It only suggests the heads of its `c` groups.
- The language policy filters names that the app shows. It never filters merged results.
- A missing `combineRelatedHashtags` key reads `true`.
- The head ranks first. Heads cover their first language, so the best synonym of each other language follows.

## Discovery

`SocialSource` gains `trendingHashtags`, `suggestHashtags` and `popularAccounts`. They default to unsupported. `MastodonDiscoveryService` and `MisskeyDiscoveryService` hold the requests. `MastodonMapper` and `MisskeyMapper` map the JSON, and they skip a malformed item. There is no capability probe: a caller treats a failure or an empty list as "hide the section".
`HashtagSuggestionService` (domain) merges catalog and server suggestions. It is built for one account session, holds a 50-entry, 5-minute in-memory cache keyed by account and prefix, and debounces 250 ms in `suggestions(prefixes, limit)`. It sends only the hashtag fragment. A server failure or a server that needs more than 2 seconds returns the catalog matches. A late answer is dropped and never cached. The owner of the session must call `release()`. `SearchOwner` builds one for the account and releases it with the owner. `ConnectedSessionHost` builds a second one for the composer through `rememberComposerHashtagSuggestions`, and releases it through `ConnectedEntryStore`.

## Search wiring

`MainActivity` injects `HashtagExpander`. `ConnectedApp` builds one `HashtagExpansionInput` from the setting and the display language. It passes the input through `ConnectedSessionHost` to `SearchHost` and `PhotoGridHost`. The hosts hand the owners a provider of the latest input. Each controller reads it only when a search or feed starts.

`HashtagExpansionInput` exposes the catalog and the language policy, so `SearchOwner` builds its suggestion service from the same input. It builds no second catalog.
`SearchContract.explore` carries `SearchExploreState` (trending, popular accounts, suggestions) to `SearchScreen`. `SearchExploreActions` has `loadTrending`, `loadPopularAccounts` and `suggestHashtags`. Each default does nothing.

## Composer

`hashtagTokenAt` finds the token at the cursor with the boundary rules of `PostTextPresentation` (`isHashtagBoundary` and `HASHTAG_TOKEN` are internal for this). `ComposerEntryRow` reports it through `ComposerEntryActions.hashtags`. The body shows the chips from `ComposerContract.hashtagSuggestions`, and the row applies a tap with `CursorField.replace`. Only the fragment before the cursor goes to the server. The warning field reports no token.

## Search screen decisions

- Related chips show whenever `relatedTags` is not empty. The "Includes" line shows only when `combinedTags` is not empty.
- A failed or empty discovery list keeps the old prompt and shows no error.

## Tests

`HashtagCatalogRepositoryTest`, `HashtagExpanderTest`, `AppPreferencesRepositoryTest`, `LocalizationResourceTest`, `SearchExploreControllerTest`, `SearchDiscoveryTest`, `SearchClearanceTest`, `HashtagTokenTest`, and `ComposerHashtagTest`.
They read the real asset from the source tree.

## Limits

The catalog data is unreviewed. Treat a build as a test build until the owner reviews it.

## Release gate

The owner must review the catalog before a release: at least the largest groups and every hashtag in `amb`. Wrong cross-language merges show up with the setting on by default.
