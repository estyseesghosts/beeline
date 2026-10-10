# Next-agent prompt — Finish Beeline 0.4.0 Phase 4D and 4E (universal tabs, measured clearance)

This is a self-contained dispatch. Follow it in order. The authoritative plan is
`docs/beeline_0.4.0.md`, sections "4D" and "4E" and the packet table (4D1-4D5, 4E1-4E2).
Source and Git outrank every plan, record, and completion claim.

## Read first

`AGENTS.md` and every page it requires, then `docs/agents/tasks/beeline-0.4.0.md` (4C record),
`docs/agents/tasks/4c.md` (untracked 4C dispatch, source of the gates), `docs/agents/handoff.md`,
`docs/agents/beeline-0.4.0-ui-baseline.md` (approved emulator values, section 6),
`docs/agents/app-shell-ownership.md`, and `docs/wiki/ui-and-navigation.md`.
Use the `finder` and `implementer` subagents as AGENTS.md allows. Keep prompts to them short and specific.

## Where the work stands (verified from Git and records on 2026-10-07)

- `main` is at `51455c5` (Phase 5 documentation and acceptance). It is 7 commits ahead of
  `origin/main` (`2cc4d7a`). The 4C record says the user authorized pushing `main` once, for the
  Profile commit. Treat that as spent. Do not push without a new request.
- Hardening plan Phases 0-5 are complete. They changed no navigation behavior. The full gate
  last passed on this tree: 52 Python tests, 1,692 JVM tests in 161 suites, lint, ktlint, both assemblies.
- **Phase 4C is complete and committed.** Slices: 4C-1 geometry characterization, 4C-2 DM recipient
  finder, 4C-3 shared vertical presentation, 4C-4 per-destination obstruction clearance
  (`9717c49`..`8c5d879`), 4C-5a fit policy (`6dcfac4`), 4C-5b safe floating activation (`79dd743`;
  absorbs the old 4C-5d), 4C-5c anchors (`b419be1`), shared chip rows (`f7dc516`), compact-wide
  Profile one-column layout (`c62e4e2`), compact-wide bottom anchoring with a contextual tab caret
  (`a04b3eb`).
- **Phase 4D is largely implemented under 4C-5c, but never closed as 4D.** `CategoryChips.kt` now
  renders one shared chip row used by Home, Search, Photo Grid, Notifications, and Profile, with a
  48 dp circular caret outside the scrolling row, per-screen saveable collapse state, and Home state
  spanning compact and wide. Feature owners keep selection. This covers the intent of 4D1-4D4.
  The 4D plan items still open or unproven are listed below.
- **Phase 4E has not started.** The wiki and shell docs still say "Measured high-font dock
  clearance remains Phase 4E work."
- Phase 3 (visual system, 200% text repairs) is complete, so 4E gates are met.

## Known gaps to resolve (investigate in source first, then slice)

1. **Caret position versus plan.** The plan says a fixed *physical-left* caret. Source places it in the
   *first logical position*, which moves right in RTL. Decide with the user or the approved baseline
   (`ui-baseline.md`: "physical-left caret (RTL blocked)") whether to change it. Do not guess.
2. **Edge-to-edge travel (4D5).** The plan wants the chip row to reach both physical display
   edges, including in wide mode, with bottom-right controls never hiding a chip. The wiki says
   "Clearance does not set chip travel", but this has not been verified as full-display travel.
   Check each destination, wide Home, Search, Photo Grid, Notifications, Profile, in LTR and RTL.
3. **Stable tab IDs.** The plan replaces `${role}:${label}` keys with stable IDs where translations
   can collide. Grep for the current key construction and decide if it is still open.
4. **4E1 numeric clearance.** The baseline records the compact navigation capsule 136/147 px under
   the IME frame, which fails the approved rule that navigation stays visible with the IME open.
   Source now uses the greater of IME and system-bar insets. Verify that fix with measured
   bounds, then derive the numeric targets the baseline marks "pending 4E1". Final-item clearance
   must stay inside scroll content, never as full-viewport padding.
5. **4E2.** Reconcile full-display chip travel with the right-side six-target capsule at
   `fontScale = 2f` in wide layouts.
6. Re-capture the six font200 screens on device (`SinglePostScreenTest` is the Compose precedent).

## Suggested slicing (one behavior per commit, one owner per slice)

Start with an audit slice that changes no code: map each gap above to "done", "open", or "needs
user decision", and write the result into `docs/agents/tasks/beeline-0.4.0.md` (rewrite state, do not append).
Then in order: 4D5 integration fixes (only gaps 1-3 that the audit proves open), 4E1, 4E2.
State `This task is larger than one safe implementation slice.` and record a slice plan before
editing. Commit and gate each slice before the next.

## Constraints

- Keep one navigator, one destination host, and the existing feature owners. No new global chip
  manager, no chip-visibility preference, no feature-selection moves, no replacement architecture.
- No protocol, persistence, or account-scope changes. No dependency additions.
- Physical edges never follow layout direction. Do not weaken or replace tests.
- Do not add ktlint baseline exemptions. The six deferred entries stay (`SavedCollectionsHost`,
  `ConnectedApp`, `PostThreadViewModel`, `MastodonIntegrationTest`, `NavigationTest`,
  `SettingsViewModelTest`).
- Stop after two failed fixes for one root problem. Stop for unapproved geometry values.

## Verification

Per slice: smallest focused tests, then `:app:lintDebug`, then the full CI-parity gate from
`docs/agents/engineering-rules.md#verification` (Gradle wrapper, `--no-daemon --console=plain`,
explicit timeout, closed stdin; `python -m unittest discover -s tools/tests` and the architecture
audit with the baseline). Known flakes: `MastodonIntegrationTest` cancellation, `MisskeyApiTest`
cancellation, `NotificationsViewModelTest` isolation. They pass alone; record in `logs/BUGS.txt`
and rerun. Add Compose tests at `fontScale = 2f` with bounds and reachable-action assertions,
IME open and closed, RTL, and reduced motion.

Device work (you run ADB; subagents do not): `emulator-5554` (API 37, 1169 x 1848). Raw ADB:
`C:\Users\julie\Documents\platform-tools\adb.exe`. Helpers: `tools/scripts/adb_inspect.py`,
`adb_flow.py`, `adb_control.py`. `adb_inspect.py --out` writes a directory containing
`screenshot.png`. Compact-wide (445 x 704 dp) and tablet need `wm size` and `wm density`
overrides, reset afterward. Judge crashes from `FATAL EXCEPTION` with the app package, not the
emulator HAL SIGABRT. Never capture credentials.

## Working tree

Work on `main` (the user does not want worktrees; `.claude/settings.json` sets
`worktree.bgIsolation` to `none`). Preserve unrelated changes: modified `.opencode/agents/{orchestrator,problem_solver,targeted_fixer}.md`
and `importantdocs/writing_style.md`; deleted `PhotoGridFeedViewModelTest.kt`, `currentbehaviour.png`,
`intendedbehaviour.png`; untracked captures, `tools/scripts/adb_*.py`, `__pycache__/`, `.claude/worktrees/`,
and the `docs/agents/tasks/*prompt*.md` files. Stage explicit reviewed paths only. Never push.

## Records and report

Per slice: update `docs/agents/tasks/beeline-0.4.0.md`, `docs/agents/handoff.md`,
`docs/agents/app-shell-ownership.md`, `docs/wiki/ui-and-navigation.md`, and a local
`logs/YYMMDD-HHMMSS.txt` (gitignored). Commit code, tests, and records together. Report each commit
hash, gate results, and what stays unverified (physical devices, TalkBack, API 29, signing, live accounts).
