# Objective

Complete `docs/02_infdev.md`: slices S1 to S10 (search field width, outline colors, hashtag bubble, inline bubble height, composer drafts, post reveal, search tabs, floating chrome depth, mention data, mention bubbles).

# Invariants

- One commit per slice, with wiki, task state and handoff in the same commit.
- Architecture audit baseline is not edited. New strings go in all 15 `values*` directories.

# Decisions

- Open decisions use the plan defaults: first Photo Grid tap reveals in place; mention tap opens a resolved profile; chip tap keeps the query.

# Completed

S1 `9cf0f475` search field width. S2 `91da7ed1` outline colors. S3 `4df1b6a0` hashtag bubble. S4 `ac6179c6` inline bubble height. S5 `372d47fd` composer drafts. S6 `f7d3246b` post reveal owner. S7 `37bc4362` Search tabs. S8a gradient (this commit).

# Current slice

S8a is committed. Next is S8b (blur on API 31+).

# Files involved

See the plan. S7: `ui/search/SearchTabs.kt`, `SearchPane.kt`, `SearchPanes.kt`, `SearchState.kt`, `ui/components/HashtagPill.kt`, `ShellNavigator.kt` (`searchTabName`), the Search shell adapters, 15 `strings.xml`.

# Verification

Focused tests per slice plus `:app:ktlintCheck`. The full CI-parity gate has not run yet. No device checks yet.

# Next

S8b, S9, S10, then the full gate and the testsigned release build.

# Blockers

None.

# Last safe commit

dc61cfb1 Redesign the composer as floating bubbles over scrolling cards (composer redesign 2)
