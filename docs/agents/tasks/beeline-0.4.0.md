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
- 4C-1 — direction-aware hinge/content coordinate characterization; commit `1dc1d39`, `Characterize wide pane coordinate behavior`, preceded by `88044c3`.
- `LargeLayoutModeTest` covers both coordinate directions and the existing pane/hinge policy. The full test/build gate reports 153 suites, 1,578 tests, zero failures/errors/skips; release assembly succeeds.
- Navigation presentation, pane-selection policy, and destinations remain unchanged. RTL hinge translation now uses the physical content origin. Physical hinge behavior remains unverified.
- 4C-2 — session-owned recipient finder, active-source account lookup, candidate validation, and cancel-safe state; commit subject `Add direct message recipient finder`, preceded by `1dc1d39`.
- Focused direct-message tests pass: 30 ViewModel tests and 15 Compose tests. Full `test assembleRelease` passes with 153 suites, 1,584 tests, zero failures/errors/skips, and successful release assembly.
- `DirectMessagesContract` now exposes the finder action. The wide navigation action remains unwired until 4C-5; presentation and destination behavior do not change in this slice.

# Current slice

4C-3 prepares the shared vertical presentation and contextual-action rendering without activating it.
The orchestrator owns implementation and Git. No child assignment exists. The preceding slice is `Add direct message recipient finder`, based on `1dc1d39`.
Compact-narrow geometry and mappings remain fixed. Production vertical navigation remains inactive until geometry approval and destination-clearance gates pass.

# Files involved

- Current slice: `ui/navigation/NavigationPresentation.kt`, `NavigationItem.kt`, `CompactAppNavigation.kt`, `ui/large/LargeNavigationRail.kt`, focused navigation tests, task/handoff/ownership/wiki records, and local log.
- Later slices use the scoped file lists above. Expand a destination into its own contract before editing if its behavior needs an independent gate.

# Verification

4C-1 focused `LargeLayoutModeTest` passes. Its required `test assembleRelease` gate passed with 153 suites,
1,578 tests, zero failures/errors/skips, and successful release assembly. A second wrapper run also completed with tasks up to date.
4C-2 focused `DirectMessageViewModelTest` and `DirectMessageScreenTest` pass: 45 tests, zero failures/errors/skips.
Its `test assembleRelease` gate passes with 153 suites, 1,584 tests, zero failures/errors/skips, and successful release assembly.
Existing Phase 4B results remain historical evidence only.
Use the repository wrapper with `--no-daemon --console=plain`, an explicit timeout, and closed stdin.
Run `test assembleRelease` for each code slice. Run the complete Phase 4C rerun/lint gate before final completion.

# Next

Prepare the shared vertical navigation presentation and reusable contextual action without activation.

# Blockers

- No production vertical capsule/action measurements are approved. Reuse only dimensions derived from the shared presentation and existing source contracts; ask the maintainer where fit policy requires a new value.
- The DM recipient finder is implemented and its contract is ready. Wire it to the wide New conversation action only in the activation slice.
- Device, physical foldable, API 29, live-server, signing, and TalkBack behavior remain unverified.
- Preserve the existing unrelated worktree, deleted Photo Grid test/PNGs, captures, scripts, caches, and writing-style edits.

# Last safe commit

`1dc1d39` — Characterize wide pane coordinate behavior.
The 4C-2 checkpoint commit subject is `Add direct message recipient finder`. Nothing was pushed.
