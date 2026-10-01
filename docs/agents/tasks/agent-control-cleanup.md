# Agent control cleanup

Status: historical (completed at f081ced; superseded by the three-profile consolidation)
Owner: Maintainers
Last reviewed: 2026-09-30

## Objective

Current operation guidance: [agent-role-consolidation](agent-role-consolidation.md) and [agent control](../agent-control.md).
The permissions, counts, and separate specialist roles below describe the earlier configuration only.

Apply the continuing-project control rules to Beeline agent configuration and documentation.
Do not change code.

## Current slice

The bounded documentation implementation and static checks are complete.
The slice commit is identified below. No further implementation slice is authorized.
The application task is unchanged. No next implementation slice is authorized.

## Acceptance criteria

- Keep one investigator and one reviewer instead of duplicate roles.
- Preserve separate implementation, Git, and device permissions.
- Use the same implementation owner for review repairs.
- Add stop conditions and bounded dispatch contracts.
- Preserve detailed project constraints through required reading links.
- Change no application code, script, test, or plugin.
- Preserve unrelated worktree changes.

## Verification

Passed: 153 of 153 consolidated subagent permission probes and 44 of 44 current orchestrator probes.
Passed: Six agent frontmatter blocks, edit boundaries, and command routing.
Passed: 81 local Markdown links and anchors in the staged slice. Active prompts contain no retired dispatch targets.
Passed: Final diff review confirms the task scope, retained project rules, and preserved unrelated changes.
Whitespace checks apply only to this slice. Preserve unrelated staged content, including its existing whitespace.
Android verification does not apply to this documentation-only slice.
Live OpenCode permission checks remain unverified.

## Next

Confirm live routing and permission prompts in a new OpenCode session.
Updating legacy test profiles requires a separate task that permits test-code changes.

## Blockers

Existing permission test profiles name the old roles. Do not edit test code for this task.
Reuse those profiles for the consolidated roles in a temporary verification command.

## Last safe commit

78b9b14 is the baseline before this slice.
Resolve the slice commit by subject: `Consolidate Beeline agent control and extract project rules`.
