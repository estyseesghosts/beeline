# Continuing Projects: Subagent Control Rules

Status: reference
Owner: Maintainers
Last reviewed: 2026-09-30
Beeline application: [agent control](agents/agent-control.md), [workflow](agents/workflow.md), and [agent profiles](../.opencode/README.md).
Use those maintained project-local pages for Beeline operations. This file retains the two-project reference rules.

Use this file only for the two existing projects that already have substantial history and architectural constraints:

- **Beeline** — Android Mastodon/Misskey client.
- **Voxel engine** — Rust voxel engine and renderer.

This file supplements `AGENTS.md` and the normal workflow documents. If this file conflicts with a project-local architecture document, the project-local architecture document wins.

---

# 1. Core rule: do not let a subagent redefine the project

These projects already have established direction. A subagent is not allowed to invent a replacement architecture because the existing design is unfamiliar, inconvenient, or incomplete.

Before changing code, every implementation agent must identify:

1. the existing owner of the behavior;
2. the existing abstraction it is extending;
3. the smallest valid change surface;
4. the tests or validation that prove the change;
5. any architecture document that constrains the work.

If any of these are unclear, investigate first. Do not guess and do not start editing.

---

# 2. One implementation owner per task

Every task has exactly one implementation owner.

Other agents may:

- inspect;
- trace behavior;
- find tests;
- identify regressions;
- review a diff;
- compare implementation options.

They must not independently edit the same task unless the orchestrator explicitly assigns a separate worktree with non-overlapping ownership.

Do not use this chain:

```text
explorer -> planner -> implementer -> fixer -> second fixer -> reviewer
```

Use this instead:

```text
explorer(s) -> optional planner -> implementation owner -> reviewer -> same implementation owner
```

The implementation owner keeps responsibility until validation passes.

---

# 3. Mandatory stop conditions

An implementation agent must stop editing and re-investigate when any of these occur:

- two attempted fixes fail for the same root problem;
- the fix needs a workaround for a previous workaround;
- the expected change expands into unrelated subsystems;
- a new global state holder or manager is being introduced;
- an existing file gains a second major responsibility;
- the agent cannot explain why the current architecture behaves as it does;
- tests fail in a way not explained by the intended change;
- the agent wants to disable, weaken, skip, or rewrite a test to make the task pass;
- the agent wants to create a generic helper because it cannot find the correct owner;
- the agent is about to duplicate logic that already exists elsewhere.

At that point, the agent must report:

```text
Observed problem:
Incorrect assumption:
Current owner of the behavior:
Why the last attempt failed:
New proposed approach:
Expected files to change:
```

Only then continue.

---

# 4. Existing code is evidence, not permission

Do not copy a bad local pattern merely because similar code already exists.

Before copying an existing implementation pattern, determine whether it is:

- intentional architecture;
- legacy code;
- temporary compatibility code;
- known technical debt;
- an isolated exception.

If uncertain, ask an explorer to trace where else the pattern is used and why.

---

# 5. No opportunistic cleanup

Do not combine a requested change with unrelated cleanup.

Allowed:

- cleanup required to make the requested change correct;
- removal of dead code made obsolete directly by the change;
- a small local refactor that reduces risk inside the touched subsystem.

Not allowed:

- renaming unrelated APIs;
- moving unrelated files;
- changing formatting across a package;
- replacing architecture while fixing one bug;
- broad dependency upgrades;
- "while I am here" refactors.

Create a follow-up task instead.

---

# 6. Beeline-specific control rules

Beeline is an Android client with existing UI, repository, account, timeline, notification, and protocol abstractions. It is especially vulnerable to large UI files, duplicated source-specific behavior, and repositories that accumulate unrelated responsibilities.

## 6.1 Preserve protocol boundaries

Mastodon- and Misskey-specific behavior must stay behind the appropriate protocol/source abstraction.

Do not:

- scatter `if Mastodon` / `if Misskey` branches through Compose UI;
- let UI code construct protocol requests directly;
- add source-specific fields to generic models unless the generic layer genuinely owns them;
- duplicate timeline, notification, or account logic per screen.

If a task needs protocol-specific behavior, first locate the existing source/repository boundary and extend it there.

## 6.2 Compose UI rules

For UI changes:

- preserve state ownership;
- do not move business logic into composables;
- do not create one huge screen composable that owns navigation, loading, persistence, and rendering;
- extract components by visual/behavioral responsibility, not just by line count;
- avoid parameter explosions caused by refusing to define the correct state model;
- do not introduce a new design system primitive if an existing Beeline primitive already owns that role.

For foldable/wide layouts, do not implement a second unrelated screen tree unless there is a real behavioral difference. Prefer responsive composition from shared state and shared components.

## 6.3 Repository and notification rules

Before changing repositories, notifications, or account state:

1. trace all callers;
2. identify the current source of truth;
3. identify caching/persistence behavior;
4. identify lifecycle/scope;
5. identify whether the behavior is account-specific, source-specific, or global.

Do not add another cache or another mutable singleton to fix synchronization problems.

## 6.4 Beeline bug workflow

For a UI bug, the explorer should answer:

```text
What state causes the incorrect UI?
Where is that state produced?
Where is it transformed?
Where is it rendered?
Is the bug state, layout, gesture, or rendering related?
What existing test can reproduce it?
```

For a data bug:

```text
Which source API produced the data?
Which mapper transformed it?
Which repository owns it?
Which cache/persistence layer touched it?
Which UI model consumed it?
```

Do not let the implementer start from the visible symptom alone.

## 6.5 Beeline validation

A Beeline change is not complete until the project-defined Android verification passes, including the relevant subset of:

- Kotlin compilation;
- Compose compilation;
- unit tests;
- lint/static analysis;
- relevant instrumentation/UI tests where available;
- manual validation for layout/gesture changes on the intended form factor.

For foldable or adaptive UI changes, validate more than one width class.

---

# 7. Voxel-engine-specific control rules

The voxel engine has stricter architecture and correctness requirements than a normal game prototype. Performance, determinism, ownership, coordinate correctness, and renderer boundaries are first-class constraints.

A subagent must not simplify a problem by weakening those constraints.

## 7.1 Engine-first rule

Do not reshape the engine around one Minecraft-like feature unless that feature demonstrates a reusable engine requirement.

When adding behavior, distinguish:

- engine primitive;
- game rule;
- compatibility behavior;
- test fixture;
- renderer concern;
- world/simulation concern.

Do not put game-specific policy into low-level engine primitives merely because it is convenient.

## 7.2 Ownership must remain explicit

Before changing renderer or world code, identify who owns:

- CPU-side mesh data;
- GPU resource lifetime;
- upload scheduling;
- command submission;
- presentation;
- world/chunk/section lifetime;
- camera/view state;
- coordinate conversion.

Do not introduce duplicate ownership between engine and viewer/application layers.

If two layers can both mutate or destroy the same resource, stop and resolve ownership first.

## 7.3 Correctness before optimization, measurement before optimization

Do not claim a performance improvement without measurement.

For performance work:

1. establish current benchmark/result;
2. identify the suspected cost;
3. make one bounded change;
4. rerun the same benchmark;
5. compare memory as well as CPU/GPU cost where relevant;
6. preserve correctness tests.

Do not replace an algorithm solely because it is theoretically faster.

Do not accept a speedup that silently creates unacceptable memory growth or changes deterministic behavior.

## 7.4 Determinism and coordinate rules

For simulation, traversal, redstone-like logic, ray casting, chunk/section coordinates, or cross-space behavior:

- define integer/fraction semantics explicitly;
- define boundary behavior explicitly;
- define tie-breaking explicitly;
- use checked arithmetic where exhaustion is possible;
- distinguish different failure/exhaustion outcomes;
- do not rely on incidental floating-point behavior for deterministic logic.

If semantics are not already frozen in project docs, planning is required before implementation.

## 7.5 Renderer changes require a change-surface check

Before editing renderer architecture, the explorer must identify:

```text
CPU data affected:
GPU data affected:
Resource lifetime affected:
Per-frame path affected:
Upload path affected:
Draw submission affected:
Synchronization affected:
Backend-specific code affected:
Benchmarks affected:
```

A renderer implementation agent must not casually modify all of these at once.

If a proposed change crosses several of them, create an explicit plan first.

## 7.6 Avoid hidden hot-path costs

Treat these as suspicious until measured:

- per-frame allocations;
- repeated hash lookups in inner loops;
- repeated coordinate conversions;
- cloning large buffers;
- hidden iterator/materialization costs;
- unnecessary GPU queries or resolves;
- redundant chunk/section scans;
- rebuilding data that could have a clear dirty/update lifecycle.

Do not "optimize" them speculatively, but do flag them during review.

## 7.7 Rust structure rules

Do not create giant modules or giant functions merely to avoid designing an abstraction.

Also do not mechanically split files into meaningless `helpers`, `utils`, or `misc` modules.

A new module must have a coherent responsibility that can be stated in one sentence.

Prefer explicit types and ownership over shared mutable global state.

Unsafe code requires a clear invariant and justification local to the unsafe boundary.

## 7.8 Voxel-engine validation

The implementation owner must run the project-defined Rust verification, normally including the relevant subset of:

- formatting;
- compilation;
- Clippy;
- unit/integration tests;
- regression tests;
- repository benchmarks affected by the change.

For performance-sensitive work, record before/after numbers in the task file.

A benchmark regression cannot be dismissed as noise without evidence.

---

# 8. Reviewer instructions for both projects

The reviewer is not another implementer.

The reviewer must inspect the actual diff and answer:

1. Does this satisfy the task contract?
2. Did the implementation change more than necessary?
3. Did ownership become less clear?
4. Was logic duplicated?
5. Was a new abstraction justified?
6. Were tests added at the correct level?
7. Were existing tests weakened?
8. Did the implementation introduce new hidden state?
9. Is any workaround compensating for another workaround?
10. Is there a simpler change that preserves the existing architecture?

For Beeline, additionally check:

- protocol leakage into UI;
- Compose state/lifecycle mistakes;
- duplicated repository behavior;
- adaptive layout regressions.

For the voxel engine, additionally check:

- determinism;
- coordinate boundaries;
- ownership/lifetime;
- hot-path cost;
- memory impact;
- benchmark coverage.

Review findings must be concrete and reference files/symbols. Do not return vague comments such as "could be cleaner".

---

# 9. Orchestrator instructions for existing projects

The orchestrator must be conservative with these repositories.

Before dispatching implementation, it must confirm that the task has:

- a bounded objective;
- explicit non-goals;
- acceptance criteria;
- a known implementation owner;
- known validation commands;
- enough investigation to identify the correct subsystem.

The orchestrator must not accept "done" from an implementation agent as proof of completion.

Completion requires:

```text
implementation complete
+ required validation passes
+ review complete
+ review findings resolved or explicitly rejected with evidence
+ final diff inspected
```

If the task is becoming larger while in progress, split it instead of allowing the agent to silently expand scope.

---

# 10. Recommended default dispatch patterns

## Small Beeline bug

```text
explorer -> implementer -> verify -> reviewer -> same implementer if needed -> verify
```

## Beeline feature spanning UI and data

```text
parallel read-only exploration of UI + data flow
-> planner if ownership is unclear
-> one implementation owner
-> verify
-> reviewer
-> same owner fixes
```

## Small voxel-engine correctness bug

```text
explorer -> implementation owner -> tests -> reviewer -> same owner
```

## Voxel-engine architecture or renderer change

```text
parallel read-only exploration
-> written plan
-> explicit ownership/change-surface review
-> one implementation owner or isolated non-overlapping worktrees
-> full validation/benchmarks
-> independent review
-> same owner fixes findings
```

---

# 11. Final rule

Do not optimize the workflow for maximum agent activity.

Optimize it for:

- minimum context loss;
- minimum simultaneous mutation;
- clear ownership;
- deterministic verification;
- easy rollback;
- small understandable diffs.

A task completed by one competent implementation owner plus one reviewer is preferable to the same task passing through six agents.
