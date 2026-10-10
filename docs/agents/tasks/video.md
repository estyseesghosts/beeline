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

# Current slice

Slice 8, upload (H.264 MP4 path first).

# Files involved

- `domain/AutoplayPolicy.kt`, `data/media/MeteredNetworkMonitor.kt`, `data/media/ReducedMotion.kt`
- `ui/media/VideoPlaybackCoordinator.kt` (single owner of the feed slot; suppresses `UnsafeOptInUsageError` because lint rejects Kotlin `@OptIn` for Media3), `ui/media/VideoTile.kt`, `ui/media/VideoControls.kt`
- `ui/media/PostMediaCarousel.kt` (video branch), `domain/MediaRequestPolicy.kt` (video poster)

# Verification

- `AutoplayPolicyTest` and `AppPreferencesRepositoryTest` pass; ktlint passes.
- Slice 3: full CI-parity gate passed (architecture audit exit 1 is pre-existing). Emulator: video plays in a hashtag feed, frames advance, sound toggle flips, scrolling away stops it. Audio output itself not heard.
- Slice 4: full gate passed. Emulator: Federated grid shows a poster with play badge and a playing muted tile with controls. Quick view is off for video tiles.
- Slice 5: full Gradle gate passed. Emulator: viewer opens from a feed tile, plays, controls auto-hide after 3 s, tap shows them, pause/play, sound toggle, seek, drag-dismiss back to the feed. `MediaPage` still shows a pre-existing audit regression (function-complexity-growth); its early branches were extracted so it is smaller than before.
- Unverified in slice 5: pinch zoom on video, the open and close reveal animation frame by frame, audio output, handoff of a non-zero start position from a live feed tile.
- Not done in slice 3: duration badge (Attachment has no duration), retry control on a failed tile.

# Next

Slice 8: `VideoPreparer`, H.264 MP4 through Media3 Transformer, then WebM after slice 7. Slice 7 (FFmpeg) needs an arm64 device and the NDK.

# Blockers

- The emulator is x86_64: FFmpeg and WebM cannot be run on it (slices 7, 8).

# Last safe commit

Slice 4 commit `099ea871`
