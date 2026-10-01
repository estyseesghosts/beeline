# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-09-30

## Continuation pointer

The agent-control cleanup is recorded in [tasks/agent-control-cleanup.md](tasks/agent-control-cleanup.md).
It changes documentation and agent configuration only. Application work remains separate in [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md).
Start with AGENTS.md and its required pages. Do not use historical setup records as current role guidance.

## Current position and next slice

The cleanup consolidates investigation and review roles and moves detailed rules out of AGENTS.md.
Static permission, frontmatter, routing, and document checks are the applicable gates.
No Android build, device, or live-server evidence comes from this slice.
The next operational check is a new-session OpenCode smoke test of routing and permission prompts.
There is no authorized next implementation slice. Resume application work only under its own task contract.

## Last safe commit

78b9b14 is the safe commit before this cleanup.
Resolve the cleanup commit with the subject `Consolidate Beeline agent control and extract project rules` in Git.
This pointer is included in that slice commit; it does not require a second record-only commit.
The prior application handoff named source commit 464b2d1. Recheck the application task and Git before resuming it.

## Limits and blockers

- Legacy permission tests contain old role names. The cleanup reused their probes with a runtime role map without changing code.
- Static permission resolution does not prove live interactive enforcement.
- ADB has broad command permission. Its prompt limits task scope; that is not a hard device-state boundary.
- Named ADB companion scripts include untracked local work. They are not available in a fresh clone.
- Current V2 documentation says edit permission covers edits, writes, and patches. The older separate-write limitation is not current guidance.
- Existing Android gate failures remain recorded in logs/BUGS.txt. This slice does not repair or reverify them.

## Worktree caution

Preserve the staged docs/classic_navigation.md, modified importantdocs/writing_style.md, deleted Photo Grid test, and deleted PNGs.
Preserve all unrelated untracked captures, inspection folders, ADB scripts, and Python caches.
Do not stage, discard, or commit those files as part of this cleanup. Do not push without a user request.
