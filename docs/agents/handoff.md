# Handoff

**Status:** Both OpenCode setup tasks are complete. The subagent permission audit
is finished and the audit is green. The Beeline application work in
`docs/agents/tasks/beeline-0.4.0.md` is untouched and continues from last safe
source commit `464b2d1`.

Task-state files for the finished work:

- `docs/agents/tasks/orchestrator-setup-fix.md`
- `docs/agents/tasks/subagent-permission-audit.md`

The plan of record for the first task, marked `implemented`, is
`.opencode/plan/orchestrator-setup-fix.md`.

## Environment facts

- Windows with PowerShell. `.` is not on `PATH`.
- The bare command `gradlew.bat` does not resolve. Only `.\gradlew.bat` does.
- `adb` is not on `PATH`. Two copies exist and both work: the SDK copy under
  `AppData`, and a copy at `C:\Users\julie\Documents\platform-tools`.
- `python` is the real interpreter. `python3` is the WindowsApps alias.
- The emulator `emulator-5554` is connected.
- The human tests on a live device. No agent records live device evidence.
- Neither setup task changed Android code, so neither has device-visible
  behavior.

## What the two tasks changed

The orchestrator was inert and unsafe. Seven defects were fixed.

- No project configuration set `default_agent`, so a new session ran `build`
  with full write and shell rights.
- Two routed subagent files were untracked, so a fresh clone lacked them.
- `/checkpoint` and `/resume` declared `agent: build`, which bypassed the whole
  permission model.
- The broad `read` allow overrode the base environment-file protection.
- `question` was denied, so the orchestrator could never ask the user.
- `external_directory` collapsed from `ask` to `deny`.
- Every `.md` file in `.opencode/agents/` loads as an agent, so the agent README
  was registered as a phantom primary agent named `README`.

The subagent audit then fixed 25 permission problems across eight agents.

- The environment-file gap existed in all eight subagents, not only the
  orchestrator.
- Neither code reviewer could run any Gradle task, because their rules assumed a
  bare wrapper and no flags, which is not a real invocation on this platform.
- The read-only explorer allowed `echo` and `printf`, so it could write a file
  through a redirect while `edit` stayed denied.
- The implementation agent allowed `python *`, which permits arbitrary code
  execution and defeats the Git mutation denies.

## User decision

The orchestrator never works directly. It always delegates to a subagent. This
replaces the AGENTS.md rule that says an agent must not use subagents by default,
for the orchestrator only. It is why the orchestrator keeps `edit` -> `deny` and
gains no write allow rule. AGENTS.md records the decision.

## How to verify

Static, from the repository root:

```text
python tools/tests/permission_matrix.py .opencode/agents/orchestrator.md
python tools/tests/agent_audit.py
```

The first reproduces the V2 resolution order for the orchestrator and reports 42
of 42. The second checks every subagent against its declared purpose and reports
247 of 247. Both are models of the rule order, not live permission decisions.

The audit also has negative probes. A reviewer still resolves `clean`, `publish`,
and a signing task to `deny`, so the Gradle rules do not over-permit.

Live, through the OpenCode API:

```text
opencode api get /api/config --header "x-opencode-directory: <project path>"
opencode api get /api/agent  --header "x-opencode-directory: <project path>"
```

## What remains unverified

- The interactive permission smoke test. Only a real interactive session can
  prove it.
- The merged `default_agent` value. The API does not expose it. Start a new
  session and confirm it opens with the orchestrator.
- The Gradle gate. Neither task changed Kotlin, and the gate is already known red
  for reasons that predate both tasks.

## Known limitations left in place

- `targeted_fixer` may edit existing files but cannot create a new one, because
  its `edit` rule has no separate write allow. Creating a file needs a decision
  from the user.
- The environment-file protection is a permission ask, not a hard deny. A user
  who approves can still read an environment file.
- `adb` is allowed broadly for `adb_handler`, so a raw `adb` command can change
  device state. The prompt forbids it, but the rule does not.

## Worktree caution

The worktree holds unrelated user changes. Do not stage, revert, or commit them.
This includes a deleted Photo Grid test, a staged `classic_navigation` document,
deleted PNGs, and many untracked captures and helper scripts. All local commits
are unpushed.
