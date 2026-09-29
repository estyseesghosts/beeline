# Beeline OpenCode v2 agents

Copy these files to `.opencode/agents/` in the Beeline repository.

Recommended primary agent: `orchestrator`.

Agents:
- `orchestrator`: delegates and controls scope; never works directly.
- `problem_solver_low`: normal investigation/planning.
- `problem_solver_high`: difficult architecture/cross-protocol investigation.
- `targeted_fixer`: project-file editing and Android/Kotlin implementation.
- `code_reviewer_low`: normal independent review, and the Gradle test run.
- `code_reviewer_high`: deep/escalated review.
- `git_handler`: Git-only staging, commit, and push workflow.
- `codebase_explorer_android`: read-only codebase mapping.
- `adb_handler`: device checks through `adb` only.

The definitions use OpenCode v2 `permissions`/`shell`/`subagent` syntax. Non-Git agents may run common read-only Git commands automatically. Unusual Git commands ask for approval; Git mutations remain denied. The Git handler commits directly, while staging and push ask for approval.

The orchestrator delegates every change. It keeps `edit` set to `deny` and has no write allow rule. Route file changes to `targeted_fixer` and Git mutations to `git_handler`.

The analysis-script permissions assume the companion scripts are copied to `tools/scripts/`.
