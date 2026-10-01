# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-01

## Continuation pointer

Read [tasks/agent-role-consolidation.md](tasks/agent-role-consolidation.md) for the current configuration task.
Only orchestrator, problem_solver, and targeted_fixer remain.
Application work stays separate in [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).
Start with AGENTS.md, agent control, workflow, and operation rules.

## Current position and next action

The orchestrator may implement heavier slices, update records, and run Gradle, ADB, and Git directly.
Problem_solver investigates and reviews without edits or execution of Gradle and ADB.
Targeted_fixer accepts only small explicit work packages. It may use Git under assigned checkpoint instructions.
Keep one implementation owner and one Git operator at a time.
The next operational check is a new-session smoke test of these role and permission changes.
There is no authorized next application implementation slice in this task.

## Last safe commit

f081ced is the safe commit before this consolidation.
Resolve the new slice commit by subject: `Consolidate Beeline delivery into three agent roles`.
The prior application handoff named source commit 464b2d1. Recheck that task and Git before resuming application work.

## Limits

- Legacy permission scripts need a separate task that permits test-code changes.
- Static permission probes do not prove live OpenCode enforcement.
- Broad ADB and wrapper permissions still require explicit task scope.
- Some ADB scripts are untracked local work, not fresh-clone resources.
- No Android, device, or live-server check comes from this documentation-only slice.
- Existing Android gate failures remain in logs/BUGS.txt and were not repaired here.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
The orchestrator model-field removal is preserved. Local model changes in the explicitly retired ADB and Git profiles are recorded in task state.
Do not stage or commit unrelated files. Do not push without a user request.
