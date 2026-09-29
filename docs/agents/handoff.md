# Handoff

**Status:** The OpenCode orchestrator setup task is complete. All six slices are
committed. The Beeline application work in `docs/agents/tasks/beeline-0.4.0.md` is
untouched and continues from last safe source commit `464b2d1`.

The task-state file for the finished work is
`docs/agents/tasks/orchestrator-setup-fix.md`. The plan of record, now marked
`implemented`, is `.opencode/plan/orchestrator-setup-fix.md`.

## Environment facts

- The emulator `emulator-5554` is connected. Assume it is available for testing.
- `adb` is not on `PATH`. Use
  `C:\Users\julie\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- The human tests on a live device. No agent records live device evidence.
- The orchestrator task changed no Android code. It has no device-visible
  behavior.

## What was wrong

The orchestrator agent was inert and unsafe. Six defects were confirmed by
reading the OpenCode V2 permission guide and reproducing its rule order.

- No project configuration set `default_agent`, so a new session ran `build`
  with full write and shell rights.
- Two routed subagent files were untracked, so a fresh clone lacked them.
- `/checkpoint` and `/resume` declared `agent: build`, which bypassed the whole
  permission model.
- The broad `read` allow overrode the base `.env` protection, because agent rules
  load after the base policy and the last matching rule wins.
- `question` was denied, so the orchestrator could never ask the user.
- `external_directory` collapsed from `ask` to `deny`.

A seventh defect appeared only under live service verification. Every `.md` file
in `.opencode/agents/` loads as an agent definition, so the agent README was
registered as a phantom primary agent named `README`. It moved to
`.opencode/README.md`.

## User decision

The orchestrator never works directly. It always delegates to a subagent. This
replaces the AGENTS.md rule that says an agent must not use subagents by default,
for the orchestrator only. It is why the orchestrator keeps `edit` -> `deny` and
gains no write allow rule. AGENTS.md records the decision.

## How to verify

Static, from `tools/tests/permission_matrix.py`:

```text
python tools/tests/permission_matrix.py .opencode/agents/orchestrator.md
```

It reproduces the V2 resolution order, base policy then agent rules, and reports
the last matching effect. It reports 42 of 42 for the current file. Run it
against a pre-fix copy to see it fail, which is what proves it detects defects
rather than rubber-stamping them.

Live, through the OpenCode API:

```text
opencode api get /api/config --header "x-opencode-directory: <project path>"
opencode api get /api/agent  --header "x-opencode-directory: <project path>"
```

The first lists the loaded config documents. The second lists agents with their
mode.

## What remains unverified

- The interactive permission smoke test. Only a real interactive session can
  prove it. The static matrix is a model of the rule order, not a live
  permission decision.
- The merged `default_agent` value. The API does not expose it. Start a new
  session and confirm it opens with the orchestrator.
- The Gradle gate. The task changes no Kotlin source, and the gate is already
  known red for reasons that predate this task.

## Known blockers for the next task

- The prompt bodies of `adb_handler.md` and `codebase_explorer_android.md` are
  unverified. A separate task must review them.
- `targeted_fixer` cannot run Gradle and cannot create a new file, so the
  implementation agent cannot verify its own work and cannot add a new file.
  This looks like a defect in the subagent permission sets.

Both are recorded in `logs/BUGS.txt`.

## Worktree caution

The worktree holds unrelated user changes. Do not stage, revert, or commit them.
This includes modified subagent files, a deleted Photo Grid test, a staged
`classic_navigation` document, deleted PNGs, and many untracked captures and
helper scripts. All 83 local commits are unpushed.
