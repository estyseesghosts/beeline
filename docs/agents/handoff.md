# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current boundary

Slice 1C is complete across 1C-a `ec12ed1`, 1C-b `6b33e3d`, and 1C-c from base `6b33e3d`. This sub-slice adds parity and edge tests, honest test names, and records. Do not stage or commit.

## Next steps

Continue with 1B5-M, 1B5-K, 1D3 adapter, 1D3 UI, 1E1, and 1E2. Muted-word data is deferred and requires maintainer approval plus independent Mastodon and Misskey semantics review.

Awaited input: decision-4 maintainer UI prototype only.

## Hygiene

Never stage `docs/beeline_0.4.0.md`, `docs/260923_current_state.md`, `docs/*.png`, `tools/scripts/*`, `.opencode/*`, `logs/*`, or pre-existing dirty files. Preserve staged `docs/classic_navigation.md` and all unrelated worktree changes.

## Device state

Emulator: FOLDED, Home, LTR, font scale 1.0, Default theme, rotation 0. Animator settings remain at their original values. No device check was performed for this sub-slice. Physical-device, API 29, RTL, TalkBack, and signed-release checks remain unverified.

## Last safe boundary

Base HEAD: `6b33e3d`. The 1C-c changes remain uncommitted and unstaged.
