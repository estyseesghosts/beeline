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

S1 `4b9871d6` hashtag input. S2 `f5e477ce` Photo Grid black tile investigation: debug-only `MediaLoadDiagnostics`. S3 edge to edge (this commit).

# Current slice

S3 edge to edge (this commit). S4 is next.

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

S3 audit of bottom-anchored controls (S3-D10). "Inset" means the bottom navigation inset.

| Surface | Owner of the inset | Result |
| --- | --- | --- |
| Compact pill and chip rows (`ShellContent`) | `compactGlobalNavigationPositioningInsets`, band before the padding | Unchanged, correct |
| Compact Photo Grid and Search stack | `compactContextualControlsPositioningInsets` | Band now before the padding (`floatingChromeDock`), reaches the edge |
| Compact Notifications and Profile chip rows | same insets, no band | Unchanged. No band, so S4 only adds the shadow |
| Large docks (Home, Search, Photo Grid, Notifications, Profile) | `LocalLargeBottomInset` in `LargeBottomDock` | Fixed: the band reaches the edge, controls above the bar |
| Large list ends (Home, Photo Grid, Notifications, Profile, Search, DM inbox) | `LocalLargeBottomInset` added to the end clearance | Fixed |
| Compact fallback bar on a wide layout | `compactFallbackClearance` (now the whole inset) and inset zero for panes | Fixed, no double count |
| Floating navigation stack | `calculateNavigationFit` (reads `systemBars`) | Unchanged, correct |
| Post detail (`SinglePostScreen`) | `detailScrollEndClearance()` | Fixed (was a fixed 96 dp) |
| Saved posts | own `navigationBars` padding | Correct. It counted the inset twice before, because the large region was padded |
| DM conversation editor | `imePadding` then navigation inset | Correct: Compose consumes the IME inset, so the sum does not double count. Not device verified |
| Composer body and dialog | `navigationBarsPadding().imePadding()`, `DialogSystemBars` | Bar now transparent over the surface. Not changed otherwise |
| Sheets and small dialogs (`ModalBottomSheet`, `AlertDialog`) | Material 3 defaults | Not changed. Not checked on a device |
| Setup, sign-in, connecting | `SystemBars` in `ConnectedApp`; `SetupScreens` pad the bars | Bar and window now match the theme |

Gate: tests, lint, ktlint, assembleDebug and assembleRelease passed on the rerun. The first run failed on one import order (fixed) and one timing flake in `MisskeyApiTest.cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure` (passes alone).
Not run: transparent bars on a phone, API 29 emulator (no system image installed), landscape phone and 600/840 dp tablets on AVDs (the 240 dpi override of emulator-5554 gave a compact-wide layout), font scale 2.0, Dark and Pure black with system light mode. The "navigation bar" strip on emulator-5554 is drawn by the launcher Taskbar.

# Next

S4.

# Blockers

None.

# Last safe commit

801044e6 Record the infdev 02 final state in the handoff
