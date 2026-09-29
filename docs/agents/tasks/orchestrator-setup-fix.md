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

# Completed

- Slice 0 — task log, plan file moved into the repository, task-state file.
- Slice 1 — orchestrator permission hardening.

# Current slice

Slice 2 — track the two untracked subagent definitions.

# Files involved

- `.opencode/agents/adb_handler.md`
- `.opencode/agents/codebase_explorer_android.md`

# Verification

- `tools/tests/permission_matrix.py` reproduces the V2 last-match-wins order
  with the base policy, then the agent rules. It reports 42 of 42 cases matching
  the intended effect for the current `orchestrator.md`.
- The same tool reports 35 of 42 against the `HEAD` version, and fails on the
  exact three defects this task fixes: `.env` reads resolved to `allow`, the
  `question` action resolved to `deny`, and `external_directory` resolved to
  `deny`. The test has teeth.
- The YAML frontmatter parses. It holds 39 rules and no malformed rule.
- `git show HEAD:.opencode/agents/orchestrator.md` also failed the `adb_handler`
  and `codebase_explorer_android` cases, because the allow rules for them were
  uncommitted at that point.

# Next

Run `git add` for the two untracked agent files. Change no content. Confirm that
`git ls-files .opencode/agents` lists nine files.

# Blockers

- The content of the two untracked subagent files is unverified. This task only
  adds them to Git. A later task must review both.
- The permission behavior needs a real interactive session. A non-interactive
  client cannot prove it.

# Last safe commit

Pending for slice 1. The last safe source commit before this task is `464b2d1`.
