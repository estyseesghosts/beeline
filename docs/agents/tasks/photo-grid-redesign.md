# Objective

Implement `docs/photo-grid-redesign-plan.md`: card visuals, favorite button, compact-wide rail underlap, and press-and-hold quick-view.

# Invariants

Short tap opens the post. No control sits under the rail. Quick-view acts only for the current account and session revision.

# Decisions

- Slices 1 to 4 landed in one commit; slice 5 (drag and release) is not done.
- 16:9 clamp applies to the image area.
- Collapsed content-warning cards show the warning in the image area and no footer caption, to avoid duplicating covered text.
- Repost confirms inline in the quick-view because the existing confirmation renders only inside post rows.
- Report has no direct entry; the share sheet keeps it.

# Completed

- Slices 1 to 4.

# Current slice

None.

# Files involved

ui/photogrid/PhotoGridScreen.kt, PhotoQuickView.kt, ui/shell overlay, bubble host, search destination; PhotoGrid tests.

# Verification

Photo Grid unit tests pass; emulator (Pixel 10 Pro Fold cover screen, compact-wide): cards, underlap, favorite toggle, long-press quick-view, share sheet.

# Next

Optional slice 5; verify on a phone-width device and an expanded layout.

# Blockers

None.

# Last safe commit

29b7a484 Keep the compact pill and action in place when the contextual caret hides
