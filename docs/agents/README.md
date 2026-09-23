# Beeline Agent Wiki

Status: current  
Owner: Maintainers  
Last reviewed: 2026-09-15  
Stale when: The state-recovery process changes.

This directory holds agent-facing engineering documentation.

## Layers

- `AGENTS.md` holds permanent project law.
- `docs/agents/tasks/<task>.md` holds the current truth of one long task.
- Git commits hold verified history.

## Rule

- Treat repository state as authoritative.
- Treat conversation context as disposable.

## Task state

- Keep one task-state file for each long task.
- Rewrite the file at each slice boundary.
- Do not append to the file.
- Start each file from [tasks/_template.md](tasks/_template.md).

## State recovery

Read these items at the start of a session, after compaction, and when you are unsure:

1. `AGENTS.md`.
2. The active task-state file.
3. `git status`.
4. Recent relevant commits.
5. The current diff.

Rebuild the TODO list from these items.

## Tooling

- `.opencode/plugins/compaction-state.ts` adds the task-state files to the compaction prompt.
- `/checkpoint` runs the slice checkpoint order.
- `/resume` runs the state recovery order.

## Current tasks

The active task is [Beeline 0.4.0](tasks/beeline-0.4.0.md). Keep the
[task template](tasks/_template.md) for new task states.

## Historical tasks

- [Docs archive](../archive/agents/docs-archive.md)
- [Plan 04 utility ownership and retention](../archive/agents/plan04-utility-retention.md) (historical; 04-A through 04-K have committed work; archive records earlier ditch decision)
- [Plan 03 protocol and notification persistence](../archive/agents/plan03-protocol-notifications.md)
- [Plan 03 gate partials](../archive/agents/plan03-gate-partials.md)
- [Decomposition 01 and 02 completion](../archive/agents/decomposition-01-02-completion.md)
- [S1 shell split](../archive/agents/palustrisapp-decomposition.md)
- [P1 package migration](../archive/agents/ui-package-migration.md)
- [Q1 static analysis](../archive/agents/q1-static-analysis.md)
- [T1 test mirror](../archive/agents/t1-test-mirror.md)
- [Localization string extraction](../archive/agents/localization-string-extraction.md) (slices 1 through 4; resume needs a new task)
- [Gradle no-daemon](../archive/agents/gradle-no-daemon.md)
- [Profile Liked tab](../archive/agents/profile-liked-tab.md)
- [Profile Featured tab](../archive/agents/profile-featured-tab.md)
- [Wide detail interaction fix](../archive/agents/wide-detail-interaction-fix.md)
- [Wide detail photo sizing](../archive/agents/wide-detail-photo-sizing.md)
- [SVG icon replacement](../archive/agents/svg-icon-replacement.md)
- [Archive index](../archive/README.md). It holds the app-shell decomposition, state and
  lifecycle repair, cancellation and shell continuation, and reference localization task
  states.

## Pages

- [Acceptance matrix](decomposition-01-02-acceptance-matrix.md)
- [App shell ownership](app-shell-ownership.md)
- [Protocol and session ownership](protocol-and-session-ownership.md)
- [Retention inventory](retention-inventory.md)
