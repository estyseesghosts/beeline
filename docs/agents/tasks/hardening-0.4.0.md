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
- 1.1 verified: notification and DM contracts pass directly through the existing destination adapter. Focused and full gates pass.

# Current slice

1.2: extract the Home shell destination into `ui/shell/ShellHomeDestination.kt`.
Allowed scope: the new Home adapter, the router Home branch, Home and shell tests, ownership documentation, and records.
Acceptance: preserve the connected versus empty Home refresh distinction, timeline chip clearing, compact-wide caret registration, shell-owned homeChipListState and homeChipRowVisible, and the wide bottom dock.
Non-goals: Home state ownership, layout, persistence, protocol, and navigation changes.
Validation: HomeFeedTest, HomeClearanceTest, HomePagingDemandTest, NavigationTest, WideNavigationTest, ShellCharacterizationTest, then full CI parity.
The new adapter owns wiring only, not Home state. Do not recreate chip state in the new file.

# Files involved

- ui/shell/ShellHomeDestination.kt (new)
- ui/shell/ShellDestinationContent.kt (short router call)
- Home and shell tests
- docs/agents/app-shell-ownership.md, docs/wiki/ui-and-navigation.md, and task records

# Verification

1.1 review: the adapter reads notifications.state, notifications.actions, directMessages.state, and directMessages.actions; shell notification and settings routes, account identity, and clearance stay explicit.
Focused NotificationsClearanceTest, NotificationsScreenTest, DirectMessageScreenTest, WideNavigationTest, and ShellCharacterizationTest pass.
The known MastodonIntegrationTest cancellation flake passes alone.
The full CI-parity gate passes: 51 Python tests, zero architecture regressions, 1,691 debug tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
No baseline exemptions were added and no test was weakened.
Run focused Home and shell tests, then the local CI-parity gate before the 1.2 checkpoint.
Use the wrapper with closed stdin, explicit timeout, and required daemon flags.

# Next

Start 1.2: extract the Home shell destination into `ui/shell/ShellHomeDestination.kt`.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts, caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain unverified.

# Last safe commit

63d8231 — Repair existing ktlint gate violations.
