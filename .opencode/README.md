# Beeline OpenCode V2 Agents

Status: current
Owner: Maintainers
Last reviewed: 2026-09-30
Stale when: Agent definitions, routing, or permissions change.

Default agent: `orchestrator`. Project configuration sets it in
`.opencode/opencode.json`. Without that file a new session falls back to
`build`, which has full write and shell rights.

This file sits in `.opencode/` on purpose. Every `.md` file in `.opencode/agents/`
is loaded as an agent definition, so agent documentation must stay outside that
directory.

Agents:
- `orchestrator`: delegates and controls scope; never works directly.
- `problem_solver`: read-only exploration, root-cause analysis, and bounded planning.
- `targeted_fixer`: one implementation owner for edits, validation, documentation, and review repairs.
- `code_reviewer`: independent review at the depth the task requires; never implements repairs.
- `git_handler`: Git-only staging, commit, and push workflow.
- `adb_handler`: device checks through ADB and the named companion scripts.

There are six custom profiles: one primary agent and five subagents.
The previous low/high planner and reviewer pairs are consolidated.
The investigator also owns the former Android explorer responsibility.
Change review depth instead of handing the task to another model-tier role.

The definitions use OpenCode V2 `permissions`/`shell`/`subagent` syntax.
Rules use last-match resolution. Environment-file reads ask for approval, except example files.
The investigator, reviewer, and implementation owner can inspect Git. ADB cannot run Git.
Git mutations stay with `git_handler`. Explicit-path staging and normal commits are allowed; push and broad staging ask for approval.
Prompt rules still require explicit user authorization for push.

The orchestrator delegates every change. It keeps `edit` set to `deny` and has no write allow rule. Route file changes to `targeted_fixer` and Git mutations to `git_handler`.

Keep the same implementation session through review repairs and record updates.
Read [agent control](../docs/agents/agent-control.md), [workflow](../docs/agents/workflow.md), and [AGENTS.md](../AGENTS.md).
These pages own shared policy. Agent prompts do not copy the full project rules.

## Verification limits

`tools/tests/agent_audit.py` and `permission_matrix.py` still contain the retired role names.
Their unchanged default runs do not fully check consolidated routing or roles.
Reuse their permission resolver and old role profiles against the new files without changing test code in this task.
Check all six frontmatter blocks, current dispatch targets, edit boundaries, environment reads, and denied operations.
See [the task record](../docs/agents/tasks/agent-control-cleanup.md) for results.

Static checks model permission resolution. They do not prove live interactive enforcement.
Current V2 documentation says `edit` covers edits, writes, and patches. There is no documented separate write permission.
The prior handoff claim that `targeted_fixer` cannot create files is therefore not current guidance.
Confirm live tool behavior in a new session before relying on it.

The script permissions assume the named scripts exist under `tools/scripts/`.
Some ADB scripts are local untracked work. A fresh clone does not contain those scripts.
