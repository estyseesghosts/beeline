#!/usr/bin/env bash
# Builds the FFmpeg shared libraries that Beeline uses to encode WebM (VP9 + Opus).
#
# Output: tools/ffmpeg/prebuilt/arm64-v8a/*.so and tools/ffmpeg/prebuilt/include/.
# Target: arm64-v8a only, Android API 29. License: LGPL v2.1+ (no GPL, no nonfree, no libx264).
# libvpx and libopus are BSD licensed and are linked into libavcodec.so as static archives.
#
# Host: Git Bash on Windows or a POSIX shell on Linux or macOS, with curl, tar, make, sed, awk, perl.
# On Windows the NDK ships make.exe; see tools/ffmpeg/README.md for the space-free Git path it needs.
#
# Usage: tools/ffmpeg/build.sh [work-directory]
#   ANDROID_NDK_HOME  NDK root (r28c, 28.2.13676358, is the tested version). Default: newest under the SDK.
#   FFMPEG_JOBS       parallel jobs. Default 8.
set -euo pipefail

FFMPEG_VERSION=7.1.2
FFMPEG_SHA256=089bc60fb59d6aecc5d994ff530fd0dcb3ee39aa55867849a2bbc4e555f9c304
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz"
VPX_VERSION=1.15.0
VPX_SHA256=e935eded7d81631a538bfae703fd1e293aad1c7fd3407ba00440c95105d2011e
VPX_URL="https://github.com/webmproject/libvpx/archive/refs/tags/v${VPX_VERSION}.tar.gz"
OPUS_VERSION=1.5.2
OPUS_SHA256=65c1d2f78b9f2fb20082c38cbe47c951ad5839345876e46941612ee87f9a7ce1
OPUS_URL="https://downloads.xiph.org/releases/opus/opus-${OPUS_VERSION}.tar.gz"

API=29
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${1:-/c/ffb}"
JOBS="${FFMPEG_JOBS:-8}"
PREFIX="$WORK/prefix"
OUT="$HERE/prebuilt"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$LOCALAPPDATA/Android/Sdk" "$HOME/Android/Sdk"; do
    [ -d "$sdk/ndk" ] || continue
    ANDROID_NDK_HOME="$(ls -d "$sdk"/ndk/*/ | sort -V | tail -1)"
    break
  done
fi
[ -d "${ANDROID_NDK_HOME:-}" ] || { echo "Set ANDROID_NDK_HOME to an NDK" >&2; exit 1; }
NDK="$(cd "$ANDROID_NDK_HOME" && pwd)"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) HOST_TAG=windows-x86_64; HOST_EXE=.exe ;;
  Darwin) HOST_TAG=darwin-x86_64; HOST_EXE= ;;
  *) HOST_TAG=linux-x86_64; HOST_EXE= ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[ -d "$NDK/prebuilt/$HOST_TAG/bin" ] && export PATH="$NDK/prebuilt/$HOST_TAG/bin:$PATH"
# Windows make.exe resolves SHELL to a path with a space unless sh sits in a space-free directory.
[ -d /c/gitbin/usr/bin ] && export PATH="/c/gitbin/usr/bin:$PATH"
export PATH="$TC:$PATH"

CC="$TC/aarch64-linux-android${API}-clang"
CXX="$TC/aarch64-linux-android${API}-clang++"
AR="$TC/llvm-ar$HOST_EXE"
NM="$TC/llvm-nm$HOST_EXE"
RANLIB="$TC/llvm-ranlib$HOST_EXE"
STRIP="$TC/llvm-strip$HOST_EXE"

mkdir -p "$WORK/dl" "$WORK/src" "$WORK/obj" "$WORK/tmp" "$PREFIX"
# configure writes test scripts to TMPDIR; a backslashed Windows path breaks it.
export TMPDIR="$WORK/tmp"

fetch() { # url sha256 file
  local file="$WORK/dl/$3"
  [ -f "$file" ] || curl -fsSL -o "$file" "$1"
  echo "$2  $file" | sha256sum -c - >/dev/null || { echo "Checksum mismatch for $3" >&2; exit 1; }
}
unpack() { # file dir
  [ -d "$WORK/src/$2" ] || tar --force-local -xf "$WORK/dl/$1" -C "$WORK/src"
}

fetch "$FFMPEG_URL" "$FFMPEG_SHA256" "ffmpeg-$FFMPEG_VERSION.tar.xz"
fetch "$VPX_URL" "$VPX_SHA256" "libvpx-$VPX_VERSION.tar.gz"
fetch "$OPUS_URL" "$OPUS_SHA256" "opus-$OPUS_VERSION.tar.gz"
unpack "ffmpeg-$FFMPEG_VERSION.tar.xz" "ffmpeg-$FFMPEG_VERSION"
unpack "libvpx-$VPX_VERSION.tar.gz" "libvpx-$VPX_VERSION"
unpack "opus-$OPUS_VERSION.tar.gz" "opus-$OPUS_VERSION"

if [ ! -f "$PREFIX/lib/libopus.a" ]; then
  mkdir -p "$WORK/obj/opus" && cd "$WORK/obj/opus"
  CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 -fPIC" \
    "$WORK/src/opus-$OPUS_VERSION/configure" --host=aarch64-linux-android --prefix="$PREFIX" \
    --disable-shared --enable-static --disable-doc --disable-extra-programs --disable-intrinsics
  make -j"$JOBS" && make install
fi

if [ ! -f "$PREFIX/lib/libvpx.a" ]; then
  mkdir -p "$WORK/obj/vpx" && cd "$WORK/obj/vpx"
  CC="$CC" CXX="$CXX" LD="$CC" AS="$CC" AR="$AR" NM="$NM" STRIP="$STRIP" \
    "$WORK/src/libvpx-$VPX_VERSION/configure" --target=arm64-linux-gcc --prefix="$PREFIX" \
    --disable-examples --disable-tools --disable-docs --disable-unit-tests --disable-install-bins \
    --disable-vp8-decoder --disable-vp9-decoder --enable-vp8 --enable-vp9 --enable-pic \
    --enable-static --disable-shared --enable-realtime-only --extra-cflags="-O2"
  make -j"$JOBS" && make install
fi

# FFmpeg asks pkg-config about libopus and libvpx. The host has none, so answer for those two.
SHIM="$WORK/pkg-config-shim.sh"
cat > "$SHIM" <<'SHIMEOF'
#!/usr/bin/env bash
# Minimal pkg-config for the two static archives built by build.sh.
prefix="${BEELINE_FF_PREFIX}"
mode=""; pkg=""
for a in "$@"; do
  case "$a" in
    --exists|--cflags|--libs|--modversion) mode="$a" ;;
    --version) echo "0.29.2"; exit 0 ;;
    --static|--print-errors|--short-errors) ;;
    -*) ;;
    opus|libopus|vpx|libvpx) pkg="${a#lib}" ;;
  esac
done
[ -n "$pkg" ] || exit 1
case "$mode" in
  --exists) exit 0 ;;
  --cflags) [ "$pkg" = opus ] && echo "-I$prefix/include/opus" || echo "-I$prefix/include" ;;
  --libs) [ "$pkg" = opus ] && echo "-L$prefix/lib -lopus -lm" || echo "-L$prefix/lib -lvpx -lm" ;;
  --modversion) [ "$pkg" = opus ] && echo "1.5.2" || echo "1.15.0" ;;
esac
SHIMEOF
chmod +x "$SHIM"
export BEELINE_FF_PREFIX="$PREFIX"

# --host-cc is the cross compiler: FFmpeg builds no host tools here, and the host has no C compiler.
mkdir -p "$WORK/obj/ffmpeg" && cd "$WORK/obj/ffmpeg"
"$WORK/src/ffmpeg-$FFMPEG_VERSION/configure" \
  --prefix="$PREFIX/ffmpeg" --target-os=android --arch=aarch64 --cpu=armv8-a --enable-cross-compile \
  --cc="$CC" --cxx="$CXX" --host-cc="$CC" --ar="$AR" --nm="$NM" --ranlib="$RANLIB" --strip="$STRIP" --pkg-config="$SHIM" \
  --disable-everything --disable-programs --disable-doc --disable-debug --disable-network \
  --disable-autodetect --disable-avdevice --disable-postproc --disable-symver \
  --enable-shared --disable-static --enable-pic --disable-gpl --disable-nonfree \
  --enable-libvpx --enable-libopus \
  --enable-decoder=h264,hevc,vp8,vp9,mpeg4,aac,aac_latm,mp3,mp3float,opus,vorbis,flac,pcm_s16le,pcm_u8 \
  --enable-parser=h264,hevc,vp8,vp9,mpeg4video,aac,aac_latm,mpegaudio,opus,vorbis,flac \
  --enable-demuxer=mov,matroska,avi,mpegts,mp3,aac,flac \
  --enable-muxer=webm,null \
  --enable-encoder=libvpx_vp9,libopus \
  --enable-protocol=file \
  --enable-bsf=vp9_superframe,vp9_superframe_split,h264_mp4toannexb,hevc_mp4toannexb,extract_extradata \
  --enable-filter=buffer,buffersink,abuffer,abuffersink,scale,transpose,hflip,vflip,format,fps,aresample,aformat,anull,null \
  --extra-cflags="-I$PREFIX/include -O2 -fPIC" \
  --extra-ldflags="-L$PREFIX/lib -Wl,-z,max-page-size=16384"

# Native Windows make.exe cannot read the POSIX-style source path that configure recorded.
if command -v cygpath >/dev/null 2>&1; then
  FF_SRC="$WORK/src/ffmpeg-$FFMPEG_VERSION"
  sed -i "s|$FF_SRC|$(cygpath -m "$FF_SRC")|g" Makefile ffbuild/config.mak
fi

# Android loads libraries by file name, so drop the version suffix FFmpeg adds to shared libraries.
make -j"$JOBS" SLIBNAME_WITH_MAJOR='$(SLIBNAME)' SLIB_INSTALL_NAME='$(SLIBNAME)' SLIB_INSTALL_LINKS=''
make install SLIBNAME_WITH_MAJOR='$(SLIBNAME)' SLIB_INSTALL_NAME='$(SLIBNAME)' SLIB_INSTALL_LINKS=''
make install-headers
for lib in avcodec avformat avutil avfilter swresample swscale; do
  ls "$PREFIX/ffmpeg/include/lib$lib/"*.h >/dev/null 2>&1 || { echo "No headers installed for lib$lib" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/arm64-v8a" "$OUT/include"
for lib in avcodec avformat avutil avfilter swresample swscale; do
  cp "$PREFIX/ffmpeg/lib/lib$lib.so" "$OUT/arm64-v8a/lib$lib.so"
  "$STRIP" --strip-unneeded "$OUT/arm64-v8a/lib$lib.so"
  cp -r "$PREFIX/ffmpeg/include/lib$lib" "$OUT/include/"
done
echo "$FFMPEG_VERSION+vpx$VPX_VERSION+opus$OPUS_VERSION" > "$OUT/BUILD_ID"
ls -l "$OUT/arm64-v8a"
