# FFmpeg for WebM upload

Status: current  
Owner: Maintainers  
Last reviewed: 2026-10-10  
Stale when: `build.sh` changes a pinned version, a configure flag, or the output set.

Beeline encodes videos as WebM (VP9 video, Opus audio) before upload when the server and the device allow it.
This directory builds the FFmpeg libraries for that. [docs/video.md](../../docs/video.md) section 6 is the design.

## What is built

| Component | Version | License |
|---|---|---|
| FFmpeg (libavcodec, libavformat, libavfilter, libavutil, libswresample, libswscale) | 7.1.2 | LGPL 2.1 or later |
| libvpx (VP9 encoder, linked into libavcodec.so) | 1.15.0 | BSD, WebM patent grant |
| libopus (Opus encoder, linked into libavcodec.so) | 1.5.2 | BSD |

- ABI: `arm64-v8a` only. Android API 29. 16 KB page alignment.
- FFmpeg configure flags: `--disable-gpl --disable-nonfree --enable-shared --disable-static`. It does not use libx264 or any GPL library.
- Decoders: h264, hevc, vp8, vp9, mpeg4, aac, mp3, opus, vorbis, flac, pcm. Demuxers: mov (MP4), matroska (WebM), avi, mpegts, mp3, aac, flac.
- Encoders: libvpx-vp9 and libopus. Muxers: webm and null.
- AV1 is not decoded. FFmpeg's own AV1 decoder needs a hardware path. Add dav1d (BSD) to change this.

## Outputs

`build.sh` writes `prebuilt/arm64-v8a/*.so`, `prebuilt/include/`, and `prebuilt/BUILD_ID`. The repository commits them,
so a normal Gradle build does not need this script. `app/src/main/cpp/CMakeLists.txt` links the app's JNI library
against them. Other ABIs compile the JNI library as a stub, and WebM is unavailable there.

Stripped size on arm64-v8a, 2026-10-10:

| Library | Bytes |
|---|---|
| libavcodec.so | 5,261,176 |
| libswscale.so | 758,456 |
| libavutil.so | 718,824 |
| libavformat.so | 565,000 |
| libavfilter.so | 191,840 |
| libswresample.so | 98,192 |
| Total | about 7.3 MiB |

## Rebuild

Requirements: the Android NDK (r28c, `28.2.13676358`, is the tested version), curl, tar, make, sed, perl, and a POSIX shell.

```text
export ANDROID_NDK_HOME=<sdk>/ndk/28.2.13676358
tools/ffmpeg/build.sh <short-work-directory>
```

The script downloads each source archive, checks its SHA-256, and stops on a mismatch.
The build takes a long time because `configure` is slow on Windows.

Windows notes:

- Run the script from Git Bash. Use a short work directory such as `/c/ffb`. Long paths break the build.
- The NDK's `make.exe` cannot run a recipe when `sh.exe` sits in a path with a space. Create a junction without a space:
  `mklink /J C:\gitbin "C:\Program Files\Git"`. The script puts `/c/gitbin/usr/bin` first on PATH when it exists.
- The host has no C compiler, so the script passes the cross compiler as `--host-cc`. FFmpeg builds no host tools in this configuration.
- The script writes a small `pkg-config` replacement for libvpx and libopus. The host has no pkg-config.

## LGPL conditions

- FFmpeg ships as separate shared libraries, unmodified. The application package can take rebuilt replacements.
- `NOTICE` and `licenses/` carry the license texts. `build.sh` is the source offer: it names each pinned source and its checksum.
- Update `NOTICE` and `docs/wiki` when a version changes.
