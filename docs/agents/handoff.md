# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-07

## Continuation pointer

Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the active Phase 4D5/4E task, audit, and slice plan.
Read AGENTS.md and its required pages before continuing.
The completed hardening records remain at [tasks/hardening-0.4.0.md](tasks/hardening-0.4.0.md) and [tasks/hardening-0.4.0-phase2.md](tasks/hardening-0.4.0-phase2.md).

## Current position

Hardening Phases 0-5 are complete. Phase 4D5/4E is in progress: the audit is recorded, and slices 1 (stable tab IDs) and 2 (chip travel to the display edge) are committed with passing full gates. Next is slice 3 (4E1, measured clearance).
The caret stays in the first logical position (user decision); it flips in RTL. Do not push without user direction.

## Ownership and caution

The orchestrator owns implementation, review, records, validation, and Git.
The user allows targeted_fixer for narrowly scoped work packages; keep one implementation
owner per slice.
Two read-only subagents supported this closeout: one reviewed the extraction against the
invariants, one checked test and documentation coverage.
Preserve unrelated agent/style edits, the deleted Photo Grid test and PNGs, untracked captures, scripts, caches, and `tasks/4c.md`.
Recheck external Java/Gradle activity before builds. Use explicit reviewed commit paths.

## Last safe commit

Preceding safe commit: `51455c5` — `Reconcile documentation and record Phase 5 acceptance (H14)`.
The slice 1 commit `Use stable keys for Notifications and Photo Grid chips` follows it; resolve its hash from Git.
The ktlint baseline still carries 177 file entries. Six deferred entries remain by decision:
`SavedCollectionsHost` (filename), `ConnectedApp` and `PostThreadViewModel` (keyword-spacing),
`MastodonIntegrationTest` (paren-spacing), `NavigationTest` (string-template), and
`SettingsViewModelTest` (function-expression-body).

## Limits

Physical-device rendering, API 29 instrumentation, TalkBack, signing, and live-server behavior remain unverified.
The local release assembly does not establish release signing or runtime acceptance.
The Phase 5 debug build installs and launches on emulator-5554 (API 37); see
`logs/phase5-launch2/screenshot.png` and `logs/phase5-search.png`. No application crash appears
in logcat. A SIGABRT in an emulator HAL process (`android.hardwar`) is not the application.
Compact-wide, tablet, IME, Profile chips, restoration, and forced RTL runtime checks were not run.
Live thread loading on a real Misskey account remains unverified; no test account exists.
