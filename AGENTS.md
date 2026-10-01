# Beeline Agent Guide

## Required reading

- Read [workflow](docs/agents/workflow.md) before work and after context recovery.
- Read [agent control](docs/agents/agent-control.md) before delegation, investigation, implementation, or review.
- Read [engineering rules](docs/agents/engineering-rules.md) before code changes or reviews of code.
- Read [documentation rules](docs/agents/documentation-rules.md) before documentation changes.
- Read [writing style](importantdocs/writing_style.md) before writing.
- Read the active task state and [handoff](docs/agents/handoff.md) before continuing work.
- Read the relevant ownership pages in [the agent wiki](docs/agents/README.md).
- Read [operation rules](docs/agents/operation-rules.md) before Git, Gradle, or ADB work.

Linked rules remain required. This file is the entry point, not a second copy of those rules.
Follow higher-priority instructions. Verify project conflicts against source, or ask the user for a decision.

## Product and platform

- Use **Beeline** in all product text, labels, registration names, documentation, and User-Agent values.
- Palustris is internal only. Preserve compatibility identifiers unless the task includes a dedicated migration.
- Beeline is one Android application module, `:app`, with minimum SDK 29.
- Use Kotlin, Jetpack Compose, and Material 3. Do not add XML layouts, AppCompat, or Material 2.
- Support both Misskey-family and Mastodon-compatible servers through their adapters.

## Ownership and boundaries

- One mutable state has one authoritative owner. One lifetime has one explicit release rule.
- Extend the existing feature owner and abstraction. Do not invent a replacement architecture.
- Keep feature code in its feature package. Composition roots wire features; they do not implement them.
- ViewModels must not privately construct production authorities or caches.
- Name the responsibility and lifetime of each new abstraction.
- Keep shared domain behavior protocol-neutral. Keep protocol behavior behind adapter and capability boundaries.
- Keep transport, persistence, account ownership, and protocol JSON outside Compose.
- Treat large files as warnings, not automatic split requirements.

## Safety and scope

- Treat current source, tests, build files, manifests, and CI as authorities, not old plans or completion claims.
- Inspect Git status, recent relevant commits, and the current diff before substantial work.
- Preserve unrelated changes. Never discard user work to make a task or commit easier.
- Do not broaden scope, duplicate behavior, weaken tests, or bypass denied permissions.
- Validate authenticated origins. Keep sessions and secrets account-scoped. Never log secrets or complete API responses.
- Respect explicit scope limits. If the user forbids code changes, do not edit code, scripts, tests, or plugins.

## Agent ownership

- Each slice has one implementation owner. The same owner resolves review findings until validation passes.
- The `orchestrator` owns delivery and may implement larger slices, run ADB and Gradle, and operate Git directly.
- Use `targeted_fixer` only for small explicit work packages with deliverables, exit gates, non-goals, and fail gates.
- Use `problem_solver` for read-only investigation and review. Do not create separate reviewer, ADB, or Git agents.
- Assign one implementation owner and one Git operator per slice. Do not mutate a delegated scope concurrently.
- Other agents do not spawn subagents unless the user explicitly requests delegation.
- Investigation and review are read-only. Use optional investigation only when it reduces uncertainty.
- Stop after two failed fixes for one root problem. Follow all stop conditions in [agent control](docs/agents/agent-control.md).

## Completion

- Verify each slice, inspect its diff, update its records, and commit only its reviewed files.
- Use the Gradle wrapper with `--no-daemon --console=plain`, an explicit timeout, and closed standard input.
- For code changes, run relevant tests and `test assembleRelease` before declaring completion.
- For documentation-only work, check links, configuration, permissions, and the final diff instead of Android builds.
- Report blocked checks and unverified device or live-server behavior. Never push unless the user requests it.
