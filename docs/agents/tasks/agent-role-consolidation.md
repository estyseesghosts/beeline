# Agent role consolidation

Status: complete
Owner: Maintainers
Last reviewed: 2026-10-01

## Objective

Reduce Beeline to one primary agent and two subagents.
Give the orchestrator direct implementation, ADB, Gradle, and Git responsibilities.
Merge investigation and review into problem_solver.
Give targeted_fixer Git access for explicit small work packages.

## Current slice

The permissions, role prompts, dispatch contracts, checkpoint commands, and maintained guidance are complete.
The current agent owns this documentation and configuration slice and its scoped commit.
No next implementation slice is authorized.

## Deliverables and exit gates

- Keep only orchestrator, problem_solver, and targeted_fixer definitions.
- Allow orchestrator edits, Git staging and commits, Gradle, and ADB.
- Allow fixer edits, Gradle, and bounded Git staging and commits.
- Keep problem_solver read-only for investigation and review.
- Require explicit instructions, files, deliverables, non-goals, exit gates, and fail gates for fixer assignments.
- Keep one implementation owner and one Git operator at a time for each slice.
- Preserve environment-file protection, destructive Git restrictions, and explicit push authorization.
- Pass static permission, routing, Markdown link, and scoped whitespace checks.
- Preserve unrelated changes and change no application code, script, test, or plugin.

## Non-goals

Do not run Android builds or device actions for this configuration-only task.
Do not repair legacy test code or change provider configuration.
Do not push.

## Fail gates

Stop if a required permission cannot be explained by a role responsibility.
Stop if unrelated work must be discarded or included in the slice commit.
Stop if documentation and active prompts disagree about ownership or allowed actions.

## Baseline and local edits

Last safe commit: f081ced.
The orchestrator has a local model-field removal. Preserve model inheritance.
The retiring ADB and Git profiles have local gpt-5.6-luna#low preferences.
Those preferences lose their role targets when the user-requested profiles are removed. Do not apply them to surviving roles.
Preserve all unrelated staged documents, source deletion, captures, scripts, caches, and writing-style edits.

## Verification

Passed: 255 of 255 current-role permission probes, including root-pathspec staging regressions.
Passed: Three frontmatter blocks, profile modes, model inheritance, and current command routing.
Passed: 72 local Markdown links and anchors.
Initial independent review found root-pathspec staging aliases that needed approval rules. The owner added the rules and regression probes.
Passed: Independent problem_solver re-review approves the implementation with no required implementation changes.
Passed: Scoped working-diff and staged-slice whitespace checks.
The task log is explicitly included despite its ignore rule. Preserve unrelated staged content.
Live OpenCode enforcement remains unverified. Android and device verification do not apply to this slice.

## Next

Confirm live role routing and permissions in a new session.
Legacy test-profile updates require a separate task that permits test-code changes.

## Slice commit pointer

Last safe commit before this slice: f081ced.
Resolve the new slice commit by subject: `Consolidate Beeline delivery into three agent roles`.
