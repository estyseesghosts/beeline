# Beeline OpenCode V2 Agents

Status: current
Owner: Maintainers
Last reviewed: 2026-10-01
Stale when: Agent definitions, routing, or permissions change.

## Three profiles

| Profile | Mode | Responsibility |
| --- | --- | --- |
| `orchestrator` | primary | Delivery, larger implementation slices, records, Gradle, ADB, and Git. |
| `problem_solver` | subagent | Read-only investigation, planning, and actual-diff review. |
| `targeted_fixer` | subagent | Small explicit work packages, bounded validation, and assigned Git checkpoints. |

Project configuration sets `default_agent` to orchestrator in [.opencode/opencode.json](opencode.json).
The primary profile has no model override. Preserve the selected session model.
Changing the default or profile definitions does not replace the agent already stored on an existing session.
Only agent definitions belong in agents/. Every Markdown file there loads as a profile.

The separate code reviewer, ADB handler, and Git handler are removed.
Use problem_solver in investigation mode or review mode, not a chain of planning and reviewer profiles.
The orchestrator may implement directly. Use the fixer only when its small work package has a complete contract.

## Ownership and gates

Read [AGENTS.md](../AGENTS.md), [agent control](../docs/agents/agent-control.md),
[workflow](../docs/agents/workflow.md), and [operation rules](../docs/agents/operation-rules.md).
Those pages own shared policy. Do not copy full project rules into each agent prompt.

Keep one slice implementation owner and one Git operator at a time.
Do not edit a delegated scope or mutate the index while its assigned child session owns that operation.
Keep the same fixer through bounded review repairs. Stop and clarify its contract when a fail gate fires.
Use the [fixer dispatch template](../docs/agents/agent-control.md#fixer-dispatch-template).
Require explicit instructions, deliverables, exit gates, non-goals, fail gates, file scope, and commit instructions.
Tool permission does not authorize unassigned commits or pushes.

## Permission boundaries

The orchestrator can edit and run general shell commands, including PowerShell, interpreters, and the Gradle wrapper with any argument order.
The fixer retains its named-script and flagged-wrapper allowlist. Both operators can stage explicit paths and commit.
Only the orchestrator has direct ADB access. Both operators require approval for unknown Git actions, broad staging, amend, or push.
Force-push and destructive Git operations remain denied. Never push without an explicit user request.
Problem_solver cannot edit, mutate Git, run Gradle, or use ADB. It reviews verification evidence from the operators.
Environment-file reads ask for approval; example files remain readable.
Rules use V2 last-match resolution. Current V2 documentation says edit covers edits, writes, and patches.

The fixer's named script permissions assume the scripts exist and fit the task scope.
ADB companion scripts include untracked local work. A fresh clone does not contain them.
General shell access does not enforce a sandbox or prevent indirect Git or secret access. Follow task boundaries and operation rules.

## Verification limits

Legacy permission scripts still describe retired profiles and earlier permission expectations.
Their default runs are not full evidence for the three-profile configuration.
Reuse their resolver with explicit current-role probes without changing test code under a documentation-only task.
See [agent-role-consolidation](../docs/agents/tasks/agent-role-consolidation.md) for the current evidence and limits.
See [orchestrator shell access](../docs/agents/tasks/orchestrator-shell-access.md) for the subsequent permission change and checks.
Earlier [cleanup evidence](../docs/agents/tasks/agent-control-cleanup.md) is historical.

Static permission checks do not prove live interactive enforcement.
Confirm the three roles, direct operator permissions, and review routing in a new OpenCode session.
