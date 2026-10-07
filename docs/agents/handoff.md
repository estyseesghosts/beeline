# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-06

## Continuation pointer

Read [tasks/hardening-0.4.0-phase2.md](tasks/hardening-0.4.0-phase2.md) for the active
hardening task, constraints, and verification.
Read AGENTS.md and its required pages before continuing.
The completed Phase 0 and 1 record remains at [tasks/hardening-0.4.0.md](tasks/hardening-0.4.0.md).
The preceding adaptive-navigation record remains at [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).

## Current position

Phase 0 through Phase 4 are committed and gate-verified. Phase 4 Slice 4.1 recorded the
architecture metrics baseline (`22e62d5`). Slice 4.2 sorted imports and removed unused
imports in 20 files, and removed 18 stale entries from `app/ktlint-baseline.xml`. It added no
baseline exemption and changed no behavior. The task record carries the Phase 4 section.
Phase 5 (documentation integrity and final acceptance) is next.
Do not push. Do not begin Phase 5 without user direction.

## Ownership and caution

The orchestrator owns implementation, review, records, validation, and Git.
The user allows targeted_fixer for narrowly scoped work packages; keep one implementation
owner per slice.
Two read-only subagents supported this closeout: one reviewed the extraction against the
invariants, one checked test and documentation coverage.
Preserve unrelated agent/style edits, the deleted Photo Grid test and PNGs, untracked captures, scripts, caches, and `tasks/4c.md`.
Recheck external Java/Gradle activity before builds. Use explicit reviewed commit paths.

## Last safe commit

Preceding safe commit: `22e62d5` — `Record complete architecture metrics baseline (Slice 4.1, after b8b0ba5)`.
Slice 4.2 is the commit that follows it; resolve its hash from Git.
The ktlint baseline still carries 177 file entries. Six deferred entries remain by decision:
`SavedCollectionsHost` (filename), `ConnectedApp` and `PostThreadViewModel` (keyword-spacing),
`MastodonIntegrationTest` (paren-spacing), `NavigationTest` (string-template), and
`SettingsViewModelTest` (function-expression-body).

## Limits

Physical-device rendering, API 29 instrumentation, TalkBack, signing, and live-server behavior remain unverified.
The local release assembly does not establish release signing or runtime acceptance.
The fresh `0bd8e7d` debug build installs and launches on emulator-5554 (API 37) with
`MainActivity` resumed and no application crash; see `logs/phase31-emulator-launch.png`.
Live thread loading on a real Misskey account remains unverified; no test account exists.
The Slice 4.2 debug build installs and launches on emulator-5554 (API 37) with `MainActivity`
resumed; see `logs/phase42-emulator-launch.png`. A SIGABRT in an emulator HAL process
(`android.hardwar`) appeared in the crash log and is not the application.
