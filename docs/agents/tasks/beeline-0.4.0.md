# Beeline 0.4.0 task state

## Status

Phase 3B and Phase 5 are pushed (`origin/main` at `fc058f55`). Phase 6 is in progress: 6A is done in the commit titled "Keep hidden media out of thumbnail-to-viewer transitions (6A)". 6C is done in the commit titled "Cover collapsed content warnings in Photo Grid tiles and detail (6C)". 6B is done in the commit titled "Fade the viewer backdrop with drag and fade out without a return source (6B)". Phase 6 is complete.

| Slice | State |
| --- | --- |
| 6A thumbnail-to-viewer ownership | done |
| 6C dense Photo Grid, calm detail | done |
| 6B viewer drag, zoom, safe return | done |

## Open items

- 6B gesture-level zoomed-drag behavior, rapid dismiss/reopen, and lifecycle interruption are unverified on a device; a Robolectric zoomed-swipe test did not reproduce zoom and was dropped.

- Device checks need a signed-in account: Repost / Quote choice (including Back dismissal), pending dimming, bubble spacing, 200% text, themes, compact and wide layouts, haptic feel, sensitive-cover viewer behavior, thumbnail return.
- 200% font-scale tests for 3C1, 3C2 and 4E1; the implicit 3A spacing scale.
- `architecture_audit.py --check` fails at the clean base with four `function-complexity-growth` regressions (`DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`, `ProfileLargePresentation.dock`). Phase 6 must add none.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`, `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`, `MastodonIntegrationTest` cancellation.
