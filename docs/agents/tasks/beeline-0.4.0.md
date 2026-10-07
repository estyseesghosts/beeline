# Objective

Finish Phase 4D and 4E: one universal tab bar whose chips travel to both physical display edges,
and measured high-font clearance. Keep one navigator, one destination host, and the existing feature owners.

Status: audit and slices 1, 2, 2b, 3, and 4 complete. Remaining unverified items are listed under Limits.
Owner: orchestrator
Last reviewed: 2026-10-07
Authority: [AGENTS.md](../../../AGENTS.md), current source, tests, and Git.

# Invariants

- Feature owners keep chip selection, queries, paging, and scroll state. No global chip manager or visibility preference.
- Physical edges and clearance never follow layout direction. Scroll content owns final-item clearance; viewports stay full size.
- The caret sits in the first logical position. It moves to the right in RTL by decision (user, 2026-10-07).
  This supersedes the plan's "fixed physical-left caret" and the baseline's "physical-left caret (RTL blocked)".
- Compact-narrow, compact-wide, and tablet navigation geometry (4C) stays unchanged. No protocol, persistence, or account changes.
- Preserve unrelated work. Do not push. Do not add ktlint baseline exemptions.

# Completed

- Phases 4A–4C, including shared chip rows (`f7dc516`), compact-wide Profile (`c62e4e2`), and the compact-wide caret (`a04b3eb`).
  Verified history lives in Git. Phases 0–5 of the hardening plan are complete at `51455c5`.

# Audit (slice 0, source at `51455c5`)

| Gap | State | Evidence |
| --- | --- | --- |
| 1 Caret position | Done by decision | Logical-first is intended; source already matches. Docs and baseline need the wording fixed. |
| 2 Edge travel | **Done in slice 2** (was open) | Wide Home, Search, Photo Grid, Notifications, and Profile docks wrap `DestinationChipRow` in `absolutePadding(left, right = obstruction clearance)` plus `LargeBottomDock` 12 dp padding. The scroll viewport ends at the clearance boundary. Compact rows sit inside `CompactOverlayHorizontalPadding` (16 dp), and wide Search also caps the chip row at 520 dp. |
| 3 Stable IDs | **Done in slice 1** (was open) | `FilterChipEntry.key` defaults to `"$role:$label"`. Notifications (Mark-all, filters, Settings) and Photo Grid (timelines, hashtags, Add) pass no key. Mark-all changes label while confirming, so its key changes. Equal labels would crash `LazyRow`. Home, Search, and Profile already pass stable keys. |
| 4 4E1 numeric clearance | **Partly done in slice 3**: wide Search dock now rises above the IME (measured defect: dock hidden behind the keyboard in compact-wide at 100% and 200%); empty states clear the dock. Compact capsule 136/147 px not re-measured | Source uses the greater of IME and system-bar insets (`compactGlobalNavigationPositioningInsets`). The 136/147 px baseline failure is not re-measured. |
| 5 4E2 | **Done in slice 4 (emulator)** | At fontScale 2.0 in compact-wide with the vertical capsule, chip rows span the display, scroll beneath the capsule and caret, and rest the last chip clear. Home, Notifications, Photo Grid, Profile, and Search checked on `emulator-5554`. |
| 6 Font200 recapture | **Done in slice 4** | Six screens captured locally in `logs/s3/font200/` (gitignored). Not a physical device. |

# Slice plan

This task is larger than one safe implementation slice. Each slice is committed and gated before the next.

1. **Stable tab IDs.** Give Notifications and Photo Grid entries explicit stable keys. Tests assert the keys survive label change and duplicate labels.
2. **4D5 edge travel.** `DestinationChipRow` takes physical left and right resting insets. The chip viewport fills the display width.
   The viewport starts beside the inline caret (chips never scroll beneath it, which would steal taps) and reaches the far display edge; with the caret hidden it starts at the display edge. `contentPadding` keeps the resting first and last chips clear of the caret and the floating navigation,
   while the scroll path continues under both. Callers stop wrapping the row in side padding and pass the clearance instead.
   Compact rows pass the 16 dp overlay padding as a resting inset. Wide Search keeps the 520 dp field but not a 520 dp chip row.
   Tests cover wide Home, Search, Photo Grid, Notifications, and Profile in LTR and RTL.
3. **4E1.** Measure compact tab, filter, Search-field, and navigation bounds at `fontScale = 2f` with the IME open and closed.
   Derive numeric targets. Fix only measured failures. Final-item clearance stays inside scroll content.
4. **4E2.** Reconcile full-display chip travel with the six-target capsule at `fontScale = 2f` in wide layouts. Recapture the six font200 screens.

Non-goals: new dimensions without approval, feature-state moves, navigation geometry changes, backdrop blur.
Fail gates: two failed fixes for one root problem; unapproved geometry values; chips hidden or unreachable beside the capsule.

# Limits

Physical devices, TalkBack, API 29, signing, live accounts, a real RTL locale, and the compact-narrow capsule IME measurement (136/147 px) stay unverified. The emulator is `emulator-5554` (API 37).

# Last safe commit

`51455c5`; slice 1 is `Use stable keys for Notifications and Photo Grid chips`. Slice 2 is `Let chip rows travel to the display edge`. Slice 3 is `Lift the wide Search dock above the keyboard`. Slice 2b is `Let chip rows break out of the pane margin`:
the pane outer margin still clipped chips 16 dp inside the display, so the primary pane bleeds into it for chip destinations.
