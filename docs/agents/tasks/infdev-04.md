# Objective

Complete `docs/04_infdev.md`: S1 blur band, S2 shadows and opaque pill, S3 edge veil, S4 composer attachment menu.

# Invariants

- One commit per slice, with wiki, task state and handoff in the same commit.
- The architecture audit baseline is not edited. No new audit regression.
- Open decisions use the plan defaults. Device checks use `emulator-5554` only.

# Decisions

- The owner asked for the plan to be finished to completion, with lint and ktlint passing.
- A band that does not rest on a screen edge also fades out past the node (S1, to avoid a hard edge on lifted docks).

# Completed

None committed yet.

# Current slice

S1: blur band geometry, mirror tile mode, one effect per band, Home backdrop only with a dock.

# Files involved

`ui/components/ChromeBandGeometry.kt`, `ui/components/FloatingChromeBlur.kt`, `ui/components/FloatingChromeBackdrop.kt`, `ui/feed/HomeFeed.kt`, `ChromeBandGeometryTest.kt`, `docs/wiki/ui-and-navigation.md`.

# Verification

S1 device (emulator-5554, debug, light theme, compact-wide Home): the blur is full strength behind the chip row and fades above it; the image under the chips is blurred with no sharp copy. S1-D7: the 63 px strip is the launcher Taskbar (`dumpsys window`: Taskbar owns the `navigationBars` inset at `[0,1785][1169,1848]`; the Beeline window frame is `[0,0][1169,1848]`).
Not run: S1-D0 and the repeat recording (the emulator Taskbar hides the bottom edge, and the SM-G986W is not for testing), API 29 and 30 emulator.

# Next

Run the CI-parity gate and commit S1, then S2.

# Blockers

None.

# Last safe commit

ec0b232a Show loading and failed Photo Grid tiles and retry media once (infdev 03 S6)
