# Beeline 0.4.0 task state

## Status

Phase 3B, Phase 5, and Phase 6 are pushed (`origin/main` at `efc9a3eb`). Phase 7 is complete locally and pushed once at its end because the user asked for it: 7A and 7B ("Verify restored composer targets and close orphaned profile editors (7A, 7B)"), 7C ("Add one trigger-to-surface presentation contract for composer, profile editor, and share (7C)"), 7D ("Render Search as idle, entry, and results bubbles in one field (7D)"), and 7E ("Reuse bubble style and motion on sign-in without touching authentication (7E)"). Next is Phase 8 in `docs/beeline_0.4.0.md`.

| Slice | State |
| --- | --- |
| 7A composer open-overlay restoration | done |
| 7B profile editor restoration | done |
| 7C trigger-to-surface contract | done |
| 7D Search three visual states | done |
| 7E sign-in bubbles | done |

## Decisions

- 7A: reply and quote target ids live in the saveable `ComposerEditorState` with the account and session revision they were chosen under. `ComposerOwner.bindSession` keeps verified targets, drafts the editor (keeping its reply link) for the same account under a new revision, and clears it for another account. The composer overlay is still never restored. `requestNew` keeps the audience of a restored reply.
- 7B: a restored `EditProfile` overlay key closes when no profile editor is open at first composition or account change. The profile patch uses the base the draft started from (`editorDraftBase`).
- 7C: Back and scrim-tap dismissal skip the reverse animation; the contract has no back handler. A followers-only or direct post asks before Copy or Share.
- 7D: focus is not saved across rotation; the query is.

## Device evidence

None for Phase 7. Compose/Robolectric tests only. Unverified on a device: trigger-to-sheet motion, the share card scale, Search bubble to IME transition, sign-in keyboard movement and password managers, browser callback return, pure-black theme, 200% text on hardware, wide layout, RTL, reduced motion, process-death restoration of the composer and profile editor.

## Open items

- 6B gesture-level zoomed-drag behavior, rapid dismiss/reopen, and lifecycle interruption are unverified on a device.
- Device checks still open: Repost / Quote choice (including Back dismissal), pending dimming, bubble spacing, 200% text, themes, compact and wide layouts, haptic feel, sensitive-cover viewer behavior, thumbnail return.
- 200% font-scale tests for 3C1, 3C2 and 4E1; the implicit 3A spacing scale.
- `architecture_audit.py --check` fails at the clean base with four `function-complexity-growth` regressions (`DestinationChipRow`, `LargeBottomDock`, `LargeScreenShell`, `ProfileLargePresentation.dock`). See the Phase 7 gate result in `logs/BUGS.txt`.
- Known flakes, each passing alone: `NotificationsViewModelTest.dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint`, `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`, `MastodonIntegrationTest` and `MisskeyApiTest` cancellation.
