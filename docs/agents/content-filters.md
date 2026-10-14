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

## Invariants

- A filtered Mastodon post keeps its body. The UI gates it through `PostFilterPolicy`. Every surface that shows post text must call the policy.
- Each surface passes its own `FilterContext`: Home feed and lists use Home, other feeds use Public (`FeedSource.filterContext`). Threads and the photo detail use Thread. Profiles use Account. Search and Photo Grid use Public. Notifications use Notifications. A quote uses Home.
- Mastodon sends every matching filter, whatever the context. The client applies the context. Source: `StatusRelationshipsPresenter` in Mastodon.
- `Remove` wins over `Collapse`. `hide` removes. `warn`, `blur`, and unknown actions collapse.
- A removing filter never removes the post that a screen opened. The standard detail sets `PostRowPresentation.removable = false`; the photo detail and quotes collapse the post themselves.
- An Android notification never previews a post with a server filter match.
- Stored matches with an unknown action decode as `Remove`. Unknown contexts drop; none left means every context.
- Legacy notification posts with `contentVisibility = Filtered` keep the old placeholder. Their body is empty.
- Revealing a filter does not expand the content warning, and changing the warning keeps the reveal.

## Tests

`MastodonFilterResultTest`, `PostRowFilterTest`, `PostRevealOwnerTest`, `NotificationJsonCodecTest`, `NotificationDeliveryPlannerTest`.

## Limits

- No live check ran against a Mastodon server with filters.
- The detail screen has no "hide again" control. A new session covers the post again.
