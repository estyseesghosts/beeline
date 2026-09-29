# Objective

Make sure every Beeline subagent has the permissions its declared purpose
requires. A read-only agent must be read-only. An agent that must edit files,
use the shell, use adb, or use Git must be able to do exactly that.

# Invariants

- No agent may read an environment file without approval.
- No agent may change repository state except through the Git agent.
- Read-only agents must resolve every filesystem write to `deny`.
- Unrelated worktree changes stay untouched.
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
  corrected here.

# Completed

- Slice 1 — build `tools/tests/agent_audit.py`.

# Current slice

Slice 2 — restore the environment-file protection in all eight subagents.

# Files involved

- `.opencode/agents/problem_solver_low.md`
- `.opencode/agents/problem_solver_high.md`
- `.opencode/agents/targeted_fixer.md`
- `.opencode/agents/code_reviewer_low.md`
- `.opencode/agents/code_reviewer_high.md`
- `.opencode/agents/git_handler.md`
- `.opencode/agents/codebase_explorer_android.md`
- `.opencode/agents/adb_handler.md`

# Verification

`tools/tests/agent_audit.py` reports 216 of 241 expectations met across eight
agents before any fix. The 25 failures are:

- 16 environment-file reads resolve to `allow` in all eight agents. The broad
  `read` allow erases the base policy protection.
- 6 Gradle failures in the two reviewers. None of the three probed wrapper forms
  matches a rule, so neither reviewer can run a Gradle task.
- 2 write failures in `codebase_explorer_android`. `echo` and `printf` resolve
  to `allow`, so a read-only agent can write a file through redirection.
- 1 analysis-script failure in `targeted_fixer`. It allows `python *` but not
  `python3`.

# Next

Add the three environment-file rules to each of the eight subagents, directly
after its broad `read` allow, then rerun the audit.

# Blockers

- None.

# Last safe commit

Slice 6 of the previous task: `7f4ea63` Make the orchestrator the default agent
and remove a phantom agent. The app workstream stays at `464b2d1`.
