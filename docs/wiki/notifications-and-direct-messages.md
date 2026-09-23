# Notifications and Direct Messages

Status: current, partial coverage  
Owner: Notifications and messaging maintainers  
Last reviewed: 2026-09-23  
Stale when: Notification delivery or direct-message behavior changes.

Sources: `AGENTS.md`, `data/notifications/`, `data/directmessages/`, notification tests, and messaging tests.

## Purpose

<!-- Explain user-visible notification and direct-message behavior. -->

## Entries

<!-- Add inbox filters, unread state, delivery, push limits, account routing, and direct-message privacy limits. -->

### Direct-message threads

Source: `data/directmessages/DirectMessageRepository.kt`,
`data/mastodon/MastodonDirectMessageService.kt`,
`data/misskey/MisskeyDirectMessageService.kt`, and the direct-message tests.

- A conversation thread loads from a known post anchor in the stored conversation. A returned
  Misskey post with no parent establishes its reply root; a reply without its returned root stays
  provisional.
- A conversation keeps an explicit identity. A server-issued identity may receive a server
  mark-read. A provisional local conversation clears unread state on this device and sends no
  server request until the server confirms the conversation.
- The Mastodon adapter loads the anchor through supported status endpoints. It never calls an
  undocumented individual conversation endpoint.
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
