# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-06

## Continuation pointer

Read [tasks/hardening-0.4.0-phase2.md](tasks/hardening-0.4.0-phase2.md) for the active Phase 2 task, constraints, and verification.
Read AGENTS.md and its required pages before continuing.
The completed Phase 0 and 1 record remains at [tasks/hardening-0.4.0.md](tasks/hardening-0.4.0.md).
The preceding adaptive-navigation record remains at [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).

## Current position

The user requests Phase 2 of `docs/fix_0.4.0.md`: presentation-only decomposition of
high-value Compose functions. State does not move. Keep every existing owner, contract,
invariant, and test. This phase does not touch protocol, persistence, account scope, or
navigation policy.
Phase 0 and 1 are committed and pushed. `main` is at `f28430a`.
Phase 2 slice 2.1 is committed at `6920602`: `HomeFeed` delegates paging and scroll effects
and feed rendering to private helpers without moving state.
Phase 2 slice 2.2 is committed at `b342a3f`: `ProfileScreen` selects the presentation and
delegates compact and compact-wide to the new `ui/profile/ProfileTimelinePresentation.kt`.
Phase 2 slice 2.3 is implemented and gate-verified: `DestinationChipRow` keeps the chip row
assembly and delegates one chip to `DestinationFilterChip` and the caret to
`DestinationChipVisibilityButton`.
Phase 2 slice 2.4 is next: share the notification filter-row presentation in
`ui/notifications/NotificationsScreen.kt`.
Do not split ShellContent. Do not push. Do not begin Phase 3.

## Ownership and caution

The orchestrator owns implementation, review, records, validation, and Git.
The user allows targeted_fixer for narrowly scoped work packages; keep one implementation
owner per slice.
The user prohibits problem_solver. Do not push.
Preserve unrelated agent/style edits, the deleted Photo Grid test and PNGs, untracked captures, scripts, caches, and `tasks/4c.md`.
Recheck external Java/Gradle activity before builds. Use explicit reviewed commit paths.

## Last safe commit

Preceding safe commit: `b342a3f` — `Extract Profile timeline presentation`.
The 2.3 slice commit subject is `Decompose destination chip rendering`.
The next slice commit subject is `Share notification filter-row presentation`.

## Limits

Physical-device rendering, API 29 instrumentation, TalkBack, signing, and live-server behavior remain unverified.
The local release assembly does not establish release signing or runtime acceptance.
