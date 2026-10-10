# Objective

Implement `docs/video.md`: video autoplay in the feed and photo grid, a video viewer page, and WebM/H.264 upload.

# Invariants

- One autoplay owner at a time. At most two ExoPlayers: one feed or grid slot, one viewer slot.
- Metered or unknown networks never autoplay. Reduced motion turns autoplay off.
- TextureView for tiles and the viewer. No `media3-ui`, no Material components.
- FFmpeg stays LGPL, shared libraries, arm64-v8a only.

# Decisions

- Slice order: 6, 2, 3, 4, 5, 8, 7, 9. Slice 1 (spike) is measured notes only and runs on the emulator, which is x86_64.
- `AutoplayPolicy` is stateless; the caller passes the current key back for hysteresis.
- `MeteredNetworkMonitor.metered` is a cold flow. The network callback lives while collected.

# Completed

- Slice 6, Media3 dependencies — see Git log "Add Media3 ExoPlayer and headless Compose state".
- Slice 2, setting, network monitor, policy — see Git log "Add the autoplay setting, metered network monitor, and autoplay policy".
- Slice 3, feed autoplay, `8e635e18`.
- Slice 4, photo grid, `099ea871`.
- Slice 5, viewer — see Git log "Play videos in the media viewer".
- Slice 8, upload H.264 MP4 path first: `VideoUploadPolicy`, `VideoPreparer`, `DeviceVideoCapabilities`, Media3 Transformer MP4 transcoder, `VideoModule` wiring with `UnavailableWebmEncoder`. WebM selection logic is present but the encoder stays unavailable until slice 7.

# Current slice

Slice 8 second half, pending: WebM path after slice 7 (FFmpeg bridge, benchmark on device), composer video attachments with picker support and progress UI, and recording of `UNALLOWED_FILE_TYPE` into `WebmRejections`.

# Files involved

- `domain/VideoUploadPolicy.kt` (protocol-neutral three-way choice, 2 MiB exclusive original rule, Mastodon list gate, Misskey retry state)
- `data/media/VideoPreparer.kt` (`VideoPreparer`, `WebmRejections`, `VideoServerTarget`, `PreparedVideo`), `data/media/VideoTranscoding.kt` (`VideoProbe`, `VideoTranscoder`, `WebmEncoder`, `UnavailableWebmEncoder`), `data/media/AndroidVideoSupport.kt` (`MediaMetadataVideoProbe`, `Media3Mp4Transcoder`, `PreferencesVideoBenchmarkStore`), `data/media/DeviceVideoCapabilities.kt` (arm64 gate, 1.5x benchmark, cache keyed by model, SDK level, and build id)
- `di/VideoModule.kt` (wires `VideoPreparer` with the unavailable WebM encoder; slice 7 replaces it)
- `app/build.gradle.kts` (`media3-transformer:1.11.1` for H.264 MP4 conversion through device codecs, `media3-effect:1.11.1` for `Presentation.createForHeight`)

# Verification

- `AutoplayPolicyTest` and `AppPreferencesRepositoryTest` pass; ktlint passes.
- Slice 3: full CI-parity gate passed (architecture audit exit 1 is pre-existing). Emulator: video plays in a hashtag feed, frames advance, sound toggle flips, scrolling away stops it. Audio output itself not heard.
- Slice 4: full gate passed. Emulator: Federated grid shows a poster with play badge and a playing muted tile with controls. Quick view is off for video tiles.
- Slice 5: full Gradle gate passed. Emulator: viewer opens from a feed tile, plays, controls auto-hide after 3 s, tap shows them, pause/play, sound toggle, seek, drag-dismiss back to the feed. `MediaPage` still shows a pre-existing audit regression (function-complexity-growth); its early branches were extracted so it is smaller than before.
- Unverified in slice 5: pinch zoom on video, the open and close reveal animation frame by frame, audio output, handoff of a non-zero start position from a live feed tile.
- Not done in slice 3: duration badge (Attachment has no duration), retry control on a failed tile.
- Slice 8 H.264 first: focused `VideoUploadPolicyTest` 7/7, `VideoPreparerTest` 11/11, `DeviceVideoCapabilitiesTest` 4/4 pass. Full unit suite 2147 tests, 0 failures. `tools/tests` 52 OK. `ktlintCheck`, `lintDebug`, `assembleDebug`, `assembleRelease` pass. Architecture audit still exits 1 on pre-existing regressions (`MediaPage` function-complexity-growth and others); the new `VideoPreparer` retention finding was fixed with an explicit bound and `clear()`. Unverified: `VideoTranscodeInstrumentedTest` on a device, WebM encode and benchmark on arm64, live-server WebM allow lists, and composer upload wiring.

# Next

Slice 7 (FFmpeg arm64-v8a build, JNI bridge, licenses, size report, arm64 smoke test), then slice 8 second half (WebM path, composer video attachments, `UNALLOWED_FILE_TYPE` recording), then slice 9 (docs).

# Blockers

- The emulator is x86_64: FFmpeg and WebM cannot be run on it (slices 7, 8). `VideoTranscodeInstrumentedTest` and the VP9 benchmark need an arm64 device.
- Whether live Mastodon servers list `video/webm` in `supported_mime_types` is unverified.
- Default max quality tier (720p proposed) still needs user confirmation before it becomes a constant.

# Last safe commit

Slice 5 commit `2340a592` (preceding safe commit; slice 8 H.264 commit subject: "Upload videos as H.264 MP4 (video slice 8)")
