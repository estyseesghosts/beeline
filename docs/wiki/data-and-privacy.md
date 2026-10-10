# Data and Privacy

Status: planned  
Owner: Data maintainers  
Last reviewed: 2026-10-02  
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

### Notification storage

Source: `data/notifications/NotificationRepository.kt`,
`data/notifications/NotificationStore.kt`,
`data/notifications/db/NotificationDao.kt`,
and `NotificationRetentionMeasurementTest`.

- The repository keeps at most 500 visible records for each account.
  New pages merge with stored records and drop the oldest records beyond 500.
  Fifty thousand ingested events leave exactly 500 visible records.
- Dismissal tombstones grow by one entry for each distinct dismissed notification.
  The harness measures exactly 1,000 tombstones after 1,000 dismissals
  and 10,000 tombstones after 10,000 dismissals.
  No age bound and no count bound evict them.
- Checkpoints grow by one entry for each stable query key.
  Five filter queries hold five checkpoints.
  Repeat baselines add no entries.
- Delivery records grow by one entry for each delivered event after the baseline.
  Dismissal and account removal release them.
  Finished records stay until dismissal or removal.
- The Room store writes one state row for each account.
  The sibling event, actor, group, query, dismissal, delivery,
  acknowledgement, push, and settings tables hold zero rows
  under the current writer.
  Account removal deletes all ten tables in one transaction.
- Page replay never restores a dismissed record.
  A newer-page replay never creates a delivery record for a dismissed
  record or for an already-known record.
  Stream events after the baseline can still create a delivery record
  for a known record.
  Removal cleans one account and keeps the sibling account intact.
  Re-added identifiers under a strictly newer generation start
  with empty tombstones.
- Heap bytes and database file bytes remain unmeasured.
  Unit counts do not prove device memory or disk behavior.

## Upload image preparation

`UploadImagePreparer` (`data/media/`) prepares one image for each call. It never edits the draft's own
file. It writes a temporary copy in the cache directory only when the upload differs from the draft
file, and the publisher deletes that copy after the upload. A copy made for an upload carries no
location data: an unchanged JPEG, PNG, or WebP is copied and its GPS tags are removed, and a
re-encoded image carries no metadata. An image that cannot be cleaned in place is re-encoded. GIF and
other animated images pass unchanged and carry no change to their metadata.

