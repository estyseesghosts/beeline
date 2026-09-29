# Objective

Make sure every Beeline subagent has the permissions its declared purpose
requires. A read-only agent must be read-only. An agent that must edit files,
use the shell, use adb, or use Git must be able to do exactly that.

# Invariants

- No agent may read an environment file without approval.
- No agent may change repository state except through the Git agent.
- Read-only agents must resolve every filesystem write to `deny`.
- Reviewers may run verification tasks and no other Gradle task.
- Unrelated worktree changes stay untouched. The eight subagent files already
  held uncommitted user edits and must keep them.
- No Kotlin source changes in this task.
- The user tests on a live device. This task has no device-visible behavior.

# Decisions

- The permission matrix models the V2 order, so it is a model of the rule order
  and not a live permission decision.
- Gradle rules must match the invocation AGENTS.md mandates: the wrapper, then
  `--no-daemon --console=plain`, then the task. On Windows only the backslash
  wrapper resolves.
- `adb_handler` must keep Git denied. Its prompt states that it never runs Git.
- The previous task's claim that `targeted_fixer` denies Gradle was wrong.
  `targeted_fixer` allows `.\gradlew.bat *` and `./gradlew *`.
- Edit scripts must locate the rule indent from the `- action:` header line, not
  from the nested `resource:` or `effect:` line.

# Completed

- Slice 1 — build `tools/tests/agent_audit.py`, commit `a018db9`.
- Slice 2 — restore the environment-file protection in all eight subagents,
  commit `e7c465c`.
- Slice 3 — give the two reviewers working Gradle rules.

# Current slice

Slice 4 — close the read-only write hole and the implementation script gap.

# Files involved

- `.opencode/agents/codebase_explorer_android.md`
- `.opencode/agents/targeted_fixer.md`

# Verification

- The audit improved from 232 of 241 to 244 of 247, and the total rose by 6
  because negative probes were added.
- The audit now also asserts that a reviewer cannot run `clean`, `publish`, or a
  signing task. All three resolve to `deny`, so the new rules did not
  over-permit.
- Both reviewer files parse as valid YAML. Each grew from 73 to 94 rules.
- All eight agent files parse. Rule counts rose by exactly three in slice 2, and
  the diff against `HEAD` was inspected line by line to confirm the only added
  lines are the environment block.

The 3 remaining failures are:

- 2 in `codebase_explorer_android`. `echo` and `printf` resolve to `allow`, so a
  read-only agent can write a file through redirection.
- 1 in `targeted_fixer`. It allows `python *` but not `python3`.

# Next

Remove the `echo` and `printf` allows from the read-only explorer, and replace
the broad `python *` in the implementation agent with the documented
`tools/scripts` entry points in both interpreter spellings.

# Blockers

- None.

# Last safe commit

Slice 2: `e7c465c` Restore the environment-file protection in all eight
subagents. Slice 1: `a018db9`. Slice 6 of the previous task: `7f4ea63`. The app
workstream stays at `464b2d1`.
