# Objective

Deliver `docs/beeline_infdev.md` milestone by milestone. M0, M5, M1 (polls), M3 (delete and edit), M2 (quote interop) and M4 (local-only and scheduled posts) are done. M6 to M8 are not started.

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

# Decisions (M1, polls)

- Domain: `Poll(id, options, multiple, expiresAtEpochMillis, expired, votersCount, voted)`; `PollOption(text, votes, picked)`. `Post.poll` replaces `Post.pollOptions`.
- `PollRequest` carries a relative `expiresInSeconds` and `hideTotals`, so a saved draft never holds a past absolute time. Misskey sends `poll.expiredAfter` in ms; Mastodon sends `poll[expires_in]` (default one day) and refuses a poll together with media.
- `PostingCapabilities.poll: PollLimits?`. Mastodon reads `configuration.polls`, then Pleroma's `poll_limits`, then documented defaults. Misskey family is fixed at 10 choices of 50 characters.
- `SocialSource.votePoll(postId, pollId, choices)` returns the poll. Mastodon posts `choices[]` once; Misskey calls `notes/polls/vote` per choice, then refetches the note.
- Votes live in the session-scoped `PostPollOwner` (via `LocalPostPolls`), like translations. This deviates from the plan's `PostInteractionMutationOwner`: that owner has no per-surface seam for a new action, and a session overlay shows the same poll in every surface without touching each one. A copy of the post that already shows the account voted, with at least as many votes, wins over the overlay.
- Misskey sign-in now requests `write:votes` (`AccessScope.PollVote`). Misskey sessions made before this need signing in again to vote; the vote fails with the server's policy error until then. Mastodon `write` already covers voting.
- Drafts keep a poll on any entry (`PostDraftEntry.poll`). The notification cache keeps its frozen `pollOptions` array and adds `poll` beside it when the poll has an ID.
- Capability schema version is 10 (adds poll limits and the edit status).

# Decisions (M3, edit)

- `SocialSource.edit(id, EditPostRequest)` and `editSource(id)` replace `edit(id, text)`. A null field in the request means unchanged: the composer sends the text always, the warning only when it changed, the image list only when it changed, and the poll only when it changed.
- Capability: Mastodon API from version 3.5 or `editing` in `pleroma.metadata.features` (a server with a `pleroma` block is judged by that list alone). Misskey family from `notes/edit` in `POST /api/endpoints`. The endpoint probe adds one request to the Misskey probe for token sessions.
- Mastodon: `GET statuses/:id/source` for the raw text, `PUT statuses/:id` with `status`, `spoiler_text`, `media_ids[]`, `media_attributes[i][id|description]` and poll fields.
- Sharkey: the adapter reads the note again with `notes/show` and builds `notes/edit` from it, so visibility, local-only, reaction acceptance, reply, quote and channel survive. This is more current than the plan's cached post, and needs no new Post fields. A changed alt text is a separate `drive/files/update`.
- The composer opens at once on `ComposerNavigation.Edit` and fills when the source arrives. Edit mode hides the audience row, drafts, thread button, photo and poll toggle, and reads Save. Closing an edit drops it and never makes a draft. An edit editor is not restored after the process dies. A poll can be changed but not removed (an edit cannot take a poll away). New images cannot be added in an edit.
- An unchanged poll is never validated or sent. An ended poll's remaining length is below the server minimum, and that must not block a save.
- The saved post is kept in the session-scoped `PostEditOwner` (`LocalPostEdits`) and applied by `PostRow` and `SinglePostScreen`. Rows in quote cards, notifications and the Photo Grid detail keep the old text until they reload.
- `Post.editedAtEpochMillis` comes from `edited_at` or `updatedAt`. Rows show an Edited label. Edit history is not built.

# Decisions (M2 quotes, M4 local-only and scheduling)

- Quote parsing is lenient across the Mastodon-API family: Mastodon 4.5 `quote{state,quoted_status}`, a shallow `quoted_status_id`, Fedibird `quote`/`quote_id`/`quote_muted`, Akkoma state plus copy, Pleroma `pleroma.quote*`, the legacy `quoted_status`, and a trailing `RE:` link or `p.quote-inline`. A non-accepted state becomes `Post.quoteUnavailable` and shows a placeholder card with the reason. A reference without a body (`quoteId`, `quoteUrl`) shows a placeholder whose tap loads it.
- Quote support without evidence is `Unknown`, not `Unsupported`: the composer still offers Quote, with a note that the server may drop it. A create whose answer holds no quote marks quotes `Unsupported` for the session only, keeps the post, and tells the user (`PublishNotice.QuoteDropped`).
- `quote_approval.current_user = denied` disables Quote on that post. `quote_approval_policy` (public, followers, nobody) is sent on create only where the server shows Mastodon 4.5 evidence.
- Links and quote cards open in the app through `SessionPostLinkResolver` (`LocalPostLinkResolver`) and `LocalPostOpener`, both provided per session. Only post-shaped URLs are tried, with a 3 second limit; real answers are cached; anything else goes to the browser. This replaced threading `onOpenPost` through every row.
- `PostOptions(localOnly, quotePolicy, scheduledAtEpochMillis)` rides on the draft, the thread publication and the request. Local-only routes: Misskey `localOnly`; glitch-soc (`api_versions.glitch`) and Hometown (`+hometown-`) `local_only`; Pleroma and Akkoma `visibility=local` for public posts. Vanilla Mastodon shows no Local only chip.
- Scheduling is a separate `SocialSource` call (`schedule`, `scheduledPosts`, `cancelScheduled`, `reschedule`), not a result type on `create`. Routes: Mastodon `scheduled_at` with `scheduled_statuses` (5 minute lead); Sharkey `notes/schedule/*` (no update, so reschedule is cancel and recreate); Misskey `notes/drafts/*`. The capability comes from `POST /api/endpoints` and the role policy limit (`scheduleNoteMax`, `scheduledNoteLimit`; 0 turns it off). A thread of several posts cannot be scheduled. The Scheduled tab lives in the composer's drafts browser only.
- Capability schema version is 11 (quote approval policy, local-only route, scheduling).

# Completed

- M2 and M4 share one commit because the composer owner, capability model, session host and strings changed together. Live on `emulator-5554`: quote created on mstdn.ca, rendered as a card, and the card opened the quoted post in the app; local-only post on mstdn.ca (its server reports `local_only`, shown as a Local only label); a scheduled post on mstdn.ca created, listed in the Scheduled tab and cancelled; the dvd.chat composer shows Local only and Schedule and no quote policy. Not live: dvd.chat (Sharkey) refused `notes/schedule/create` and `notes/schedule/list` for the test account with a permission error, so Sharkey scheduling is fixture-tested only; vanilla Misskey draft scheduling, Pleroma and Akkoma local-only, Fedibird quotes, and Mastodon 4.5 quote policy are fixture-tested only.

- M0 and M5 share one commit because the share sheet, capability schema and adapters changed together.
- M3: delete and edit. Live on `emulator-5554`: Sharkey edit on dvd.chat (text changed, Edited label, poll and votes kept) and Mastodon edit on mstdn.ca (text changed, Edited label). Not live: alt text and image removal, a followers-only local-only Sharkey note (covered by a contract test), the server web UI.
- M1: poll model, adapters, composer editor, drafts and voting. Live on `emulator-5554`: created a single-choice poll on mstdn.ca and a multiple-choice poll on dvd.chat, voted on the dvd.chat poll (both choices, results bars and checks showed). Mastodon voting is covered by unit tests only.

# Current slice

Finished. The next unit of work is M6, at the owner's choice.

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

Start M6. Check Sharkey scheduling with an account whose role allows it.

# Blockers

- `architecture_audit.py --check` exits 1. Regressions predate this work, but `PostShareSheet.kt`, `ShellOverlayHost.kt`, `ServerCapabilities.kt` and both capability probes also grew. The baseline was not edited.

# Last safe commit

c46ec6b3 Fix video publish hang, bloated conversions, and viewer open frame; add duration badge and retry
