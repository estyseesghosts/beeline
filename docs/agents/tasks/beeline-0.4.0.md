# Objective

Complete Phase 4C: adaptive floating navigation shared by compact-wide and tablet layouts.
Compact-narrow keeps its existing grouped navigation, destination tree, feature owners, and state.

# Invariants

- Keep one `ShellNavigator`, one destination host, and the existing feature owners.
- Compact-narrow keeps four grouped positions, its 212 × 56 dp capsule, four 48 dp targets, and separate 56 dp action.
- Compact-wide and tablet share one vertical six-target presentation in the required order.
- Preserve account switching, grouped child memory, back behavior, route restoration, queries, drafts, tabs, scroll positions, and feature lifetimes.
- Keep navigation presentation separate from pane and back policy. Ignore IME height for permanent presentation.
- Keep floating content visible underneath chrome. Put interactive and final-item clearance in actual content owners, not the full viewport.
- Keep chip geometry separate from navigation exclusion. Preserve shared motion and reduced-motion behavior. Do not add backdrop blur.
- Do not invent production geometry or widen scope. Preserve all unrelated worktree changes. Do not push.

# Decisions

This task is larger than one safe implementation slice. The orchestrator owns implementation,
records, validation, review, and Git. The maintainer prohibits `problem_solver` use for this task.
No fixer owns an active scope. Each slice remains serial and has the orchestrator as Git operator.

Source characterization at `88044c3`:

- `LargeLayoutMode.kt` selects compact/single/expanded from 600/840 dp width cutoffs.
- `LargeScreenShell.kt` applies system-bar padding, places an 80 dp rail beside content, and subtracts that rail plus insets from hinge coordinates.
- Pane policy consumes post-rail content dimensions. Existing tests cover vertical/horizontal hinges, non-separating creases, minimum panes, and single-pane return.
- `PalustrisApp.kt` and `ShellContent.kt` use one `largePresentation` value for navigation, screen presentation, system bars, and back policy.
- `NavigationPresentation.kt` and `NavigationItem.kt` already render a reusable six-target vertical capsule fixture. Fixture dimensions are not production geometry.
- `ShellNavigator.selectLargeTarget` already preserves the compact Search/Photo Grid and Notifications/DM child memory.
- `DirectMessagesContract` only starts a conversation for a selected `Account`. Existing profile messaging supplies that recipient. The DM inbox has no recipient-selection flow.
- Material 3 Adaptive 1.3.0 documents `HingeInfo.bounds` in window coordinates. The current LTR pane origin includes the physical left system inset and rail. In RTL the rail is physically right, so content begins at the left inset only.

Navigation-fit policy for later activation:

- Build stable safe regions from window geometry, drawing/gesture insets, and separating or occluding folding features. Ignore non-separating creases.
- Ignore IME height when selecting permanent presentation. Keep pane/detail selection independent.
- Use the actual shared capsule and contextual-action bounds. Select vertical navigation only when one safe region contains the full controls and leaves the approved useful content width. Otherwise keep compact navigation.
- Anchor to the physical right edge of the eligible safe region. Do not add a report-derived aspect ratio, rail exclusion, or fixed production capsule size.
- The useful-content minimum and any placement gap still need maintainer approval if they cannot be derived from existing tested values.
- Initial Git state has unrelated modified agent definitions and writing style, deleted Photo Grid tests and PNGs, and untracked captures/scripts/caches. Preserve them. The supplied `docs/agents/tasks/4c.md` is untracked user input; do not stage it.

# Slice plan

## 4C-1 — Characterize window, pane, and hinge geometry

- **Objective:** Record the current coordinate transforms and pane/hinge invariants. Define a geometry contract that separates navigation fit from detail-pane selection without activating new presentation.
- **Owner and existing abstraction:** Orchestrator; `LargeLayoutMode.kt` owns pane and folding geometry, `LargeScreenShell.kt` assembles it, and `LargeLayoutModeTest.kt` verifies it.
- **Allowed files:** `LargeLayoutMode.kt`, `LargeScreenShell.kt` only for a focused coordinate helper if tests require it, `LargeLayoutModeTest.kt`, this task state, handoff, app-shell ownership, UI/navigation wiki, and the local task log.
- **Non-goals:** Production navigation activation, rail replacement, invented thresholds/dimensions, destination changes, DM behavior, and device state changes.
- **Deliverables:** Characterization tests for window/content/hinge coordinates and existing pane behavior; a direction-aware content-origin transform; the safe-region/fit policy above; a recorded list of maintainer decisions if current evidence cannot determine them.
- **Acceptance:** LTR/RTL hinge coordinates match the current rail placement. Separating/occluding hinges remain excluded, non-separating creases remain usable, and pane/detail policy remains independent of navigation fit. No navigation behavior changes.
- **Validation:** Run `LargeLayoutModeTest`; check touched-document links and whitespace; inspect the full diff.
- **Exit gates:** Characterization and full required test/build gate pass; no pane assumptions change without tests; geometry policy is recorded without claiming unapproved measures; affected docs and handoff are current.
- **Fail gates:** Stop if folding-feature coordinates or safe-inset ownership cannot be established, a fit rule needs an unapproved measure, tests expose an unexplained regression, or scope expands.
- **Git operator:** Orchestrator; commit only reviewed slice paths after validation.

## 4C-2 — Add truthful DM recipient selection

- **Objective:** Provide the wide DM `New conversation` action with a valid recipient-selection flow under the existing DM feature owner.
- **Owner and existing abstraction:** Orchestrator; `DirectMessageViewModel`, `DirectMessagesHost`, and `DirectMessagesContract` own DM state and actions. The active `SocialSource` already supports account search.
- **Allowed files:** `ui/directmessages/DirectMessageViewModel.kt`, `DirectMessageUiState.kt`, `DirectMessagesHost.kt`, `DirectMessageRecipientFinder.kt`, `ui/shell/DirectMessagesContract.kt`, `app/src/main/res/values/strings.xml`, `DirectMessageViewModelTest.kt`, `DirectMessageScreenTest.kt`, task state, handoff, ownership/wiki pages, and local task log.
- **Non-goals:** A new account/search owner, changes to send/session validation, arbitrary recipients, notification toggles, or composer substitution.
- **Deliverables:** A per-session recipient finder in the DM host, a cancelable modal, explicit selection of a current search result through `startConversation(Account)`, and race/account guards.
- **Acceptance:** The contract opens recipient selection; search uses only the connected session's source; selection starts a DM with a returned account; cancel preserves an existing conversation/editor; stale results cannot replace current results.
- **Validation:** Focused direct-message ViewModel and Compose tests, then `test assembleRelease`.
- **Exit gates:** Recipient lookup and selection stay with the current DM owner; all changed-state and cancellation tests pass; no existing send behavior changes.
- **Fail gates:** Stop if source lookup cannot satisfy account/session ownership, cancellation loses user text, or a second owner/cache is proposed.
- **Git operator:** Orchestrator; scoped commit after review and gates.

## 4C-3 — Prepare shared vertical presentation and action rendering

- **Objective:** Reuse the shared capsule/buttons vertically and make contextual action rendering reusable without production activation.
- **Owner and existing abstraction:** Orchestrator; `NavigationButton`, `NavigationCapsule`, `WideNavigationItem`, and compact contextual action rendering own shared presentation.
- **Allowed files:** `ui/navigation/NavigationPresentation.kt`, `NavigationItem.kt`, `CompactAppNavigation.kt`, `ui/large/LargeNavigationRail.kt`, focused navigation tests, task state, handoff, ownership/wiki pages, and local task log.
- **Non-goals:** Activating compact-wide navigation, changing narrow dimensions or mappings, duplicating action rendering, and choosing unapproved production pill geometry.
- **Deliverables:** One stateless vertical presentation over the existing six items, one reusable contextual action button, correct Profile avatar/long-press behavior, and LTR/RTL/reduced-motion/focus tests.
- **Acceptance:** Shared visuals and one indicator remain authoritative; all targets stay at least 48 dp; narrow bounds and grouped actions remain unchanged.
- **Validation:** Focused navigation presentation, compact selection, and wide-navigation tests; then `test assembleRelease`.
- **Exit gates:** Reuse is proven by tests; no production rail behavior changes; action callback ownership remains in the shell/feature contract.
- **Fail gates:** Stop if the capsule needs invented dimensions, shared component behavior changes compact geometry, or rendering is copied.
- **Git operator:** Orchestrator; scoped commit after review and gates.

## 4C-4 — Protect destinations from floating chrome

- **Objective:** Pass right-side obstruction geometry through existing destination branches and keep interactive content and final items clear before activation.
- **Owner and existing abstraction:** Orchestrator; shell owns shared geometry; Home, Search, Photo Grid, Notifications, DMs, and Profile own their scroll content and controls.
- **Allowed files:** `ui/shell/ShellContent.kt`, `ShellDestinationContent.kt`, `AppShellState.kt`, `ui/layout/CompactOverlayMetrics.kt`, `ui/large/LargeBottomDock.kt`, `ui/feed/HomeFeed.kt`, `ui/search/SearchScreen.kt`, `ui/photogrid/PhotoGridScreen.kt`, `ui/notifications/NotificationsScreen.kt`, `ui/directmessages/DirectMessageInboxScreen.kt`, `DirectMessageConversationScreen.kt`, `ui/profile/ProfileScreen.kt`, `ProfileTimelineList.kt`, and their focused Compose tests. Include only needed callbacks and geometry types. Update task state, handoff, ownership/wiki pages, and local task log. Split independent destination gates into separate commits.
- **Non-goals:** Full-viewport padding, tab redesign, chip travel/exclusion coupling, unrelated screen cleanup, and activating an overlapping rail/capsule.
- **Deliverables:** Named right/bottom clearance inputs; per-surface tests for interactive bounds and fully reachable final content; preserved IME/query/editor behavior.
- **Acceptance:** Surfaces retain full-size viewports and visual underlay; interactive controls and last content clear the measured obstruction; chips remain separate.
- **Validation:** Focused Compose tests for each changed destination at compact-wide dimensions and applicable IME states; then `test assembleRelease`.
- **Exit gates:** Every affected owner has a passing obstruction test; no known overlap remains; no viewport-wide exclusion padding is introduced.
- **Fail gates:** Stop on any untested affected surface, unexplained failure, lost state, or overlap that requires an unapproved exclusion measurement.
- **Git operator:** Orchestrator; one reviewed commit per bounded destination slice.

### First bounded destination slice — DM inbox clearance

- **Objective:** Protect the direct-message inbox from future floating navigation without changing its current presentation.
- **Current behavior and invariant:** `DirectMessageInboxScreen` owns the full-size inbox and its conversation list. Wide inbox rows currently have no right-side content clearance. Compact-narrow keeps its existing bottom-clearance policy.
- **Owner and abstraction:** `DirectMessageInboxScreen` owns its header, clickable conversation rows, and final list content. `ShellContent` and the existing destination branches pass immutable clearance values from the shell.
- **Implementation owner:** Orchestrator; no fixer owns this slice.
- **Allowed files:** `ui/shell/ShellContent.kt`, `ui/shell/ShellDestinationContent.kt`, `ui/shell/AppNotificationsDestinationContent.kt`, `ui/directmessages/DirectMessageInboxScreen.kt`, `app/src/test/java/me/foxtails/palustris/ui/directmessages/DirectMessageScreenTest.kt`, this task state, handoff, app-shell ownership, UI/navigation wiki, and the local task log.
- **Forbidden files and behavior:** Do not change `DirectMessageConversationScreen`, DM feature state or actions, navigation activation, safe-region calculation, production geometry, compact-narrow layout, or unrelated destinations. Do not add full-viewport padding or modify chip travel.
- **Clearance contract:** The shell supplies physical-right and bottom obstruction clearance as `Dp` inputs. The inbox does not calculate or invent their values. Wide inbox content keeps its full viewport and row surface backgrounds. Header text, refresh action, row text, and row click targets stay left of physical-right clearance. Bottom clearance extends only the scroll range. Compact layout ignores both wide-only inputs and retains its existing compact clearance.
- **Deliverables:** Thread both named inputs through the existing DM inbox branch. Add wide Compose coverage for physical-right clearance in LTR and RTL, full-size viewport and row-background underlay, clear header and row interaction bounds, final-content reach above bottom clearance, and unchanged compact behavior.
- **Non-goals:** Do not alter conversation-editor IME policy or claim DM conversation clearance. Define that surface in its own contract before changing it.
- **Validation:** Run `:app:testDebugUnitTest --tests me.foxtails.palustris.ui.directmessages.DirectMessageScreenTest`, then `test assembleRelease`, with the required wrapper flags, timeout, and closed stdin.
- **Exit gates:** The focused tests and full test/build gate pass; current DM conversation tests still pass; the shell branch carries the inputs without changing current zero-clearance behavior; the full viewport remains unchanged.
- **Fail gates:** Stop if physical-right padding cannot be verified in RTL, row visuals or click bounds require viewport exclusion, compact geometry changes, existing editor behavior changes, or any failure remains unexplained.
- **Git operator:** Orchestrator; stage and commit only reviewed slice paths after validation and review.

### Next bounded destination slice — DM conversation/editor clearance

- **Objective:** Clear the wide DM conversation and editor from future floating navigation without changing editor state or IME positioning.
- **Current behavior and invariant:** `DirectMessageConversationScreen` owns the full-size conversation viewport, header, transcript, and editor. The wide editor uses `imePadding()` plus navigation-bar positioning insets. Compact layout uses `compactContextualControlsPositioningInsets`. Keep both policies and the editor's stored text unchanged.
- **Owner and abstraction:** `DirectMessageConversationScreen` owns conversation presentation. `ShellContent`, `ShellDestinationContent`, and `AppNotificationsDestinationContent` carry immutable shell clearances through the existing destination branch.
- **Implementation owner:** Orchestrator; no fixer owns this slice.
- **Allowed files:** `ui/shell/ShellContent.kt`, `ui/shell/ShellDestinationContent.kt`, `ui/shell/AppNotificationsDestinationContent.kt`, `ui/directmessages/DirectMessageConversationScreen.kt`, `app/src/test/java/me/foxtails/palustris/ui/directmessages/DirectMessageScreenTest.kt`, this task state, handoff, app-shell ownership, UI/navigation wiki, and the local task log.
- **Forbidden files and behavior:** Do not change the completed DM inbox slice, DM feature state or send actions, navigation activation, safe-region calculation, production geometry, compact-narrow geometry, or unrelated destinations. Do not alter IME policy or add viewport-wide exclusion padding.
- **Clearance contract:** The shell supplies the existing physical-right and bottom obstruction `Dp` values. The conversation screen ignores both in compact layout. In wide layout, keep the full viewport. Keep header, notice, and error text; thread bubbles; retry/continue controls; editor text field; and Send action left of physical-right clearance. Add bottom clearance only to the transcript scroll range. Do not add it to the editor's insets. Preserve wide `imePadding()` and navigation-bar positioning, and preserve compact contextual-control positioning. The shell currently supplies zero values because floating navigation is inactive.
- **Deliverables:** Forward both inputs through the existing conversation branch. Add Compose tests for LTR/RTL right clearance, full-width viewport, header/thread/editor interaction bounds, and final transcript reach. Verify editor visibility and retained text with the IME open and closed. Verify compact layout ignores both wide-only inputs and keeps its current editor positioning.
- **Non-goals:** Do not wire the wide New conversation action, change recipient selection, or claim physical-device IME behavior from Compose tests.
- **Validation:** Run focused `DirectMessageScreenTest`, then `test assembleRelease` with the required wrapper flags, timeout, and closed stdin. Confirm no external Gradle build is using the worktree first.
- **Exit gates:** Focused tests and full test/build gate pass; transcript and editor interactions clear physical right in LTR and RTL; final transcript content is reachable; editor state and IME placement remain unchanged; compact behavior remains unchanged.
- **Fail gates:** Stop if IME positioning cannot be tested in both states, editor text is lost, any interaction overlaps clearance, bottom clearance moves the editor, compact geometry changes, or a failure remains unexplained.
- **Git operator:** Orchestrator; stage and commit only reviewed implementation and records.

## 4C-5 — Integrate safe placement and activate adaptive navigation

- **Objective:** Replace the production rail with one safe-region floating vertical capsule/action and select presentation from stable window geometry.
- **Owner and existing abstraction:** Orchestrator; `LargeLayoutMode.kt` remains the geometry owner, `PalustrisApp.kt` supplies window inputs, `LargeScreenShell.kt` places chrome/panes, and `ShellContent.kt` composes existing owners.
- **Allowed files:** `ui/large/LargeLayoutMode.kt`, `LargeScreenShell.kt`, `LargeNavigationRail.kt`, `LargeBottomDock.kt`, `ui/PalustrisApp.kt`, `ui/SystemBars.kt`, `ui/navigation/ShellBackPolicy.kt`, `ShellNavigator.kt`, `NavigationPresentation.kt`, `NavigationItem.kt`, `CompactAppNavigation.kt`, `ui/shell/ShellContent.kt`, `ShellDestinationContent.kt`, `AppShellState.kt`, `DirectMessagesContract.kt`, affected DM owner files, and their focused geometry/navigation/restoration/destination tests. Update task state, handoff, ownership/wiki pages, and local task log.
- **Non-goals:** Separate destination hosts/navigation state, automatic tablet detail behavior from vertical presentation, Phase 4D tabs, Phase 4E measurement redesign, and backdrop blur.
- **Deliverables:** Geometry-driven compact/vertical presentation unaffected by IME; physical-right placement inside safe bounds; hinge-safe panes in one coordinate space; direct six-target navigation; truthful Photo Grid/DM actions; route/detail/back continuity tests.
- **Acceptance:** 4C-1 through 4C-4 gates pass; no target intersects cutout, bars, gesture region, or structural hinge; pane and back policy remain independent; repeated resizing preserves all existing feature state.
- **Validation:** Focused geometry, navigation, shell, restoration, and destination tests; required `test assembleRelease`; then `:app:lintDebug`; capture and inspect compact-wide emulator screenshots without changing device state beyond task-authorized navigation.
- **Exit gates:** No known content overlap; geometry decisions are approved or derived from existing tested component/content values; full Phase 4C test gate and final review pass.
- **Fail gates:** Stop for unapproved geometry, physical safe-region ambiguity, state reset, unexplained test failure, or any incomplete destination clearance.
- **Git operator:** Orchestrator; commit only explicitly reviewed implementation and records. Do not push.

# Completed

- Phase 4A, 4B-1, 4B-2, and 4B-3 are complete. The pre-4C safe commit was `88044c3`.
- 4C-5a — geometry and fit policy. Commit subject `Add navigation fit policy`, preceded by `8c5d879`.
  - Focused `LargeLayoutModeTest` passes: 17 tests. Full `test assembleRelease` passes with 158 suites,
    1,635 tests, zero failures/errors/skips, and successful release assembly.
  - `calculateNavigationFit` builds safe regions from window geometry, system bar insets, gesture insets,
    cutout bounds, and separating or occluding folding features. It selects vertical navigation only when
    one safe region contains the full controls (64 dp width, 360 dp height) and leaves 360 dp useful
    content width. Otherwise it falls back to compact. IME height does not affect the fit. Pane and
    detail selection remain independent.
- 4C-1 — direction-aware hinge/content coordinate characterization; commit `1dc1d39`, `Characterize wide pane coordinate behavior`, preceded by `88044c3`.
- `LargeLayoutModeTest` covers both coordinate directions and the existing pane/hinge policy. The full test/build gate reports 153 suites, 1,578 tests, zero failures/errors/skips; release assembly succeeds.
- Navigation presentation, pane-selection policy, and destinations remain unchanged. RTL hinge translation now uses the physical content origin. Physical hinge behavior remains unverified.
- 4C-2 — session-owned recipient finder, active-source account lookup, candidate validation, and cancel-safe state; commit `a0136e9`, `Add direct message recipient finder`, preceded by `1dc1d39`.
- Focused direct-message tests pass: 30 ViewModel tests and 15 Compose tests. Full `test assembleRelease` passes with 153 suites, 1,584 tests, zero failures/errors/skips, and successful release assembly.
- `DirectMessagesContract` now exposes the finder action. The wide navigation action remains unwired until 4C-5; presentation and destination behavior do not change in this slice.
- 4C-3 — stateless shared vertical six-target presentation and reusable contextual action rendering; commit subject `Prepare shared vertical navigation presentation`, preceded by `a0136e9`.
- Focused navigation checks pass: 52 tests across presentation, compact selection, shell navigation, and wide navigation. Full `test assembleRelease` passes with 153 suites, 1,585 tests, zero failures/errors/skips, and successful release assembly.
- `LargeNavigationRail` remains unchanged. Compact presentation delegates to the shared contextual-action button; production vertical navigation remains inactive.
- 4C-4 DM inbox clearance — commit subject `Add DM inbox obstruction clearance`, preceded by `1d2050b`. Focused DM screen tests pass: 18 tests. The full `test assembleRelease` gate passes with 153 suites, 1,588 tests, zero failures/errors/skips, and successful release assembly.
- Later gate retries overlapped a separate `gradlew.bat clean installDebug` process in this worktree. They reported missing test classes, missing R8 inputs, and a locked `R.jar`; no source changes followed the passing gate. Do not start another Gradle run while that external build may still be active.
- 4C-4 DM conversation/editor clearance — commit subject `Add DM conversation obstruction clearance`, preceded by `62506ba`.
- Focused DM screen tests pass: 22 tests. Full `test assembleRelease` passes with 153 suites, 1,592 tests, zero failures/errors/skips.
- Wide conversation content clears physical right in LTR/RTL. Bottom clearance extends only transcript scrolling. Editor state, IME policy, and compact geometry remain unchanged.
- 4C-4 Home clearance — commit subject `Add Home obstruction clearance`, preceded by `f8f1056`.
- Focused `HomeClearanceTest` passes: 7 tests. Existing `HomeFeedTest` and `NavigationTest` pass: 73 tests.
- Full `test assembleRelease` passes with 154 suites, 1,599 tests, zero failures/errors/skips, and successful release assembly.
- Home retains full viewports and transparent row underlay. Interaction content and the wide dock clear physical right in LTR/RTL.
- Final post/footer content clears the dock and supplied bottom obstruction. Compact layout, chip travel, and scroll ownership remain unchanged.
- 4C-4 Search clearance — commit subject `Add Search obstruction clearance`, preceded by `9717c49`.
- Focused `SearchClearanceTest` passes: 8 tests. Existing suites pass unchanged: `HomeClearanceTest` 7,
  `DirectMessageScreenTest` 22, `HomeFeedTest` 37, `NavigationTest` 36, `SearchOwnerTest` 8,
  `SearchPanelRestorationTest` 3.
- Full `test assembleRelease` passes with 155 suites, 1,607 tests, zero failures/errors/skips, and successful release assembly.
- Hashtag rows, account rows, the continuation item, chips, and the field clear physical right in LTR/RTL.
  Viewports, outer rows, and dividers keep their width. The wide dock keeps its placement and measured height.
- 4C-4 Photo Grid clearance — commit subject `Add Photo Grid obstruction clearance`, preceded by `5579742`.
- Focused `PhotoGridClearanceTest` passes: 9 tests. Existing suites pass unchanged: `PhotoGridScreenTest` 8,
  `PhotoGridOwnerTest` 10, `SearchClearanceTest` 8, `HomeClearanceTest` 7, `DirectMessageScreenTest` 22,
  `NavigationTest` 36.
- Full `test assembleRelease` passes with 156 suites, 1,616 tests, zero failures/errors/skips, and successful
  release assembly.
- Photo Grid keeps its full-size `photo_grid_content` viewport. Tiles, the load-older control, the paging-error
  surface, the up-to-date label, and the empty state clear physical right in LTR/RTL through the grid content inset.
- The maintainer chose the grid content inset over a per-tile click-bound inset. A tile is opaque media and one
  click target, so a per-tile inset would leave an untappable strip in every lane. Tile media therefore no longer
  passes under floating chrome. This is the only deviation from the row-underlay rule in the clearance slices.
- The wide filter-chip dock clears physical right and sits above bottom obstruction, matching `HomeFeed`. The grid
  end spacing grows by the same value. Chip travel and chip selection keep their own owners.
- Compact Photo Grid ignores both inputs. Saved hashtags, timeline selection, feed state, and supplied grid state
  keep their owners. Production clearances remain zero. Floating navigation remains inactive.
- 4C-4 Notifications clearance — commit subject `Add Notifications obstruction clearance`, preceded by `4763311`.
- Focused `NotificationsClearanceTest` passes: 8 tests. Existing suites pass unchanged: `NotificationsScreenTest` 14,
  `NotificationRouteResolverTest` 2, `NotificationLaunchHostTest` 5, `NavigationTest` 36.
- Full `test assembleRelease` passes with 157 suites, 1,624 tests, zero failures/errors/skips, and successful
  release assembly.
- The `NotificationsPanel.Notifications` branch forwards both shell clearances. Wide layout clears physical right in
  the top chip row and in the notification list content. A notification row is not a single opaque target: it always
  carries a dismiss control and can carry follow-request controls, so the row card keeps its full width for visual
  underlay and its interactive content clears physical right through an absolute right inset inside the row.
- The empty state, error/storage retry controls, sync-delayed banner, paging/load-older control, and paging error text
  clear physical right. Bottom clearance extends the wide list end spacing only. Wide layout has no bottom dock; its
  chip row sits at the top.
- Compact Notifications ignores both inputs and keeps its floating bottom chip row, contextual-control placement, and
  scroll clearance. Chip travel and selection keep their owners. Production clearances remain zero. Floating
  navigation remains inactive.
- 4C-4 Profile clearance — commit subject `Add Profile obstruction clearance`, preceded by `29c38f5`.
- Focused `ProfileClearanceTest` passes: 11 tests. Existing suites pass unchanged: `ProfileScreenTest` 24,
  `ProfileViewModelTest` 28, `ProfileTimelinePagerTest` 10, `WideNavigationTest` 9, `NavigationTest` 36.
- Full `test assembleRelease` passes with 158 suites, 1,635 tests, zero failures/errors/skips, and successful
  release assembly.
- The `Destination.Profile` branch forwards both clearances. `ProfileScreen` ignores both inputs in compact and adds
  the supplied bottom obstruction to `LargeBottomDockClearance` for the wide end of list.
- A profile row is `PostRow`, a transparent column with separate state and footer items. Its content therefore takes
  an absolute right padding while the item divider keeps the full width, matching `HomeFeed`. The row precedent is not
  the Photo Grid tile inset.
- The wide summary `Row` mirrors in RTL, so the column that reaches the pane physical right edge changes with the
  layout direction. Clearance now applies to the timeline column in LTR and to the summary column that holds the
  header and details in RTL. The other column keeps its usable width. Without the summary the timeline fills the pane
  and always clears.
- The wide category dock keeps its bottom-start placement and `LargeBottomDock` spacing, clears physical right, and
  sits above the supplied bottom obstruction. Chip travel and selection keep their owners. Production clearances
  remain zero. Floating navigation remains inactive.
- The wide header is not a clearance surface outside that mirrored column. The presentation without the large
  summary (`compactLayout = false` with `largeLayout = false`) is produced only by tests, because
  `ShellDestinationContent` always pairs `compactLayout = false` with `largeLayout = true`.

# Current slice

## 4C-5 sub-slice plan

This task is larger than one safe implementation slice. Four sub-slices, each with its own contract,
allowed files, exit gates, fail gates, validation, and commit.

### 4C-5a — Geometry and fit policy

- **Objective:** Build stable safe regions from window geometry, drawing/gesture insets, and separating
  or occluding folding features. Determine if the vertical capsule + contextual action can fit in a
  safe region. Select presentation (compact vs vertical) from stable window geometry.
- **Approved decisions:** Useful-content minimum = 360 dp. Placement gap = 8 dp. Fit fallback = compact.
- **Owner and existing abstraction:** Orchestrator; `LargeLayoutMode.kt` owns geometry.
- **Allowed files:** `ui/large/LargeLayoutMode.kt`, `LargeLayoutModeTest.kt`, task state, handoff,
  app-shell ownership, UI/navigation wiki, and local task log.
- **Non-goals:** Production navigation activation, rail replacement, destination changes, DM behavior,
  display settings toggles, and device state changes.
- **Deliverables:** Safe-region calculation from window geometry, drawing/gesture insets, and folding
  features. Fit policy that selects vertical vs compact presentation. IME-independent fit. Pane/detail
  selection remains independent of navigation fit.
- **Acceptance:** Safe regions exclude cutout, gesture, and structural hinge areas. Fit policy selects
  vertical only when one safe region contains the full controls and leaves 360 dp useful content.
  Otherwise falls back to compact. IME height does not affect permanent presentation.
- **Validation:** Run `LargeLayoutModeTest`; check touched-document links and whitespace; inspect the full diff.
- **Exit gates:** Safe-region and fit policy tests pass; no pane assumptions change without tests; geometry
  policy is recorded without claiming unapproved measures; affected docs and handoff are current.
- **Fail gates:** Stop if folding-feature coordinates or safe-inset ownership cannot be established,
  a fit rule needs an unapproved measure, tests expose an unexplained regression, or scope expands.
- **Git operator:** Orchestrator; commit only reviewed slice paths after validation.

### 4C-5b — Capsule and action placement + wide DM action wiring

- **Objective:** Replace the production rail with the floating vertical capsule and contextual action.
  Place the capsule at the configured edge. Wire the wide New conversation action to `DirectMessagesContract`.
- **Owner and existing abstraction:** Orchestrator; `LargeScreenShell.kt` places chrome/panes,
  `ShellContent.kt` composes existing owners, `WideNavigationPresentation` and
  `ContextualNavigationActionButton` own shared presentation.
- **Allowed files:** `ui/large/LargeScreenShell.kt`, `LargeNavigationRail.kt`, `ui/PalustrisApp.kt`,
  `ui/shell/ShellContent.kt`, `ShellDestinationContent.kt`, `DirectMessagesContract.kt`, affected DM
  owner files, focused geometry/navigation tests, task state, handoff, ownership/wiki pages, and local task log.
- **Non-goals:** Display settings toggles, destination clearance changes, Phase 4D tabs, Phase 4E
  measurement redesign, and backdrop blur.
- **Deliverables:** Floating vertical capsule replaces the rail. Capsule placed at the configured edge
  with 8 dp gap. Contextual action below the capsule. Wide DM action wired to `DirectMessagesContract`.
- **Acceptance:** No target intersects cutout, bars, gesture region, or structural hinge. Six-target
  order preserved. DM action opens recipient finder.
- **Validation:** Focused geometry, navigation, shell, and DM tests; then `test assembleRelease`.
- **Exit gates:** Capsule placement is safe in both layout directions; DM action is wired; full test/build
  gate passes.
- **Fail gates:** Stop for unapproved geometry, physical safe-region ambiguity, state reset, unexplained
  test failure, or any incomplete destination clearance.
- **Git operator:** Orchestrator; commit only explicitly reviewed implementation and records.

### 4C-5c — Display settings toggles for anchor position

- **Objective:** Add two new display settings toggles for the navigation anchor position (left/right).
  Tablet (expanded) defaults to left. Compact-wide (single) defaults to right.
- **Owner and existing abstraction:** Orchestrator; `AppLayoutDirection` and `DisplaySettingsScreen`
  own display settings. `AppPreferences` owns persisted preferences.
- **Allowed files:** `domain/AppPreferences.kt`, `ui/settings/DisplaySettingsScreen.kt`,
  `ui/LayoutDirectionPolicy.kt`, `ui/ConnectedApp.kt`, focused display settings tests, task state,
  handoff, ownership/wiki pages, and local task log.
- **Non-goals:** Navigation activation, capsule placement, destination changes, and device state changes.
- **Deliverables:** Two new toggle settings for anchor position. Defaults: tablet=left, compact-wide=right.
  Settings persist and apply to the shell.
- **Acceptance:** Settings persist across recomposition. Defaults are correct. Shell reads the setting.
- **Validation:** Focused display settings tests; then `test assembleRelease`.
- **Exit gates:** Settings persist and apply; defaults are correct; full test/build gate passes.
- **Fail gates:** Stop if settings cannot persist, defaults are wrong, or scope expands.
- **Git operator:** Orchestrator; commit only explicitly reviewed implementation and records.

### 4C-5d — Activation switch with full gates

- **Objective:** Pass real clearance values from `PalustrisApp` through `ShellContent` to all destinations.
  Verify all seven destinations under real values. Run full test gate and lint.
- **Owner and existing abstraction:** Orchestrator; `PalustrisApp.kt` supplies window inputs,
  `ShellContent.kt` composes existing owners.
- **Allowed files:** `ui/PalustrisApp.kt`, `ui/shell/ShellContent.kt`, `ShellDestinationContent.kt`,
  `AppShellState.kt`, focused geometry/navigation/restoration/destination tests, task state, handoff,
  ownership/wiki pages, and local task log.
- **Non-goals:** New production obstruction values, destination clearance changes, Phase 4D tabs,
  Phase 4E measurement redesign, and backdrop blur.
- **Deliverables:** Real clearance values flow from the shell into each destination. All destinations
  verified under real values. Full test gate and lint pass.
- **Acceptance:** All seven destinations consume real clearance values. No known content overlap.
  Full Phase 4C test gate and final review pass.
- **Validation:** Focused geometry, navigation, shell, restoration, and destination tests; required
  `test assembleRelease`; then `:app:lintDebug`; capture and inspect compact-wide emulator screenshots.
- **Exit gates:** No known content overlap; geometry decisions are approved or derived from existing
  tested component/content values; full Phase 4C test gate and final review pass.
- **Fail gates:** Stop for unapproved geometry, physical safe-region ambiguity, state reset, unexplained
  test failure, or any incomplete destination clearance.
- **Git operator:** Orchestrator; commit only explicitly reviewed implementation and records. Do not push.

# Files involved

- Completed slices: DM inbox, DM conversation and editor, Home, Search, Photo Grid, Notifications, and Profile clearance.
- The Profile slice used `ProfileScreen.kt`, `ProfileLargePresentation.kt`, `ProfileTimelineList.kt`, the `Destination.Profile` branch of `ShellDestinationContent.kt`, and new `ProfileClearanceTest.kt`.
- `ShellContent` and `ShellDestinationContent` already forward both inputs. They need no changes in the clearance slices.
- Later slices use the scoped file lists above. Expand a destination into its own contract before editing if its behavior needs an independent gate.

# Verification

4C-1 focused `LargeLayoutModeTest` passes. Its required `test assembleRelease` gate passed with 153 suites,
1,578 tests, zero failures/errors/skips, and successful release assembly. A second wrapper run also completed with tasks up to date.
4C-2 focused `DirectMessageViewModelTest` and `DirectMessageScreenTest` pass: 45 tests, zero failures/errors/skips.
Its `test assembleRelease` gate passes with 153 suites, 1,584 tests, zero failures/errors/skips, and successful release assembly.
4C-3 focused `NavigationPresentationTest`, `CompactNavigationSelectionTest`, `NavigationTest`, and `WideNavigationTest` pass: 52 tests, zero failures/errors/skips.
Its `test assembleRelease` gate passes with 153 suites, 1,585 tests, zero failures/errors/skips, and successful release assembly.
Existing Phase 4B results remain historical evidence only.
4C-4 DM inbox focused `DirectMessageScreenTest` passes with 18 tests. Its `test assembleRelease` gate
passed with 153 suites, 1,588 tests, zero failures/errors/skips, and successful release assembly.
Later retries ran while a separate `gradlew.bat clean installDebug` process was active in this
worktree. The combined retry reported class-loading errors and missing R8 intermediates. A serialized
forced retry could not delete a locked `R.jar`. These retries do not replace the earlier completed
green gate. Device state was not checked.
4C-4 DM conversation/editor focused tests pass: 22 tests. Full `test assembleRelease` passes with
153 suites, 1,592 tests, zero failures/errors/skips, and successful release assembly.
Process checks before validation found no external Java/Gradle build. No external process was stopped.
Tests cover full viewport, LTR/RTL physical right, header/bubble/control/editor bounds, transcript
reach, branch forwarding, synthetic IME open/close, retained text, and unchanged compact bounds.
The editor and Send bounds stay fixed when transcript bottom clearance changes in either IME state.
Two initial focused runs exposed test setup errors: an incorrect Back label and retained list
scroll position across direction changes. Both were corrected without production changes.
Review was direct because the maintainer prohibits `problem_solver`. No existing test was weakened.
No ADB or live-server check ran. Physical-device IME behavior remains unverified.
4C-4 Home focused `HomeClearanceTest` passes: 7 tests. Existing `HomeFeedTest` and `NavigationTest`
pass unchanged: 37 and 36 tests. Full `test assembleRelease` passes with 154 suites, 1,599 tests,
zero failures/errors/skips, and successful release assembly. The completed DM suite still passes with 22 tests.
Tests cover 800 × 600 dp wide fixtures, a 500 × 1000 dp wide branch fixture with scrolling timeline chips,
and 411 × 891 dp compact compatibility. Synthetic obstruction values are test inputs, not production measurements.
Full viewport, outer-row/divider and error-surface underlay, interaction bounds, final post/footer reach,
null-Home dock, branch forwarding, timeline callbacks, and retained scroll position are test verified.
Initial focused attempts found new-test compilation and chip-label errors. Corrections used current source/resources.
No existing tests were changed or weakened. Direct review found no unresolved required findings.
Process checks before each Gradle run found no external Java/Gradle build. No external process was stopped.
Compact IME policy was not changed. Existing navigation tests pass. No ADB or live-server check ran.
Touched-document links and slice-only whitespace checks pass. Device rendering remains unverified.
4C-4 Search focused `SearchClearanceTest` passes: 8 tests. Existing Home, DM, navigation, and Search-owner
suites pass unchanged: 7 + 22 + 37 + 36 + 8 + 3 tests. Full `test assembleRelease` passes with 155 suites,
1,607 tests, zero failures/errors/skips, and successful release assembly.
Fixtures cover 800 x 600 dp and 800 x 1000 dp wide layouts, a 600 x 800 dp wide dock fixture, a
600 x 1000 dp shell branch fixture, and 411 x 891 dp compact layouts. Synthetic obstruction values are test
inputs, not production measurements.
Tests cover full viewport and divider underlay, post row and account row click bounds, continuation
callbacks, final result reach above the dock and above bottom obstruction, dock size and position stability,
chip selection callbacks, branch forwarding, retained query and category, retained list position, and
synthetic IME open and close.
Wide Search applies no IME field inset. Compact Search keeps its measured 336 dp IME control gap.
Three test setup errors were corrected without production changes: the shell branch fixture left the
navigator on Home, a single-word query is an exact hashtag in this codebase, and a fixed shared tab value
cannot record a chip selection. One production correction followed a real finding: account rows now clear
through the list's absolute content inset, because padding inside the account row chain left its clickable
bounds at the full item width.
One unrelated pre-existing flake appeared in one full run: `MastodonIntegrationTest
cancelingTimelinePageCancelsRequestAndAllowsRetry` failed with `IOException: Gave up waiting for queue to
shut down`. That suite passes 67 tests in isolation and is untouched by this slice. The rerun gate is green.
No existing test was weakened. Direct review found no unresolved required findings.
Process checks before each Gradle run found no external Java/Gradle build. No external process was stopped.
No ADB or live-server check ran. Compact IME policy and chip travel were not changed.
4C-4 Photo Grid focused `PhotoGridClearanceTest` passes with 9 tests. Existing suites pass unchanged with 8 + 10
+ 8 + 7 + 22 + 36 tests. Full `test assembleRelease` passes with 156 suites, 1,616 tests, zero failures/errors/skips,
and successful release assembly.
Fixtures cover 800 x 600 dp and 600 x 800 dp wide layouts, a 600 x 1000 dp shell branch fixture, and 411 x 891 dp
compact layouts. Synthetic obstruction values are test inputs, not production measurements.
Tests cover full viewport, tile bounds and the sensitive-tile reveal control, continuation and paging-error retry
callbacks, final-tile reach above the dock and above bottom obstruction, dock size and clearance stability, timeline
and saved-hashtag chip selection, the add-hashtag dialog, branch forwarding, retained grid position, and compact
compatibility in both layout directions.
A separate external `gradlew.bat clean installDebug` process started in this worktree during the first full gate and
removed `app/build/test-results` and `app/build/outputs`. That run could not be counted and is not slice evidence.
The process was not stopped; it finished on its own. The focused rerun and the repeated full gate are the recorded
results.
Three test-setup corrections followed real fixture behavior, all without production changes: the production tile key
repeats the origin, the `PhotoGridFeed.Hashtag` runtime name is unqualified, and the staggered grid distributes tiles
across lanes so no single tile owns the left edge. Two touch injections needed a real target: a tile is clicked by its
own tag, and the add-hashtag chip must be scrolled into view first. A temporary tag-dump test was used to read the
real tile key and was removed. No existing test was weakened.
Review was direct because the maintainer prohibits `problem_solver`. No unresolved required findings.
Process checks before each Gradle run found no external Java/Gradle build, except the external clean noted above.
No ADB or live-server check ran. Device rendering and production obstruction geometry remain unverified.
4C-4 Notifications focused `NotificationsClearanceTest` passes with 8 tests. Existing suites pass unchanged:
`NotificationsScreenTest` 14, `NotificationRouteResolverTest` 2, `NotificationLaunchHostTest` 5, `NavigationTest` 36.
Full `test assembleRelease` passes with 157 suites, 1,624 tests, zero failures/errors/skips, and successful
release assembly.
Fixtures cover an 800 x 600 dp wide layout, an 800 x 1000 dp shell branch fixture, and a 411 x 891 dp compact layout.
Synthetic obstruction values are test inputs, not production measurements.
Tests cover full viewport and list width, top chip-row bounds, row-surface underlay, row action bounds, dismiss and
follow-request control bounds and callbacks, load-older and retry callbacks, empty/error/sync-banner content bounds,
final-item reach above bottom obstruction, branch forwarding, retained filter selection and scroll position, and
compact compatibility in both layout directions.
One test-setup error was corrected without production changes: the compact comparison read a row after scrolling it
out of composition. The comparison now uses rows that stay visible. No existing test was weakened.
Review was direct because the maintainer prohibits `problem_solver`. No unresolved required findings.
Process checks before each Gradle run found no external Java/Gradle build. No external process was stopped.
No ADB or live-server check ran. Device rendering and production obstruction geometry remain unverified.
4C-4 Profile focused `ProfileClearanceTest` passes with 11 tests. The Profile, wide-navigation, and navigation suites
pass unchanged: `ProfileScreenTest` 24, `ProfileViewModelTest` 28, `ProfileTimelinePagerTest` 10, `WideNavigationTest` 9,
`NavigationTest` 36. Full `test assembleRelease` passes with 158 suites, 1,635 tests, zero failures/errors/skips, and
successful release assembly.
Fixtures cover 800 x 600 dp and 800 x 1000 dp wide layouts, an 800 x 1000 dp shell branch fixture, and 411 x 891 dp
compact layouts. Synthetic obstruction values are test inputs, not production measurements.
Tests cover full viewports, item-divider underlay, row and interaction bounds, the mirrored wide columns, header
message and follow controls, the dock start placement and its right and bottom clearance, final row and up-to-date
footer reach, load-older and retry callbacks, the error, empty, and null-account states, the Featured title, pinned
rows, details fields, the inline category row, branch forwarding, retained category selection and scroll position,
and compact compatibility in both layout directions.
The first focused run failed at test compilation because `ProfileLargePresentation` lacked the `dp` import. The second
focused run exposed one production gap and three test errors. The gap was real: the wide summary `Row` mirrors in RTL,
so the summary column reaches the pane physical right edge in RTL. Clearance now follows that column. The three test
errors were test setup only: a missing `onRefresh` callback recorder, branch assertions written for the presentation
without the summary, and a retained-scroll comparison that included the row edge that clearance moves.
A later focused run added distinct pinned and timeline divider tags plus null-account empty-state coverage, then the
final gate was repeated. Both full gates passed with the same counts.
Review was direct because the maintainer prohibits `problem_solver`. No unresolved required findings.
No existing test was weakened. Process checks before each Gradle run found no external Java/Gradle build.
No ADB or live-server check ran. Compact navigation, chip travel, dock geometry, paging, and feature state did not change.
Use the repository wrapper with `--no-daemon --console=plain`, an explicit timeout, and closed stdin.
Run `test assembleRelease` for each code slice. Run the complete Phase 4C rerun/lint gate before final completion.

# Next

All destination-clearance gates are complete. Complete the 4C-5 activation gate: integrate safe placement and
activate adaptive navigation. Record the 4C-5 contract before editing. Do not invent production geometry, and do not
change the completed clearance slices.

# Blockers

- No production vertical capsule/action measurements are approved. Reuse only dimensions derived from the shared presentation and existing source contracts; ask the maintainer where fit policy requires a new value.
- The DM recipient finder is implemented and its contract is ready. Wire it to the wide New conversation action only in the activation slice.
- All seven destination-clearance gates are complete: DM inbox, DM conversation, Home, Search, Photo Grid, Notifications, and Profile.
- The wide Profile summary column scroll range has no dock clearance. Its final details rows can end under the existing
  category dock. That overlap predates the clearance slices and the dock owns it, so 4C-5 must verify it before activation.
- The physical-right anchor needs a check against the RTL rail placement. `LargeScreenShell` places the rail at the
  physical right in RTL, so an anchored capsule and that rail can share that edge.
- A separate external `gradlew.bat clean installDebug` process ran in this worktree during an earlier slice and wiped
  shared build outputs. It was not stopped. Recheck shared-output activity before later Gradle gates.
  Device state was not checked.
- Device, physical foldable, API 29, live-server, signing, and TalkBack behavior remain unverified.
- Preserve the existing unrelated worktree, deleted Photo Grid test/PNGs, captures, scripts, caches, and writing-style edits.

# Last safe commit

`Add Profile obstruction clearance`, based on `29c38f5` — Add Notifications obstruction clearance.
Resolve this checkpoint's hash from Git. Nothing was pushed.
