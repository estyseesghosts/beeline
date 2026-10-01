---
description: Validate and review the current slice, update records, and commit only its scoped files.
agent: orchestrator
---

Run the checkpoint under `docs/agents/workflow.md` and `docs/agents/operation-rules.md`.
You may implement, validate, update records, and operate Git directly.
Keep the assigned implementation owner responsible for repairs. Do not edit a live fixer-owned scope.

1. Run or obtain the smallest relevant checks and required gates from the implementation owner.
   Use document and configuration checks for documentation-only work, not Android builds.
2. Ask `problem_solver` in review mode to inspect the actual diff, task contract, and verification evidence.
   Resolve required findings with the same owner. Repeat checks after repairs.
3. Inspect Git status and the actual staged and unstaged diffs yourself.
4. Update task state, task log, handoff, and affected documentation directly or through the explicitly assigned owner.
   Inspect the final diff after record updates. Do not omit those updates from review.
5. Name one Git operator: you, or the fixer under explicit commit instructions.
   Stage reviewed paths only. Preserve unrelated staged content; use a scoped path-only commit when safe.
6. Commit the reviewed slice and records together. Report its hash and next action.

If a gate fails, do not commit. Record the blocker and return the slice to its implementation owner.
If a fail gate fires or the scope expands, stop edits and investigate before continuing.
Do not push unless the user explicitly requests it.
