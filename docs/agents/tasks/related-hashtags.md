# Objective

Ship related hashtags as `docs/related-tags.md` describes: the setting, combined results, suggestions, trending hashtags, popular accounts, related chips, and composer autocomplete.
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

# Completed

- Slice 1 (catalog, rules, setting) — commit 2b551c12.
- Slice 2 (combined results) — commit c8771358.
- Slice 3 (server discovery, suggestion service) — commit 0829f3d4.

# Current slice

Slice 4: Hashtags tab (implemented; the commit that carries this record is the slice 4 commit).

# Files involved

See `docs/agents/related-hashtags.md`.

# Verification

Slice 1: focused tests, then the CI-parity gate. See the commit.

# Next

Slice 5: Profiles tab (popular accounts in the blank state of `AccountSearchResults`). Slice 6: composer autocomplete.

# Blockers

None.

# Last safe commit

`0829f3d4` Add server discovery reads and the hashtag suggestion service.
