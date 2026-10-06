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
Phase 0 is committed: 0.1 at `3c5b2ca`, 0.2 at `a850d40`, and the mechanical ktlint repair at `63d8231`.
1.1 is committed at `150d0d2` and passes the notification and DM contracts through the shell adapter.
1.2 passes direct review and the full gate. It extracts the Home shell destination into `ui/shell/ShellHomeDestination.kt`.
The 1.2 slice commit subject is `Extract Home shell destination`.
Next is 1.3, which extracts Search and Photo Grid shell routing into `ui/shell/ShellSearchDestination.kt`.
Do not begin Phase 2 or split ShellContent.

## Ownership and caution

The orchestrator owns implementation, review, records, validation, and Git. The targeted_fixer's stopped scope is reassigned to the orchestrator.
The user prohibits problem_solver. No agents remain active. Do not push.
Preserve unrelated agent/style edits, the deleted Photo Grid test and PNGs, untracked captures, scripts, caches, and `tasks/4c.md`.
Recheck external Java/Gradle activity before builds. Use explicit reviewed commit paths.

## Last safe commit

Preceding safe commit: `150d0d2` — `Pass notification and DM contracts through shell`.
The slice commit subject is `Extract Home shell destination`.

## Limits

Physical-device rendering, API 29 instrumentation, TalkBack, signing, and live-server behavior remain unverified.
The local release assembly does not establish release signing or runtime acceptance.
