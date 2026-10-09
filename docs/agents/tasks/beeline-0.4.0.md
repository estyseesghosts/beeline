# Beeline 0.4.0 task state

## Status

Phases 3B, 5, 6, and 7 are pushed (`origin/main` at `cfbd6ff3`). Phase 8 is in progress: 8A and 8B are done. Next is 8C.

| Slice | State |
| --- | --- |
| 8A one emoji tile | done |
| 8B picker identity, grouping, account scope | done |
| 8C compact pop-out and full picker | next |

## Decisions

- 8A: `EmojiPickerTile` replaces `PickerCell`. `EmojiCatalogViewModel` owns `pendingPins` and `pinFailed`. The pinned state follows saved preferences, never the request. The grid takes `EmojiPinState` and `EmojiPreferenceActions` so its parameter count stays at the audit baseline.
- 8B: `EmojiCatalogState.scope` is a per-owner token and the grid keys its pin confirmation on it. `EmojiRecents` is the only holder of recents. It is composition-local and shared by both picker sizes. Recents stay unpersisted.
- 7A to 7E decisions are recorded in the Phase 7 commits and `docs/wiki/ui-and-navigation.md`.

## Device evidence

8A on emulator-5554 (Mastodon account, debug build, light theme, 100% font): composer picker opens, long press shows the pin confirmation, confirm moves the emoji to Favorite, and a forced write failure (read-only `no_backup`) shows the error line without a pinned tile or a crash. 8B on the same device: the persisted pin survives an app update. Only one account is signed in, so account replacement is unit-tested only. Unverified: dark theme, 200% font scale, reduced motion, TalkBack action, account switch on a device, API 29.

## Open items

- 6B gesture-level zoomed-drag behavior, rapid dismiss/reopen, and lifecycle interruption are unverified on a device.
- Device checks still open: Repost / Quote choice (including Back dismissal), pending dimming, bubble spacing, 200% text, themes, compact and wide layouts, haptic feel, sensitive-cover viewer behavior, thumbnail return.
- 200% font-scale tests for 3C1, 3C2 and 4E1; the implicit 3A spacing scale.
- `architecture_audit.py --check` fails at the clean base with four `function-complexity-growth` regressions (`DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`, `ProfileLargePresentation.dock`). See the Phase 7 gate result in `logs/BUGS.txt`.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`, `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`, `MastodonIntegrationTest` and `MisskeyApiTest` cancellation.
