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
  1. Original. A file under 2 MiB (exclusive) is sent unchanged when the server accepts its container. The codec does not matter.
  2. WebM. VP9 video and Opus audio, when the server accepts WebM, the device passes the benchmark, and the source is at most 1080p. A larger source skips the benchmark and goes to MP4.
  3. H.264 MP4. H.264 video and AAC audio through Media3 Transformer and the device codecs. A clip that is already efficient (common codec, no downscale, bitrate within 2.5 times the target) is sent as it is.
  Every conversion has a bitrate target: 0.07 bits per pixel at 30 fps (75 percent of that for VP9), at most 90 percent of the source bitrate, and within the size limit. A conversion that is not smaller than the source is dropped when the server accepts the original.
- The upload tier is 900p. It limits the shorter edge. A 1920x1080 clip becomes 1600x900. A 1080x1920 clip becomes
  900x1600. A smaller clip is never scaled up. The WebM path caps the frame rate at 30.
- Mastodon gate: `video/webm` must appear in `media_attachments.supported_mime_types`. Otherwise the upload uses MP4.
- Misskey gate: the server has no type list, so the client tries WebM. A refusal maps to `Unsupported("media.file-type")`.
  The preparer records the origin in `WebmRejections`, and the publisher uploads the video again as MP4. The record lives
  until the process ends.
- Device gate: the process must run on arm64-v8a and the FFmpeg library must load. The benchmark encodes 3 seconds at a
  900 pixel short edge and must reach 1.5 times realtime. The result is cached under the device model, the SDK level, and
  the FFmpeg build id. A failed benchmark counts as too slow.
- Draft media uses a chunked AES-GCM format (`DraftMediaCipher`, 1 MiB chunks). The older single-message format cannot stream, so a large old draft fails with an error instead of stalling. Attach the video again.
- Upload calls have no total timeout. They fail after 120 s without progress. Mastodon processing polls for up to 300 s.
- Size limit: `maxVideoBytes` is the Mastodon `video_size_limit` (default 99 MiB) or the Misskey file size limit.
  A result over the limit stops the publish with `ResourceLimit`.
- Mastodon answers a large upload with 202. The existing `MastodonMediaService` polling handles it, so video uses
  the same `uploadMedia` call as images.
- The WebM output carries no source metadata, so it has no location data.
- A Mastodon server refuses a post that mixes a video with images. The composer does not prevent the mix.
- Not verified: whether live Mastodon servers list `video/webm`, and live Misskey behavior on `UNALLOWED_FILE_TYPE`.

## Own-post actions and translation

- Delete: supported on the Mastodon API and the Misskey API. Edit stays hidden until its capability is reported as supported.
- Translation: the Mastodon API reads `configuration.translation.enabled`, or `akkoma:machine_translation` in the feature list, and offers it for public and unlisted posts only. The Misskey API needs `translatorAvailable` in `meta` and the role policy `canUseTranslator`. A server that offers neither never shows the Translate row.
- Automatic translation (infdev 03 S5) uses the same capability and the same endpoints. Mastodon answers 429 with a rate-limit error and 503 with a busy error, and the app pauses automatic requests for five minutes on either. Misskey answers `UNAVAILABLE` when no translator is configured or the call failed. The adapter maps it to unsupported, and the owner then stops automatic requests for that account session. Misskey posts carry no language, so a post in the reader's language is translated and then hidden when the answer names the same source language. Not verified on a live server: the translation provider, the quota and the rate limits.

## Polls

- Create: Mastodon sends `poll[options][]`, `poll[expires_in]` (default one day), `poll[multiple]` and `poll[hide_totals]`. A poll and media never go in one Mastodon post. Misskey sends `poll.choices`, `poll.multiple` and `poll.expiredAfter` in milliseconds.
- Limits: Mastodon reads `configuration.polls` (`max_options`, `max_characters_per_option`, `min_expiration`, `max_expiration`). Pleroma-family servers report `poll_limits` with `max_option_chars` instead. Neither `meta` of Misskey nor Sharkey lists poll limits, so the Misskey family uses 10 choices of 50 characters, the lower of the two.
- Vote: Mastodon `POST /api/v1/polls/:id/votes` with `choices[]` returns the poll. Misskey `notes/polls/vote` takes one choice per call, so a multiple-choice vote is several calls and one `notes/show`.
- Access: Misskey needs the `write:votes` permission. Sessions signed in before it was requested get the server's policy error on a vote until the account signs in again. Mastodon `write` covers voting.
- Misskey notes carry no expired flag; the end time decides it. Mastodon may hide totals (`votes_count` null), which reads as zero.

## Edit

- Mastodon API: version 3.5 or later, or `editing` in `pleroma.metadata.features`. A server with a `pleroma` block is judged by that list only, because its version string names the Mastodon API level. `GET /api/v1/statuses/:id/source` gives the raw text and warning. `PUT /api/v1/statuses/:id` sends `status` always and the warning, `media_ids[]`, `media_attributes[]` and poll only when they changed.
- Misskey family: `notes/edit` in `POST /api/endpoints` means Sharkey. Vanilla Misskey has no edit route and never shows Edit. The adapter reads the note with `notes/show`, then sends `notes/edit` with the note's own visibility, `localOnly`, reaction acceptance, reply, quote and channel, because a missing field resets to its default. Alt text lives on the drive file, so a changed description is `drive/files/update`.
- The edit shows in rows through a session overlay and survives until a copy of the post with a later edit time arrives. An edit that touches a poll resets its votes on the server.

## Quotes

- Reading: Mastodon 4.5 `quote{state,quoted_status}`; a shallow `quoted_status_id` (the card loads by ID); Fedibird `quote`, `quote_id`, `quote_muted`; Akkoma state plus a copy; Pleroma `pleroma.quote`, `quote_id`, `quote_url`, `quote_visible`; legacy `quoted_status`; and a trailing `RE:` link or `p.quote-inline` paragraph, which is hidden from the body once it is read as a reference.
- Writing: `quoted_status_id` and `quote_id` are both sent. Quote support is `Supported` with version 4.5, `feature_quote`, the Akkoma `quote_posting` feature or a Fedibird flag, and `Unknown` otherwise. `Unknown` still offers Quote; a create that comes back without a quote marks quotes `Unsupported` for the session. A quote cannot carry a poll, and carries media only where the server shows quote-with-media evidence.
- `quote_approval.current_user` of `denied` disables Quote on that post. `quote_approval_policy` is sent on servers with Mastodon 4.5 evidence.

## Local-only posts

- Misskey family: the `localOnly` note field. Mastodon API: `local_only` on glitch-soc and Hometown; `visibility=local` on Pleroma and Akkoma, for public posts only. Vanilla Mastodon has no route and shows no chip.

## Scheduled posts

- Mastodon API: `scheduled_at` on create (at least 5 minutes ahead), `GET/PUT/DELETE /api/v1/scheduled_statuses`.
- Sharkey: `notes/schedule/create`, `list`, `delete`; there is no update. Role policy `scheduleNoteMax` (default 5). A server can refuse an account these routes with a permission error, as dvd.chat did for the test account.
- Misskey: `notes/drafts/*` with `scheduledAt` and `isActuallyScheduled`; role policy `scheduledNoteLimit` (default 1). A limit of 0 turns scheduling off.
- The route comes from `POST /api/endpoints`. A scheduled post never enters a timeline.

## Search, Trends and profile links

Source: `data/mastodon/MastodonSearchService.kt`, `data/misskey/MisskeySearchService.kt`, `SearchAdapterTest`. Status: test verified.

- Query kinds: handle, hashtag, URL and free text. `classifySearchQuery` decides, and the adapters never see a URL.
- Suggestions return at most 3 accounts and 6 hashtags. A hashtag query asks for no accounts. A handle query asks for no hashtags. One failed call keeps the other result.
- Mastodon suggestions: `GET api/v2/search` with `type=accounts&limit=3` and `type=hashtags&limit=6`. `resolve=true` goes only with a full `@user@host` handle.
- Misskey-family suggestions: `users/search` (`users/search-by-username-and-host` for a handle) and `hashtags/search`.
- Mastodon post search: `GET api/v2/search?type=statuses&limit=20&offset=N`. The cursor is the offset. The server returns nothing without Elasticsearch or OpenSearch, so the screen shows an empty state.
- Misskey-family post search: `notes/search` paged by `untilId`. The role policy `canSearchNotes` goes into `ServerCapabilities.search`. A denied policy blocks the call and the screen says so.
- Mastodon Trends: `api/v1/trends/tags` (10), `trends/statuses` (20, paged by offset) and `trends/links` (10). A refused section stays empty. Pleroma and Akkoma have no posts or links route.
- Misskey-family Trends: `hashtags/trend` and `notes/featured` paged by `untilId`. Checked live on dvd.chat: `untilId` pages, `offset` does nothing. There are no trending links.
- Profile links: Mastodon `GET api/v2/search?resolve=true&type=accounts&limit=1`. Misskey-family `users/show` for an address on the same server, otherwise `ap/show` with type `User`.
- Photo Grid carry-over: the grid shows the Search pages and cursor from `SearchController`. It never fetches search pages itself, and the search feed is never saved.
- Capability schema version is 12 (adds `search.posts`).

## Feed chips: lists, followed hashtags and antennas

Source: `data/mastodon/MastodonFeedService.kt`, `data/misskey/MisskeyFeedService.kt`, `FeedChipAdapterTest`. Status: test verified.

- `SocialSource.feed(source, cursor)` serves one `FeedSource`. A built-in timeline keeps `timeline()`. A hashtag uses `searchHashtags`.
- Mastodon lists: `GET api/v1/lists`, then `GET api/v1/timelines/list/:id?limit=40`, paged by the `Link` header.
- Mastodon followed tags: `GET api/v1/followed_tags?limit=200`, paged by the `Link` header for at most five pages. Follow and unfollow are `POST api/v1/tags/:name/follow` and `/unfollow`. Needs Mastodon 4.0 or later. The probe marks 4.0 and later, and any server with `api_versions`, as Supported. A server below 4.0 is Unsupported and keeps tags on the device. A server with no version is Unknown.
- Mastodon has no antennas.
- Misskey-family lists: `users/lists/list`, then `notes/user-list-timeline` with `listId`. Antennas: `antennas/list`, then `antennas/notes` with `antennaId`. Both page by `untilId`. The probe reads the endpoint list, so a server without an endpoint hides that kind.
- Misskey-family servers have no followed-tags route. Saved tags stay on the device and a Remove never reaches the server.
- Capability schema version is 13 (adds `feedChips`).

## Media requests

- A server can answer preview requests with 429 when many tiles load at once. mstdn.ca did this for its media route during a Photo Grid scroll (infdev 03 S2). The media client retries once after a timeout, a 5xx answer or a 429 answer, and waits for `Retry-After` up to 3 s. It does not retry any other 4xx answer.
- The same route answered some previews with 404 and then served them on a later request. The failed tile shows a retry control.
- Not verified: the limits of other servers, and the effect of a User-Agent on a CDN.

## Video posters

- Mastodon API servers always send `preview_url`. Misskey does not for videos that federated in: `thumbnailUrl` is empty and `properties` is empty, so a tile had no poster and the viewer had nothing to fling from, and fell back to a fade.
- A video without a poster now shows its first frame, read with `MediaMetadataRetriever` (`VideoFrameFetcher`), which asks the server for only the byte ranges it needs. A server poster always wins. Feed tiles fetch the frame only while autoplay is on and the network is unmetered; otherwise they keep the placeholder. The viewer and the Photo Grid always fetch it.
