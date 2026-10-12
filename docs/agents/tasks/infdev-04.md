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

S1 `5ef55e75` blur band.

# Current slice

S2: `ExpandableContent` no longer clips at rest, opaque navigation pill, chip row collapse test.

# Files involved

`ui/motion/MotionContent.kt`, `ui/navigation/NavigationPresentation.kt`, `ChipRowCollapseTest.kt`, `docs/wiki/ui-and-navigation.md`.

# Verification

S1 gate passed (tests, lint, ktlint, assembleDebug, assembleRelease); audit regressions identical to the base commit.
S2 device (emulator-5554, compact-wide Home): chip shadows are soft with no straight cut; the pill is flat. Callers of `ExpandableContent`: chip row, composer warning field, notifications sync notice, post body behind a warning. None needs a clip at rest. Other parents were not found to clip. Not checked: 600/840 dp, font scale 2.0, dark themes, Search, Photo Grid, Notifications, Profile.

S1 device (emulator-5554, debug, light theme, compact-wide Home): the blur is full strength behind the chip row and fades above it; the image under the chips is blurred with no sharp copy. S1-D7: the 63 px strip is the launcher Taskbar (`dumpsys window`: Taskbar owns the `navigationBars` inset at `[0,1785][1169,1848]`; the Beeline window frame is `[0,0][1169,1848]`).
Not run: S1-D0 and the repeat recording (the emulator Taskbar hides the bottom edge, and the SM-G986W is not for testing), API 29 and 30 emulator.

# Next

Run the gate and commit S2, then S3.

# Blockers

None.

# Last safe commit

5ef55e75 Make the blur band full strength under the controls and fix its edges (infdev 04 S1)
