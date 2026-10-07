# Beeline 0.4.0 task state

## Status

Phase 3B and Phase 5 (5A, 5C, 5B, 5D) are complete and committed. Nothing is in progress.

| Slice | Commit |
| --- | --- |
| 3B haptic event map | `cbbbdf13` |
| 5A post content policy | `0a97b557` |
| 5C hashtag and link bubbles | `40627a58` |
| 5B Repost / Quote choice | `5cdeae19` |
| 5D optimistic action states | `5ac07d5e` |

## Open items

- Device checks need a signed-in account: Repost / Quote choice (including Back dismissal), pending dimming, bubble spacing, 200% text, themes, compact and wide layouts, haptic feel.
- 200% font-scale tests for 3C1, 3C2 and 4E1; the implicit 3A spacing scale.
- `architecture_audit.py --check` fails at the clean base with four `function-complexity-growth` regressions (`DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`, `ProfileLargePresentation.dock`). Phase 5 added none.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`, `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`.
