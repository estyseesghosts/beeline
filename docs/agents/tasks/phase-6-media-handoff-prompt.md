# Next-agent prompt — Push Phase 3B and Phase 5, then finish all of Phase 6 of the Beeline 0.4.0 plan (media and Photo Grid)

The authoritative spec is `docs/beeline_0.4.0.md`, section "Phase 6 — media and Photo Grid" (slices 6A, 6B, 6C).
Read the plan's "Rules for every slice", "Execution map", and the "Adaptive floating navigation direction" section first.
Source, tests, and Git outrank every plan and completion claim.

Finish Phase 6 in its entirety: all three slices, each gated and committed, plus records. Do not stop after one slice
unless a stop condition below applies.

## Step 0. Push the existing commits (the user has authorized this push)

The user asked for the local commits to be pushed. This is the only push you are authorized to make.

1. Run `git status -sb` and `git log --oneline origin/main..HEAD`. Expect `main` ahead of `origin/main` by 6 commits:
   `cbbbdf13` (3B), `0a97b557` (5A), `40627a58` (5C), `5cdeae19` (5B), `5ac07d5e` (5D), `fc058f55` (records).
2. If the list differs, stop and report the difference. Do not push commits you cannot account for.
3. Run `git push origin main`. Never force-push. If the push is rejected, fetch, inspect, and report instead of rebasing blindly.
4. Do not push again after Phase 6 unless the user asks.

## Required reading (AGENTS.md)

`docs/agents/workflow.md`, `agent-control.md`, `engineering-rules.md`, `documentation-rules.md`,
`operation-rules.md`, `importantdocs/writing_style.md`, `docs/agents/handoff.md`, and the ownership pages
for media, Photo Grid, posts, and shell in `docs/agents/README.md`. Read `docs/agents/tasks/beeline-0.4.0.md`
(the completed Phase 3B/5 state) before you rewrite it.

## Subagents

- Use only the project agents `finder` (read-only tracing and sweeps) and `implementer` (bite-sized work packages).
- Keep subagent prompts short and specific: files, deliverable, non-goals. Never use general-purpose agents.
- You own design, ADB, Gradle, verification, and Git. Subagents never run ADB, Gradle, or Git.

## Current state (verify with Git; do not trust this list over it)

- Branch `main`, HEAD `fc058f55` before the push.
- Phase 3B and Phase 5 (5A, 5C, 5B, 5D) are done. The active task file `docs/agents/tasks/beeline-0.4.0.md` describes
  that finished state. Rewrite it for Phase 6 when you start; do not append.
- Unrelated untracked captures, scripts, logs, and prompt files exist. Leave them. Stage only reviewed paths with
  explicit `git add <path>`.
- Useful helpers from Phase 5: `PostPendingLookup` / `LocalPostPendingLookup` (pending post-action state),
  `PostContentPolicy.kt` (`isPostContentVisible`, shared by row and Photo Grid detail), `ui/motion` (motion and haptic authority).

## Gates to verify before starting

- **6C depends on 1C** (Phase 1 visibility defects). Confirm from source and Git history. 6A and 6C run in parallel in the
  plan, but you work sequentially in this one tree. Suggested order: 6A, 6C, 6B (6B needs 6A).
- If 1C is unmet, stop and report. Do not implement it silently.

## Slices (one behavior, one reviewable diff, one commit each)

### 6A. Preserve and strengthen thumbnail-to-viewer ownership

- Files: `ui/media/PostMediaCarousel.kt`, `MediaTransitionState.kt`, `MediaTransitionRegistryExtensions.kt`,
  `MediaViewerTransitionState.kt`.
- Use account/session/post/media identity, never list position. Validate source membership before reveal, especially
  sensitive media. Release registry entries on row disposal and viewer end; reject a stale close from an older viewer owner.
  Reserve known attachment dimensions.
- Tests: `ui/media/PostMediaCarouselTest.kt`, `MediaTransitionStateTest.kt`, `ui/SinglePostScreenTest.kt`. Cover hidden content,
  source removal, reorder, account replacement, rotated window, and reduced motion. Keep the old carousel shape and the
  current open/close path.
- Result: no old-account image survives a new session or appears behind a hidden cover.

### 6B. Tune viewer drag, zoom, and safe return (after 6A)

- Files: `ui/media/MediaViewerScreen.kt`, `MediaViewerTransitionLayer.kt`, `ZoomableMediaImage.kt`, `MediaViewerChrome.kt`.
- Preserve zoom priority over drag/paging and the existing velocity-plus-distance settling rule. Fade the backdrop with
  unzoomed drag. Return to the registered thumbnail if it remains valid; otherwise fade. Keep reduced-motion direct
  manipulation without decorative overshoot. Keep the video playback owner explicit if video behavior changes; add no
  autoplay or pooling by default.
- Tests: `ui/media/MediaViewerScreenTest.kt` and platform gesture tests. Cover zoomed drag, horizontal page, missing return
  source, rapid dismiss/reopen, lifecycle interruption, and sensitive media.
- Result: the viewer returns safely without a flash or wrong-item handoff. The known intermittent viewer timing test
  (`MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`) is stabilized only if you reproduce it
  during this slice.

### 6C. Make Photo Grid discovery dense and detail calm (after 1C)

- Files: `ui/photogrid/PhotoGridScreen.kt`, `PhotoPagerSizing.kt`, `ui/posts/SinglePostScreen.kt`,
  `ui/shell/AppLargeDetailPane.kt`, `ui/media/PostMediaCarousel.kt`.
- Keep the image-first grid, the focused image visible with supporting detail, account-scoped selection, and the current
  known/unknown aspect fallback. Apply the same reveal rule to grid thumbnails and details (reuse `isPostContentVisible`).
  Keep Photo Grid feed selection and tags independent of Home.
- Tests: `ui/photogrid/PhotoGridScreenTest.kt`, `ui/SinglePostScreenTest.kt`, and wide detail tests. Cover 1:1, 4:5, 16:9,
  taller media, unknown dimensions, multiple photos, empty feed, failed page, and account change.
- Result: compact and wide detail honor the current photo sizing tests. Hidden media never serves as a transition source.

## Per-slice procedure

1. At the start create `logs/YYMMDD-HHMMSS.txt` (goal, slices, files, risks). Rewrite `docs/agents/tasks/beeline-0.4.0.md` for Phase 6.
2. Inspect `git status`, recent commits, and the diff. Preserve unrelated changes.
3. Add a characterization test before any risky extraction. Implement, then run the smallest named test first, then the
   relevant Compose suites. Run lint after any contract, adapter, or security change.
4. Run the full CI-parity gate before each commit that changes code (see `engineering-rules.md#verification`):

   ```text
   python -m unittest discover -s tools/tests
   python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
   .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
   ```

   Use `GRADLE_OPTS=-Dorg.gradle.daemon=false`, closed stdin, and an explicit timeout. Recheck for external Java or Gradle
   activity first. Do not edit source during a gate run.
5. Update the affected `docs/wiki/` and `docs/agents/` ownership pages in the same slice. Rewrite `docs/agents/handoff.md`.
   Record remaining issues in `logs/BUGS.txt`.
6. Inspect the final diff, stage only that slice's files, and commit the slice with its records. One commit per slice.
   End commit messages with the attribution line the harness gives you.
7. Runtime check on `emulator-5554` (API 37) for UI slices: install the debug APK, launch `me.foxtails.palustris/.MainActivity`,
   capture to `logs/`. Check light, dark, and pure-black themes, 200% text, compact and wide, RTL, and reduced motion where
   the emulator allows. Use `tools/scripts/adb_*.py`. Reset any `wm size` or density override afterward.

## Known baseline (do not count as new)

- `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check` exits 1 at the clean base
  with four `function-complexity-growth` regressions: `DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`,
  `ProfileLargePresentation.dock`. Your slices must add no new finding. Fixing these four is out of scope unless a slice touches them.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`,
  `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`, and `MastodonIntegrationTest` cancellation.
  Rerun alone and record in `logs/BUGS.txt`.
- Tests that leave a gated deferred pending hang `runTest`; a killed Gradle run then reports "daemon disappeared unexpectedly".
  Complete every gate at the end of each test.
- Windows: use `MSYS_NO_PATHCONV=1` with `//sdcard/...` for adb in Git Bash. Use `shell screencap` plus `pull`, not `exec-out`.
  Edit mixed LF/CRLF or UTF-8 files with `encoding='utf-8', newline=''`.
- The emulator has no signed-in account. If it still has none, record device behavior as unverified rather than mocking it.

## Invariants

- One mutable state, one owner. Extend existing owners; do not invent a replacement architecture or a second modal authority.
- Shared domain and generic Compose stay protocol-neutral. Capability differences go through `domain/ServerCapabilities.kt` and adapters.
- Use `ui/motion` as the only motion authority. Respect system animator scale 0.
- Keep effective 48 dp targets, truthful semantics, and account or session scoping of all transient state.
  No private or hidden thumbnail before reveal.
- Product text says **Beeline**. Preserve Palustris compatibility identifiers.
- No persisted-format migration, new dependency, new ktlint baseline exemption, weakened test, or tolerated audit finding.
- Never log secrets or full API responses.

## Stop conditions

- The 1C dependency gate is unmet.
- Two failed fixes for one root problem.
- A slice needs an unapproved dimension, a new preference, or a protocol or storage change. Ask the user.
- Never substitute mocks for live checks. Record unverified items instead.

## Unverified items to record

API 29 instrumentation, physical devices, TalkBack, signing, live Misskey and Mastodon media behavior, real RTL locale,
haptic feel, and any device check that needs a signed-in account.

## Deliverables

- Push of the six existing commits (Step 0), reported with the resulting `origin/main` hash.
- Three slice commits (6A, 6C, 6B), each gated and with records.
- `docs/agents/tasks/beeline-0.4.0.md` and `docs/agents/handoff.md` rewritten for the final Phase 6 state.
- Wiki and ownership pages current; `logs/` entry and `logs/BUGS.txt` updated.
- Final report: commit hashes, gate results, emulator evidence paths, and every unverified item with its reason.
  Open follow-ups from earlier phases stay recorded in the task file: 200% font-scale tests for 3C1, 3C2 and 4E1, and the
  implicit 3A spacing scale.
