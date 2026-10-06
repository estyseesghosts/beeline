# Objective

Complete Phase 2 of [the hardening plan](../../fix_0.4.0.md): presentation-only
decomposition of high-value Compose functions. State does not move. Keep every existing
owner, contract, invariant, and test. This phase does not touch protocol, persistence,
account scope, or navigation policy.
Status: in progress. Owner and Git operator: orchestrator. Last reviewed: 2026-10-06.

# Invariants

- Keep existing feature contracts, owners, navigation, restoration, and account scope.
- Keep physical clearance, compact-wide caret registration, and shell-owned Home chip state.
- Do not change protocol behavior, persisted formats, dependencies, or unrelated work.
- Do not split ShellContent. Do not add a layer only to hit a line-count target.
- Do not weaken, skip, or rewrite tests. Do not add ktlint baseline exemptions.
- Preserve unrelated worktree changes: `.opencode/agents/*.md`,
  `importantdocs/writing_style.md`, the deleted `PhotoGridFeedViewModelTest.kt` and PNGs,
  untracked captures/scripts/caches, and `docs/agents/tasks/4c.md`.
- Do not push. Do not begin Phase 3.

# Decisions

This task is larger than one safe implementation slice.
Slices 2.1 through 2.4 are complete only when each is committed and gate-verified.
The orchestrator owns implementation, records, validation, and Git.
The user prohibits problem_solver. The orchestrator reviews actual diffs directly before
explicit-path commits.
Commit each verified slice separately before starting the next one.
Known flakes: `MastodonIntegrationTest` cancellation and `NotificationsViewModelTest`
test-isolation (`UncaughtExceptionsBeforeTest`). They pass alone; record them and rerun the gate.

# Completed

- 2.1 committed: preceding safe commit `f28430a`, subject `Separate Home feed effects from
  rendering`. `HomeFeed` now keeps the fallback bubble, derived visible rows,
  `HomePagingDemand`, and list-state selection. `HomePagingEffects` and
  `HomeScrollDirectionEffect` observe that state without owning it. `HomeFeedContent`
  renders the pull-to-refresh, list, error/empty/filtered-empty states, post rows,
  loading-more/footer, and wide dock. No Home state moved. The HomeFeed import ordering is
  fixed and its ktlint baseline entry is removed.
- 2.2 committed: preceding safe commit `6920602`, subject `Extract Profile timeline
  presentation`. New `ui/profile/ProfileTimelinePresentation.kt` renders the compact and
  compact-wide one-column Profile: `ProfileTimelineList`, mobile header, details, compact
  floating category row, compact-wide `LargeBottomDock`, and destination chips.
  `ProfileScreen` retains account resolution, `LaunchedEffect(account.id)`, physical
  clearance normalization, the profile-keyed saveable category visibility, the category chip
  `LazyListState`, self-profile determination, end-clearance calculation, and presentation
  selection. Compact and compact-wide share the one presentation; no third Profile
  implementation exists. A new `ProfileScreenTest` case covers compact ↔ compact-wide
  category visibility retention. The ProfileScreen import ordering is fixed and its ktlint
  baseline entry is removed.
- 2.3 committed: preceding safe commit `b342a3f`, subject `Decompose destination chip
  rendering`. `DestinationChipRow` keeps the selected-entry lookup, selected-entry scroll
  effect, visibility animation, and `LazyRow` assembly. New private `DestinationFilterChip`
  renders one chip (colors, press animation, selection scale, test tag, and semantics).
  The renamed private `DestinationChipVisibilityButton` renders the caret. The public/internal
  call contract, geometry, caret position and rotation, accessibility descriptions,
  selectable-group semantics, reduced-motion behavior, selected-chip auto-scroll, and
  keys/test tags are unchanged. The CategoryChips import ordering is fixed and its ktlint
  baseline entry is removed.
- 2.4 committed: preceding safe commit `490f674`, subject `Share notification filter-row
  presentation`. New private `NotificationFilterRow` renders the shared `DestinationChipRow`
  configuration for both the compact and wide branches. `NotificationsScreen` keeps the filter
  state (`selectedFilterName`, `chipRowVisible`, `chipListState`, `chipEntries`) and the two
  positioning containers: the compact `Box` with `compactContextualControlsPositioningInsets`
  and the wide `LargeBottomDock` with `notificationCaretPresentation`. No test tag, container,
  behavior, or state changed. The NotificationsScreen import ordering is fixed, the unused
  `WindowInsets` import is removed, and its ktlint baseline entry is removed.

# Current slice

Phase 2 is complete. All four slices (2.1–2.4) are committed and gate-verified. Await user
direction. Do not begin Phase 3.

# Files involved

- None. Phase 2 is complete.

# Verification

2.1 focused: `HomeFeedTest`, `HomePagingDemandTest`, `HomeClearanceTest` pass.
2.1 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The import-ordering fix removes the HomeFeed ktlint baseline entry with no new exemption.
  Two known cancellation flakes failed in separate runs and pass alone (see `logs/BUGS.txt`).
2.2 focused: `ProfileScreenTest`, `ProfileClearanceTest`, and `WideNavigationTest` pass.
2.2 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The ProfileScreen import-ordering fix removes its ktlint baseline entry with no new exemption.
2.3 focused: `CategoryChipsGeometryTest`, `WideNavigationTest`, `HomeFeedTest`,
  `NotificationsClearanceTest`, and `ProfileClearanceTest` pass.
2.3 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The CategoryChips import-ordering fix removes its ktlint baseline entry with no new exemption.
2.4 focused: `NotificationsScreenTest`, `NotificationsClearanceTest`,
  `CategoryChipsGeometryTest`, and `WideNavigationTest` pass.
2.4 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The NotificationsScreen import-ordering fix removes its ktlint baseline entry with no new exemption.
  Slice 2.4 was implemented by a targeted_fixer child session under a complete dispatch contract;
  the orchestrator reviewed the diff and ran the Python test suite.

# Next

Phase 2 is complete. Await user direction. Do not begin Phase 3.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts,
caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain
unverified.

# Last safe commit

490f674 — Decompose destination chip rendering.
