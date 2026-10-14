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

- S1 filter model and presentation: commit "Collapse or remove posts that a server filter matches" (`58704740`).
- S2 Mastodon filter management: commit "Manage Mastodon filters in Settings" (`a008b159`). Settings > Blocks and mutes > Filters, v2 on Mastodon 4.0 and later, v1 after a v2 404, and client matching through `ClientFilterMatcher` and `ContentFilterOwner`.
- S3 Misskey word mutes: commit "Edit Misskey word mutes in Settings" (`acbf1e2b`). `MisskeyWordMuteService`, `FilterPattern.Expression`, `FilterExpressions`, the simple and advanced editor modes, and `SourceError.InvalidInput`.
- S4 account mutes and blocks: commit "Hide posts of muted and blocked accounts" (`b234f8db`). `AccountFilter`, account lists in `ContentFilterOwner`, the two settings on Blocks and mutes.
- S5 boost mutes: commit "Hide boosts from chosen accounts". `ProfileRelationship.boostsHidden`, `SocialSource.setBoostsHidden` (Misskey `renote-mute`, Mastodon follow with `reblogs`), `ContentFilterOwner.boostMuted`, the post menu row and the profile pill.

# Current slice

None. The task is complete.

# Files involved

`domain/PostFilter.kt`, `domain/ContentFilters.kt`, `domain/ClientFilterMatcher.kt`, `domain/AccountFilter.kt`, `domain/ProfileModels.kt`, `domain/SocialSource.kt`, `data/mastodon/MastodonFilterMapper.kt`, `data/mastodon/MastodonFilterService.kt`, `data/mastodon/MastodonModerationService.kt`, `data/misskey/MisskeyModerationService.kt`, `ui/posts/PostFilterPresentation.kt`, `ui/posts/PostShareSheet.kt`, `ui/posts/PostPopupOwner.kt`, `ui/profile/`, `ui/filters/`, `ui/settings/`.

# Verification

S1: focused tests (`MastodonFilterResultTest`, `PostRowFilterTest`, `PostRevealOwnerTest`, `NotificationJsonCodecTest`, `NotificationDeliveryPlannerTest`) and the full gate.
S2: focused tests (`ClientFilterMatcherTest`, `MastodonFilterServiceTest`, `ContentFilterEditorTest`, `ContentFilterViewModelTest`, `ContentFilterSettingsScreenTest`, `PostRowFilterTest`, `SettingsRouteRestorationTest`, `PhotoGridScreenTest`) and the full gate. No live check against Mastodon, Pleroma, or Akkoma. No device check.
S5: focused tests (`ModerationServiceTest`, `ClientFilterMatcherTest`, `ContentFilterViewModelTest`, `PostShareSheetTest`, `ProfileViewModelTest`, `ProfileScreenTest`) and the full gate. An independent `problem_solver` review ran with no blocking findings; it asked for mapper unknown-flag tests, which `ModerationServiceTest.boostFlagStaysUnknownWhenTheServerDoesNotReportIt` covers. No live check against a Misskey-family or Mastodon server. No device check (the profile pill row width is uneyeballed). No native-speaker check for the German and Chinese strings.

# Next

None. Follow-ups outside this task: live-server checks, device checks for the post menu row and the profile pill, native-speaker review.

# Blockers

None.

# Last safe commit

b234f8db Hide posts of muted and blocked accounts
