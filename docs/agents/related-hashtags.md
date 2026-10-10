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

## Invariants

- The asset is `app/src/main/assets/hashtag-catalog.json`, version 1. A missing or invalid file gives an empty catalog.
- Catalog names are identities: lowercase with `Locale.ROOT`. Accent variants are separate members.
- A hashtag in an `amb` list never expands, and never joins another merge.
- Only `h` and `s` members start a merge. `n` members join a merge. `r` members are only suggested.
- A group never merges members of the groups in its `x` list. It only suggests the heads of its `c` groups.
- The language policy filters names that the app shows. It never filters merged results.
- A missing `combineRelatedHashtags` key reads `true`.
- The head ranks first. Heads cover their first language, so the best synonym of each other language follows.

## Search wiring

`MainActivity` injects `HashtagExpander`. `ConnectedApp` builds one `HashtagExpansionInput` from the setting and the display language. It passes the input through `ConnectedSessionHost` to `SearchHost` and `PhotoGridHost`. The hosts hand the owners a provider of the latest input. Each controller reads it only when a search or feed starts.

## Tests

`HashtagCatalogRepositoryTest`, `HashtagExpanderTest`, `AppPreferencesRepositoryTest`, and `LocalizationResourceTest`.
They read the real asset from the source tree.

## Limits

The catalog data is unreviewed. Treat a build as a test build until the owner reviews it.
