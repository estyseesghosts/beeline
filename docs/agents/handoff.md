# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-06

## Continuation pointer

Read [tasks/hardening-0.4.0.md](tasks/hardening-0.4.0.md) for the active task, constraints, and verification.
Read AGENTS.md and its required pages before continuing.
The preceding adaptive-navigation record remains at [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).

## Current position

The user requests Phases 0 and 1 of `docs/fix_0.4.0.md`, in that order.
0.1 relocates the unchanged layout policy to `ui/layout/`. Focused tests and architecture audit pass.
0.1 is committed at `3c5b2ca`. 0.2 completion documentation passes direct review and local-link checks.
The user-authorized mechanical ktlint repair is complete on the working tree.
The full CI-parity gate passes with 1,691 JVM tests, lint, ktlint, and debug/release assembly.
Direct source comparison and review find no feature behavior changes. No baseline exemptions were added.
Checkpoint Phase 0 slices separately, then start 1.1. Do not begin Phase 2 or split ShellContent.

## Ownership and caution

The orchestrator owns implementation, review, records, validation, and Git. The targeted_fixer's stopped scope is reassigned to the orchestrator.
The user prohibits problem_solver. No agents remain active. Do not push.
Preserve unrelated agent/style edits, the deleted Photo Grid test and PNGs, untracked captures, scripts, caches, and `tasks/4c.md`.
Recheck external Java/Gradle activity before builds. Use explicit reviewed commit paths.

## Last safe commit

Preceding safe commit: `3c5b2ca` — `Move layout direction policy into layout package`.
The 0.2 checkpoint subject is `Align completion verification with CI`.

## Limits

Physical-device rendering, API 29 instrumentation, TalkBack, signing, and live-server behavior remain unverified.
The local release assembly does not establish release signing or runtime acceptance.
