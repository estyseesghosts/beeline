# Notifications and Direct Messages

Status: current, partial coverage  
Owner: Notifications and messaging maintainers  
Last reviewed: 2026-10-01  
Stale when: Notification delivery or direct-message behavior changes.

Sources: `AGENTS.md`, `data/notifications/`, `data/directmessages/`, notification tests, and messaging tests.

### Notification launch handoff

Source: `data/notifications/NotificationLaunch.kt`, `NotificationLaunchStore.kt`,
`NotificationLaunchRouter.kt`, `ui/notifications/NotificationLaunchHost.kt`,
`data/notifications/AndroidNotificationPresenter.kt`, and the notification launch tests.

- Tap intents use the app-owned `palustris://notification/open/<key>` URI and four validated extras.
- The pending launch stores its origin, local account ID, protocol, and notification ID in shared
  preferences. Invalid or partial stored values do not produce a pending launch.
- The host waits during startup and account switching. It clears a launch only after the shell accepts
  its route. Missing accounts use the unavailable route.
- Tap and dismiss pending intents use separate components and actions. Both use the stable Android
  notification ID for their request code.

### Prepared Android notification text

Source: `data/notifications/NotificationTextResolver.kt`,
`data/notifications/AndroidNotificationPresenter.kt`, and `NotificationPresentationTest`.

- The data notification boundary resolves localized Android title and body text.
- The resolver keeps content-warning, actor, empty-preview, and reaction fallback precedence stable.
- The Compose notification formatter remains in `ui.notifications` and is not imported by data code.

## Purpose

<!-- Explain user-visible notification and direct-message behavior. -->

## Entries

### Notification lifecycle

Source: `data/notifications/NotificationSyncOrchestrator.kt`,
`data/notifications/NotificationRepository.kt`, and
`NotificationSyncOrchestratorTest`.

- Removal revokes the account token before durable deletion completes.
- A replacement succeeds only with a generation newer than the removal tombstone.
- Late stream events after removal or unregister write nothing.
- Removal after publication cancels the poll job and leaves no orphan job.
- A cancelled removal still revokes the old token.
- Push source selection stays bound to its notification token. A stale token miss ends as a
  no-op. It never uses the account registry entry or a transient factory source, which could
  belong to a replacement session. Token-null removal cleanup uses the stored session snapshot
  only while that snapshot still matches the store. Ownership is rechecked before remote
  subscription mutation. Remote cleanup failure still completes local opt-out.
- Live-server push delivery remains unverified.

### Direct-message threads

Source: `data/directmessages/DirectMessageRepository.kt`,
`data/mastodon/MastodonDirectMessageService.kt`,
`data/misskey/MisskeyDirectMessageService.kt`, and the direct-message tests.

- A conversation thread loads from a known post anchor in the stored conversation. A returned
  Misskey post with no parent establishes its reply root; a reply without its returned root stays
  provisional.
- A thread call returns a flat result with posts, an opaque continuation, limitations, and an
  acquisition state. Source semantics follow the existing Misskey `untilId`
  implementation. Live Misskey/Sharkey ordering is unverified. A fresh Misskey
  call loads the reply root, at most 20 ancestors, and a bounded breadth-first
  descent of at most 3 `notes/children` requests. The fresh call queues the root
  at depth 1. Each mapped direct child is enqueued at depth plus one, up to a
  fixed depth limit of 10. Pending work travels in the continuation, so the next
  call resumes the same breadth-first acquisition. Accepted descendant IDs also
  travel in the continuation, so overlapping pages never count or enqueue the
  same descendant twice, and loaded always equals the accepted ID count.
  Limitations accumulate across the chain, so a terminal call stays limited
  after any earlier truncation. At most 40 authenticated thread requests run
  across the whole chain, including the root and ancestor reads. A chain that
  reaches the total stops with a request limit and no continuation, even when
  pages keep advancing. At most 200 accepted descendants are kept; the cap
  records a node limit with no continuation. The bounded breadth-first
  frontier holds at most 200 pending parents; a child that would exceed the
  frontier records a pending-frontier limit with the actual frontier size,
  clears pending work, and ends the batch with no continuation and no parent
  re-queue. A drained terminal call with any carried limitation reports a
  limited acquisition state. A full page after the call
  budget returns a continuation. A full page with no usable last ID, a page that
  does not advance, or a nonempty page with no new mapped post IDs returns
  uncertain server truncation. The thread cursor binds origin, account, session
  revision, source instance, conversation, accepted IDs, limitations, and the
  chain request count. A bad cursor fails
  before any request. The Mastodon adapter returns its anchor and context result
  as finished with no cursor. The conversation footer shows one trailing status: spinner while loading or continuing; error with Retry when any thread acquisition fails, fresh load or continuation; Load more messages when a cursor remains; static partial notice when limitations remain with no cursor; nothing when finished. Retry reuses the stored cursor or reloads fresh when none remains; a rejected continuation cursor clears so retry falls back to fresh. Thread errors stay separate from inbox and send errors. A finished Mastodon result shows no extra control.
  DirectMessageViewModelTest and DirectMessageScreenTest cover the footer states.
- A conversation keeps an explicit identity. A server-issued identity may receive a server
  mark-read. A provisional local conversation clears unread state on this device and sends no
  server request until the server confirms the conversation.
- The Mastodon adapter loads the anchor through supported status endpoints. It never calls an
  undocumented individual conversation endpoint. One thread call reads the anchor through
  `GET /api/v1/statuses/:id` and its context through `/context`, then reports Finished with no
  cursor. A non-null thread cursor fails as unsupported before any request. Anchor 403, 404, and
  410 map to the same unsupported error, as do blank or foreign anchor and conversation identities
  (no request), malformed anchor bodies, and non-direct anchors. Repeated rows across the anchor
  and context responses merge without duplicates.
- The repository thread needs a stored conversation preview with the last-post anchor. A missing
  stored conversation fails as unsupported with no source call. Remote posts merge with cached
  rows with cross-call dedup. A failed load keeps cached rows and the preview. A late thread write
  after session replacement is rejected. Retry with no cursor reloads fresh context.
- Live direct-message evidence is blocked. No disposable account or approval exists for synthetic
  direct posts. Server truncation beyond one context response stays unverified. No continuation is
  invented from identifier order, and no unsupported pagination ships.
- Direct messages are federated private posts. They are not encrypted messaging.

### Misskey inbox pagination

Source: `data/misskey/MisskeyDirectMessageService.kt` and
`DirectMessageSourceTest`.

- The inbox merges `notes/mentions` items in endpoint order, then `users/notes` items in endpoint
  order. Deduplication follows the merge, so the mentions copy wins. Conversation groups retain
  first-seen group order, while each group selects its latest post for display.
- The opaque cursor keeps each endpoint continuation from the last raw item returned. It binds the
  origin, `account`, session revision, source instance, protocol variant, and fallback mode.
- An empty raw page marks that stream exhausted. Later pages skip it. The cursor becomes null when
  both streams are exhausted, so the ViewModel stops paging without another request.
- A nonempty raw page with no usable last-item ID also ends that stream. The client cannot continue
  safely, and replaying page one would repeat items forever.
- A 404 from `notes/mentions` latches mentions to filtered `i/notifications` pages. This fallback
  uses notification IDs in a separate cursor field. Later pages do not retry `notes/mentions`;
  refresh starts with that route again. Other failures keep their normal error mapping and do not
  latch the fallback.
