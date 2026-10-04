# Objective

Implement Phase 4B: compact floating navigation from shared buttons.

# Invariants

Preserve unrelated worktree and index changes. Keep one navigator, four fixed compact groups,
remembered child icons, profile account switching, and separate contextual actions.
Use the approved 212 × 56 dp capsule and 48 dp targets. Use existing motion and geometry owners.
Do not pad the full-screen viewport. Do not change baselines, deleted tests, or Phase 4C–4E behavior.
Do not push.

# Decisions

This task is larger than one safe implementation slice. Complete and commit each slice before the next.
Each slice has one implementation owner. The orchestrator owns records, validation, and Git.
The maintainer requests no further problem_solver use. The orchestrator plans and reviews fixer-owned changes directly.

Maintainer clarification (2026-10-04), required end state:

- Compact-narrow, compact-wide, and large/tablet share the underlying navigation components.
  Complete this reuse by the end of the navigation work; intermediate slices may prepare it.
- Compact-narrow always retains the existing four-button grouped bar and existing narrow layout.
- Compact-wide and large/tablet use the same vertical, expanded six-button navigation presentation:
  Home, Search, Photo Grid, Notifications, Direct Messages, Profile.
  Tablet detail panes may differ; they do not justify separate navigation components or state.
- The available virtual device is intended to simulate compact-wide. Use it for that acceptance target.
  Do not treat its current rendering as evidence of the final layout policy or a narrow-phone baseline.
- Current source still classifies presentation by width (600/840 dp in LargeLayoutMode.kt).
  Compact-wide activation and the vertical container remain Phase 4C work, not completed 4B-1 behavior.

- 4B-1 — one traveling compact selection indicator. Keep button geometry, callbacks, and contextual actions unchanged.
- 4B-2 — reusable navigation button and capsule presentation for horizontal and future vertical containers.
  Do not replace the wide rail until Phase 4C. Preserve existing unread behavior without increasing height.
- 4B-3 — consistent compact IME/system-bar placement and scroll-content clearance through existing geometry owners.
  Confirm the destination owner map before editing. Split further if destination behaviors require independent gates.

# Completed

Phase 4A is complete: e9c0e2c, 6c0bd07, 90a132e; gate records 6e50217.
4B-1 is complete at 0117210. The maintainer clarification is committed at 131b9a7.
4B-2 is implemented, verified, and reviewed by the orchestrator independently of its fixer owner.
NavigationButton shares tint, scale, press treatment, and semantics. NavigationCapsule shares material and indicator presentation.
Compact navigation uses these components. A six-target vertical fixture verifies reuse; the production wide rail remains unchanged.

# Current slice

4B-2 is complete. The same fixer handled implementation and review repairs; it has no active edit assignment.
The next slice is 4B-3: coordinate compact IME/system-bar placement and scroll-content clearance.
Bound the destination owner map and smallest change surface before editing. The orchestrator owns this investigation.
Keep clearance inside scroll content. Coordinate Search controls and the DM editor with navigation placement.
Non-goals: production wide migration, adaptive activation, unread data wiring, tab redesign, and full-viewport padding.
Fail gates: unexplained test failure, two failed fixes for one root problem, changed navigation ownership,
unapproved geometry, or unrelated scope expansion. Stop and investigate before more edits.

# Files involved

- app/src/main/java/me/foxtails/palustris/ui/navigation/CompactAppNavigation.kt
- app/src/main/java/me/foxtails/palustris/ui/navigation/NavigationPresentation.kt
- app/src/main/java/me/foxtails/palustris/ui/navigation/NavigationItem.kt (contract documentation only)
- app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationPresentationTest.kt
- docs/wiki/ui-and-navigation.md
- docs/agents/app-shell-ownership.md
- docs/agents/tasks/beeline-0.4.0.md
- docs/agents/handoff.md
- logs/261004-190000.txt (local, ignored)

# Verification

CompactNavigationSelectionTest and NavigationPresentationTest each report three passing tests.
The screenshot-enabled navigation/feed/WideNavigationTest run passes: 11 suites, 165 tests, zero failures/errors/skips.
The grouped run passes again after review repairs. Existing compact selection tests remain unchanged.
The full test assembleRelease --rerun-tasks :app:lintDebug gate passes: 105 executed tasks;
153 suites, 1574 tests, zero failures/errors/skips. Release assembly and lintDebug succeed.
The final post-review test assembleRelease :app:lintDebug gate passes: 20 executed, 85 up-to-date tasks;
the same 153 suites and 1574 tests pass. The orchestrator reviewed the fixer-owned diff and resolved required findings.
Home and Search fixture screenshots were visually inspected at app/build/ui-screenshots/.
They show one selected bubble behind the selected icon. Robolectric captures do not verify device rendering.
Document links and slice whitespace checks pass. No baseline changed. No device state changed.
Use the wrapper with --no-daemon --console=plain, explicit timeout, and closed stdin.
Run test assembleRelease --rerun-tasks and :app:lintDebug before Phase 4B completion.
Review the actual slice diff independently before each commit. Capture screenshots where available.

# Next

4B-3 — bound coordinated IME placement and scroll-content clearance before editing.
Geometry investigation confirms that capsule placement, contextual docks, and scroll clearance must change together.
The global IME policy must also coordinate the DM editor to prevent overlap. Bound that slice before editing.

# Blockers

ktlintCheck has pre-existing repository-wide findings. Do not change its baseline.
The deleted Photo Grid test remains excluded from the available suite.
API 29 smoke, device restoration, live-server behavior, signing, physical foldable behavior,
and TalkBack remain unverified. Logs and the main plan are ignored; do not force-add them.

# Last safe commit

131b9a7 — Record shared adaptive navigation and compact-wide emulator requirements.
Completed slice subject: Share navigation button and capsule presentation.
Resolve the new slice hash from Git. Nothing was pushed.
