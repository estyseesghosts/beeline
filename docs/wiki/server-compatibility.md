# Server Compatibility

Status: planned  
Owner: Protocol maintainers  
Last reviewed: 2026-10-09  
Stale when: A supported server family, capability, endpoint, or verification result changes.

Sources: `AGENTS.md`, `domain/`, `data/misskey/`, `data/mastodon/`, adapter tests, and capability code.

## Purpose

<!-- Explain supported Mastodon-compatible and Misskey-family behavior. -->

## Entries

### Mastodon moderation paging

Source: `data/mastodon/MastodonModerationService.kt`,
`data/mastodon/MastodonSource.kt`, `ModerationServiceTest`,
`MastodonIntegrationTest`, and `ModerationViewModelTest`.

- Blocked and muted cursors bind account, kind, variant, route, session
  revision, source instance, and URL in a version-2 payload. Same-account
  replay on another source or revision fails before any request. Legacy
  version-1 and raw-URL cursors fail safely.
- The first-page self-Link check uses the exact initial request URL. A
  self-Link to the first page cannot become a continuation.
- A failed next-page load keeps current rows. Live-server moderation paging
  remains unverified.

### Mastodon report validation

Source: `data/mastodon/MastodonModerationService.kt` and `ModerationServiceTest`.

- `report` validates its target with `validateTarget` before it constructs
  fields. A same-origin Misskey target or a blank target ID fails as
  unsupported without a request. A foreign target keeps the foreign-origin
  error. The optional status ID keeps same-origin validation and rejects a
  blank value without a request.
- Valid account-only and account-plus-status reports keep the exact form
  fields. Reserved characters stay form-encoded. Live-server report delivery
  remains unverified.

<!-- Add the dated support matrix, capability states, protocol differences, and known unverified cases. -->

### Media upload and attachments

Source: `data/mastodon/MastodonMediaService.kt`, `data/misskey/MisskeyMediaService.kt`,
`domain/MediaUploadRequest.kt`, `MastodonMediaServiceTest`, `MisskeyMediaTest`,
and `AuthenticatedHttpClientTest`.

- `SocialSource.uploadMedia(MediaUploadRequest)` takes an `open` function, not a
  stream. Each attempt opens a new stream, because a consumed stream cannot be
  replayed. The transport streams the file without buffering it and closes the
  stream once on success, failure, and cancellation.
- Mastodon uploads to `POST /api/v2/media` with `file` and `description`. A 202
  answer means background processing. The adapter polls `GET /api/v1/media/:id`
  after 1 s, then 2 s, then every 5 s. A 206 answer continues, a 200 answer ends,
  and a 422 answer fails with the server error. Polling stops after 60 s. A 404
  from the v2 route falls back to the deprecated `POST /api/v1/media`. A 413
  answer maps to `ResourceLimit`.
- Mastodon `create` sends `media_ids[]` and accepts an empty `status` when media
  is attached. `CreatePostRequest.idempotencyKey` becomes the `Idempotency-Key`
  header, which the server keeps for one hour. A quote with media is still
  rejected before any request.
- Misskey uploads to `POST /api/drive/files/create` with the token as the form
  field `i`, plus `name`, `comment` (alt text), `isSensitive`, and `force`.
  The endpoint needs the `write:drive` permission. `create` sends `fileIds` and
  leaves out `text` when it is blank and files are present.
- Misskey drive errors: `NO_FREE_SPACE` and `MAX_FILE_SIZE_EXCEEDED` (and HTTP
  413) map to `ResourceLimit`. `UNALLOWED_FILE_TYPE`, `INAPPROPRIATE`, and
  `commentTooLong` map to `Unsupported` with separate keys. `PERMISSION_DENIED`
  maps to `AccessDenied`.
- Limits come from `ServerCapabilities.posting`. Live-server uploads remain
  unverified.

### Media in the composer

Source: `data/media/DraftMediaImporter.kt`, `domain/DraftMediaImport.kt`,
`domain/ServerCapabilities.kt`, `data/mastodon/MastodonCapabilityProbe.kt`,
`data/auth/AccountFileStore.kt`, `DraftMediaImporterTest`, and `ComposerMediaRulesTest`.

- The composer copies each picked image into the encrypted draft store before the picker grant ends.
  It sniffs the type with a bounds decode and rejects non-images, empty files, files over 100 MiB, and
  unreadable files. The server limits apply at publish time.
- `PostingCapabilities.quoteWithMedia` is true by default and false on Mastodon-compatible servers,
  which reject a quote that carries media. The composer disables the photo button for a quote there
  instead of failing at publish. Capability schema version 7 stores the flag; older stored snapshots
  read as true.
- The photo button follows `mediaUpload`: Unsupported disables it, Denied starts the sign-in-again
  flow, and Unknown and Supported allow the pick. `ConnectedSessionContext.mediaAccess` carries the
  token access.
- Live-server image publishing from the composer (dvd.chat and Mastodon) remains unverified.

### Upload image preparation

Source: `data/media/UploadImagePreparer.kt`, `data/media/ImageFormatInspector.kt`,
`UploadImagePreparerTest`, and `UploadImagePreparerInstrumentedTest`.

- With compression on (Misskey default), JPEG, PNG, and WebP images are resized to at most 2000 px on
  the long edge and encoded as WebP at quality 85. The EXIF orientation is applied to the pixels. The
  original is kept when the result is not smaller.
- GIF, APNG, and animated WebP are never re-encoded. They pass unchanged when they fit. They fail
  with `ImageDoesNotFit` when they exceed a limit.
- An image over `maxImageBytes` or `maxImagePixels`, or with a type outside `uploadTypes` (or a type
  other than JPEG, PNG, and WebP, such as HEIC), is re-encoded as JPEG at quality 90 and scaled down
  in up to six steps. When it still does not fit, `ImageDoesNotFit` stops the publish before any request.
- Compression is not the same as fitting: with compression off, an image that fits is uploaded as it is,
  with location data removed.

