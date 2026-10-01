# Orchestrator setup fix plan

Status: historical (implemented against the earlier agent configuration)
Owner: Maintainers
Last reviewed: 2026-09-30
Verification: Historical source and service evidence below. Current routing and interactive permissions need their own verification.
Applies to: `.opencode/agents/orchestrator.md` and the OpenCode project configuration

Current guidance: [agent control](../../docs/agents/agent-control.md) and [agent profiles](../README.md).
The role names and permission assumptions below are not current operation rules.

## Slice commits

| Slice | Commit |
| --- | --- |
| 1 — permission hardening | `d3d88d8` |
| 2 — track the two subagent files | `7526732` |
| 3 — bind the commands to the orchestrator | `5174d2f` |
| 4 — reconcile AGENTS.md | `12fc632` |
| 5 — prompt and README corrections | `54f0e13` |
| 6 — default agent | recorded in the handoff |

## Deviations from this plan

- Slice 1 gained `tools/tests/permission_matrix.py`. The plan relied on an
  interactive smoke test, which a non-interactive client cannot run. The
  simulator reproduces the V2 rule order instead, and it fails on the pre-fix
  file, so it detects the defects rather than rubber-stamping them.
- Slice 3 routed the Gradle run to `code_reviewer_low` under an incorrect assumption that `targeted_fixer` denied Gradle.
  The later permission audit retracted that claim. The implementation owner could run the permitted wrapper.
- Slice 6 also moved `.opencode/agents/README.md` to `.opencode/README.md`. Live
  service verification showed that every `.md` file in `.opencode/agents/` loads
  as an agent, so the README was registered as a phantom primary agent named
  `README`. The plan did not anticipate this.

## Decision that this plan implements

The user decided that the orchestrator never works directly. It always delegates
to a subagent. It does not edit, write, patch, stage, or commit by itself.

This decision resolves one earlier finding. The orchestrator keeps
`edit` -> `deny`, and the per-slice documentation writes move into a subagent
instead of into the orchestrator.

## Scope

This plan fixes the orchestrator only. The contents of the subagent files are out
of scope. A later task must review them.

## Findings this plan addresses

| ID | Finding | Severity | Slice |
| --- | --- | --- | --- |
| F1 | The orchestrator is not the default agent, so a new session runs `build` | Serious | 6 |
| F2 | `adb_handler.md` and `codebase_explorer_android.md` are not in Git | Serious | 2 |
| F3 | `/checkpoint` and `/resume` run as `build`, which bypasses the permission model | Serious | 3 |
| F4 | `read` -> `allow` overrides the built-in `.env` protection | Security | 1 |
| F5 | `question` is denied, so the orchestrator cannot ask the user | Functional | 1 |
| F6 | The orchestrator cannot write, but AGENTS.md requires slice-boundary writes | Process | 3 |
| F7 | `external_directory` becomes `deny` instead of `ask` | Minor | 1 |
| F8 | The orchestrator prompt contradicts itself about pipeline agents | Minor | 5 |
| F9 | `.opencode/agents/README.md` is stale | Minor | 5 |
| F10 | AGENTS.md and orchestrator.md describe different working models | Policy | 4 |

## Rule order background

OpenCode V2 uses the last matching permission rule. Agent rules load after the
base policy and after global and project rules. A later rule therefore overrides
an earlier rule.

The base policy already contains these rules:

- `*` / `*` -> `allow`
- `external_directory` / `*` -> `ask`
- `read` / `*.env` -> `ask`
- `read` / `*.env.*` -> `ask`
- `read` / `*.env.example` -> `allow`

Every fix in this plan depends on that order.

## Slice 1: Harden the orchestrator permissions

Purpose: remove the security hole and the blocked question action before any
session uses the orchestrator.

Change `.opencode/agents/orchestrator.md`. Keep the broad `*` / `*` -> `deny`
rule first. Keep the existing `read`, `glob`, and `grep` allows.

Insert these rules after the `read` / `*` -> `allow` rule:

```yaml
  - action: read
    resource: "*.env"
    effect: ask
  - action: read
    resource: "*.env.*"
    effect: ask
  - action: read
    resource: "*.env.example"
    effect: allow
```

Insert these rules after the `grep` / `*` -> `allow` rule:

```yaml
  - action: external_directory
    resource: "*"
    effect: ask
  - action: question
    resource: "*"
    effect: allow
```

Keep `edit` / `*` -> `deny`. The user decided that the orchestrator never edits
directly. That rule now has a purpose.

Do not add a `write` allow rule.

Verification: run the permission smoke test in the Verification section.

Risk: a session that reads a `.env` file now asks. This is intended.

## Slice 2: Track the missing subagent definitions

Purpose: make the routed agents exist in a fresh clone.

Run:

```text
git add .opencode/agents/adb_handler.md .opencode/agents/codebase_explorer_android.md
git commit -m "Track the adb and codebase explorer subagent definitions"
```

Do not change the file contents in this slice.

Verification:

```text
git ls-files .opencode/agents
```

The output must list nine agent files.

Risk: the contents of both files are unverified. Record this in
`logs/BUGS.txt`. A later task must review both files and both must declare a
valid `mode` of `subagent`.

## Slice 3: Bind the workflow commands to the orchestrator

Purpose: stop the two workflow commands from switching to `build` and bypassing
the permission model.

In `.opencode/command/checkpoint.md`, change the frontmatter:

```yaml
agent: build
```

to:

```yaml
agent: orchestrator
```

Make the same change in `.opencode/command/resume.md`.

The checkpoint command text must also change. The orchestrator cannot run
`git add` or `git commit` and cannot write the task-state file. Rewrite the
command body so that each step names the responsible subagent:

1. Ask `code_reviewer_low` to confirm that the slice is complete and has no
   BLOCKING or REQUIRED finding.
2. Ask the agent that owns the test command to run the smallest relevant test
   set.
3. Ask the orchestrator to inspect `git status` and `git diff`. The orchestrator
   may run these two commands directly.
4. Ask `targeted_fixer` to rewrite the active task-state file. State that the
   orchestrator cannot write files.
5. Ask `git_handler` to stage only the files in the slice and to commit. State
   that `git_handler` runs the Git commands.
6. Ask the orchestrator to report the commit hash and the next slice.

Add one rule to the command body: the orchestrator must never run `git add`,
`git commit`, or any other write command itself.

Add one sentence to the `resume` command body: the orchestrator may read files
and run read-only Git commands, and it must not change files.

Verification: start a session with the orchestrator as the active agent, run
`/checkpoint`, and confirm that the session does not change files and does not
run a write command. Confirm that the session starts a `git_handler` subagent.

Risk: a user who expects `/checkpoint` to commit directly will now see a
delegation step. This is the intended consequence of the decision.

## Slice 4: Reconcile AGENTS.md with the delegation model

Purpose: remove the conflict between AGENTS.md and orchestrator.md.

AGENTS.md currently says:

```text
- Work directly on the requested task.
- Do not use subagents by default. Do not delegate work to another agent by
  default.
- Use a subagent only when the user explicitly requests one.
- Do not create a subagent because the task is large.
```

AGENTS.md also says that it is gospel and that a conflict means the agent must
stop and ask a human. The user has now resolved the conflict. Record the
decision in AGENTS.md.

Replace the four quoted lines with:

```text
- The orchestrator never works directly. It always delegates to a subagent.
- Any other agent works directly on the requested task.
- Any other agent must not use subagents unless the user explicitly requests
  one.
- Any other agent must not create a subagent because the task is large.
```

Also add one line under the Handoff section: the orchestrator asks
`targeted_fixer` to rewrite the handoff file, because the orchestrator cannot
write files.

Verification: read AGENTS.md and orchestrator.md together. Confirm that no rule
in AGENTS.md forbids the delegation model that the orchestrator implements.

Risk: this changes a rule that AGENTS.md marks as a non-negotiable constraint.
The commit message must record that the user made this decision.

## Slice 5: Correct the orchestrator prompt and the agent README

Purpose: remove the self-contradiction and the stale agent list.

In `.opencode/agents/orchestrator.md`:

- Change the opening role line so that it states that the orchestrator never
  edits files and always delegates.
- Change the `codebase_explorer_android` line. It now reads "map out the
  codebase first" for every task. Make it conditional. Use the explorer when the
  task is unfamiliar or spans more than one feature package. Do not use it for a
  small, local change.
- Keep the line that says not to invoke agents merely to satisfy a pipeline.

In `.opencode/agents/README.md`:

- List all nine agents. The file lists seven. Add `adb_handler` and
  `codebase_explorer_android`.
- Confirm that the statement about the Git handler matches the file.

Verification: count the files in `.opencode/agents` and confirm that the README
lists the same set.

Risk: none.

## Slice 6: Make the orchestrator the default agent

Purpose: stop new sessions from running `build` with full write and shell rights.

Do this slice last, after the agent is correct.

Add `.opencode/opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "default_agent": "orchestrator"
}
```

Do not add a top-level `permissions` array. Each agent already carries its own
rules, and a shared array risks granting one agent the rights of another.

Verification: start a new session in the project. Confirm that the active agent
is `orchestrator`. Confirm that the configuration file is valid.

Risk: a session that already exists keeps its stored agent. The user must start a
new session or switch the agent by hand. Record this in `logs/BUGS.txt` if the
user reports confusion.

## Target permission matrix for the orchestrator

| Operation | Effect | Source |
| --- | --- | --- |
| `read` any project file | allow | agent rule |
| `read` `.env` | ask | agent rule, added in slice 1 |
| `read` `.env.example` | allow | agent rule, added in slice 1 |
| `glob` | allow | agent rule |
| `grep` | allow | agent rule |
| `shell` `git status` | allow | agent rule |
| `shell` `git diff` | allow | agent rule |
| `shell` `git log` | allow | agent rule |
| `shell` other Git read | ask | agent rule |
| `shell` `git commit` | deny | agent rule |
| `shell` any non-Git command | deny | base deny rule |
| `edit`, `write`, `patch` | deny | agent rule |
| `subagent` for a listed agent | allow | agent rule |
| `subagent` for any other agent | deny | agent rule |
| `question` | allow | agent rule, added in slice 1 |
| any path outside the project | ask | agent rule, added in slice 1 |

## Verification

Run this smoke test after slice 1 and again after slice 6. Start a session with
the orchestrator as the active agent.

| Test | Expected result |
| --- | --- |
| Read a tracked source file | Succeeds without a prompt |
| Read a `.env` file | Asks for approval |
| Run `git status` | Succeeds without a prompt |
| Run `git log -1` | Succeeds without a prompt |
| Run `git branch` | Asks for approval |
| Run `git commit -m test` | Is blocked |
| Run a non-Git shell command | Is blocked |
| Attempt to edit a file | Is blocked |
| Ask the user a question | The question prompt appears |
| Start `targeted_fixer` | Succeeds without a prompt |
| Start `general` | Is blocked |

For slices 2 through 5, run the per-slice verification listed above.

Then run the repository gate from AGENTS.md before the task is complete:

```text
gradlew.bat --no-daemon --console=plain test assembleRelease
```

This gate confirms that the configuration change did not break the project. The
change touches no Kotlin source, so a full build is a formality.

## Risks and limits

- The content of the subagent files is unverified. This plan does not review it.
- The global configuration has both `opencode.json` and `opencode.jsonc`. This
  plan does not change the global configuration.
- The plugin `compaction-state.ts` is agent-neutral. This plan does not change it.
- No device test and no live server test cover this change. The permission
  results above are the only evidence.
- The permission smoke test needs a real session. It cannot run in a
  non-interactive client.

## Out of scope

- Reviewing or changing the content of any subagent file.
- The build agent, the plan agent, and the global configuration.
- The `tools/scripts` Python helpers.

## Completion check

- The orchestrator is the default agent for a new session in this project.
- A fresh clone contains all nine agent files.
- `/checkpoint` and `/resume` run as the orchestrator.
- The orchestrator cannot read a `.env` file without approval.
- The orchestrator can ask the user a question.
- The orchestrator cannot edit, write, stage, or commit.
- AGENTS.md and orchestrator.md no longer conflict.
- The agent README lists nine agents.
- Each slice has one commit, and the commit includes the related documentation.
