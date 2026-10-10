# Objective

Ship related hashtags as `docs/related-tags.md` describes: the setting, combined results, suggestions, trending hashtags, popular accounts, related chips, and composer autocomplete. All six slices are done.
The catalog has 74 groups and 1,814 members, not the 62 groups and 1,370 members that the plan lists. All 74 ship.

# Invariants

- The app reads only `app/src/main/assets/hashtag-catalog.json`. No network access for the catalog.
- The setting is on by default. A stored file without the key reads `true`.
- Mastodon accepts 3 extra hashtags. Misskey-family servers accept 10.
- Only the typed hashtag fragment goes to the server for suggestions.
- Cursors stay bound to the extra set.

# Decisions

- The plan counts are stale. Tests assert 74 groups and 1,814 members.
- Code lives in `domain/hashtags/` and `data/hashtags/`.
- The head ranks first in `HashtagExpander`. Its first language counts as covered.
- D1 to D5 follow the recommended defaults.
- Related chips show whenever `relatedTags` is not empty. The "Includes" line shows only when `combinedTags` is not empty.
- A failed or empty discovery list keeps the old prompt and shows no error.
- Composer chips treat `:shortcode:` emoji and Unicode emoji as hashtag boundaries, like posts do.

# Completed

- Slice 1 (catalog, rules, setting): 2b551c12.
- Slice 2 (combined results): c8771358.
- Slice 3 (server discovery, suggestion service): 0829f3d4.
- Slice 4 (Hashtags tab): 3df65066.
- Slice 5 (Profiles tab): 3901c0f8.
- Slice 6 (composer autocomplete): the commit "Suggest hashtags in the composer while the user types one".

# Current slice

None. The task is complete.

# Files involved

See `docs/agents/related-hashtags.md`.

# Verification

Each slice ran focused tests and the full local CI-parity gate. Slices 4 and 5 were checked on an emulator signed in to a Mastodon account: trending, suggestions, tap to search, and popular accounts.
Not verified on a device: a Misskey account, related chips for a catalog hashtag, and the composer chips.

# Next

Release gate: the owner reviews the catalog before a release. Review at least the largest groups and every hashtag in `amb`. Until then treat a build as a test build.

# Blockers

None.

# Last safe commit

The slice 6 commit.
