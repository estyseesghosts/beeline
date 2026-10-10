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

# Completed

- Slice 1 (catalog, rules, setting) — commit 2b551c12.

# Current slice

Slice 2: combined hashtag results (implemented, gate pending).

# Files involved

See `docs/agents/related-hashtags.md`.

# Verification

Slice 1: focused tests, then the CI-parity gate. See the commit.

# Next

Slice 3: server discovery and the suggestion service.

# Blockers

None.

# Last safe commit

`b8fc99c6` Release 0.2.13: set version and changelog.
