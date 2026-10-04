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
The orchestrator owns implementation, records, validation, and Git for each slice.

- 4B-1 — one traveling compact selection indicator. Keep button geometry, callbacks, and contextual actions unchanged.
- 4B-2 — reusable navigation button and capsule presentation for horizontal and future vertical containers.
  Do not replace the wide rail until Phase 4C. Preserve existing unread behavior without increasing height.
- 4B-3 — consistent compact IME/system-bar placement and scroll-content clearance through existing geometry owners.
  Confirm the destination owner map before editing. Split further if destination behaviors require independent gates.

# Completed

Phase 4A is complete: e9c0e2c, 6c0bd07, 90a132e; gate records 6e50217.
The prior full gate passed with 96 actionable tasks. lintDebug passed.
4B-1 is implemented, verified, and independently reviewed. The capsule owns one traveling indicator.

# Current slice

4B-1 is complete. The next slice is 4B-2: shared navigation presentation.
Current owner: CompactContextualNavigationBar in CompactAppNavigation.kt.
Smallest change surface: that component and CompactNavigationSelectionTest, plus records and navigation documentation.
Acceptance: exactly one indicator before, during, and after selection; stable four button bounds;
indicator settles beneath the selected item in LTR and RTL; reduced motion snaps; no callback on animation completion.
Non-goals: button extraction, wide redesign, unread data wiring, IME/clearance changes, tab redesign.
Fail gates: unexplained test failure, two failed fixes for one root problem, changed navigation ownership,
unapproved geometry, or unrelated scope expansion. Stop and investigate before more edits.

# Files involved

- app/src/main/java/me/foxtails/palustris/ui/navigation/CompactAppNavigation.kt
- app/src/test/java/me/foxtails/palustris/ui/navigation/CompactNavigationSelectionTest.kt
- docs/wiki/ui-and-navigation.md
- docs/agents/app-shell-ownership.md
- docs/agents/tasks/beeline-0.4.0.md
- docs/agents/handoff.md
- logs/261004-180000.txt (local, ignored)

# Verification

CompactNavigationSelectionTest reports 3 passing tests. NavigationTest reports 34 passing tests.
The feed package and WideNavigationTest grouped run passes.
The full test assembleRelease --rerun-tasks gate passes: 96 executed tasks;
152 suites, 1571 tests, zero failures, errors, or skipped tests. Release assembly succeeds.
lintDebug passes. Independent review reports no blocking or required findings.
The screenshot-enabled NavigationTest run passes. Home and Search fixture screenshots were visually inspected
at app/build/ui-screenshots/. They show a single selected bubble behind the selected icon.
These Robolectric captures do not verify device rendering.
Use the wrapper with --no-daemon --console=plain, explicit timeout, and closed stdin.
Run test assembleRelease --rerun-tasks and :app:lintDebug before Phase 4B completion.
Review the actual slice diff independently before each commit. Capture screenshots where available.

# Next

4B-2 — share button and capsule presentation without implementing the Phase 4C wide rail replacement.
Read-only investigation will bound 4B-3 before implementation.

# Blockers

ktlintCheck has pre-existing repository-wide findings. Do not change its baseline.
The deleted Photo Grid test remains excluded from the available suite.
API 29 smoke, device restoration, live-server behavior, signing, physical foldable behavior,
and TalkBack remain unverified. Logs and the main plan are ignored; do not force-add them.

# Last safe commit

6e50217 — Record the green Phase 4A gate.
4B-1 slice commit subject: Move one compact selection indicator between fixed navigation slots.
Resolve the new slice hash from Git. Do not create a separate records commit.
