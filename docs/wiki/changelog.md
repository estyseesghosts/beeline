# Beeline Changelog

Status: current  
Owner: Maintainers  
Last reviewed: 2026-10-09  
Stale when: A release ships without an entry here.

Each entry lists the user-visible changes in one release.
The GitHub release notes do not repeat this list. Source: [release workflow](../../.github/workflows/release.yml).

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
