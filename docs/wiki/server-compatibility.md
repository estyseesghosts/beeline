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

### Combined hashtag search

Source: `data/mastodon/MastodonSource.kt`, `MastodonPageClient.kt`, `data/misskey/MisskeySource.kt`, and the integration tests.

- `SocialSource.maxCombinedHashtags` reports how many extra hashtags one search accepts. The default is 0.
- Mastodon uses `v1/timelines/tag/<primary>` with one `any[]` for each extra. The server applies 3 extras and ignores the rest. The documentation says 4. A check on mastodon.social (4.8.0-nightly) on 2026-10-10 showed 3.
- The `Link: next` header of an `any[]` request drops `any[]`. The adapter adds every extra back to each next page. The sorted extras are part of the cursor identity, so a cursor from another extra set fails with `pagination.cursor`.
- Misskey-family servers use `notes/search-by-tag` with `query`: one inner array for each hashtag. A search without extras keeps `tag`. A check on dvd.chat with 10 inner arrays returned 30 notes.
- Akkoma, other forks, and other Mastodon versions are unverified. A fork that ignores `any[]` shows the "Includes" line without merging.

### Hashtag and account discovery

Source: `data/mastodon/MastodonDiscoveryService.kt`, `data/misskey/MisskeyDiscoveryService.kt`, and `MastodonDiscoveryTest`, `MisskeyDiscoveryTest`.

- No capability probe. Each call works on demand. A failure or an empty list hides the section.
- Mastodon trending: `GET api/v1/trends/tags?limit=20` (at most 20). The count is the accounts of the latest two history days.
- Mastodon suggestions: `GET api/v2/search?q=<prefix>&type=hashtags&limit=8`. The weight is `log2(1 + sum of accounts in the history)`.
- Mastodon popular accounts: `GET api/v2/suggestions?limit=20`. It needs a token, so any failure falls back to `GET api/v1/directory?order=active&local=true&limit=20`.
- Misskey-family trending: `POST hashtags/trend` with `{}`, top 10. An empty answer falls back to `hashtags/list` sorted by `+mentionedUsers`. The count is `usersCount`.
- Misskey-family suggestions: `POST hashtags/search`. It returns bare names with no weight.
- Misskey-family popular accounts: `POST pinned-users`, then `POST users` with `sort: "+follower"`, `state: "alive"`, `origin: "local"`. The result has no duplicates and omits the signed-in account. `users/recommendation` needs sign-in and is not used.
- Unverified: `v2/suggestions` with a token, Akkoma and other forks, other Mastodon versions.

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

- The composer copies each picked image or video into the encrypted draft store before the picker grant ends.
  It sniffs an image with a bounds decode and a video with `MediaMetadataRetriever`. It rejects other files,
  empty files, images over 100 MiB, videos over 256 MiB, and unreadable files. The server limits apply at publish time.
- `PostingCapabilities.quoteWithMedia` is true by default and false on Mastodon-compatible servers,
  which reject a quote that carries media. The composer disables the photo button for a quote there
  instead of failing at publish. Capability schema version 7 stores the flag; older stored snapshots
  read as true. Version 8 adds `maxVideoBytes`; the app probes the server again when it reads an older snapshot.
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

### Video upload

Source: `domain/VideoUploadPolicy.kt`, `data/media/VideoPreparer.kt`, `data/media/DeviceVideoCapabilities.kt`,
`data/media/FfmpegBridge.kt`, `data/media/DraftThreadImagePreparer.kt`, `domain/ThreadPublisher.kt`,
`VideoUploadPolicyTest`, `VideoPreparerTest`, `DeviceVideoCapabilitiesTest`, `ThreadPublisherVideoTest`,
and `FfmpegBridgeTest`. Live-server uploads are unverified.

- The picker offers videos when `PostingCapabilities.acceptsVideo` is true. That holds when the server lists no
  types or lists at least one `video/` type. Mastodon without a type list reports images only, so it offers none.
- The publisher prepares each video in this order. The first rule that applies wins.
  1. Original. A file under 2 MiB (exclusive) is sent unchanged when the server accepts its container and codec.
  2. WebM. VP9 video and Opus audio, when the server accepts WebM and the device passes the benchmark.
  3. H.264 MP4. H.264 video and AAC audio through Media3 Transformer and the device codecs.
- The upload tier is 900p. It limits the shorter edge. A 1920x1080 clip becomes 1600x900. A 1080x1920 clip becomes
  900x1600. A smaller clip is never scaled up. The WebM path caps the frame rate at 30.
- Mastodon gate: `video/webm` must appear in `media_attachments.supported_mime_types`. Otherwise the upload uses MP4.
- Misskey gate: the server has no type list, so the client tries WebM. A refusal maps to `Unsupported("media.file-type")`.
  The preparer records the origin in `WebmRejections`, and the publisher uploads the video again as MP4. The record lives
  until the process ends.
- Device gate: the process must run on arm64-v8a and the FFmpeg library must load. The benchmark encodes 3 seconds at a
  900 pixel short edge and must reach 1.5 times realtime. The result is cached under the device model, the SDK level, and
  the FFmpeg build id. A failed benchmark counts as too slow.
- Size limit: `maxVideoBytes` is the Mastodon `video_size_limit` (default 99 MiB) or the Misskey file size limit.
  A result over the limit stops the publish with `ResourceLimit`.
- Mastodon answers a large upload with 202. The existing `MastodonMediaService` polling handles it, so video uses
  the same `uploadMedia` call as images.
- The WebM output carries no source metadata, so it has no location data.
- A Mastodon server refuses a post that mixes a video with images. The composer does not prevent the mix.
- Not verified: whether live Mastodon servers list `video/webm`, and live Misskey behavior on `UNALLOWED_FILE_TYPE`.
