# Beeline Documentation Archive

Status: historical
Owner: Maintainers
Last reviewed: 2026-09-19
Stale when: An archived item gains a current requirement that no maintained page records.

This directory holds superseded plans, exploration reports, roadmaps, and finished task
states. Each item is historical. Git history and the original task logs hold the permanent
record.

Do not cite an archived item as proof of current behavior. Use the current source, current
tests, `AGENTS.md`, `docs/wiki/`, and `docs/agents/` instead.

## Archived On 2026-09-19

The owner ditched Plan 04 and emptied the active tasks folder. Every
task file moved unchanged. The active folder keeps only `_template.md`.

| File | Reason | Replacement or owner |
| --- | --- | --- |
| `agents/plan04-utility-retention.md` | Ditched plan. Slices 04-A through 04-D and 04-E1 through 04-E3 are committed. Slices 04-F through 04-K will not run. | `docs/archive/agents/docs-archive.md` |
| `agents/decomposition-01-02-completion.md` | Finished task state. | `docs/agents/decomposition-01-02-acceptance-matrix.md` |
| `agents/plan03-gate-partials.md` | Finished task state. | `docs/archive/agents/plan03-protocol-notifications.md` |
| `agents/plan03-protocol-notifications.md` | Finished task state. | `data/notifications/` |
| `agents/palustrisapp-decomposition.md` | Finished task state. | `docs/agents/app-shell-ownership.md` |
| `agents/ui-package-migration.md` | Finished task state. | Feature packages under `ui/` |
| `agents/q1-static-analysis.md` | Finished task state. | Current ktlint baseline |
| `agents/t1-test-mirror.md` | Finished task state. | Mirrored test packages |
| `agents/localization-string-extraction.md` | Paused record through slice 4. Resume needs a new task. | Current locale resources |
| `agents/gradle-no-daemon.md` | Complete wrapper record. The commit-on-request step never ran and is dropped. | `AGENTS.md`, `build-debug.bat`, `build-release.bat` |
| `agents/profile-liked-tab.md` | Finished task state. | `ui/profile/` |
| `agents/profile-featured-tab.md` | Finished task state. | `ui/profile/` |
| `agents/wide-detail-interaction-fix.md` | Finished task state. | `ui/thread/` |
| `agents/wide-detail-photo-sizing.md` | Finished task state. | `ui/photogrid/`, `ui/SinglePostScreen.kt` |
| `agents/svg-icon-replacement.md` | Implemented icon task. | `ui/BeelineSvgPaths.kt` |
| `agents/docs-archive.md` | Finished archive task. | `docs/agents/tasks/_template.md` |

## Archived On 2026-09-15

The maintainer moved these items out of the active documentation paths. Each item describes
work that current source already implements, or a plan that a later plan replaces.

### Root Plans

| File | Reason | Replacement or owner |
| --- | --- | --- |
| `0.2.2.cont.md` | Visual polish plan. Implemented. | `ui/navigation/HomeTimelineTabs.kt`, `ui/components/BubbleGeometry.kt`, `ui/HashtagBubble.kt` |
| `LetsPolish.md` | Visual specification for `0.2.2.cont.md`. Implemented. | `docs/archive/0.2.2.cont.md` |
| `FIXTHISSHIT2.md` | Setup-screen, share-sheet, and launcher-icon fixes. Implemented. | Current resources and `ui/setup/` |
| `interactionparity.md` | v0.2.1 interaction parity task. Merged. | Shared post interaction owners in `ui/` |
| `decomposition.md` | Broad decomposition guide. Superseded. | `docs/decomposition_3/` plans |

### Exploration Tasks And Reports

| File | Reason | Replacement or owner |
| --- | --- | --- |
| `decomp/palustrisapp.md` | Exploration prompt. Superseded. | `docs/agents/app-shell-ownership.md` |
| `decomp/screens.md` | Exploration prompt. Superseded. `Screens.kt` no longer exists. | Feature packages under `ui/` |
| `decomp/sourceservice.md` | Exploration prompt. Superseded. | `docs/agents/protocol-and-session-ownership.md` |
| `decomp/notificationrepository.md` | Exploration prompt. Superseded. | `docs/decomposition_3/03.md` |
| `decomp/results/expnotifrepo.md` | Evidence map. Claims corrected by later plans. | `docs/agents/protocol-and-session-ownership.md` |
| `decomp/results/exppalustrisapp.md` | Evidence map. Superseded. | `docs/agents/app-shell-ownership.md` |
| `decomp/results/expscreens.md` | Evidence map. Superseded. | Feature packages under `ui/` |
| `decomp/results/expsourceservice.md` | Evidence map. Superseded. | `docs/agents/protocol-and-session-ownership.md` |
| `decomposition_2/01.md` through `06.md` | Exploration briefs. Completed. | `docs/archive/decomposition_2/results/` |
| `decomposition_2/07.md` | Implementation brief for the finished 01/02 series. | `docs/agents/decomposition-01-02-acceptance-matrix.md` |
| `decomposition_2/results/01.md` through `06.md` | Exploration reports. Reference only. | `docs/decomposition_3/` plans |

### Roadmaps

| File | Reason | Replacement or owner |
| --- | --- | --- |
| `roadmaps/notifications.md` | Implementation plan. Implemented. | `data/notifications/` |
| `roadmaps/notifications_progress.md` | Progress record. Superseded. | `docs/archive/agents/plan03-protocol-notifications.md` |
| `roadmaps/profile_timeline_roadmap.md` | Implementation plan. Implemented. Its file manifest is stale. | `ui/profile/` |

### Finished Agent Task States

| File | Reason | Replacement or owner |
| --- | --- | --- |
| `agents/cleanup-progress-report.md` | Historical report. Superseded. | `docs/agents/decomposition-01-02-acceptance-matrix.md` |
| `agents/app-shell-decomposition.md` | Finished task state. | `docs/archive/agents/decomposition-01-02-completion.md` |
| `agents/state-and-lifecycle-repair.md` | Finished task state. | `docs/archive/agents/decomposition-01-02-completion.md` |
| `agents/cancellation-and-shell-continuation.md` | Finished task state. | `docs/archive/agents/decomposition-01-02-completion.md` |
| `agents/reference-localization.md` | Finished localization task. | Current locale resources |
| `agents/plan04-utility-retention.md` | Ditched plan. 04-A through 04-E3 are committed. 04-F through 04-K will not run. | `docs/archive/agents/docs-archive.md` |

## Dangling References

These archived items reference files that never entered the repository:

- `0.2.2.cont.md` references `docs/0.2.2.md`. The file does not exist.
- `roadmaps/profile_timeline_roadmap.md` references `docs/roadmaps/profile_ui_roadmap.md`.
  The file does not exist.

## Rules

- Keep an archived item unchanged. Record a correction in a maintained page instead.
- Do not link an archived item from a current page as a requirement.
- Delete an archived item only when it has no historical value and no unrecovered
  requirement.
