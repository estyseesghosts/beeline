# Objective

Redesign the composer as floating bubbles over scrolling cards, as `docs/composer.md` describes. A reply or quote takes the audience of its target.

# Invariants

- The owner, editor state, drafts, and publish flow stay as they are.
- The existing test tags stay: `composer_post`, `composer_option_local_only`, `composer_option_schedule`, `composer_option_quote_policy`, `composer_poll_toggle`.
- A bubble the server does not support is absent. It is never disabled to hint at a missing feature.
- Later entries follow the audience and options of the first entry.

# Decisions

- The four open questions of `docs/composer.md` follow the design defaults: feed media row as the image reference, reply avatar on the right, remove circle kept on entries after the first, quote policy as a row 2 pill.
- The thread line runs in the avatar column of the upper card. It runs down to the next card.
- Row 2 sits below row 1. The hashtag chips sit directly above row 1.
- The back bubble keeps the description "Close composer" so the guarded-close tests and TalkBack stay stable.
- The new bubbles reuse existing strings. No locale folder changed.

# Completed

- Slice 1, audience rule: `bef96a5b`.

# Current slice

Slice 2, the visual redesign: bubbles, cards, reply and quote cards, media tile row, wiki.

# Files involved

`ui/composer/`: `ComposerSurface.kt`, `ComposerBody.kt`, `ComposerEntryRow.kt`, `ComposerMediaStrip.kt`, `ComposerOptionsRow.kt`, `ComposerPollEditor.kt`, `ComposerBubbles.kt`, `ComposerTargetCards.kt`, `ComposerAudienceLabels.kt`, `ComposerIcons.kt`. `docs/wiki/ui-and-navigation.md`.

# Verification

Composer unit and Compose tests pass. The full local gate is green: unit tests, lintDebug, ktlintCheck, assembleDebug, assembleRelease. The architecture audit exits 1 on findings that predate this work; the slice adds none.

# Next

Device checks from `docs/composer.md`: row 1 width on a 360 dp phone with the poll circle, card growth with the keyboard open, bubbles over long text at both scroll ends.

# Blockers

None.

# Last safe commit

`bef96a5b` Make a reply or quote take the audience of its target.
