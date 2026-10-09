# Beeline 0.4.0 task state

## Status

Phases 3B, 5, 6, and 7 are pushed. Phase 8 is complete locally and pushed once at its end because the user asked for it: 8A, 8B, and 8C. Next is Phase 9 in `docs/beeline_0.4.0.md`.

| Slice | State |
| --- | --- |
| 8A one emoji tile | done |
| 8B picker identity, grouping, account scope | done |
| 8C compact pop-out and full picker | done |

## Decisions

- 8A: `EmojiPickerTile` replaces `PickerCell`. `EmojiCatalogViewModel` owns `pendingPins` and `pinFailed`. The pinned state follows saved preferences, never the request. The grid takes `EmojiPinState` and `EmojiPreferenceActions` so its parameter count stays at the audit baseline.
- 8B: `EmojiCatalogState.scope` is a per-owner token and the grid keys its pin confirmation on it. `EmojiRecents` is the only holder of recents. It is composition-local and shared by both picker sizes. Recents stay unpersisted.
- 8C: `buildCompactEmojiChoices` fixes the compact order (selected, favorites, recents, post-specific, standard, server) with fixed limits. `reactionPickerSurface` moves the expanded picker to `ReactionPickerSheet` at font scale 1.5 or more, with less than 360 dp beside the anchor, or with a missing anchor. The sheet and the pop-out share one catalog, one recents holder, and one choice callback. `PostActionBubbleHost` keeps account binding through `ShellOverlayPresenter`.
- 7A to 7E decisions are recorded in the Phase 7 commits and `docs/wiki/ui-and-navigation.md`.

## Device evidence

emulator-5554 (one Mastodon account, debug build):
- 8A: composer picker opens, long press shows the pin confirmation, confirm moves the emoji to Favorite, and a forced write failure (read-only `no_backup`) shows the error line, no pinned tile, and no crash.
- 8B: a pinned emoji persists across an app update.
- 8C: the new build starts and loads Home with no app crash.

Not verified on a device: the reaction pop-out, the expanded picker, and the sheet fallback (this Mastodon account has no reaction mutation, so no Misskey account was available), account replacement, TalkBack custom action, Back order between the pin confirmation and the picker, API 29, reduced motion, 200% font scale with the picker open, pure-black theme. A dark, 200% font, animation-off start was begun but the emulator did not finish starting. These are Compose/Robolectric-tested only.

## Open items

- Phase 8: the reaction pop-out needs a Misskey-family account on a device. Check long-press, upward slide, expand, sheet fallback at 200% font, Back order, and account switch there.
- 6B gesture-level zoomed-drag behavior, rapid dismiss/reopen, and lifecycle interruption are unverified on a device.
- Device checks still open: Repost / Quote choice (including Back dismissal), pending dimming, bubble spacing, 200% text, themes, compact and wide layouts, haptic feel, sensitive-cover viewer behavior, thumbnail return.
- 200% font-scale tests for 3C1, 3C2 and 4E1; the implicit 3A spacing scale.
- `architecture_audit.py --check` fails at the clean base with five `function-complexity-growth` regressions (`DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`, `MediaTransitionImageCanvas`, `ProfileLargePresentation.dock`). See the Phase 7 gate result in `logs/BUGS.txt`.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`, `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`, `MastodonIntegrationTest` and `MisskeyApiTest` cancellation.
