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

# Current slice

2.2 — Extract the non-expanded Profile presentation (new `ui/profile/ProfileTimelinePresentation.kt`;
`ui/profile/ProfileScreen.kt`, 494 lines). Move the compact + compact-wide one-column body:
`ProfileTimelineList`, mobile header, details, compact floating category row, compact-wide
`LargeBottomDock`, and destination chips. `ProfileScreen` retains: account resolution,
`LaunchedEffect(account.id)`, physical clearance normalization, profile-keyed category visibility
state, the category chip `LazyListState`, self-profile determination, end-clearance calculation,
and presentation selection; then delegates to `ProfileLargePresentation` or
`ProfileTimelinePresentation`. Critical: do not create category visibility or category chip list
state inside the new presentation; `ProfileScreen` keeps ownership so state survives placement
changes. Compact and compact-wide must share the same one-column presentation; do not create a
third Profile implementation.
Tests: `ProfileScreenTest`, `ProfileClearanceTest`, `WideNavigationTest`. Add an explicit
regression test for compact ↔ compact-wide category state retention if none exists.
Commit subject: `Extract Profile timeline presentation`.

# Files involved

- ui/profile/ProfileScreen.kt and the new ui/profile/ProfileTimelinePresentation.kt
- docs/agents/app-shell-ownership.md and docs/wiki/ui-and-navigation.md
- task records

# Verification

2.1 focused: `HomeFeedTest`, `HomePagingDemandTest`, `HomeClearanceTest` pass.
2.1 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The import-ordering fix removes the HomeFeed ktlint baseline entry with no new exemption.
  Two known cancellation flakes failed in separate runs and pass alone (see `logs/BUGS.txt`).
For each later slice: run focused tests, then the complete local CI-parity gate with closed
stdin and an explicit timeout.

# Next

Implement slice 2.2, run focused tests and the full CI-parity gate, update records, and
commit as `Extract Profile timeline presentation`.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts,
caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain
unverified.

# Last safe commit

f28430a — Reduce shell destination content to routing.
