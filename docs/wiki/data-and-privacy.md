# Data and Privacy

Status: planned  
Owner: Data maintainers  
Last reviewed: 2026-10-01  
Stale when: A storage format, migration, retention rule, or privacy boundary changes.

Sources: `AGENTS.md`, `data/`, storage tests, and migration definitions.

## Purpose

<!-- Explain local storage, migrations, retention, and sensitive data handling. -->

## Entries

<!-- Add sessions, preferences, drafts, notifications, media, emoji caches, Room, and removal behavior. -->

### Draft writers

Source: `data/auth/DraftWriteAuthority.kt`, `data/auth/DraftActions.kt`,
and `DraftActionsTest`.

- One writer generation exists for each account. Activation issues a new
  generation and revokes the previous writer.
- `DraftActions` captures the generation and routes save and delete through
  `commitIfCurrent`. A revoked writer writes nothing and reports no success.
- Account removal revokes the draft writer before it deletes rows.
- Test fixtures activate a real authority for the test account and pass that
  generation to the action owner. The fixture scope ends with the test.

### Emoji assets

Source: `data/emoji/EmojiAssetStore.kt`, `data/emoji/EmojiAssetLease.kt`,
`data/emoji/EmojiCacheDao.kt`, and `EmojiAssetStoreTest`.

- Custom emoji bytes live under `noBackupFilesDir/emoji/assets` and are named by
  content hash. The metadata lives in the `EmojiCacheDatabase`.
- The URL mapping table keeps at most 4,096 inactive rows. Eviction uses access
  order: the referenced asset `lastUsedEpochMillis` ascending, then the
  canonical URL ascending.
- Stored bytes target 128 MiB for inactive content. When the content stays
  referenced, cleanup evicts the least recently used inactive mapping first.
- A reader holds a closeable lease while it reads a file. Open leases and active
  writes are exempt from eviction, so the byte target can be exceeded until the
  last reader releases the file.
- Emoji bytes are shared and credential-free. Account removal does not delete
  them.

### Upload inputs

Source: `data/transport/AuthenticatedHttpClient.kt`,
`data/transport/UploadStreamOwner.kt`, and `AuthenticatedHttpClientTest`.

- A multipart upload streams its input without buffering the whole file.
  The request body reports unknown length, so the client never consumes input
  to measure it. Framing follows the negotiated protocol.
- The upload call owns its input from entry to termination. It closes the input
  exactly once on success, failure, and cancellation.
- A consumed input cannot be replayed. An explicit retry needs a newly opened input.
- Cancellation closes the input while the client cancels the call.
- Closing an arbitrary input may not interrupt every provider. Tests use an input
  that unblocks when closed. Device and provider limits stay recorded per slice.
