# Objective

Implement `docs/video.md`: video autoplay in the feed and photo grid, a video viewer page, and WebM/H.264 upload.

# Invariants

- One autoplay owner at a time. At most two ExoPlayers: one feed or grid slot, one viewer slot.
- Metered or unknown networks never autoplay. Reduced motion turns autoplay off.
- TextureView for tiles and the viewer. No `media3-ui`, no Material components.
- FFmpeg stays LGPL, shared libraries, arm64-v8a only.
- The upload tier is 900p. It limits the shorter edge, so a portrait 1080x1920 clip becomes 900x1600.

# Decisions

- Slice order: 6, 2, 3, 4, 5, 8, 7, 9. Slice 1 (spike) is measured notes only and runs on the emulator, which is x86_64.
- `AutoplayPolicy` is stateless; the caller passes the current key back for hysteresis.
- `MeteredNetworkMonitor.metered` is a cold flow. The network callback lives while collected.
- The user chose 900p over the proposed 720p on 2026-10-10. `VideoUploadPolicy.MAX_SHORT_SIDE` holds it and `heightCap` turns it into an output height.
- The JNI library builds for all four ABIs. Only arm64-v8a links FFmpeg. Other ABIs get a stub, so the x86_64 emulator still installs the app and WebM is unavailable there. The app already ships native libraries for every ABI through `avif-coder`.
- FFmpeg libraries and headers are committed under `tools/ffmpeg/prebuilt/`, so a normal build does not need the NDK toolchain script. `tools/ffmpeg/build.sh` rebuilds them.
- A video draft keeps its encrypted copy up to 256 MiB. The poster is the first frame, decoded on demand from a decrypted temporary file.
- A server that refuses a WebM upload with `Unsupported` is recorded in `WebmRejections` by origin (process lifetime). The publisher prepares the attachment again and uploads once more as MP4.
- Capability schema version is 8. `PostingCapabilities.maxVideoBytes` comes from Mastodon `video_size_limit` (default 99 MiB) and from the Misskey file size limit. `acceptsVideo` decides whether the picker offers videos.

# Completed

- Slice 6, Media3 dependencies: `164621d6`.
- Slice 2, setting, network monitor, policy: `6de35511`.
- Slice 3, feed autoplay: `8e635e18`.
- Slice 4, photo grid: `099ea871`.
- Slice 5, viewer: `2340a592`.
- Slice 8, H.264 MP4 upload path first: `ed49c4cc`.

# Current slice

Slices 7, 8 second half, and 9 are written, gated on the host, and the FFmpeg and H.264 instrumented tests pass on the SM-G986W (Android 13). The composer UI and a live-server upload are still unverified. The debug build is installed on the phone's main profile for a manual run.

Written, uncommitted:

- Slice 7: `tools/ffmpeg/` (build script, README, NOTICE, licenses, prebuilt arm64 libraries and headers), `app/src/main/cpp/` (`ffmpeg_jni.cpp`, `CMakeLists.txt`), `ndkVersion` and `externalNativeBuild` in `app/build.gradle.kts`, `data/media/FfmpegBridge.kt`, `FfmpegBridgeTest`, `FfmpegBridgeInstrumentedTest` with two fixture clips in `androidTest/assets`.
- Slice 8 second half: 900p tier, WebM wiring in `VideoModule`, composer video attachments (`DraftMediaImporter`, `DraftThreadImagePreparer`, `ThreadPublisher`, `ComposerMediaControls`, `ComposerMediaStrip`, conversion progress through `FeedState`, `ComposerContract`, and the top bar), `maxVideoBytes`, tests.
- Slice 9: wiki pages and changelog. See Next.

# Verification

- Host gate on 2026-10-10: `python -m unittest discover -s tools/tests` passes. `:app:testDebugUnitTest` passes with 2165 tests, 0 failures. `lintDebug`, `ktlintCheck`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` pass.
- The architecture audit exits 1. Its eight regressions predate this work and none are in files this work changed (`MediaPage` and others). Do not edit the baseline.
- FFmpeg arm64 build: AArch64, LGPL 2.1 or later, no versioned sonames, 16 KB aligned. Stripped size is about 7.3 MiB (`libavcodec.so` 5.26 MB).
- The filter chain and encoder options were checked with the desktop `ffmpeg` command (rotation, 900p, CFR 30, VP9 realtime, Opus). That is not the JNI code.
- Earlier slices: slice 3 feed autoplay and slice 4 grid ran on the emulator. Slice 5 viewer ran on the emulator.

- Device run on 2026-10-10 (SM-G986W, arm64): `FfmpegBridgeInstrumentedTest` 6 of 6 pass (WebM with VP9 and Opus, rotated clip upright, height cap, cancel removes the output, bad input fails cleanly, benchmark). The VP9 900p benchmark measured 2.32 times realtime, above the 1.5 threshold. `VideoTranscodeInstrumentedTest` 2 of 2 pass; the Samsung encoder returns 128 for a 120 cap (16 pixel alignment), so the test accepts 120 to 128.

# Not verified

- WebM playback of an encoded file, and WebM upload end to end.
- Composer video attach, poster, progress text, and publish on a device.
- Live-server behavior: whether Mastodon servers list `video/webm`, and Misskey `UNALLOWED_FILE_TYPE` handling.
- Decrypting a large draft video (GCM may buffer the whole file).
- Slices 3 to 5 debt: duration badge on video tiles (`Attachment` has no duration), retry control on a failed tile, pinch zoom on video, the reveal animation frame by frame, audio output, a non-zero start position from a feed tile.
- Mastodon refuses a post that mixes a video with images. The composer does not prevent it.

# Device run 2026-10-10 (SM-G986W, Misskey dvd.chat demo account)

- Publish works end to end: a 20 s 1080p clip became a post with an autoplaying video. The earlier hang came from decrypting a 25 MB draft through one AES-GCM message (heap thrash). Drafts now use chunked AEAD, and an old large draft fails fast.
- Open transition: the viewer open frame used a 4:3 fallback when the server sent no video size, then snapped to the real frame. The coordinator now remembers decoded sizes by URL and the viewer uses them. Verified frame by frame for a video that had played in the feed. A video that never played and has no server size still falls back to 4:3.
- Added: duration badge and retry control on tiles (unit tested only on host).
- The user confirmed on the phone: pinch zoom on video, audio output, and the start position handed off from a feed tile.
- Still unverified: Mastodon account upload (the user is testing it) and the retry control on a failed tile.

# Next

1. Sign in on the phone and attach a real video in the composer. Check the poster, progress text, and a live upload.
2. Record the result here and in `docs/agents/handoff.md`.

# Blockers

- A signed-in account on the phone for the live composer run.

# Last safe commit

`ed49c4cc` (slice 8, "Upload videos as H.264 MP4 (video slice 8)").
