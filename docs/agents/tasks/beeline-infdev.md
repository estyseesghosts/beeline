# Objective

Deliver `docs/beeline_infdev.md` milestone by milestone. This task covers M0 (own-post actions in the share sheet) and M5 (server-side translation). M1 to M4 and M6 to M8 are not started.

# Invariants

- The share sheet opens from one path (`PostPopupOwner.open`). No second entry point.
- A row shows only when its capability is `Supported`. Unknown and Unsupported hide it.
- Translation is server-side only. There is no on-device fallback.
- Every deletion reaches every loaded surface through `PostProjectionCoordinator.forwardDeletedPost`.
- Translations live for the connected session in `PostTranslationOwner`, not in Compose state.

# Decisions

- Own-post detection is `Post.isOwnedBy(accountId)`: the row's author equals the account. A repost row carries the wrapped post's author, so no separate repost rule is needed.
- Capabilities: `ServerCapabilities.ownPosts` (delete, edit) and `ServerCapabilities.translation` (`TranslationCapability(status, publicOnly)`). Schema version is 9.
- Mastodon-API translation sets `publicOnly`, because the route answers public and unlisted posts only. Misskey sets no restriction.
- Delete is supported on both adapters. Edit stays `Unknown`, so the Edit row never shows until M3 sets it.
- Mastodon translate: 404 maps to `Unsupported("translate")`, 503 to `ResourceLimit("translate.busy")`. Misskey `UNAVAILABLE` maps to `Unsupported("translate")`.
- Notifications do not drop a deleted post's item yet. Deleted-post handling there is left for M3.
- `Post.language` is new (Mastodon `language`). Misskey notes carry none, so the Translate row shows for them whenever the server supports it.

# Completed

- M0 and M5 share one commit because the share sheet, capability schema and adapters changed together.

# Current slice

Finished. The next unit of work is M1 (polls) or M3 (edit), at the owner's choice.

# Files involved

- domain: `OwnedPost.kt` (`isOwnedBy`, `canTranslateTo`), `PostDeletion.kt`, `ServerCapabilities.kt`, `SocialModels.kt` (`Translation`, `Post.language`), `SocialSource.kt` (`translate`)
- data: Mastodon and Misskey sources, mappers and capability probes, `AccountFileStore.kt`
- ui/posts: `PostShareSheet.kt`, `PostShareRows.kt`, `PostPopupOwner.kt`, `PostTranslationOwner.kt`, `PostTranslationFooter.kt`, `PostRow.kt`, `SinglePostScreen.kt`
- ui sinks for deletion: feed, photo grid, search, profile, saved, thread
- wiring: `ConnectedSessionHost.kt`, `ShellOverlayHost.kt`, `PostProjectionCoordinator.kt`

# Verification

- Unit tests: `OwnPostTest`, `PostShareRowsTest`, `PostOwnActionsTest`, `OwnPostAndTranslationAdapterTest`, `PostShareSheetOwnPostTest`.
- Emulator `emulator-5554`, signed in to a mstdn.ca test account: own-post card shows Delete and Share only; Delete asks twice; a real delete removed the post from Home; Translate on another account's post showed "Translated from English by LibreTranslate" and Show original.
- Not checked live: Misskey-family translate and delete (dvd.chat), thread and profile removal after a delete, delete of a reply's parent count.

# Next

Run the dvd.chat checks above, then start M1 or M3.

# Blockers

- `architecture_audit.py --check` exits 1. Regressions predate this work, but `PostShareSheet.kt`, `ShellOverlayHost.kt`, `ServerCapabilities.kt` and both capability probes also grew. The baseline was not edited.

# Last safe commit

c46ec6b3 Fix video publish hang, bloated conversions, and viewer open frame; add duration badge and retry
