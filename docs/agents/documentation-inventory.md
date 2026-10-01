# Documentation Inventory

**Status:** current.

**Owner:** Maintainers.

**Last reviewed:** 2026-09-30 (agent-control entries only; other classifications retain their earlier review).

**Stale when:** A document is added, removed, moved, or reclassified.

This page classifies every reviewed document. Use one of these classes: `current`, `planned`,
`historical`, `reference`, `stale`, or `unreviewed`.

- `current`: the content matches source and is maintained.
- `planned`: the content describes future work.
- `historical`: the content records past work. Git history and `docs/archive/` hold the record.
- `reference`: the content provides evidence or external material.
- `stale`: the content contradicts current source or a newer document.
- `unreviewed`: the content is outside this review scope.

## Agent Wiki

| Path | Class | Notes |
| --- | --- | --- |
| `docs/agents/README.md` | current | Agent wiki index. |
| `docs/agents/agent-control.md` | current | Beeline dispatch, ownership, stop conditions, and review rules. |
| `docs/agents/workflow.md` | current | Task records, recovery, slices, and checkpoints. |
| `docs/agents/engineering-rules.md` | current | Detailed policy moved from AGENTS.md; not a new runtime certification. |
| `docs/agents/documentation-rules.md` | current | Documentation authority, coverage, maintenance, comments, and style. |
| `docs/agents/tasks/agent-control-cleanup.md` | current | Agent-control cleanup state and verification limits. |
| `docs/agents/tasks/orchestrator-setup-fix.md` | historical | Earlier setup record. Not current role or permission guidance. |
| `docs/agents/tasks/subagent-permission-audit.md` | historical | Earlier eight-subagent audit. Counts describe the old configuration only. |
| `docs/agents/documentation-inventory.md` | current | This page. |
| `docs/agents/app-shell-ownership.md` | current | Current shell ownership and gaps. |
| `docs/agents/protocol-and-session-ownership.md` | current | Current source and session ownership. |
| `docs/agents/retention-inventory.md` | current | Current long-lived state bounds, lifetimes, release rules, and retention risks. |
| `docs/agents/decomposition-01-02-acceptance-matrix.md` | current | Status for every 01 and 02 exit condition. |
| `docs/agents/handoff.md` | current | Continuation pointer to the active task state. |
| `docs/agents/tasks/_template.md` | current | Task-state template. |
| `docs/agents/tasks/beeline-0.4.0.md` | current | Active task state for the Beeline 0.4.0 execution (owner/caller/test map, decisions, slice progress). |
| `docs/agents/beeline-0.4.0-ui-baseline.md` | planned | Requirement classification and device-capture baseline for the 0.4.0 UI work; device measures pending. |
| `docs/archive/agents/decomposition-01-02-completion.md` | historical | Completed 01/02 task. Kept as the durable record. |
| `docs/archive/agents/plan03-gate-partials.md` | historical | Completed gate task. Kept as the slice record. |
| `docs/archive/agents/plan03-protocol-notifications.md` | historical | Completed Plan 03 task. Kept as the durable record. |
| `docs/archive/agents/plan04-utility-retention.md` | historical | 04-A through 04-K have committed work. The archive records the earlier ditch decision (`b0d3ddd`) as history. |
| `docs/archive/agents/palustrisapp-decomposition.md` | historical | Completed S1 task. Kept as the durable record. |
| `docs/archive/agents/ui-package-migration.md` | historical | Completed P1 task. Kept as the durable record. |
| `docs/archive/agents/q1-static-analysis.md` | historical | Completed Q1 task. Kept as the durable record. |
| `docs/archive/agents/t1-test-mirror.md` | historical | Completed T1 task. Kept as the durable record. |
| `docs/archive/agents/localization-string-extraction.md` | historical | Localization record through slice 4. Resume needs a new task. |
| `docs/archive/agents/gradle-no-daemon.md` | historical | Complete Gradle wrapper record. The tree files are the authority. |
| `docs/archive/agents/profile-liked-tab.md` | historical | Completed Liked tab task. Kept as the durable record. |
| `docs/archive/agents/profile-featured-tab.md` | historical | Completed Featured tab task. Kept as the durable record. |
| `docs/archive/agents/wide-detail-interaction-fix.md` | historical | Completed wide-detail fix. Kept as the durable record. |
| `docs/archive/agents/wide-detail-photo-sizing.md` | historical | Completed photo-sizing task. Kept as the durable record. |
| `docs/archive/agents/svg-icon-replacement.md` | historical | Implemented icon task. Kept as the durable record. |
| `docs/archive/agents/docs-archive.md` | historical | Completed archive task. Kept as the durable record. |
| `docs/archive/README.md` | current | Archive index for superseded material. |

Completed historical task files live in `docs/archive/agents/`. Plan 04-A through 04-K have committed work. The archive preserves the earlier ditch decision (`b0d3ddd`) as history. The localization file covers slices 1 through 4 only.

## Decomposition Plans

| Path | Class | Notes |
| --- | --- | --- |
| `docs/decomposition_3/progressreport.md` | current | Completion specification for Plan 01 and Plan 02. |
| `docs/decomposition_3/decomposing.md` | current | Documentation reconciliation plan. |
| `docs/decomposition_3/01.md` | historical | Shell plan. Implemented through the completion slices. Plan 01 exits are met. Kept in place as the planning record. Status: acceptance matrix and `03_corrected.md`. |
| `docs/decomposition_3/02.md` | historical | State and lifecycle plan. Implemented through the completion slices. Plan 02 exits are met except blocked device verification. Kept in place as the planning record. Status: acceptance matrix and `03_corrected.md`. |
| `docs/decomposition_3/03.md` | historical | Protocol and notification persistence plan. Rebased at `b715430`. Every chunk is implemented and test verified. Kept in place as the planning record. Status: `03_corrected.md`. |
| `docs/decomposition_3/03_corrected.md` | reference | Corrected completion audit for Plans 01, 02, and 03. Source verified at `beefcb0`. |
| `docs/decomposition_3/04.md` | historical | Historical planning record. 04-A through 04-K have committed work; the archive records the earlier ditch decision (`b0d3ddd`). See [`docs/archive/agents/plan04-utility-retention.md`](../archive/agents/plan04-utility-retention.md). Last reviewed 2026-09-20. |

The earlier `docs/decomp/`, `docs/decomposition_2/`, and `docs/decomposition.md` material is
historical and archived under `docs/archive/`. Do not cite it as current architecture.

## Human Wiki

| Path | Class | Notes |
| --- | --- | --- |
| `docs/wiki/README.md` | current | Human wiki index. |
| `docs/wiki/overview.md` | current | Product overview. |
| `docs/wiki/architecture.md` | current | Layer and owner map. |
| `docs/wiki/accounts-and-sessions.md` | current | Session ownership, identity, removal, and verification limits. |
| `docs/wiki/server-compatibility.md` | planned | Metadata and empty Purpose/Entries placeholders; no substantive body. |
| `docs/wiki/data-and-privacy.md` | current | Partial body: emoji asset storage and retention; guide incomplete. |
| `docs/wiki/ui-and-navigation.md` | current | Partial body: Profiles and Photo Grid detail media are documented; guide incomplete. |
| `docs/wiki/notifications-and-direct-messages.md` | current | Partial body: direct-message threads are documented; guide incomplete. |
| `docs/wiki/build-test-and-release.md` | planned | Metadata and empty Purpose/Entries placeholders; no substantive body. |
| `docs/wiki/troubleshooting-and-contribution.md` | planned | Metadata and empty Purpose/Entries placeholders; no substantive body. |

## Root Documents

| Path | Class | Notes |
| --- | --- | --- |
| `README.md` | current | User-facing readme. Its TODO section is partially stale. |
| `importantdocs/writing_style.md` | current | Required writing style. |
| `AGENTS.md` | current | Core constraints and required rule links. |
| `.opencode/README.md` | current | Six configured profiles and current verification limits. |
| `.opencode/plan/orchestrator-setup-fix.md` | historical | Earlier setup plan. Superseded for current roles and checkpoint routing. |
| `docs/CONTINUING_PROJECTS_AGENT_CONTROL.md` | reference | Two-project input; Beeline applies it through project-local agent control. |
| `docs/archive/` | historical | Superseded plans, reports, roadmaps, and finished task states. See `docs/archive/README.md`. |
| `docs/README` assets | reference | Images, fonts, and i18n spreadsheets. |

The root plans `decomposition.md`, `0.2.2.cont.md`, `LetsPolish.md`, `FIXTHISSHIT2.md`, and
`interactionparity.md`, and the `docs/roadmaps/` plans, now live in `docs/archive/`.

## Logs

| Path | Class | Notes |
| --- | --- | --- |
| `logs/BUGS.txt` | current | Open limits and corrections. |
| `logs/<date>.txt` | historical | Per-task logs. |

`logs/DONE.txt` and `logs/TODO.txt` are retired. Git history and the task-state files hold
that record.

## Disposition Rules

- Keep a `current` page accurate. Update it in the same slice as the behavior it describes.
- Give a `planned` page an owner, a review date, and a stale condition.
- Mark a superseded page `historical`. Move it to `docs/archive/` and name its replacement.
- Do not cite a `historical` or `stale` page as proof of current behavior.
- Delete a `stale` page when its owner confirms it has no current or historical value.
