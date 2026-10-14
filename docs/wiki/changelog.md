# Beeline Changelog

Status: current  
Owner: Maintainers  
Last reviewed: 2026-10-13  
Stale when: A release ships without an entry here.

Each entry lists the user-visible changes in one release.
The GitHub release notes do not repeat this list. Source: [release workflow](../../.github/workflows/release.yml).

## Unreleased

## 0.3.2

### New

- The Media tab of a profile opens as a grid of square tiles. One post gives one tile. A tap on a tile opens the post.
- A floating control on the Media tab switches between the grid and the list. The grid is the default.
- A tile for a post with several images or videos shows dots over the photo: two dots for two, three dots for three or more. Swipe the tile sideways to see the next one. This applies to Photo Grid and to the profile grid.
- Posts that match a Mastodon filter now follow the filter. A warning filter collapses the post behind "Filtered: title" with a "Show anyway" button. A hiding filter removes the post. Before, every filtered post showed only a short notice and could not be opened.
- Settings > Blocks and mutes > Filters lists, adds, edits, and deletes the filters of a Mastodon-compatible account. On Pleroma and Akkoma, Beeline matches the filters on the device.
- On Misskey-family servers the same page edits word mutes. A word mute can collapse or remove posts. A simple word mute can ignore capitalization. An advanced word mute is a regular expression.
- Posts of muted and blocked accounts that the server still sends, such as replies in threads, quotes, and search results, now collapse or leave the list. Settings > Blocks and mutes chooses which. A new mute or block applies to the loaded feeds at once.
- The post menu and the profile hide the boosts of one account without muting it. A hidden boost leaves the list.

### Fixed

- Home, Local, and Federated timelines load on Pleroma servers. Before, the feed showed "This instance doesn't support this feature."
- Hashtag timelines load on Pleroma servers. Before, the search showed "Hashtag search failed."
- Notifications open on Pleroma servers without an unread-count route. The unread badge stays hidden there.
- One broken post no longer empties a Mastodon-compatible timeline. The feed skips it and shows the rest.

## 0.3.1

### Improved

- French, European Spanish, European Portuguese, Russian, German, Japanese and Indonesian carry the strings for the glossary terms.
- Arabic has the strings for the feed, publishing, drafts and placeholders.
- The README describes the current capabilities.

## 0.3.0

### New

- Arabic is an app language. Arabic uses a right-to-left layout.
- Arabic has a grammatical gender setting.
- Hindi strings for the glossary terms are added.
- Appearance has a Font list: Device default, Device serif, Device sans, OpenDyslexic and the bundled fonts.
- Settings is one list of pages: Appearance, Language, Timeline and media, Posting, Content warnings, Blocks and mutes, Privacy, Notifications and About.
- Favourite reaction (Posting, Misskey-family servers): choose the emoji that the heart button sends. The emoji picker opens, so a custom server emoji works too.
- Disable blur effects (Appearance): turns off the blur behind floating controls and on profile banners. It is on by default.
- The About page shows the version, the license and the open-source components.
- Content warnings has a page for all accounts and a page for the rules of the current account.

### Improved

- Appearance has three sections: Theme, Text, and Screen and layout.
- Appearance offers twelve accent palettes in two rows of six: six pastel and six vibrant. The colours are hand-picked.
- A stored orange, yellow or violet palette loads as its gold or indigo replacement.
- Back in Settings returns to the page before, not to the main page.
- With one account, Notifications opens that account at once.
- A switch row in Settings toggles as one control, so a screen reader reads the name and the state together.
- Long settings pages scroll.
- The settings pages are translated in every supported language.
- Each language has a default display font. A clean install in English uses Inter.
- A post uses one font for all of its text, chosen by the script of the text.
- Inter for Latin characters, on by default, draws Latin letters in Inter in a non-Latin language.
- Setup screens use the default font of the device language.

### Fixed

- A reaction with a custom emoji of your own server now shows as yours after a refresh on Misskey-family servers.

## 0.2.16

### New

- Hashtag groups: in the add hashtag dialog, select Create hashtag group and add tag fields.
- A hashtag group searches its tags together as one chip. Its name defaults to the first tag.
- Mastodon-compatible servers allow four tags in a group. Misskey-family servers allow 100.
- Groups are saved on this device. Long-press a group chip to remove it.
- Polls work on Misskey-family and Mastodon-compatible servers. Create a poll, vote and see the results.
- You can edit your own posts on Mastodon and Sharkey servers.
- Quote posts interoperate with other servers. Posts can be local-only or scheduled.
- A video without a server-sent poster image uses its first frame as the poster.
- Search has one field with suggestions, post search, lookups and Trends.
- Search has Trending and News tabs.
- Photo Grid starts with your Search results as a chip that you can dismiss.
- Home shows a chip row with your lists, hashtags and antennas.
- Mastodon-compatible servers follow the hashtags you follow.
- Mentions appear as inline bubbles with the account name and avatar.
- The attachment menu holds Photo, Poll and Content warning.
- A new setting translates posts automatically. It is off by default.

### Improved

- The composer uses floating bubbles over scrolling cards.
- A reply or quote uses the audience of the post it answers.
- A post you reveal stays revealed in the feed, post detail, media viewer and Photo Grid.
- The navigation bar is transparent, and content draws under it.
- Floating controls share one shadow. The top blur band is stronger.
- On Android 12 and later, content under the controls blurs. Android 10 and 11 show a gradient.
- Photo Grid tiles show a loading indicator, or a failed state with a Retry button.
- Beeline retries a media request once after a timeout, a server error or a rate limit.
- The hashtag field removes the # sign and stores tags without it.

### Fixed

- Photo Grid tiles no longer stay empty after rate limit or server errors.
- Mentions show complete handles on both server families.
- A blank composer opens every time. Beeline never keeps an empty draft.

## 0.2.15

### New

- Polls work on Misskey-family and Mastodon-compatible servers. Create a poll in the composer, vote on polls in the feed, and see the results.
- You can edit your own posts on Mastodon and Sharkey servers.
- Quote posts interoperate with other servers. Posts can be local-only or scheduled.
- A video without a server-sent poster image uses its first frame as the poster.

## 0.2.14

### New

- Search and Photo Grid hashtag feeds include related hashtags in other forms and languages. A new switch, Combine related hashtags, turns this off. The results show which hashtags they include and a Show only button.
- The Hashtags tab lists trending hashtags from your server. It suggests hashtags as you type and shows related hashtags under the results.
- The Profiles tab lists popular accounts from your server.
- The composer suggests hashtags while you type one. The app sends the typed fragment after the hash sign to your server for this.
- The bundled hashtag catalog has not been reviewed. Treat this build as a test build.
- Videos play in the feed, in Photo Grid tiles, and in a new video viewer. Videos start muted and loop when short. A new switch, Autoplay videos, turns autoplay off. Autoplay never runs on a metered network.
- The composer attaches videos. Beeline converts a video to WebM when your server and phone allow it, and to H.264 MP4 otherwise. Small files are sent as they are. The output is limited to 900p on the shorter edge.
- Beeline bundles FFmpeg (LGPL) for WebM on arm64 phones. See tools/ffmpeg/NOTICE.

## 0.2.13

### Fixed

- Misskey sessions saved without the publish flag can publish again.

## 0.2.12

### New

- The composer attaches images from the photo picker, with a thumbnail strip, a remove button, and a description dialog for each image.
- A post can be only images. Each entry in a thread has its own images.
- The Upload compression setting Ask now asks once when you post, and the answer applies to that post.
- On Mastodon-compatible servers, the photo button is off while you quote a post, because those servers reject a quote with images.

## 0.2.11

### New

- A tap on the repost action opens a Repost and Quote choice. The tap never sends a repost.
- The profile and contextual follow buttons ask for confirmation in a bubble before they follow or unfollow.
- Sharing a followers-only or direct post outside Beeline asks for confirmation first.
- A post action shows a Pending state until the server confirms it.
- Search shows one field. The field expands for typing and collapses to a bubble that keeps the query.
- The reaction picker has a compact pop-out and a full sheet. Both share recents and pinned emoji.
- Pinned emoji changes have a TalkBack and keyboard action.

### Improved

- The profile header puts the Message and Follow actions beside the avatar.
- The display name is larger than the handle. A handle that must wrap splits at the domain.
- The profile biography uses smaller text.
- On wide compact screens, the profile banner fills the display width and extends under the status bar.
- The compact navigation group centers the caret, the pill, and the contextual action with equal gaps.
- Narrow compact screens of 372 dp or more show the contextual tab caret beside the navigation pill.
- On other compact screens, the tab caret scrolls with the category chips.
- Photo Grid tiles and the Photo Grid detail view follow content warnings. Tile shapes stay between 1:2 and 2:1.
- The media viewer backdrop fades as you drag. The viewer fades out when no thumbnail is available.

### Fixed

- Compact screens no longer show two tab carets on Search, Notifications, Photo Grid, and Profile.
- Media viewer pages keep moving with the carousel while full images load.
- Covered sensitive media no longer appears in thumbnail-to-viewer transitions.
- A failed emoji preference write no longer crashes Beeline. The emoji does not show as pinned.
- A restored composer keeps verified reply and quote targets.
- A restored profile editor closes when its editor no longer exists.
