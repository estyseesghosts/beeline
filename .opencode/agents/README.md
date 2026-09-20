# Beeline OpenCode v2 agents

Copy these files to `.opencode/agents/` in the Beeline repository.

Recommended primary agent: `orchestrator`.

Agents:
- `orchestrator`: delegates and controls scope; does not edit files.
- `problem_solver_low`: normal investigation/planning.
- `problem_solver_high`: difficult architecture/cross-protocol investigation.
- `targeted_fixer`: project-file editing and Android/Kotlin implementation.
- `code_reviewer_low`: normal independent review.
- `code_reviewer_high`: deep/escalated review.
- `git_handler`: Git-only staging/commit/push workflow.

The definitions use OpenCode v2 `permissions`/`shell`/`subagent` syntax. Non-Git agents may run common read-only Git commands automatically. Unusual Git commands ask for approval; Git mutations remain denied. The Git handler may stage and commit; broad staging, amend, and push require approval.

The analysis-script permissions assume the companion scripts are copied to `tools/scripts/`.
