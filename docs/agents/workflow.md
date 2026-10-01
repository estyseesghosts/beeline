# Beeline Task Workflow

Status: current
Owner: Maintainers
Last reviewed: 2026-10-01
Stale when: Task ownership, checkpoint order, or recovery changes.

Authority: [AGENTS.md](../../AGENTS.md), [agent control](agent-control.md), and current Git state.
Configuration: [checkpoint](../../.opencode/command/checkpoint.md) and [resume](../../.opencode/command/resume.md).

## Start and recover

1. Read AGENTS.md and its required task-relevant pages.
2. Read the active task state and handoff.
3. Inspect Git status, recent relevant commits, and the current diff.
4. Identify pre-existing staged, modified, deleted, and untracked files.
5. Identify the current behavior owner, abstraction, callers, invariants, and affected tests.
6. Identify affected protocol boundaries, persisted state, UI state, and account scope.
7. State the objective, non-goals, acceptance criteria, and validation method.

Use source and Git as evidence. Do not use conversation memory or old plans as implementation evidence.
Use `/resume` after context loss. Stop implementation when task state is incomplete.

## Size and ownership

Each slice has one implementation owner. That owner keeps responsibility through validation and review repairs.
The orchestrator may implement a slice directly. It delegates only small explicit work packages to targeted_fixer.
Problem_solver performs read-only investigation and review. Other agents do not edit the same owned scope.
Do not create subagents merely because a task is large.

Define a slice by one behavior, not by file count. Include its tests, migrations, contracts, and documentation.
Keep each slice independently reviewable and verifiable. Do not mix unrelated cleanup.
For several architectural changes, unrelated behaviors, or independent validation stages, record a slice plan first.
Use this statement: `This task is larger than one safe implementation slice.`
Complete and commit one slice before starting another.

## Durable records

- Keep core constraints and required reading in AGENTS.md. Keep detailed permanent rules in docs/agents/.
- Keep current long-task state in `docs/agents/tasks/<task>.md`. Use [the template](tasks/_template.md).
- Rewrite task state at each slice boundary. Do not append task history to it.
- Keep verified history in Git commits. Keep audit evidence in logs/.
- Create `logs/YYMMDD-HHMMSS.txt` when coding starts. Record the goal, slices, files, and risks.
- Record failures and external blockers in `logs/BUGS.txt`. Remove resolved obsolete entries.
- Do not maintain `logs/DONE.txt` or `logs/TODO.txt`.
- Keep all records free of secrets, credentials, and complete server responses.
- Respect the user scope. A documentation-only task does not permit code changes.

## Slice checkpoint

1. The implementation owner implements the bounded slice.
2. The owner runs the smallest relevant checks, then required gates.
3. Inspect the actual diff and complete review. Resolve required findings with the same owner.
4. The owner updates affected documentation, task state, task log, and handoff.
5. Inspect the final diff, including the record updates.
6. The assigned Git operator commits only the reviewed slice files and their records together.
7. Report the commit hash, remaining limits, and next slice.

Use `/checkpoint` to coordinate this order. Do not commit a knowingly broken slice.
Do not use a separate documentation commit for the same implementation slice.
Do not squash slice commits unless the user requests it.
Never push unless the user requests it.
The orchestrator and fixer may operate Git. Assign one operator; never run concurrent index or history mutations.
Use [operation rules](operation-rules.md) when unrelated staged changes exist.

## Handoff

Rewrite `docs/agents/handoff.md` after each completed slice.
Name the durable task-state file instead of duplicating it.
Record the current position, next slice, last safe commit, and known blockers.
The orchestrator may update these records directly or explicitly assign them to the same implementation owner.
Do not edit the same record concurrently. Include record scope in fixer instructions when it owns the update.
Inside a commit, name the preceding safe commit and the slice commit subject.
The next session can resolve the new hash from Git without a second record-only commit.

## Verification and completion

For code changes, use [engineering verification](engineering-rules.md#verification).
Use the wrapper with `--no-daemon --console=plain`. Set an explicit timeout and close standard input.
Set `GRADLE_OPTS=-Dorg.gradle.daemon=false` in the agent environment when supported.
Do not bypass shell permissions to set it. Report an environment limitation instead.

For documentation-only slices, check links, configuration, role routing, applicable permissions, and the final diff.
Do not claim that documentation checks prove Android builds, device rendering, or live-server behavior.
Existing failing gates must remain visible. Fix failures caused by the slice within the permitted scope.

Before completion, confirm the requested result, documentation coverage, verification evidence, records, and slice commit.
Confirm that unrelated work remains intact and no secrets entered the diff.
Report unavailable checks and unresolved risks.
