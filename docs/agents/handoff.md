# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state. This file is the next-agent pointer.

## Current slice: 0B measure approval records

This is a documentation-only slice. Measure approval completes 0B with this commit. Base HEAD before this slice is `4fcaecd`.

The maintainer approved emulator-observed prototype baselines: compact capsule and targets, 36 dp inset, wide rail targets, chip row heights, detail split, and IME anchors. The values are Pixel Fold emulator observations at 420 dpi. Physical-device verification remains unverified. The IME navigation-capsule observation is failure evidence under decision 3, not an approved design value; packet 4E1 will fix it.

Three measure rows remain pending for lack of device evidence: media dismissal threshold (media viewer not captured), physical-left caret (RTL blocked), and physical-bottom-right wide action (dock built in 4C).

## Next steps

1. Commit this slice.
2. Start 1B characterization. Inspect `FeedViewModelRequestTest.failedPageKeepsCursorForARetry` and `MastodonIntegrationTest.kt` first. Add independent cancellation and failed-next-page-retains-rows evidence only if missing.
3. Start 1C repair after the maintainer's quote-level `Remove` decision.
4. Start 1B5-M.
5. Start 1B5-K.
6. Start 1D3 adapter.
7. Start 1D3 UI.
8. Start 1E1.
9. Start 1E2.
10. Run the full `test assembleRelease` gate.

Awaited inputs: maintainer UI prototype for decision 4; quote-level `Remove` decision for 1C.

## Hygiene rules

Never stage `docs/beeline_0.4.0.md`, `docs/260923_current_state.md`, `docs/*.png`, `tools/scripts/*`, `.opencode/*`, `logs/*`, or any pre-existing dirty or staged file. Preserve all unrelated worktree changes.

## Device state

The emulator is FOLDED, Home, LTR, font 1.0, theme Default, rotation 0, with original animator settings.
