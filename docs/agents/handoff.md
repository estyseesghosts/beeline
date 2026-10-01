# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-01

## Continuation pointer

Read [tasks/orchestrator-shell-access.md](tasks/orchestrator-shell-access.md) for the current configuration task.
The earlier role setup is recorded in [tasks/agent-role-consolidation.md](tasks/agent-role-consolidation.md).
Only orchestrator, problem_solver, and targeted_fixer remain.
Application work stays separate in [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).
Start with AGENTS.md, agent control, workflow, and operation rules.

## Current position and next action

The orchestrator may implement heavier slices, update records, and run Gradle, ADB, and Git directly.
It now has general shell access. Specific Git safeguards and task boundaries still apply.
Problem_solver investigates and reviews without edits or execution of Gradle and ADB.
Targeted_fixer accepts only small explicit work packages. It may use Git under assigned checkpoint instructions.
Keep one implementation owner and one Git operator at a time.
The next operational check is a new-session smoke test of these role and permission changes.
There is no authorized next application implementation slice in this task.

## Last safe commit

9f0bb10 is the safe commit before this shell-access change.
Resolve the new slice commit by subject: `Allow general orchestrator shell commands`.
The prior application handoff named source commit 464b2d1. Recheck that task and Git before resuming application work.

## Limits

- Legacy permission scripts need a separate task that permits test-code changes.
- Static permission probes do not prove live OpenCode enforcement.
- General shell access is not a sandbox. Task scope and indirect-access safeguards still apply.
- Some ADB scripts are untracked local work, not fresh-clone resources.
- No Android, device, or live-server check comes from this documentation-only slice.
- Existing Android gate failures remain in logs/BUGS.txt and were not repaired here.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve unrelated captures, inspection folders, ADB scripts, and Python caches.
The orchestrator model-field removal is preserved. Local model changes in the explicitly retired ADB and Git profiles are recorded in task state.
Preserve the current local model edits in problem_solver and targeted_fixer.
Do not stage or commit unrelated files. Do not push without a user request.
