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

S1 hashtag input (commit follows `801044e6`, subject "Filter # from the hashtag field and store tags without it (infdev 03 S1)").

# Current slice

S2 Photo Grid black tiles: investigation.

# Files involved

`ui/chips/AddHashtagDialog.kt`, `domain/HashtagValidation.kt`, `ui/chips/FeedChipCatalog.kt`, `FileSavedHashtagRepository`, `ui/chips/FeedChipEntries.kt`.

# Verification

S1: focused tests, full CI-parity gate (tests, lint, ktlint, assembleDebug, assembleRelease) passed. The Python audit exits 1 on findings that predate this work.
S1 device (emulator-5554, dvd.chat, build debug): typing `#cats` in the dialog gave `cats` with no error. The existing `#video` chip shows one `#`. S1-D0 did not reproduce a "(tag)" display: the source never builds one. The add-then-chip check for old stored `#tag` values was not run on a device (unit tests cover it). Mastodon account not checked on a device. Photo Grid header shows no tag label; every tag label goes through `hashtagChipLabel`.

# Next

S2.

# Blockers

None.

# Last safe commit

801044e6 Record the infdev 02 final state in the handoff
