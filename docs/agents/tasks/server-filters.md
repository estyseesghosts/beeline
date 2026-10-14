# Objective

Support server-side content filters, word mutes, account mutes, account blocks, and boost mutes on Mastodon-compatible and Misskey-family servers. A user chooses whether matching posts collapse behind a notice or leave the feed.

# Invariants

- One decision owner: `PostFilterPolicy`. Surfaces pass their own `FilterContext`.
- Protocol JSON stays in adapters. Filter rules reach the UI as protocol-neutral values.
- A filtered post never shows its body before the viewer chooses "Show anyway".
- New strings exist in English, German, Simplified Chinese (`values-zh`), and Traditional Chinese (`values-zh-rTW`) only. The user asked for this.

# Decisions

- Simple Misskey mutes store `/escaped/i` for a case-insensitive match and a plain keyword for a case-sensitive match. Bracket expansion is not used.
- Misskey `mutedWords` collapse and `hardMutedWords` remove. Beeline matches them on the client, as Misskey web does.
- Mastodon v2 filters on 4.0 and later. Pleroma and Akkoma use v1 filters, matched on the client.
- Account mutes default to Collapse, blocks to Remove. The user can choose both for each kind.
- "Muted boosts" covers the per-account boost mute and boosts of posts by muted or blocked accounts.

# Completed

- S1 filter model and presentation: commit "Collapse or remove posts that a server filter matches".

# Current slice

S2: Mastodon filter management (v2 CRUD, v1 for Pleroma and Akkoma) and client matching of v1 filters.

# Files involved

`domain/PostFilter.kt`, `data/mastodon/MastodonFilterMapper.kt`, `ui/posts/PostFilterPresentation.kt`, `ui/settings/`.

# Verification

S1: focused tests (`MastodonFilterResultTest`, `PostRowFilterTest`, `PostRevealOwnerTest`, `NotificationJsonCodecTest`, `NotificationDeliveryPlannerTest`) and the full gate.

# Next

S2, then S3 Misskey word mutes, S4 account mutes and blocks, S5 boost mutes.

# Blockers

None.

# Last safe commit

4eac6fb0 Page through the images of a post inside a grid tile
