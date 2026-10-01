# Beeline Agent Control

Status: current
Owner: Maintainers
Last reviewed: 2026-09-30
Stale when: Agent roles, permissions, dispatch, or review contracts change.

Authority: [AGENTS.md](../../AGENTS.md) and [agent definitions](../../.opencode/README.md).
This page applies the Beeline rules from [Continuing Projects](../CONTINUING_PROJECTS_AGENT_CONTROL.md).
That document is reference input for two projects. Its voxel-engine rules do not apply to Beeline.
Project-local architecture constraints take precedence over that reference within higher-priority instructions.

## One implementation owner

Each task has exactly one implementation owner. Keep that owner through validation and review repairs.
Other agents may investigate, compare options, identify tests, or review. They do not independently edit the task.
Allow another editor only through an explicit separate worktree and non-overlapping ownership assignment.
Do not pass one task through a chain of implementers and fixers.

Default flow:

```text
optional investigator -> implementation owner -> verify -> reviewer
-> same implementation owner resolves findings -> verify -> Git checkpoint
```

Use investigation only when ownership, behavior, or the change surface is unclear.
Use parallel read-only investigation only for independent questions that reduce uncertainty.
Do not dispatch agents merely to satisfy a pipeline.

## Dispatch contract

Before implementation, record:

- bounded objective and explicit non-goals;
- acceptance criteria and implementation owner;
- current behavior owner and existing abstraction;
- smallest valid change surface;
- relevant constraints, tests, and validation commands.

Investigate before editing when any item is unclear.
Do not let unfamiliar code justify a replacement architecture.
Classify a copied pattern as intentional, legacy, compatibility code, technical debt, or an exception.
Investigate uncertain patterns before copying them.
Keep cleanup limited to what makes the requested change correct. Record unrelated work as a follow-up.

## Stop conditions

Stop editing and re-investigate when:

- two attempted fixes fail for the same root problem;
- a workaround needs another workaround;
- scope expands into unrelated subsystems;
- a new global state holder, mutable singleton, or manager is proposed;
- an existing file gains a second major responsibility;
- the owner cannot explain current architectural behavior;
- test failures are not explained by the intended change;
- the owner wants to disable, weaken, skip, or rewrite a test to make it pass;
- a generic helper substitutes for finding the correct owner;
- an edit would duplicate existing logic.

Report before proposing further edits:

```text
Observed problem:
Incorrect assumption:
Current owner of the behavior:
Why the last attempt failed:
New proposed approach:
Expected files to change:
```

Continue only after investigation supports a bounded approach within the task contract.
Ask the user when continuation needs a scope or architecture decision.
Do not work around rejected permissions. Correct a permitted command or request approval.

## Beeline investigation

For a UI bug, identify the state, its producer, transformations, and renderer.
Classify the failure as state, layout, gesture, or rendering. Find a test that can reproduce it.
For a data bug, trace source API -> mapper -> repository -> cache or persistence -> UI model.
Do not start implementation from the visible symptom alone.

Before repository, notification, or account changes, trace callers, authority, persistence, caching, lifetime, and account scope.
Do not add a second cache or mutable singleton to repair synchronization.
Keep protocol-specific behavior behind existing source and adapter boundaries.
Keep business logic outside composables. Reuse existing Beeline UI primitives.
Use responsive composition from shared state and components unless behavior genuinely differs.
Do not create a second unrelated screen tree for wide or foldable layouts.

## Review contract

The reviewer is not another implementer. Inspect the actual final diff, not completion claims.
Answer whether the result satisfies the task and changes only the necessary scope.
Check ownership, duplicate logic, justified abstractions, hidden state, and compounded workarounds.
Check test placement and coverage. Confirm that existing tests were not weakened.
Consider a simpler change that preserves the architecture.
For Beeline, check protocol leakage, Compose state and lifecycle, repository duplication, and adaptive layout regressions.
Give concrete file and symbol references. Do not return vague cleanup advice.
Separate BLOCKING, REQUIRED, OPTIONAL, and OUT OF SCOPE findings.
The same owner resolves required findings or records rejection with evidence.

## Completion and permission limits

Completion requires implementation, applicable validation, review, resolved required findings, and final diff inspection.
Use the project Android verification for code changes. Validate relevant width classes for adaptive UI changes.
Use document and configuration checks for documentation-only work.
Split tasks that grow beyond the dispatch contract.

Permissions constrain tool access. Prompts constrain task behavior. Neither static permission probes nor prompts prove live safety.
Read-only means no source edits; approved checks may write build outputs.
ADB may change device state or create diagnostic captures. Keep those actions within the assigned device-check task.
Never treat an allowed shell command as authorization for unrelated work.
