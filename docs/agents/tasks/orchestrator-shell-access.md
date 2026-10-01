# Orchestrator shell access

Status: complete
Owner: Maintainers
Last reviewed: 2026-10-01

## Objective and owner

Allow the orchestrator to run task-required commands without a narrow shell allowlist.
The current orchestrator owns this documentation and configuration slice and its Git checkpoint.
The existing permission owner is .opencode/agents/orchestrator.md.

## Deliverables and exit gates

- Allow general shell commands, including bare `.\gradlew.bat`, reordered arguments, PowerShell, and interpreters.
- Keep existing Git approval and denial rules after the general shell allow.
- Keep environment-file and external-directory protections and current subagent routing.
- Leave fixer and problem_solver definitions unchanged, including their local model edits.
- Update operator guidance and handoff to explain general shell access and its limitations.
- Pass current permission probes, Markdown links, independent review, and scoped whitespace checks.

## Non-goals and fail gates

Do not change application code, scripts, tests, plugins, providers, or subagent permissions.
Do not run Gradle builds or device actions to validate this configuration-only change.
Do not push. Preserve all unrelated index and worktree changes.
Stop if a specific Git safeguard is overridden or unrelated work enters the commit scope.

## Baseline and verification

Last safe commit: 9f0bb10.
Preserve local model edits in problem_solver and targeted_fixer, staged navigation documentation, and all other unrelated work.
Passed: 69 of 69 permission probes and 53 local Markdown links and anchors.
Passed: All previous Git and non-shell rules are identical. Subagent permissions retain their baseline values.
Passed: Independent problem_solver review approves the actual diff without required repairs.
Passed: Scoped working-diff and staged-slice whitespace checks.
Live enforcement remains unverified. General shell access does not enforce indirect-command or filesystem isolation.

## Commit and next action

Planned slice subject: `Allow general orchestrator shell commands`.
After this slice, verify the permissions in a new OpenCode session.
