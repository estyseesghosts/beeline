# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-07

## Continuation pointer

Read [tasks/hardening-0.4.0-phase2.md](tasks/hardening-0.4.0-phase2.md) for the active
hardening task, constraints, and verification.
Read AGENTS.md and its required pages before continuing.
The completed Phase 0 and 1 record remains at [tasks/hardening-0.4.0.md](tasks/hardening-0.4.0.md).
The preceding adaptive-navigation record remains at [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).

## Current position

Phase 0 through Phase 5 are complete. Phase 5 reconciled the architecture documentation
with source (four stale links fixed, final shape named), passed the full CI-parity gate
(52 Python tests, 1,692 JVM tests in 161 suites, lint, ktlint, debug and release assembly),
and captured Home and Search on emulator-5554 (API 37). The task record carries the Phase 5
section and the list of unverified items. The hardening plan has no remaining planned slice.
Do not push without user direction.

## Ownership and caution

The orchestrator owns implementation, review, records, validation, and Git.
The user allows targeted_fixer for narrowly scoped work packages; keep one implementation
owner per slice.
Two read-only subagents supported this closeout: one reviewed the extraction against the
invariants, one checked test and documentation coverage.
Preserve unrelated agent/style edits, the deleted Photo Grid test and PNGs, untracked captures, scripts, caches, and `tasks/4c.md`.
Recheck external Java/Gradle activity before builds. Use explicit reviewed commit paths.

## Last safe commit

Preceding safe commit: `9d2edf5` — `Clean ktlint debt in touched files (Slice 4.2)`.
The Phase 5 documentation and records commit (H14) follows it; resolve its hash from Git.
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
