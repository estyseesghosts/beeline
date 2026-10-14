# Content filters ownership

Status: current  
Owner: Maintainers  
Last reviewed: 2026-10-14  
Stale when: A filter source, the filter decision, or a filter surface changes.

Authority: [task state](tasks/server-filters.md) and the source below.

## Owners

| Responsibility | Owner | Lifetime |
|---|---|---|
| Filter contexts, actions, matches, verdicts | `domain/PostFilter.kt` (`FilterContext`, `PostFilterAction`, `ServerFilterMatch`, `PostFilterVerdict`) | Stateless |
| The one filter decision | `PostFilterPolicy.evaluate` | Stateless |
| Server filter matches on a post | `Post.serverFilters` | The post value |
| Mastodon `filtered` mapping | `data/mastodon/MastodonFilterMapper`, called by `MastodonMapper.post` | Stateless |
| Persisted matches of notification posts | `NotificationJsonCodec` key `serverFilters` | Notification store |
| "Show anyway" state | `PostRevealOwner.filterRevealed` | One connected session |
| Notice, label, quote notice | `ui/posts/PostFilterPresentation.kt` | Stateless composables |
| Filter list values and drafts | `domain/ContentFilters.kt` (`ContentFilter`, `ContentFilterDraft`, `ContentFilterSet`, `ContentFilterKind`) | Stateless |
| Client matching of filters the server does not match | `domain/ClientFilterMatcher.kt` | One filter set |
| Mastodon v2 and v1 filter reads and writes | `data/mastodon/MastodonFilterService`, called by `MastodonSource` | One source; the v2 or v1 choice lasts as long as the source |
| Filter snapshot of the connected account | `ui/filters/ContentFilterOwner`, created in `ConnectedApp`, read through `LocalClientFilters` | One connected entry; retires through `ConnectedEntryStore` |
| Misskey word mute reads and writes | `data/misskey/MisskeyWordMuteService`, called by `MisskeySource` | Stateless; each write reads `i` first |
| Muted and blocked account match | `domain/AccountFilter`, carried by `ClientFilterMatcher.withAccounts` | Stateless value |
| Muted and blocked account lists of the session | `ContentFilterOwner.muted` and `.blocked`; `applyRelationship` from `PostPopupOwner` through `ConnectedSessionHost` | One connected entry |
| Hidden-boost match | `AccountFilter.boostMuted` with the `post_filtered_hidden_boost` label through `rememberClientFilters`; always `Remove` | Stateless value |
| Hidden-boost list of the session | `ContentFilterOwner.boostMuted`: the Misskey `renote-mute/list` at load, session toggles elsewhere | One connected entry |
| Per-account boost toggle | `SocialSource.setBoostsHidden`: `renote-mute/create` and `renote-mute/delete` on Misskey, a follow with `reblogs=false` or `true` on Mastodon | One source call |
| Boost toggle state | `ProfileRelationship.boostsHidden`: `isRenoteMuted` on Misskey, `showing_reblogs` while following on Mastodon, null when unknown | The relationship value |
| Collapse or remove for muted and blocked accounts | `AppPreferences.accountPostActions` (`AccountPostActions`), stored as the keys `mutedAccountAction` and `blockedAccountAction`, edited in `BlocksAndMutesScreen` | App-wide preference file |
| The provided matcher | `rememberClientFilters` in `ConnectedApp` | Composition |
| JavaScript-form expressions | `domain/FilterExpressions` | Stateless |
| Settings list and editor | `ui/filters/ContentFilterViewModel`, `ContentFilterSettingsScreen`, `ContentFilterEditorFields` | The settings page (activity ViewModel store) |

## Invariants

- A filtered Mastodon post keeps its body. The UI gates it through `PostFilterPolicy`. Every surface that shows post text must call the policy.
- Each surface passes its own `FilterContext`: Home feed and lists use Home, other feeds use Public (`FeedSource.filterContext`). Threads and the photo detail use Thread. Profiles use Account. Search and Photo Grid use Public. Notifications use Notifications. A quote uses Home.
- Mastodon sends every matching filter, whatever the context. The client applies the context. Source: `StatusRelationshipsPresenter` in Mastodon.
- `PostFilterPolicy.evaluate` joins the server matches with the client matches. A surface passes `LocalClientFilters.current`, or a parameter where the code is not composable (`photoGridItems`).
- `ClientFilterMatcher.forSet` gives `Empty` for v2 (`Titled`) sets, because the server marks those posts already.
- The settings page publishes every loaded or saved set to the session owner. The owner ignores a set of another account.
- The service selects v1 only after a v2 404. Any other v2 failure stays a failure and selects nothing.
- A v2 update reads the filter again. It keeps the IDs of kept keywords and sends `_destroy` for removed ones. Keyword fields use indexed keys.
- A v1 save without expiry sends no `expires_in`, because Pleroma's schema rejects an empty value.
- A word mute ID is its list (`s:` or `h:`) and its stored JSON. A write reads `i`, replaces that one entry, and sends both lists. Entries that Beeline cannot read stay unchanged.
- Case-insensitive simple words are stored as `/escaped/i`. Beeline reads a `/literal/i` entry back as a simple word.
- Misskey array entries match with capitalization and need every word, as `checkWordMute` in Misskey does.
- The account match checks the author and the booster. It never applies to `FilterContext.Account`. It applies to the account's own posts too, which cannot be muted.
- A hidden boost matches only the booster and always removes; an opened post collapses instead. A boost of a post by a muted or blocked author keeps the mute or block verdict.
- The owner pages each list up to 25 pages. A failed first page keeps the old list. `ReloadAccountFiltersAfterSettings` reads the lists again when Settings closes.
- The Misskey hidden-boost list pages inside the adapter with the same 25-page cap. Sources without the list keep the session toggles. An unknown `boostsHidden` flag never clears them.
- The post menu shows its boost row only when `boostsHidden` is known. The profile pill follows the same rule and forwards each success to `ContentFilterOwner.applyRelationship` through `ProfileHost`. The Mastodon toggle needs a follow.
- `INVALID_REGEXP` and `TOO_MANY_MUTED_WORDS` become `SourceError.InvalidInput` with a `ContentFilterInput` feature. The editor shows them next to the field.
- `Remove` wins over `Collapse`. `hide` removes. `warn`, `blur`, and unknown actions collapse.
- A removing filter never removes the post that a screen opened. The standard detail sets `PostRowPresentation.removable = false`; the photo detail and quotes collapse the post themselves.
- An Android notification never previews a post with a server filter match.
- Stored matches with an unknown action decode as `Remove`. Unknown contexts drop; none left means every context.
- Legacy notification posts with `contentVisibility = Filtered` keep the old placeholder. Their body is empty.
- Revealing a filter does not expand the content warning, and changing the warning keeps the reveal.

## Tests

`MastodonFilterResultTest`, `PostRowFilterTest`, `PostRevealOwnerTest`, `NotificationJsonCodecTest`, `NotificationDeliveryPlannerTest`, `ClientFilterMatcherTest`, `MastodonFilterServiceTest`, `ContentFilterEditorTest`, `ContentFilterViewModelTest`, `ContentFilterSettingsScreenTest`, `SettingsRouteRestorationTest`, `MisskeyWordMuteServiceTest`, `ModerationServiceTest`, `PostShareSheetTest`, `ProfileViewModelTest`.

## Limits

- No live check ran against a Mastodon server with filters.
- The detail screen has no "hide again" control. A new session covers the post again.
- Android notifications run without the session owner. A v1 filter that only collapses does not stop a notification preview. Irreversible v1 filters drop the notification on the server.
- The profile media grid shares `photoGridItems` and uses Public filters, not Account filters.
- After a v2 edit, posts already loaded keep the old server matches until they load again.
- On Pleroma, changing an expiring v1 filter to "Never" may keep the old expiry, because Beeline sends no `expires_in`.
- Beeline matches word mutes against more text than Misskey web: poll options, media descriptions, and the quoted post too.
- Java accepts some expressions that RE2 refuses. The server then refuses the save, and Beeline shows that message.
- An account past the 25-page limit is matched only after a mute or block from the post menu in this session.
- An account past the 25-page renote-mute list is matched only after a boost toggle in this session. Mastodon never lists hidden boosts; only session toggles match there.
- Notifications whose actor is muted or blocked (follows, reactions) are not filtered; only notification posts are.
- A v2 filter with only status entries and no keywords cannot be saved from Beeline. The editor asks for a keyword.
