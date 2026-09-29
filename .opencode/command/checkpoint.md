---
description: Run the slice checkpoint order and update the task-state file.
agent: orchestrator
---

Run the slice checkpoint in this order for the current slice.

You are the orchestrator. You never change a file and you never run a Git
command that changes repository state. Your permission rules block both. Every
step below names the subagent that does the work.

1. Ask `code_reviewer_low` to review the slice. Do not continue until it reports
   no BLOCKING and no REQUIRED finding.
2. Ask `code_reviewer_low` to run the smallest relevant test set. It is the only
   routed agent that the current configuration allows to run Gradle.
3. Inspect `git status` and `git diff` yourself. These two commands are allowed.
4. Ask `targeted_fixer` to rewrite the active task-state file in
   `docs/agents/tasks/`. Update `Completed`, `Current slice`, `Verification`,
   `Next`, `Blockers`, and `Last safe commit`. Do not append. It edits the
   existing file, because the orchestrator cannot write.
5. Ask `git_handler` to stage only the files that belong to the slice and to
   commit them with a clear imperative message. It runs the staging and the
   commit, because the orchestrator cannot.
6. Report the commit hash and the next slice.

Never run `git add`, `git commit`, `git push`, or any write command yourself.

If the work does not pass verification, do not commit. Ask `targeted_fixer` to
record the blocker in the task-state file, and record it in `logs/BUGS.txt`.

Do not push. Never push unless the user asks for it.
