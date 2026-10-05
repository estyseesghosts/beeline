# Objective

Complete Phase 4C: adaptive floating navigation shared by compact-wide and tablet layouts.
Keep one navigator, one destination host, and the existing feature owners.

Status: 4C-5b implemented, validated, and directly reviewed.
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
Anchor preferences remain deferred to 4C-5c. These defaults do not follow layout direction.

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

# Current slice

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

# Verification

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

# Next

Next slice: define 4C-5c before implementation:

- Objective: persist separate anchor preferences for tablet and compact-wide navigation.
- Owners: existing `AppPreferences`, `DisplaySettingsScreen`, and shell/root preference wiring.
- Defaults: tablet left; compact-wide right. Anchors stay physical in LTR and RTL.
- Allowed scope: preference model/repository, display settings, connected root/shell wiring, focused tests, and records.
- Non-goals: another navigator, new destination behavior, changing approved fit geometry, or backdrop blur.
- Acceptance: both choices persist, missing values preserve defaults, and active shell placement/clearance follows each choice.
- Gates: focused preference/settings/placement tests, `test assembleRelease`, review, records, and scoped commit.
- Stop if persistence ownership is unclear, migration changes existing settings unexpectedly, or scope expands.

4C-5d is absorbed into 4C-5b. Do not repeat activation as an independent slice.

# Blockers and limits

- No implementation blocker remains for 4C-5b. Anchor preferences remain planned, not implemented.
- Device RTL, square-tablet hardware, physical hinge coordinates, TalkBack, physical-device IME, API 29, and release signing remain unverified here.
- Live-server recipient search and mutation behavior remain unverified. Outer-screen feed rendering is not a protocol acceptance gate.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve the deleted `PhotoGridFeedViewModelTest.kt` and PNGs, all unrelated captures/scripts/caches, and untracked `docs/agents/tasks/4c.md`.
- Recheck external Gradle activity before future runs. Prior external clean/install interference remains a worktree risk.

# Last safe commit

`6dcfac4` — `Add navigation fit policy`.
The reviewed 4C-5b checkpoint subject is `Activate safe adaptive floating navigation`.
Resolve its new hash from Git after the checkpoint. Nothing was pushed.
