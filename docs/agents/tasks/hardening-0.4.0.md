# Objective

Complete Phases 0 and 1 of [the hardening plan](../../fix_0.4.0.md).
Status: in progress. Owner and Git operator: orchestrator. Last reviewed: 2026-10-06.

# Invariants

- Keep existing feature contracts, owners, navigation, restoration, and account scope.
- Keep physical clearance, compact-wide caret registration, and shell-owned Home chip state.
- Do not change protocol behavior, persisted formats, dependencies, or unrelated work.
- Do not split ShellContent or begin Phase 2. Do not push.

# Decisions

This task is larger than one safe implementation slice.
Complete and verify slices 0.1, 0.2, and 1.1 through 1.5 in order.
The orchestrator owns implementation, records, validation, and Git.
The user prohibits problem_solver. The orchestrator reviews actual diffs directly before explicit-path commits.
The user authorized a mechanical ktlint repair slice after the full gate exposed existing errors.
The targeted_fixer stopped. The orchestrator accepted its stopped handoff and completed the mechanical repairs directly.
Commit each verified slice separately before starting the next one.

# Completed

- 0.1 committed at `3c5b2ca`: unchanged layout policy now lives in ui/layout.
- 0.2 committed at `a850d40`: completion rules and build wiki define the complete CI-parity sequence.
- Mechanical ktlint repair committed at `63d8231`: the full gate passes without new exemptions or weakened tests.
- 1.1 committed at `150d0d2`: notification and DM contracts pass directly through the existing destination adapter.
- 1.2 committed at `4af0279`: the Home shell destination is extracted into `ui/shell/ShellHomeDestination.kt`.
- 1.3 verified: Search and Photo Grid shell routing is extracted into `ui/shell/ShellSearchDestination.kt`. Focused and full gates pass.

# Current slice

1.4: extract Profile shell routing into `ui/shell/ShellProfileDestination.kt`.
Allowed scope: the new Profile adapter, the router Profile branch, Profile and shell tests, ownership documentation, and records.
Acceptance: preserve compact, compact-wide, and expanded presentation selection, selected-post clearing, drafts and saved navigation, self-profile checks, DM routing, the edit-profile callback, and reaction routing through `ProfileContract`.
Non-goals: Profile state ownership, layout, persistence, protocol, and navigation changes.
Validation: ProfileScreenTest, ProfileClearanceTest, ProfileViewModelTest, WideNavigationTest, NavigationTest, ShellCharacterizationTest, then full CI parity.
The new adapter owns wiring only. Compact-wide must still use the mobile one-column Profile. Expanded mode must still use `ProfileLargePresentation`.

# Files involved

- ui/shell/ShellProfileDestination.kt (new)
- ui/shell/ShellDestinationContent.kt (short router call)
- Profile and shell tests
- docs/agents/app-shell-ownership.md, docs/wiki/ui-and-navigation.md, and task records

# Verification

1.3 review: the router Search branch is one call to ShellSearchDestination. The adapter keeps AnimatedStatePane(navigator.searchPanel), ShellNavigator query and category state, SearchOwner results, PhotoGridOwner independent state, and the Search and Photo Grid post routing.
Focused SearchOwnerTest, SearchPanelRestorationTest, SearchClearanceTest, PhotoGridOwnerTest, PhotoGridScreenTest, PhotoGridClearanceTest, WideNavigationTest, and ShellCharacterizationTest pass.
The full CI-parity gate passes: 51 Python tests, zero architecture regressions, 1,691 debug tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
The new file adds only warning-level audit findings. No baseline exemptions were added and no test was weakened.
Run focused Profile and shell tests, then the local CI-parity gate before the 1.4 checkpoint.
Use the wrapper with closed stdin, explicit timeout, and required daemon flags.

# Next

Start 1.4: extract Profile shell routing into `ui/shell/ShellProfileDestination.kt`.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts, caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain unverified.

# Last safe commit

4af0279 — Extract Home shell destination.
