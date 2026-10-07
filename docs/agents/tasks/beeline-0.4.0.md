# Beeline 0.4.0 task state

## Status

Phase 3B and Phase 5 are pushed (`origin/main` at `fc058f55`). Phase 6 is in progress: 6A is done in the commit titled "Keep hidden media out of thumbnail-to-viewer transitions (6A)". Next slice: 6C, then 6B (needs 6A).

| Slice | State |
| --- | --- |
| 6A thumbnail-to-viewer ownership | done |
| 6C dense Photo Grid, calm detail | next (gate 1C met: `9e977dea`, `71c6f7de`) |
| 6B viewer drag, zoom, safe return | after 6C |

## Open items

- Device checks need a signed-in account: Repost / Quote choice (including Back dismissal), pending dimming, bubble spacing, 200% text, themes, compact and wide layouts, haptic feel, sensitive-cover viewer behavior, thumbnail return.
- 200% font-scale tests for 3C1, 3C2 and 4E1; the implicit 3A spacing scale.
- `architecture_audit.py --check` fails at the clean base with four `function-complexity-growth` regressions (`DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`, `ProfileLargePresentation.dock`). Phase 6 must add none.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`, `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`, `MastodonIntegrationTest` cancellation.
