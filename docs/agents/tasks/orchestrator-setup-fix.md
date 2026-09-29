# Objective

Make the Beeline OpenCode orchestrator agent work as designed. It must be the
default agent, it must keep its permission model, and its workflow commands must
run under it.

# Invariants

- The orchestrator never edits, writes, patches, stages, or commits by itself.
- The orchestrator keeps `edit` -> `deny` and gains no write allow rule.
- Unrelated worktree changes stay untouched.
- No Kotlin source changes in this task.
- The user tests live device behavior. This task has no device-visible behavior,
  so it records no device evidence.

# Decisions

- The user decided that the orchestrator always delegates and never works
  directly. This resolves the conflict with the AGENTS.md rule that says an
  agent must not use subagents by default. The replacement applies to the
  orchestrator only. Other agents keep the no-delegation rule.
- Permission hardening runs before the default-agent change. A leaky agent must
  not become the default first.
- The permission behavior is proved statically with a rule simulator. It still
  needs a real interactive session for final confirmation.
- The user chose to commit the whole of `orchestrator.md`. The file already held
  uncommitted user changes in the same feature area.
- The two previously untracked subagent files are added to Git with no content
  change. The user asked for a separate task to review their content.
- The checkpoint command routes the Gradle run to `code_reviewer_low`, because
  `targeted_fixer` resolves a Gradle shell command to `deny`. This is a routing
  consequence, not an endorsement of the subagent permission sets.

# Completed

- Slice 0 — task log, plan file moved into the repository, task-state file.
- Slice 1 — orchestrator permission hardening, commit `d3d88d8`.
- Slice 2 — track the two untracked subagent definitions, commit `7526732`.
- Slice 3 — bind `/checkpoint` and `/resume` to the orchestrator.

# Current slice

Slice 4 — reconcile AGENTS.md with the delegation decision.

# Files involved

- `AGENTS.md`

# Verification

- `tools/tests/permission_matrix.py` reports 42 of 42 cases matching the
  intended effect for the current `orchestrator.md`.
- The same tool reports 35 of 42 against the `HEAD` version, and fails on the
  three defects this task fixes. The test has teeth.
- A structural frontmatter check confirms both newly tracked subagent files
  declare `mode: subagent`, set a description, and carry no malformed permission
  rule. Both model references resolve. The prompt bodies remain unverified.
- A permission probe across all nine agent files confirms the routing used by
  the rewritten checkpoint command. `code_reviewer_low` allows Gradle.
  `targeted_fixer` denies Gradle and allows `edit`. `git_handler` allows
  `git commit` and asks for `git add` and `git push`.
- Both command files parse as valid YAML frontmatter and declare
  `agent: orchestrator`.
- The user's staged `docs/classic_navigation.md` survived every commit and is
  still staged, not committed.

# Next

Edit the four `AGENTS.md` lines under Working Process that forbid subagents.
Scope the replacement to the orchestrator and record that the user made the
decision.

# Blockers

- The permission behavior needs a real interactive session. A non-interactive
  client cannot prove it. Recorded in `logs/BUGS.txt` with the subagent content
  gap and the `targeted_fixer` Gradle finding.

# Last safe commit

Slice 2: `7526732` Track the adb and codebase explorer subagent definitions.
Slice 1: `d3d88d8`. The app workstream stays at `464b2d1`.
