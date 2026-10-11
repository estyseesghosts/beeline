# Objective

Complete `docs/03_infdev.md`: slices S1 to S6 (hashtag input, Photo Grid black tile investigation, edge-to-edge, drop shadows, automatic translation, Photo Grid tile fix).

# Invariants

- One commit per slice, with wiki, task state and handoff in the same commit.
- Architecture audit baseline is not edited. New strings go in all 15 `values*` directories.
- Device checks use `emulator-5554` only.

# Decisions

- Open decisions use the plan defaults.
- Owner request during work: the composer's floating buttons also get the shared drop shadow and a little blur. Done in S4 (add the composer to the S4-D1 inventory and the blur band).

# Completed

S1 `4b9871d6` hashtag input. S2 (this commit) Photo Grid black tile investigation: debug-only `MediaLoadDiagnostics`.

# Current slice

S2 Photo Grid black tiles: investigation (done in source, committed with this state). S3 is next.

# Files involved

`ui/chips/AddHashtagDialog.kt`, `domain/HashtagValidation.kt`, `ui/chips/FeedChipCatalog.kt`, `FileSavedHashtagRepository`, `ui/chips/FeedChipEntries.kt`.

# Verification

S1: focused tests, full CI-parity gate (tests, lint, ktlint, assembleDebug, assembleRelease) passed. The Python audit exits 1 on findings that predate this work.
S1 device (emulator-5554, dvd.chat, build debug): typing `#cats` in the dialog gave `cats` with no error. The existing `#video` chip shows one `#`. S1-D0 did not reproduce a "(tag)" display: the source never builds one. The add-then-chip check for old stored `#tag` values was not run on a device (unit tests cover it). Mastodon account not checked on a device. Photo Grid header shows no tag label; every tag label goes through `hashtagChipLabel`.

S2 (emulator-5554, debug build, light theme, 2026-10-11 about 17:25 to 17:30, `adb logcat -s MediaLoad`):

| Cause | Evidence | Result | Account | Tile type |
| --- | --- | --- | --- | --- |
| 1 Empty tile looks black | A failed tile shows the box color only. Pixel `#E7E7F1` in light theme, which is `surfaceContainerHigh`. Dark and Pure black would show `#1D1D1D`, not sampled | Confirmed (empty tile, no black bitmap) | mstdn.ca, dvd.chat | image |
| 2 Network failures stay empty | mstdn.ca: 63 HTTP 429 and 3 HTTP 404 on `mstdn.ca` preview requests (path without extension, the server media route) during 50 scroll swipes, against 200 successes from `cdn.mstdn.ca`. dvd.chat: 1 HTTP 500 on a `/proxy/static.webp` preview. Failed tiles stayed empty | Confirmed | mstdn.ca (429, 404), dvd.chat (500) | image preview |
| 2b No thumbnail falls back to the original | `thumbnailMissing=true` only on video frame requests on dvd.chat. No image failure had a missing thumbnail | Rejected for images | dvd.chat | video |
| 3 Video first frame | dvd.chat video frame requests finished in 1.2 to 2.1 s. The frame shows content (test pattern). No black frame and no retriever failure seen | Not reproduced | dvd.chat | video |
| 4 AVIF with hardware bitmaps | No AVIF in the sampled feeds. No `coil error` lines | Not reproduced | both | none |
| 5 Graphics memory | No `coil error` lines. `dumpsys meminfo` not read | Not reproduced | both | none |

Common property of the failing accounts (S2-D7): the failure is a per-server rate limit or error on the server media route, not an account property. The viewer's own server (mstdn.ca) throttles its media route with 429 when many previews load at once. It shows on posts from other servers because those previews go through the viewer's server. A 404 on the same route means the proxied preview is not available. The loader sends up to five parallel requests per host and never retries.
S6 starts with D1 (visible loading and failed states with retry) and D3 (one retry on 429, 5xx and timeout, with `Retry-After`). D2 (User-Agent), D4, D5, D6 and D7 have no evidence. D4 stays off: no timeout seen. A preview 404 can fall back to the original URL; S6 decides that with D3.
Not run: S2-D5 repeat with `allowHardware(false)` (no AVIF failure). Release build check: the release dex holds no `MediaLoadDiagnostics`, no `RedactingImageLogger` and no "coil error" string (R8 removes them).
Gate: tests, ktlint, assembleDebug and assembleRelease passed. The first lint run crashed inside lint (FIR error on unrelated test files); a rerun of `:app:lintDebug` passed.

# Next

S3.

# Blockers

None.

# Last safe commit

801044e6 Record the infdev 02 final state in the handoff
