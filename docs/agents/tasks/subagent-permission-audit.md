# Objective

Make sure every Beeline subagent has the permissions its declared purpose
requires. A read-only agent must be read-only. An agent that must edit files,
use the shell, use adb, or use Git must be able to do exactly that.

Status: historical. All five slices were committed against the earlier eight-subagent configuration.
Current policy: [agent control](../agent-control.md) and [agent profiles](../../../.opencode/README.md).
The counts below are historical evidence, not verification of the consolidated agents.

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
  `targeted_fixer` allows the backslash wrapper and the Unix wrapper.
- Edit scripts must locate the rule indent from the `- action:` header line, not
  from the nested `resource:` or `effect:` line.
- A read-only agent must not allow `echo` or `printf`, because a redirect is one
  command string that matches a broad allow.

# Completed

- Slice 1 — build `tools/tests/agent_audit.py`, commit `a018db9`.
- Slice 2 — restore the environment-file protection in all eight subagents,
  commit `e7c465c`.
- Slice 3 — give the two reviewers working Gradle rules, commit `ab9f81c`.
- Slice 4 — close the read-only write hole and the implementation script gap,
  commit `6a7fe6d`.
- Slice 5 — repair the adb prompt and retract the false `targeted_fixer` claim.

# Current slice

None. The task is complete.

# Files involved

None.

# Verification

- `tools/tests/agent_audit.py` reports 247 of 247 expectations met across eight
  agents, up from 216 of 241 before any fix.
- `tools/tests/permission_matrix.py` still reports 42 of 42 for the orchestrator.
- All nine agent files parse as valid YAML.
- The audit holds negative probes as well as positive ones. A reviewer resolves
  `clean`, `publish`, and a signing task to `deny`. A read-only agent resolves
  `echo` with a redirect, `tee`, `cp`, `mv`, `rm`, `mkdir`, `touch`, and
  `sed -i` to `deny`. `adb_handler` resolves every Git command to `deny`.

# Next

Start a new session and confirm the interactive permission behavior. Then open a
separate task for the two remaining known limitations recorded in the handoff.

# Blockers

None. Three limitations remain and are recorded in the handoff rather than here:
the fixer cannot create a new file, the environment-file rule asks rather than
denies, and `adb` is allowed broadly for the device agent.

# Last safe commit

Slice 4: `6a7fe6d` Close a write hole in the read-only explorer and narrow the
fixer Python rule. Slice 3: `ab9f81c`. Slice 2: `e7c465c`. Slice 1: `a018db9`.
The app workstream stays at `464b2d1`.
