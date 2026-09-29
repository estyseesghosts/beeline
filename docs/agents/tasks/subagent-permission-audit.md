# Objective

Make sure every Beeline subagent has the permissions its declared purpose
requires. A read-only agent must be read-only. An agent that must edit files,
use the shell, use adb, or use Git must be able to do exactly that.

# Invariants

- No agent may read an environment file without approval.
- No agent may change repository state except through the Git agent.
- Read-only agents must resolve every filesystem write to `deny`.
- Unrelated worktree changes stay untouched. The eight subagent files already
  held uncommitted user edits and must keep them.
- No Kotlin source changes in this task.
- The user tests on a live device. This task has no device-visible behavior.

# Decisions

- The permission matrix models the V2 order, so it is a model of the rule order
  and not a live permission decision.
- Gradle rules must match the invocation AGENTS.md mandates, which is the
  wrapper followed by `--no-daemon --console=plain` and then the task. Rules
  written as `gradlew.bat test *` match neither the Windows path nor the
  mandated flag order.
- `adb_handler` must keep Git denied. Its prompt states that it never runs Git.
- The previous task's claim that `targeted_fixer` denies Gradle was wrong and is
  corrected here. `targeted_fixer` allows `.\gradlew.bat *` and `./gradlew *`.
- Edit scripts must locate the rule indent from the `- action:` header line, not
  from the nested `resource:` or `effect:` line. Two earlier passes got this
  wrong and damaged the frontmatter; the final pass normalizes the region.

# Completed

- Slice 1 — build `tools/tests/agent_audit.py`, commit `a018db9`.
- Slice 2 — restore the environment-file protection in all eight subagents.

# Current slice

Slice 3 — let the two reviewers run Gradle on this platform.

# Files involved

- `.opencode/agents/code_reviewer_low.md`
- `.opencode/agents/code_reviewer_high.md`

# Verification

- The audit improved from 216 of 241 to 232 of 241.
- All eight agent files parse as valid YAML.
- Rule counts are exactly three higher than before slice 2 for the two files
  whose pre-slice counts were recorded: `adb_handler` 20 to 23, and
  `codebase_explorer_android` 57 to 60. The audit arithmetic confirms the rest,
  because 232 met plus the 9 known remaining failures equals the 241 total.
- The diff against `HEAD` was inspected. The only added lines are the 12-line
  environment block. The removed lines in `targeted_fixer.md` are the user's own
  earlier edits, which replace the narrow script and Gradle lists with broader
  ones. Nothing was lost by the scripts.

The 9 remaining failures are:

- 6 in the two reviewers. No probed wrapper form matches, so neither reviewer can
  run a Gradle task. The bare `gradlew.bat` form does not resolve in PowerShell
  and the rule ordering assumes the task follows the wrapper, while AGENTS.md
  puts two flags first.
- 2 in `codebase_explorer_android`. `echo` and `printf` resolve to `allow`, so a
  read-only agent can write a file through redirection.
- 1 in `targeted_fixer`. It allows `python *` but not `python3`.

# Next

Add Windows wrapper rules to both reviewers in the AGENTS.md flag order, for the
same task set they already allow.

# Blockers

- None.

# Last safe commit

Slice 1: `a018db9` Add a purpose audit for every subagent permission set.
Slice 6 of the previous task: `7f4ea63`. The app workstream stays at `464b2d1`.
