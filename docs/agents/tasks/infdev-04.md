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

S1 `5ef55e75` blur band. S2 `b71b5daa` shadows and opaque pill. S3 `bd5f8f9d` edge veil.

# Current slice

S4: composer attachment menu, `AnchoredChoiceBubble` scaffold, `PillAction` disabled look.

# Files involved

`ui/posts/AnchoredChoiceBubble.kt`, `ui/composer/ComposerAttachMenu.kt`, `ComposerBody.kt`, `FollowChoice.kt`, `PostRepostChoice.kt`, `PillAction.kt`, strings in 15 locales, tests, wiki.

# Verification

S3 gate passed. S4 device (emulator-5554, Mastodon test account): row 1 holds four controls; the menu pops out above the paperclip in the Repost style; Add content warning shows the field and the circle takes the selected tone. Not checked: the keyboard with the menu open (no soft keyboard on the emulator, S4-D8 open), TalkBack, Misskey poll row on a device, 320 dp on a device. Unit tests cover rows, rules, edit mode, one-run choice, sheet at font scale 1.5, tone, and the width tests.

S2 gate passed. S3 device (emulator-5554, compact-wide Home): the image under the chips fades into the background color toward the bottom edge; chips keep their contrast. Not checked: dark themes and Pure black, SM-G986W scroll shimmer (S3-D1 recording not run), API 29/30. Composer bands get no veil because they do not rest on the screen edge.

S1 gate passed (tests, lint, ktlint, assembleDebug, assembleRelease); audit regressions identical to the base commit.
S2 device (emulator-5554, compact-wide Home): chip shadows are soft with no straight cut; the pill is flat. Callers of `ExpandableContent`: chip row, composer warning field, notifications sync notice, post body behind a warning. None needs a clip at rest. Other parents were not found to clip. Not checked: 600/840 dp, font scale 2.0, dark themes, Search, Photo Grid, Notifications, Profile.

S1 device (emulator-5554, debug, light theme, compact-wide Home): the blur is full strength behind the chip row and fades above it; the image under the chips is blurred with no sharp copy. S1-D7: the 63 px strip is the launcher Taskbar (`dumpsys window`: Taskbar owns the `navigationBars` inset at `[0,1785][1169,1848]`; the Beeline window frame is `[0,0][1169,1848]`).
Not run: S1-D0 and the repeat recording (the emulator Taskbar hides the bottom edge, and the SM-G986W is not for testing), API 29 and 30 emulator.

# Next

Run the gate, commit S4, then build the testsigned release APK the owner asked for.

# Blockers

None.

# Last safe commit

bd5f8f9d Fade the blur band into the background color toward the screen edge (infdev 04 S3)
