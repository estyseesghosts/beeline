# Handoff

**Status:** The OpenCode orchestrator setup task is in progress. Slice 1 is
complete. The Beeline application work in `docs/agents/tasks/beeline-0.4.0.md`
is untouched and continues from last safe source commit `464b2d1`.

The active task-state file is `docs/agents/tasks/orchestrator-setup-fix.md`. Read
that file for the current position. The plan of record is
`.opencode/plan/orchestrator-setup-fix.md`.

## Environment facts

- The emulator `emulator-5554` is connected. Assume it is available for testing.
- `adb` is not on `PATH`. Use
  `C:\Users\julie\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- The human tests on a live device. No agent records live device evidence.
- This task changes no Android code. It has no device-visible behavior.

## Why this task exists

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

## User decision

The orchestrator never works directly. It always delegates to a subagent. This
replaces the AGENTS.md rule that says an agent must not use subagents by default,
for the orchestrator only. It is why the orchestrator keeps `edit` -> `deny` and
gains no write allow rule.

## Verification approach

`tools/tests/permission_matrix.py` reproduces the V2 resolution order and reports
the final effect for 42 operations. It is the evidence for the permission
slices. It also has a counter-test: it fails on the pre-fix file, which proves it
detects the defects rather than rubber-stamping them.

The interactive permission smoke test is still outstanding. Only a real session
can confirm it.

## Next slice

Slice 3 changes the `agent` frontmatter in `/checkpoint` and `/resume` from
`build` to `orchestrator`, and rewrites the checkpoint body so each step names
the responsible subagent.

## Known blockers

- The permission behavior needs a real interactive session. A non-interactive
  client cannot prove it.
- The prompt bodies of `adb_handler.md` and `codebase_explorer_android.md` are
  unverified. A separate task must review them. Recorded in `logs/BUGS.txt`.
- The Gradle gate is known red for reasons that predate this task. The task
  changes no Kotlin source.

## Worktree caution

The worktree holds unrelated user changes. Do not stage, revert, or commit them.
This includes modified subagent files, a deleted Photo Grid test, a staged
`classic_navigation` document, deleted PNGs, and many untracked captures and
helper scripts.
