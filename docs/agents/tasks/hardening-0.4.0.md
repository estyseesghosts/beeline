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
- 1.3 committed at `52dd028`: Search and Photo Grid shell routing is extracted into `ui/shell/ShellSearchDestination.kt`.
- 1.4 verified: Profile shell routing is extracted into `ui/shell/ShellProfileDestination.kt`. Focused and full gates pass.

# Current slice

1.5: reduce `ShellDestinationContent` to routing only.
Allowed scope: the router scaffold and inset policy, destination animation, saveable state scope, notification-detail route, local-page route, dispatch calls, imports, KDoc, and records.
Acceptance: the router keeps no substantial feature presentation body. It dispatches to `ShellHomeDestination`, `ShellSearchDestination`, `AppNotificationsDestinationContent`, and `ShellProfileDestination`.
Non-goals: new routing layers, ShellContent changes, feature state ownership, layout, persistence, protocol, and navigation changes.
Validation: ShellCharacterizationTest, NavigationTest, ShellNavigatorTest, ShellNavigatorRestorationTest, ShellBackPolicyTest, WideNavigationTest, and the five clearance suites, then full CI parity.
Do not split ShellContent. Do not add a layer only to hit a line-count target.

# Files involved

- ui/shell/ShellDestinationContent.kt (imports and KDoc)
- Shell presentation tests
- docs/agents/app-shell-ownership.md, docs/wiki/ui-and-navigation.md, and task records

# Verification

1.4 review: the router Profile branch is one call to ShellProfileDestination. The adapter keeps compact, compact-wide, and expanded selection, selected-post clearing, drafts and saved navigation, self-profile checks, DM routing, the edit-profile callback, and reaction routing through `ProfileContract`.
Focused ProfileScreenTest, ProfileClearanceTest, ProfileViewModelTest, WideNavigationTest, NavigationTest, and ShellCharacterizationTest pass.
The full CI-parity gate passes: 51 Python tests, zero architecture regressions, 1,691 debug tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
The new file adds only warning-level audit findings. No baseline exemptions were added and no test was weakened.
Run the complete shell presentation set, then the local CI-parity gate before the 1.5 checkpoint.
Use the wrapper with closed stdin, explicit timeout, and required daemon flags.

# Next

Start 1.5: reduce `ShellDestinationContent` to routing and update its KDoc.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts, caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain unverified.

# Last safe commit

52dd028 — Extract Search and Photo Grid shell destination.
