# Objective

Complete Phases 0 and 1 of [the hardening plan](../../fix_0.4.0.md).
Status: complete. Owner and Git operator: orchestrator. Last reviewed: 2026-10-06.

# Invariants

- Keep existing feature contracts, owners, navigation, restoration, and account scope.
- Keep physical clearance, compact-wide caret registration, and shell-owned Home chip state.
- Do not change protocol behavior, persisted formats, dependencies, or unrelated work.
- Do not split ShellContent or begin Phase 2. Do not push.

# Decisions

This task is larger than one safe implementation slice.
Slices 0.1, 0.2, and 1.1 through 1.5 are complete.
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
- 1.4 committed at `5cf3621`: Profile shell routing is extracted into `ui/shell/ShellProfileDestination.kt`.
- 1.5 verified: `ShellDestinationContent` is a router. It applies the scaffold and inset policy, animates destinations, scopes saveable state, resolves the notification-detail and local-page routes, and dispatches to the feature shell adapters.

# Current slice

None. Phases 0 and 1 are complete.
The destination dispatcher is decomposed into `ShellHomeDestination`, `ShellSearchDestination`, `AppNotificationsDestinationContent`, and `ShellProfileDestination`.
Do not split ShellContent and do not begin Phase 2.

# Files involved

- ui/shell/ShellDestinationContent.kt
- docs/agents/app-shell-ownership.md and docs/wiki/ui-and-navigation.md
- task records

# Verification

1.5 review: the router holds only the scaffold and inset policy, destination animation, saveable destination state scope, the notification-detail route, the local-page route, and the dispatch calls.
The complete shell presentation set passes: ShellCharacterizationTest, NavigationTest, ShellNavigatorTest, ShellNavigatorRestorationTest, ShellBackPolicyTest, WideNavigationTest, and the five clearance suites.
function_audit.py reports ShellDestinationContent as a 227-line router with 37 parameters. ShellContent remains a composition coordinator. The plan does not require a ShellContent split on size alone, so none follows.
The full CI-parity gate passes: 51 Python tests, zero architecture regressions, 1,691 debug tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
No baseline exemptions were added and no test was weakened.

# Next

Report Phase 1 completion. Do not begin Phase 2 or split ShellContent.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts, caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain unverified.

# Last safe commit

5cf3621 — Extract Profile shell destination.
