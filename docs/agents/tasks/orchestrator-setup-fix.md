# Objective

Make the Beeline OpenCode orchestrator agent work as designed. It must be the
default agent, it must keep its permission model, and its workflow commands must
run under it.

Status: historical. All six slices were committed against the earlier agent configuration.
Current policy: [agent control](../agent-control.md) and [agent profiles](../../../.opencode/README.md).
Do not use the role names or permission assumptions below as current operational guidance.

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
- The permission behavior is proved statically with a rule simulator, because a
  non-interactive client cannot run the planned interactive smoke test.
- The user chose to commit the whole of `orchestrator.md`. The file already held
  uncommitted user changes in the same feature area.
- The two previously untracked subagent files are added to Git with no content
  change. The user asked for a separate task to review their content.
- The checkpoint originally routed Gradle to `code_reviewer_low` under an incorrect assumption that `targeted_fixer` denied Gradle.
  The later permission audit retracted that assumption. The implementation owner could run the permitted wrapper.

# Completed

- Slice 0 — task log, plan file moved into the repository, task-state file.
- Slice 1 — orchestrator permission hardening, commit `d3d88d8`.
- Slice 2 — track the two untracked subagent definitions, commit `7526732`.
- Slice 3 — bind `/checkpoint` and `/resume` to the orchestrator, commit
  `5174d2f`.
- Slice 4 — reconcile AGENTS.md with the delegation decision, commit `12fc632`.
- Slice 5 — correct the orchestrator prompt and the agent README, commit
  `54f0e13`.
- Slice 6 — set the default agent, remove the phantom README agent.

# Current slice

None. The task is complete.

# Files involved

None.

# Verification

Static, from `tools/tests/permission_matrix.py`:

- 42 of 42 cases match the intended effect for the final `orchestrator.md`.
- The same tool reports 35 of 42 against the pre-fix file, and fails on the
  three permission defects. The test detects the defects.
- The YAML frontmatter parses. It holds 44 merged rules and no malformed rule.

Live service, through the OpenCode API:

- `/api/config` lists the project document
  `.opencode\opencode.json`, so the new config file is discovered and loaded.
- `/api/agent` returns the orchestrator with `mode=primary` and the model
  `opencode-go/mimo-v2.6-flash`, and its system prompt is the corrected text.
- `/api/agent` returns all nine custom subagents with `mode=subagent`.
- `/api/agent` reported a phantom agent named `README` with `mode=primary`
  before the move, and does not report it after. The count went from 17 to 16.

Not verified:

- The interactive permission smoke test. Only a real interactive session can
  prove it. The merged `default_agent` value is not exposed by the API, so a new
  session is still needed to confirm which agent it starts with.
- The Gradle gate. The task changes no Kotlin source, and the gate is already
  known red for reasons that predate this task.

# Next

Start a new session in the project. Confirm that it opens with the orchestrator.
Then run the interactive permission smoke test in the plan.

# Blockers

- The prompt bodies of `adb_handler.md` and `codebase_explorer_android.md` are
  unverified. A separate task must review them.
- `targeted_fixer` cannot run Gradle and cannot create a new file. That looks
  like a defect in the subagent permission sets.

Both are recorded in `logs/BUGS.txt`.

# Last safe commit

Slice 5: `54f0e13` Correct the orchestrator routing rules and the agent list.
Slice 4: `12fc632`. Slice 3: `5174d2f`. Slice 2: `7526732`. Slice 1: `d3d88d8`.
The app workstream stays at `464b2d1`.


## Correction

This task recorded that `targeted_fixer` denies Gradle and routed the test run
to `code_reviewer_low` for that reason. That was wrong. `targeted_fixer` allows
the backslash wrapper and the Unix wrapper, so it can run Gradle. The earlier
probe used the bare command `gradlew.bat`, which does not resolve in
PowerShell, and that produced a false denial. See
`docs/agents/tasks/subagent-permission-audit.md`.
