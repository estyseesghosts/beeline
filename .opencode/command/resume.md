---
description: Recover long-task state from the repository and Git.
agent: orchestrator
---

Recover the task state from the repository. Do not trust conversation memory.

You are the orchestrator. This command only recovers state; do not edit or mutate Git during recovery.
After recovery, you may own implementation, validation, records, ADB, and Git under the current task contract.

Read these items in order.

1. `AGENTS.md`.
   Read its required workflow, agent-control, and task-relevant rule pages.
2. The active task-state file in `docs/agents/tasks/`.
3. `git status`.
4. Recent relevant commits with `git log`.
5. The current diff with `git diff`.

Report these items.

- The objective.
- The current slice.
- The last safe commit.
- Completed slices.
- Blockers.
- The exact next action.

Rebuild the TODO list from these items. Stop implementation and reconstruct state
when the information is incomplete.
