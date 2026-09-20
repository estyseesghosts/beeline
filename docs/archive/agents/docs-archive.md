# Objective

Stop Plan 04 and remove every completed task file from the active tasks folder. Keep every pointer consistent.

**This task was larger than one safe implementation slice. The owner waived slicing for this docs-only work. One commit holds the full archive.**

# Invariants

- Keep an archived item unchanged. Record corrections in maintained pages only.
- Use Beeline in user-facing text. Keep the internal codename out of user-facing content.
- Do not force-add git-ignored planning material under `docs/decomposition_3/`.
- Ditching the plan does not revert the committed 04-A through 04-E3 slices.
- Unrelated worktree changes stay intact and uncommitted.

# Decisions

- Plan 04 is ditched. Slices 04-A through 04-D and 04-E1 through 04-E3 are committed. Slices 04-F through 04-K will not run. Their unbounded owners stay open. A later task claims them when an owner needs them.
- Every task file below moves to `docs/archive/agents/` unchanged, including this file. The active `tasks/` folder keeps only `_template.md`.
- The paused localization task moves as the durable record through slice 4. Resume needs a new task and a new slice plan.
- The gradle task moves as the complete record. `AGENTS.md`, `build-debug.bat`, and `build-release.bat` in the tree are the authority. The commit-on-request step never ran and is dropped.

# Completed

- Plan 04 moves to the archive unchanged. The handoff, the inventory, the agent index, and the archive index point at the archive task. Commit `b0d3ddd`.
- Every remaining task file moves to `docs/archive/agents/`: the 01/02 completion, the Plan 03 gate partials, the Plan 03 protocol record, the gradle record, the localization record through slice 4, the S1 record, the P1 record, the Q1 record, the T1 record, the Liked tab, the Featured tab, the wide-detail interaction fix, the wide-detail photo sizing, the icon replacement, and this archive record. The handoff, the ownership pages, the acceptance matrix, the inventory, the agent index, and the archive index point at the archive paths. This commit holds the full archive and this record.

# Current slice

None. This task is complete.

# Files involved

- `docs/archive/agents/` (15 moved task files, unchanged)
- `docs/agents/handoff.md`
- `docs/agents/documentation-inventory.md`
- `docs/agents/README.md`
- `docs/agents/app-shell-ownership.md`
- `docs/agents/protocol-and-session-ownership.md`
- `docs/agents/decomposition-01-02-acceptance-matrix.md`
- `docs/archive/README.md`
- `logs/260919-205251.txt` (local worklog, git-ignored)

# Verification

- `git status` shows only the slice files staged or moved.
- A search for `docs/agents/tasks/` in maintained pages returns only `_template.md`, this record path updated to the archive, and the agent-index rule.
- A search for `tasks/plan04`, `tasks/profile`, `tasks/wide-detail`, `tasks/svg`, `tasks/localization`, `tasks/gradle`, `tasks/plan03`, `tasks/decomposition-01-02`, `tasks/palustrisapp`, `tasks/ui-package`, `tasks/q1`, `tasks/t1-test` in maintained pages returns only historical archive references.
- No test run. This change moves documentation only.

# Next

No open docs work. The owner picks the next feature task. Start it from `docs/agents/tasks/_template.md`.

# Blockers

- Device, live-server, and signed-release checks stay unverified. This task does not cover them.

# Last safe commit

The commit that contains this record. Run `git log -1 --oneline`.
