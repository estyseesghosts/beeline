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
- A read-only agent must not allow `echo` or `printf`, because a redirect is one
  command string that matches a broad allow.

# Completed

- Slice 1 — build `tools/tests/agent_audit.py`, commit `a018db9`.
- Slice 2 — restore the environment-file protection in all eight subagents,
  commit `e7c465c`.
- Slice 3 — give the two reviewers working Gradle rules, commit `ab9f81c`.
- Slice 4 — close the read-only write hole and the implementation script gap.

# Current slice

Slice 5 — repair the adb agent prompt and correct the earlier false claim.

# Files involved

- `.opencode/agents/adb_handler.md`
- `.opencode/agents/orchestrator.md`
- `.opencode/command/checkpoint.md`
- `docs/agents/handoff.md`
- `logs/BUGS.txt`

# Verification

- The audit reports 247 of 247 expectations met across eight agents.
- `codebase_explorer_android` dropped from 60 to 58 rules, and a check confirms
  no rule resource still mentions `echo` or `printf`.
- `targeted_fixer` allows `python tools/scripts/*` and `python3 tools/scripts/*`
  and no longer allows `python *`. This matches its own prompt, which says only
  the existing `tools/scripts/*.py` entry points may run.
- The explorer prompt now states that output redirection is unavailable and that
  this is deliberate.
- The edited regions were inspected. The explorer block is tidy and the fixer
  comment reads correctly.

# Next

Fix the adb prompt, which names a `script.py` that does not exist, and remove
the false claim that `targeted_fixer` denies Gradle from the orchestrator prompt,
the checkpoint command, the handoff, the task state, and `logs/BUGS.txt`.

# Blockers

- None.

# Last safe commit

Slice 3: `ab9f81c` Give the two code reviewers Gradle rules that match a real
invocation. Slice 2: `e7c465c`. Slice 1: `a018db9`. The app workstream stays at
`464b2d1`.
