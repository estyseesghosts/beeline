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

# Current slice

Slice 3, feed autoplay.

# Files involved

- `domain/AutoplayPolicy.kt`, `data/media/MeteredNetworkMonitor.kt`, `data/media/ReducedMotion.kt`
- `ui/media/PostMediaCarousel.kt`, `ui/feed/HomeFeed.kt`

# Verification

- `AutoplayPolicyTest` and `AppPreferencesRepositoryTest` pass; ktlint passes.
- Full CI-parity gate runs at slice 3 and at the end.

# Next

Slice 3: `VideoPlaybackCoordinator`, `VideoTile`, `VideoControls`, feed wiring.

# Blockers

- The emulator is x86_64: FFmpeg and WebM cannot be run on it (slices 7, 8).

# Last safe commit

`164621d6` Add Media3 ExoPlayer and headless Compose state for video playback (video slice 6)
