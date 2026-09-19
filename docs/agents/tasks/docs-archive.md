# Objective

Stop Plan 04 and remove completed task files from the active tasks folder. Keep every pointer consistent.

**This task is larger than one safe implementation slice.**

# Invariants

- Keep an archived item unchanged. Record corrections in maintained pages only.
- Keep the repository working after each slice.
- Use one slice, one behavior, one commit.
- Stage only files that belong to the slice. Preserve unrelated worktree changes.
- Do not force-add git-ignored planning material under `docs/decomposition_3/`.
- Use Beeline in user-facing text. Keep the internal codename out of user-facing content.
- Ditching the plan does not revert the committed 04-A through 04-E3 slices.

# Decisions

- Plan 04 is ditched. Slices 04-F through 04-K will not run. The owner recorded this on 2026-09-19.
- The Plan 04 task file moves to `docs/archive/agents/plan04-utility-retention.md` unchanged. Its `in progress` header stays as history. The inventory marks it ditched.
- Completed task files move to `docs/archive/agents/` one slice at a time. Each move updates the inventory, the agent index, and the archive index in the same slice.
- The paused localization task and the gradle task need an owner decision before they move. Slice 4 records the decision.

# Completed

- Slice 1 — Plan 04 moves to the archive unchanged. The handoff, the inventory, the agent index, and the archive index point at the archive task. Commit holds this record and the worklog `logs/260919-205251.txt`.

# Current slice

Slice 1 is done. No slice is in progress.

# Files involved

- `docs/agents/tasks/docs-archive.md` (this file)
- `docs/archive/agents/plan04-utility-retention.md` (moved, unchanged)
- `docs/agents/handoff.md`
- `docs/agents/documentation-inventory.md`
- `docs/agents/README.md`
- `docs/archive/README.md`
- `logs/260919-205251.txt`

# Verification

- `git status` shows only the slice files staged.
- A search for `plan04-utility-retention` in `docs/agents/` returns only historical references.
- No test run. This slice changes documentation only.

# Next

Slice 2: archive the completed profile, wide-detail, and icon task files. Candidates: `profile-liked-tab.md`, `profile-featured-tab.md`, `wide-detail-interaction-fix.md`, `wide-detail-photo-sizing.md`, `svg-icon-replacement.md`. Update the inventory, the agent index, and the archive index in the same commit.

Slice 3 candidates: `palustrisapp-decomposition.md`, `ui-package-migration.md`, `q1-static-analysis.md`, `t1-test-mirror.md`.

# Blockers

- Slice 4 needs an owner decision on `localization-string-extraction.md` (paused) and `gradle-no-daemon.md` (uncommitted config task).
- Device, live-server, and signed-release checks stay unverified. This task does not cover them.

# Last safe commit

The commit that contains this record. Run `git log -1 --oneline`.
