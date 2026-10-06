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
Prepare independent 0.2 documentation while that repair runs. Commit verified slices separately before Phase 1.

# Completed

0.1 is verified: unchanged layout policy now lives in ui/layout, with updated callers and documented paths.

# Current slice

Checkpoint 0.1, then 0.2 documentation and the user-authorized mechanical ktlint repair.
The policy move passes direct review. No preference, publication, locale, or clearance behavior changed.
The mechanical comparison preserves source bodies after normalizing approved constant names, whitespace, and equivalent string templates.
The empty class body and two test throw expressions have equivalent behavior.
No baseline exemptions were added. Tests remain intact; the deleted Photo Grid test is pre-existing user work.

# Files involved

- ui/LayoutDirectionPolicy.kt -> ui/layout/LayoutDirectionPolicy.kt
- ui/ConnectedApp.kt and ui/settings/DisplaySettingsScreen.kt
- AppLayoutDirectionTest and SettingsDisplayTest
- docs/agents/app-shell-ownership.md, docs/wiki/ui-and-navigation.md, and tasks/force-layout-direction.md

# Verification

Architecture tool tests pass: 51 tests.
The policy move removes the only architecture regression. Focused layout/settings tests pass.
The initial gate exposed 94 main-source and 49 test-source ktlint errors. All reported errors are repaired.
The final CI-parity gate passes: 51 Python tests, zero architecture regressions, 1,691 debug tests, lint, ktlint, debug assembly, and release assembly.
All 161 JVM suites pass without failures, errors, or skips.
Two mistakenly removed required imports were restored before the final gate.
0.2 document links resolve, and the scoped documentation diff passes whitespace checks.
Run the architecture audit, focused layout/settings tests, and local CI-parity gate before checkpoint.
Use the wrapper with closed stdin, explicit timeout, and required daemon flags.

# Next

Commit the reviewed Phase 0 slices separately, then start 1.1 contract forwarding.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts, caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain unverified.

# Last safe commit

a04b3eb — Bottom-anchor compact-wide navigation with contextual tab caret.
