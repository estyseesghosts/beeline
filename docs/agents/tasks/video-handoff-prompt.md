# Handoff prompt: finish video (`docs/video.md`)

Paste this into the next session. Written 2026-10-10 against `main` at `2340a592`.

---

You are taking over the video work for Beeline. Read these before you touch code, in this order:

1. `AGENTS.md`, then `docs/agents/workflow.md`, `docs/agents/agent-control.md`, `docs/agents/engineering-rules.md`, `docs/agents/operation-rules.md`.
2. `docs/video.md`. This is the design. Revision 2. Follow it unless the source disagrees; if it does, stop and report.
3. `docs/agents/tasks/video.md`. This is the task tracker. It is out of date: it does not list the slice 8 work described below.
4. `docs/agents/handoff.md`.

## State

Committed on `main`:

- Slice 6 (Media3 deps): `164621d6`
- Slice 2 (setting, `MeteredNetworkMonitor`, `AutoplayPolicy`): `6de35511`
- Slice 3 (feed autoplay): `8e635e18`
- Slice 4 (photo grid): `099ea871`
- Slice 5 (viewer): `2340a592`

Uncommitted, slice 8 (upload). Do not discard any of it. Review it, then finish and commit it:

- `app/build.gradle.kts`: adds `media3-transformer:1.11.1`.
- `data/media/VideoPreparer.kt`, `VideoTranscoding.kt`, `AndroidVideoSupport.kt`, `DeviceVideoCapabilities.kt`.
- `domain/VideoUploadPolicy.kt`.
- `di/VideoModule.kt`.
- Tests: `VideoPreparerTest`, `DeviceVideoCapabilities` test, `VideoUploadPolicyTest`, and `androidTest/.../VideoTranscodeInstrumentedTest.kt`.

Not verified: whether these compile and pass. Run the focused tests first.

Not started:

- Slice 7 (FFmpeg). No `tools/ffmpeg`, no native source, no JNI bridge, no `abiFilters`.
- Slice 9 (docs and licensing).
- Composer video attachments. Nothing in `ui/composer` references video.

## Remaining work, in order

**A. Finish and commit slice 8 (H.264 MP4 path first).**

1. Review the uncommitted files against `docs/video.md` section 6.
   - Upload order: original under 2 MiB (exclusive, so 2 MiB itself is transcoded), then WebM, then H.264 MP4.
   - Server gate: Mastodon uses `supported_mime_types`. Misskey tries WebM and falls back on `UNALLOWED_FILE_TYPE`.
2. Run the focused unit tests for `VideoPreparer`, `DeviceVideoCapabilities`, and `VideoUploadPolicy`.
3. Get the H.264 path working end to end with the Transformer code. Use the Media3 transcoder that `VideoModule` wires.
4. Wire the composer. Video attachments need picker support, a poster or thumbnail, and the progress UI. Mastodon async media polling is in the composer plan. Find that plan under `docs/agents/tasks/` (the composer prompts) before writing it, and reuse its existing upload path rather than adding a second one.
5. Tests: the selection table, the fallback on `UNALLOWED_FILE_TYPE`, the Mastodon fallback to H.264 when WebM is not listed, and the benchmark cache key.
6. Commit when verified. Update `docs/agents/tasks/video.md` with the slice 8 entry. Commit only the reviewed files.

**B. Slice 7 (FFmpeg). Needs an arm64 device and the NDK.**

The x86_64 emulator cannot load FFmpeg or run WebM. Do not claim WebM works on the emulator.

1. Add `tools/ffmpeg/build.sh`. It builds a pinned FFmpeg source with the NDK, `arm64-v8a` only.
2. Flags: `--disable-gpl --disable-nonfree`, `--enable-shared`. Do not enable `libx264`. Use `libvpx` (VP9) and `libopus`.
3. Enable only the needed components: decoders, the mp4, webm, and matroska demuxers and muxers, and the libvpx and libopus encoders.
4. JNI in `ffmpeg_jni.cpp`: `probe`, `transcodeWebm` (with progress and cancel), `benchmarkVp9`.
5. Ship the license texts and a `NOTICE`. Commit the build script and the pinned version. Record the `.so` size.
6. Check the existing `abiFilters` and native libs. Adding FFmpeg makes the app arm64-only unless other native code already limits it.
7. Smoke test on an arm64 device.

**C. WebM path (slice 8, second half).** Depends on B. The benchmark needs 1.5× realtime at 720p. Cache the result, keyed by device model, SDK level, and FFmpeg build id.

**D. Slice 9 (docs).** Update `ui-and-navigation.md`, `server-compatibility.md`, the licensing notes, and the changelog.

**E. Verification debt from slices 3–5.** Record these in the tracker. Do not claim them done without a device run.

- Duration badge on video tiles (`Attachment` has no duration).
- Retry control on a failed video tile.
- Pinch zoom on video, the open and close reveal frame by frame, audio output, and a non-zero start position handed off from a feed tile.

## Open decisions

- Default max quality tier: 720p proposed. Confirm with the user before it becomes a constant.
- Whether live Mastodon servers list `video/webm`. Unknown.

## Rules

- Use the Gradle wrapper with `--no-daemon --console=plain`, an explicit timeout, and closed stdin.
- For code changes, run focused tests, then the full local CI-parity gate in `docs/agents/engineering-rules.md` before calling a slice done.
- The architecture audit exits 1 on `main` already. The slice 5 notes record a `MediaPage` function-complexity regression. Report both, do not hide them.
- Do not push unless the user asks.
- Only the project's own subagents. One implementation owner per slice.
- Commit messages: follow the repo's existing style, e.g. `Upload videos as H.264 MP4 (video slice 8)`.

## Report back

Give the slice, the commit hash, what was verified and how, and what is still blocked. Name any device or live-server check that was not run.
