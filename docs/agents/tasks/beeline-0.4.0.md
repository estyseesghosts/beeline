# Objective

Complete Phase 4C: adaptive floating navigation shared by compact-wide and tablet layouts.
Keep one navigator, one destination host, and the existing feature owners.

Status: 4C-5b, anchor, chip, and compact-wide Profile slices are verified. The Profile slice is ready to commit and push.
Owner: orchestrator
Last reviewed: 2026-10-05
Authority: [AGENTS.md](../../../AGENTS.md), current source, tests, and Git.

# Invariants

- Compact-narrow keeps four grouped positions, its 212 × 56 dp capsule, four 48 dp targets, and separate 56 dp action.
- Compact-wide and tablet share the vertical targets: Home, Search, Photo Grid, Notifications, Direct Messages, and Profile.
- `ShellNavigator` owns selection, grouped child memory, and route restoration. Feature owners retain queries, drafts, tabs, and scroll state.
- Navigation fit uses permanent window geometry. IME height does not select permanent presentation.
- Pane and back policy retain the independent 600/840 dp width policy.
- Physical anchors and clearance do not reverse in RTL. Scroll content owns final-item clearance; the viewport remains full size.
- Shared navigation components retain motion, reduced motion, semantics, profile avatar, and account-switch long press.
- Keep protocol behavior, account authorities, persistence, and transport unchanged. Do not add backdrop blur.
- Preserve unrelated work. Do not push.

# Decisions

This task is larger than one safe implementation slice. The orchestrator owns implementation,
review, records, validation, and Git. The maintainer prohibits `problem_solver`. No delegation occurred.

Approved geometry: 360 dp useful content minimum and 8 dp placement gap. A failed fit keeps compact navigation.
The shared capsule is 56 × 296 dp: six 48 dp slots with 4 dp padding at each end.
The action is 56 × 56 dp. The full stack is 56 × 360 dp and reserves 64 dp width with the edge gap.

Use mandatory system gesture insets, not the wider nonmandatory back-gesture strips.
Merge overlapping system-bar, display-cutout, and mandatory-gesture insets by their maximum on each physical edge.
Read insets in pixels with `LayoutDirection.Ltr`, then convert them to dp before calculating fit.
Separating or occluding hinges split safe regions on their orientation. Non-separating creases remain usable.

Expanded presentation defaults to physical left. Other fitting windows default to physical right.
4C-5c adds independent Display preferences that override these defaults. Physical edges do not follow layout direction.

Photo Grid has its own vertical target, so its action is Compose, not a duplicate panel toggle.
Direct Messages opens its session-owned recipient finder through `DirectMessagesContract.Actions.openRecipientFinder`.
Home, Search, and Notifications retain Compose. Profile shares compact edit/follow/unfollow policy.

# Completed

- Phases 4A and 4B are complete. The pre-4C safe commit was `88044c3`.
- 4C-1 characterized pane and hinge coordinates.
- 4C-2 added the session-owned DM recipient finder.
- 4C-3 prepared shared vertical presentation and contextual-action rendering.
- 4C-4 added destination-owned obstruction clearance for all seven surfaces.
- 4C-5a added the fit policy at `6dcfac4`, `Add navigation fit policy`.
- 4C-5b replaces the rail and activates safe floating navigation, including the former 4C-5d activation gates.
  The checkpoint subject is `Activate safe adaptive floating navigation`, based on `6dcfac4`.
  Verified slice history remains in Git instead of this task record.
- 4C-5c sub-slice 1 persists separate tablet and compact-wide physical anchors. Focused tests and `:app:lintDebug` pass.
  Commit: `b419be1` — `Persist physical navigation anchors`.

# Completed slice — 4C-5b

## 4C-5b — Placement, activation, and physical clearance

- Objective: replace the rail with the shared capsule/action, wire the DM action, and supply real destination clearance.
- Owner: orchestrator. `LargeLayoutMode` owns fit/panes; `LargeScreenShell` owns placement; `ShellContent` wires feature contracts.
- Allowed scope: the existing large-shell/navigation owners, `PalustrisApp`, destination clearance owners, focused tests, and four records.
- Maintainer expansion: include physical-left clearance across Home, Search, Photo Grid, Notifications, DM inbox, DM conversation, and Profile.
- Non-goals: anchor preferences, protocol changes, new feature owners, Phase 4D tabs, Phase 4E measurement redesign, and unrelated cleanup.
- Acceptance: safe physical placement in LTR/RTL; compact fallback; seven cleared surfaces; truthful DM action; preserved navigation state and pane/back independence.
- Exit gates: focused tests, lint, debug installation, outer-screen capture, `test assembleRelease`, direct review, document checks, and explicit-path commit.
- Fail gates: unsafe placement, unapproved geometry, unexplained state loss or test failure, incomplete clearance, or two failed fixes for one root problem.
- Git operator: orchestrator. Do not stage ignored logs or unrelated paths. Do not push.

Implementation:

- `NavigationFitWindowInsets.kt` reads permanent physical insets and window-space folding bounds. It does not read IME height.
- `LargeFloatingNavigation.kt` replaces `LargeNavigationRail.kt`. Enum mapping is explicit, and an absent action keeps its reserved slot.
- `LargeScreenShell` uses physical top-left alignment plus `absoluteOffset` for chrome and pane slots.
  Pane-content origin contains system insets only; no rail subtraction remains.
- `navigationPaneClearance` reserves only the pane covered by visible controls.
- `ShellContent` separates navigation fit, destination presentation, and width-based pane/back policy.
  Vertical navigation activates below 600 dp when it fits. Failed fit retains the grouped compact bar inside wide layouts.
- Wide fallback content receives scroll-end clearance. Search and the DM editor receive separate `bottomNavigationClearance` positioning.
  That value includes the fallback IME base after the pane's consumed system inset. Narrow compact behavior remains unchanged.
- Destinations consume physical-left and physical-right clearance within their existing content owners.
  Profile assigns clearance to the mirrored summary/timeline columns. Its summary scroll range now clears the category dock.
- The Photo Grid content inset remains the approved exception to visual underlay because each opaque tile is one click target.

## Verification

- Production `:app:compileDebugKotlin` passes.
- Focused geometry/navigation/shell/DM/clearance suites pass. The final focused gate also passes `:app:lintDebug`.
- Full `test assembleRelease` passes: 160 suites, 1,674 tests, zero failures, errors, or skips; release assembly succeeds.
- `:app:installDebug` succeeded on `emulator-5554` before the release gate.
- The AVD reports CLOSED/folded posture, 1169 × 1848 px, 420 dpi, and 445 × 704 dp outer-screen configuration.
- The installed app shows the six-target vertical capsule and separate action on that outer screen.
  Local evidence: `logs/4c5b-outer-home-ready.png`. Square-tablet presentation is JVM/Compose verified, not device verified.
- Coverage includes inset merging and density conversion, structural hinges, physical bounds in LTR/RTL,
  actionless slot stability, hidden-navigation clearance, compact fallback, seven physical-left surfaces,
  mirrored Profile columns, DM callback, IME-independent placement, grouped memory, and pane/back independence.
- Existing navigation/restoration, compact selection, motion, and DM owner tests pass without weaker assertions.
- Every build used the wrapper, `--no-daemon --console=plain`, closed stdin, explicit timeout, and `GRADLE_OPTS=-Dorg.gradle.daemon=false`.
  Process checks found no external Java/Gradle build. No source edits occurred during builds.
- Initial compile/test failures and their resolved causes are recorded in `logs/261005-181600.txt`.
  The known unchanged Mastodon cancellation flake did not occur in the final gate.

# Current slice — 4C-5c

This task is larger than one safe implementation slice. The orchestrator owns implementation,
review, records, validation, and Git. The maintainer prohibits `problem_solver`. Do not delegate.
The starting commit for the chip slice is `b419be1` — `Persist physical navigation anchors`.

## Source inventory and current owners

- `HomeTimelineTabs` and `FilterChipRow` are separate chip renderers. Home uses the first in compact shell chrome and the wide Home dock.
- Search, Photo Grid, Notifications, and Profile use `FilterChipRow` for their category/filter rows.
- `LargeBottomDock` places wide Home, Search, and Photo Grid rows. Wide Profile has equivalent local placement.
- Wide Notifications currently places its filter row before the list. This is the reported reachability defect.
- Home timeline and Search category selection remain with `ShellNavigator`. Photo Grid selection remains with `PhotoGridOwner`.
  Notifications selection remains with its existing screen/contract owner. Profile selection remains with `ProfileViewModel`.
- No shared owner will hold feature selection, queries, paging, or scroll state.
- `AppPreferencesRepository` stores separate tablet and compact-wide anchors. Display settings writes them.
  `PalustrisApp` selects the anchor by layout mode, and `LargeScreenShell` clears whichever pane the stack overlaps.

## Sub-slice 1 — Persist physical navigation anchors

- **Status:** committed and verified at `b419be1`. Repository, settings, adaptive-placement, and detail-clearance tests pass. `:app:lintDebug` passes.
- **Owner:** orchestrator. Existing owners: `AppPreferencesRepository`, `SettingsViewModel`, `DisplaySettingsScreen`, `ConnectedApp`, and the shell composition root.
- **Allowed files:** `domain/AppPreferences.kt`, `data/preferences/FileAppPreferencesRepository.kt`, `ui/settings/SettingsViewModel.kt`, `SettingsOverlayHost.kt`, `SettingsHost.kt`, `DisplaySettingsScreen.kt`, `ui/ConnectedApp.kt`, `ui/session/ConnectedSessionHost.kt`, `ui/PalustrisApp.kt`, `ui/large/LargeScreenShell.kt`, `ui/shell/AppLargeDetailPane.kt`, `ui/posts/SinglePostScreen.kt`, `app/src/main/res/values/strings.xml`, focused preference/settings/adaptive/detail tests, and this task's records/wiki/log.
- **Objective:** persist separate tablet and compact-wide physical-left/right choices through the existing global preference repository and Display settings owner.
- **Acceptance:** tablet defaults left; compact-wide defaults right; missing or unknown stored keys keep those defaults; updates persist independently; the active anchor controls stack placement and clears the primary or detail pane that it overlaps in LTR and RTL. Clearance stays inside scroll content; viewports, fit, pane, and back policies stay independent.
- **Non-goals:** chip-row presentation, feature state, fit geometry changes, a new settings repository, or account-scoped anchors.
- **Validation gates:** focused repository, settings-command, Display UI, and placement tests; `:app:lintDebug`; inspect the exact slice diff and records before an explicit-path commit.
- **Fail gates:** stop if existing global preference ownership cannot persist both fields without changing unrelated stored values, if physical placement follows layout direction, or if right anchoring requires replacing detail or feature owners.

The repository stores `tabletNavigationAnchor` and `compactWideNavigationAnchor` as optional fields.
Missing and unknown keys keep their independent defaults. Display settings exposes physical-left and
physical-right choices. `ConnectedApp` carries both values to `PalustrisApp` through `ConnectedSessionHost`.
`LargeScreenShell` assigns clearance to the primary or detail pane beneath the stack. `SinglePostScreen`
keeps the detail viewport full size and places edge clearance in its list content.

Focused tests: `AppPreferencesRepositoryTest`, `SettingsViewModelTest`, `SettingsDisplayTest`,
`AdaptiveNavigationTest`, and `SinglePostScreenTest`. Physical tablet rendering and device RTL remain unverified.

## Completed slice — 4C-5c sub-slice 2, shared destination chip rows

- **Result:** Home, Search, Photo Grid, Notifications, and Profile use one shared chip renderer.
  Feature owners retain selection and callbacks. Home visibility and scroll state span compact and wide placement.
  Each other destination owns its saveable visibility and chip scroll state.
- **Caret:** the 48 dp circular control matches the unselected chip surface, outline, and icon colors.
  It stays in the first logical position outside the scrollable chip row. It remains fixed when chips collapse.
  Its direction and accessible action label switch between hide and show.
- **Notifications:** wide filters use the bottom dock. The dock clears physical navigation and bottom obstruction.
  List-end clearance extends past the dock. Compact placement and IME behavior remain unchanged.
- **Tests:** component tests cover bounds, semantics, collapse, retained selection and scroll, and reduced motion.
  Fixtures cover compact-narrow, outer-screen LTR/RTL, and square-tablet layouts. Destination tests cover clearance.
  `WideNavigationTest` covers the wide dock and leading caret.
- **Verification:** focused component and destination tests pass. `:app:lintDebug` passes.
  The full `test assembleRelease` gate passes with 1,685 tests and release assembly.
- **Device:** `:app:installDebug` succeeded on `emulator-5554`. Folded outer-screen Home hide/show and the Notifications dock were inspected. The emulator was restored to OPENED.
- **Non-goals retained:** no global chip manager, chip-visibility preference, feature-selection changes, contextual-action replacement, new dimensions, or feature-owner migration.
- **Records:** `app-shell-ownership.md` and `ui-and-navigation.md` document the verified ownership and presentation. The audit log is `logs/261005-203055.txt` and stays local.

## Records and limits

- Update task state and handoff at each slice boundary. Update `app-shell-ownership.md` and `docs/wiki/ui-and-navigation.md` with verified ownership and behavior in the corresponding slice.
- Create an ignored timestamped task log when implementation starts. Never stage local logs or unrelated user files.
- Preserve modified agent definitions and writing-style changes, deleted tests/PNGs, untracked captures/scripts/caches, and `docs/agents/tasks/4c.md`.
- The user explicitly authorized pushing `main` after the Profile commit. The push includes all 133 commits currently ahead of `origin/main`.
  Report unavailable hardware, TalkBack, physical-device IME, and live-server checks.

4C-5d is absorbed into 4C-5b. Do not repeat activation as an independent slice.

## Current slice — compact-wide Profile presentation

- **Starting commit:** `f7dc516` — `Unify destination chip presentation`.
- **Objective:** make compact-wide Profile use the mobile one-column content presentation.
  Button placement is the only permitted presentation difference.
- **Finding:** before this slice, shell fit passed compact-wide content as `largeLayout`, which selected the two-column Profile summary.
- **Owner:** `ProfileScreen` and its existing profile state/list owners. `ShellContent` identifies non-expanded wide content and passes that presentation input.
- **Acceptance:** compact-wide Profile uses the mobile header, profile details, and timeline hierarchy. Preserve the expanded tablet summary. Keep feature selection, actions, profile state, list state, category state, and account scope unchanged. The existing wide contextual action remains shell-owned. Wide obstruction and dock clearance stay active.
- **Non-goals:** change tablet Profile, move profile actions to a new owner, change profile categories, revise navigation geometry, or alter account/protocol behavior.
- **Tests:** production shell coverage at 445 × 704 dp verifies one-column header and timeline bounds, the wide navigation target, and reachable category-dock interaction. Expanded tablet and compact Profile tests remain.
- **Validation:** focused `WideNavigationTest`, `ProfileScreenTest`, and `ProfileClearanceTest` pass. `:app:lintDebug` and `test assembleRelease` pass with 1,686 tests and successful release assembly.
  The authorized emulator is available. Device Profile rendering remains unverified because the active account content was not captured.
- **Fail gates:** stop if correct classification needs a new layout authority, feature state moves, wide obstruction clearance regresses, or a root problem fails twice.
- **Records:** app-shell ownership and the UI wiki now document the mode boundary and tests. The coding log is `logs/261005-210914.txt` and stays local.

# Blockers and limits

- 4C-5b remains verified at `79dd743`.
- The 4C-5c anchor slice is committed and verified at `b419be1`.
- Compact-wide Profile uses the mobile one-column content. Focused tests, lint, and the full release gate pass.
- Device RTL, square-tablet hardware, physical hinge coordinates, TalkBack, physical-device IME, API 29, and release signing remain unverified.
- Live-server recipient search and mutation behavior remain unverified. Chip placement is a presentation acceptance gate, not a protocol gate.
- Recheck external Gradle activity before each build. Prior external clean/install interference remains a worktree risk.

# Last safe commit

Preceding safe commit: `f7dc516` — `Unify destination chip presentation`.
The Profile slice commit subject is `Match compact-wide Profile to mobile layout`.
The user authorized pushing `main`, including its 133 existing commits ahead of `origin/main`.
