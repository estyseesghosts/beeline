# Objective

Complete Phase 4B-3: coordinated compact IME placement and scroll-content clearance.

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
4B-2 is complete at a318cc0, independently reviewed by the orchestrator against its fixer-owned diff.
NavigationButton shares tint, scale, press treatment, and semantics. NavigationCapsule shares material and indicator presentation.
Compact navigation uses these components. A six-target vertical fixture verifies reuse; the production wide rail remains unchanged.
4B-3 is complete: compact placement and destination clearance share the IME/system-bar base.
The DM editor clears navigation in its existing column. Phase 4B implementation is complete; device acceptance remains unverified.

# Current slice

4B-3 is implemented by the orchestrator. It has one implementation and Git owner; no child assignment exists.
The bounded change uses max(system-bar, IME) as the compact base, then adds navigation and destination controls.
The DM editor reserves the same compact navigation clearance in its existing column.
The thread keeps normal content spacing because the editor already occupies its own space.
Navigation state, callbacks, account switching, grouped icons, and wide production presentation remain unchanged.
Focused checks, direct diff review, full rerun gate, and lint pass. This checkpoint includes the reviewed slice and records.
Non-goals: production wide migration, adaptive activation, unread data wiring, tab redesign, and full-viewport padding.
Fail gates: unexplained test failure, two failed fixes for one root problem, changed navigation ownership,
unapproved geometry, or unrelated scope expansion. Stop and investigate before more edits.

# Files involved

- app/src/main/java/me/foxtails/palustris/ui/layout/CompactOverlayMetrics.kt
- app/src/main/java/me/foxtails/palustris/ui/directmessages/DirectMessageConversationScreen.kt
- app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt
- docs/wiki/ui-and-navigation.md
- docs/agents/app-shell-ownership.md
- docs/agents/tasks/beeline-0.4.0.md
- docs/agents/handoff.md
- logs/261004-210000.txt (local, ignored)

# Owner map

CompactOverlayMetrics owns compact geometry. ShellContent places navigation and Home tabs.
Search, Photo Grid, Notifications, Profile, Home, and the DM inbox keep clearance inside scroll content.
Search and Profile already pass IME insets. Other compact callers now use the same default IME policy.
The DM conversation owns its in-flow editor and weighted thread. It does not have a floating filter dock.
No feature state, protocol, persistence, account, or lifecycle boundary changes.

# Verification

Screenshot-enabled focused gate passes: NavigationTest, DirectMessageScreenTest, PhotoGridScreenTest,
WideNavigationTest, and HomeFeedTest. Five suites, 103 tests, zero failures/errors/skips.
Search IME dismissal asserts a stable gap, visible navigation, and unchanged viewport bounds.
Final-content tests cover Home, Search, Photo Grid, Notifications, Profile, and DM with IME open and closed.
The new Home assertion initially failed because scrolling hides chrome. Two undersized reverse gestures failed to reveal it.
The owner stopped and investigated. A gesture above touch slop now reveals chrome; the focused Home test passes.
Search and DM synthetic-IME screenshots were captured and visually inspected at app/build/ui-screenshots/.
Direct diff review checks shared geometry, inset consumption, DM double counting, and unchanged wide policy.
The full test assembleRelease --rerun-tasks :app:lintDebug gate passes: 105 executed tasks.
All 153 suites and 1576 tests pass with zero failures/errors/skips. Release assembly and lintDebug succeed.
Document links and slice whitespace checks pass. Direct review has no unresolved required findings.
No baseline or device state changed. Unrelated work remains intact.
Use the wrapper with --no-daemon --console=plain, explicit timeout, and closed stdin.
Run test assembleRelease --rerun-tasks and :app:lintDebug before Phase 4B completion.
Review the actual slice diff before each commit. Capture screenshots where available.

# Next

Bound Phase 4C before editing: adaptive geometry, shared vertical navigation, and production rail replacement.
Keep measured tab/filter/Search clearance work in Phase 4E after its dependencies.

# Blockers

ktlintCheck has pre-existing repository-wide findings. Do not change its baseline.
The deleted Photo Grid test remains excluded from the available suite.
API 29 smoke, device restoration, live-server behavior, signing, physical foldable behavior,
and TalkBack remain unverified. Logs and the main plan are ignored; do not force-add them.

# Last safe commit

a318cc0 — Share navigation button and capsule presentation.
Completed slice subject: Coordinate compact IME placement and scroll clearance.
Resolve its new hash from Git. Nothing was pushed.
