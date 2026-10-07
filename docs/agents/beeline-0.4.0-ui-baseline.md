# Beeline 0.4.0 UI requirements and baseline

Status: planned — requirements classified; Pixel Fold emulator posture rendering is partially verified (device verified (emulator)); all other device behavior remains unverified  
Owner: Beeline UI maintainers  
Last reviewed: 2026-09-23  
Verification: Pixel Fold AVD (`emulator-5554`), API 36, debug `app-debug.apk` version 0.2.8 (versionCode 2008), installed 2026-09-23. Theme (light/dark/pure-black), font 200%, animator-0, IME-open, hinge, and narrow-landscape captures now exist from the 2026-09-23 emulator runs; PNG dimensions were verified per posture. RTL remains blocked (see section 8). TalkBack, tablet/split-screen, and signed release remain unverified. The four adb helper scripts were fixed on 2026-09-23 (adb auto-resolve, multi-display PNG-warning stripping, optional `--display`); old claims that scripts failed or exec-out was unusable are superseded. The initial focused Gradle run exited 1 with two `NavigationTest` failures. `NavigationTest` then failed standalone at base commit `ba3fe53` with an empty `app/src` diff; this pre-existing issue is logged in `logs/BUGS.txt` and the task-state blockers. The final 0B gate excluded `NavigationTest` and passed the seven-class set: exit 0, 79 tests. Python unittest passed 51 tests; architecture audit `--check` exited 0.
Stale when: design specification, screen owner, navigation route, theme policy, account/session rule, device evidence, or measured geometry changes.  
Authorities: [UI upgrades](../ui_upgrades.md), [design specification](../designspecification.md), [polish research](../polishreport.md), [UI and navigation wiki](../wiki/ui-and-navigation.md), [notifications and direct messages wiki](../wiki/notifications-and-direct-messages.md), [AGENTS.md](../../AGENTS.md), source `app/src/main/java/me/foxtails/palustris/ui/`, tests `app/src/test/java/me/foxtails/palustris/ui/`.  
Approval: measure sign-off received 2026-09-23 for the emulator-observed values in section 6 (compact capsule/targets/inset, wide rail targets, chip row heights, detail split, and IME anchors). Media dismissal threshold, physical-left caret, and physical-bottom-right wide action remain pending for lack of device evidence. The capture matrix remains incomplete for post detail, media viewer, saved/settings, and setup surfaces. RTL and TalkBack remain unverified.

## 1. Requirement classification rules

- **Binding specification** means an instruction in `docs/designspecification.md`, subject to existing security, accessibility, and protocol rules. It does not claim that code implements it.
- **Proposal** means an idea or future acceptance condition in `docs/ui_upgrades.md` or `docs/polishreport.md`. The polish report is research/reference, not a Beeline specification. Its example dimensions, durations, and phases are never approved values.
- **Unverified device measure** means a value or visual outcome that needs a device prototype. Existing code constants and research examples do not approve it.

## 2. Classified requirement register

Rows below preserve the operative requirements. Ranges group inseparable clauses from a single numbered instruction; `device measure` flags values or outcomes that need physical validation. `Implemented` means source evidence only, not device approval.

| ID | Requirement (operative quote) | Source + line | Class | Current source evidence or gap | Affected screens | Affected tests | Device status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D01 | “Implement the bottom navigation bar as a custom reusable component.” | designspecification.md:5 | binding | CompactAppNavigation.kt exists; wide rail differs. | Home, Search, Notifications, Profile | NavigationTest, WideNavigationTest | unverified |
| D02 | “Implement each navigation button as a custom component.” | designspecification.md:6 | binding | Custom buttons are present in compact and large owners. | Four destinations | NavigationTest, WideNavigationTest | unverified |
| D03 | “Search/Photo Grid mode must persist visually across navigation.” | designspecification.md:7-9 | binding | ShellNavigator saves mode; CompactAppNavigation mounts one indicator per item. At `CompactAppNavigation.kt:169-172`, it selects the Photo Grid icon only when `destination == Destination.Search`; therefore the icon does not persist while Search is inactive, contrary to the specification. | Search, Photo Grid, all destinations | NavigationTest, ShellNavigatorTest | unverified |
| D04 | “Contextual action button.” | designspecification.md:15 | binding | Compact action and large compose FAB exist. | Composer overlay | AppShellStateTest | unverified |
| D05 | “Post composer.” | designspecification.md:16 | binding | Composer feature exists; no shared origin transition. | Composer overlay | ReplyComposerTest, ComposerOwnerTest | unverified |
| D06 | “Profile editor.” | designspecification.md:17 | binding | Editor exists as sheet; shared transition absent. | Profile editor | ProfileScreenTest | unverified |
| D07 | “Share sheet.” | designspecification.md:18 | binding | Share currently uses anchored popup. | Post detail, Home | PostShareSheetTest | unverified |
| D08 | “Start from the triggering button”; “Expand outward”; “Resolve into the full card or sheet”; “Reverse the same motion” | designspecification.md:20-27 | binding | Shared trigger-to-surface transition is absent. | Composer, profile editor, share | ReplyComposerTest, ProfileScreenTest, PostShareSheetTest | unverified |
| D09 | “Compact inline clickable `#hashtag`”; “Opens search results”; “normal post text” | designspecification.md:33-38 | binding | Inline parser and links exist; verify edge cases. | Home, Search, profile, detail | PostTextPresentationTest | unverified |
| D10 | “Clickable `#hashtag + N` bubble-button.” | designspecification.md:40-45 | binding | Compact summary exists. | Posts and detail | PostTextPresentationTest | unverified |
| D11 | “Expand smoothly into a vertical stack”; “approximately 3–5 large hashtag bubbles”; “responsive to device height”; “Include a `See all` bubble.” | designspecification.md:46-49 | binding + unverified device measure | HashtagBubble.kt exists, but precise count and geometry need prototype. | Posts and detail | PostTextPresentationTest | unverified |
| D12 | “See all opens a larger hashtag view”; compact bubbles; larger See less; collapse back | designspecification.md:51-57 | binding | Verify full list and return behavior against source. | Posts and detail | PostTextPresentationTest | unverified |
| D13 | Bubble variants use logogram; inline text does not; reserve space; no overlap or clipping | designspecification.md:59-64 | binding | Inline and bubble owners exist; no proof all text sizes fit. | Posts, detail | PostTextPresentationTest, InlineEmojiTextTest | unverified |
| D14 | Reusable inline link bubble; preserve clickable behavior; link logogram; spacing; no clipping; shared motion | designspecification.md:66-74 | binding | InlineEmojiText.kt has inline entity rendering; validate spacing/motion. | Posts, detail | InlineEmojiTextTest, ExternalLinkHandlerTest | unverified |
| D15 | Preserve existing timeline image shape and open/close animation; extract reusable media component | designspecification.md:76-84 | binding | PostMediaCarousel.kt and MediaTransitionState.kt exist; PhotoPagerSizing.kt implements documented sizing. | Home, detail, media viewer | PostMediaCarouselTest, MediaTransitionStateTest, SinglePostScreenTest | unverified |
| D16 | Like, bookmark, repost state changes use animated transitions; not color flash alone; communicate activation/deactivation/state | designspecification.md:86-99 | binding | PostInteractionPresentation.kt animates some counts/buttons; full behavior needs coverage. | Home, detail, profile, Search | PostInteractionIndicatorTest, HomeFeedTest | unverified |
| D17 | Repost must not act immediately; tap opens animated pop-out with Repost and Quote; button animates | designspecification.md:100-109 | binding | Current tap is repost/undo confirmation; quote is long-press/accessibility. | Posts and detail | PostRepostConfirmationStateTest, PostPopupOwnerTest | unverified |
| D18 | Comment and share have clear tap animation without persistent state | designspecification.md:111-113 | binding | Check common press response. | Posts and detail | PillActionTest, PostShareSheetTest | unverified |
| D19 | Emoji hold pins/removes favorite; explicit state transitions | designspecification.md:115-122 | binding | Picker supports confirmation; animated transition is incomplete. | Composer/reaction picker | EmojiPickerTest, EmojiCatalogViewModelTest | unverified |
| D20 | Replace existing tabs with one shared custom tab-bar system; Home is not separate | designspecification.md:124-128 | binding | HomeTimelineTabs and FilterChipRow remain separate. | Home, Search, Photo Grid, Profile, Notifications | HomeFeedTest, PhotoGridScreenTest, ProfileScreenTest, NotificationsScreenTest | unverified |
| D21 | Tab bar above content and navigation; horizontal edge-to-edge chips; no cropping margins; chips enter/exit physical edges | designspecification.md:130-136 | binding | CategoryChips.kt has no edge-to-edge behavior. | Tabbed screens | relevant screen tests; none proves device edge travel | unverified device measure |
| D22 | Leftmost circular caret; expanded `<`, collapsed `>`; chip-height, same style, fixed left edge | designspecification.md:138-155 | binding | CategoryChips.kt has no caret. | Tabbed screens | No dedicated tab-bar component test | unverified device measure |
| D23 | Collapse/expand animates smoothly | designspecification.md:157 | binding | Shared tab collapse not implemented. | Tabbed screens | No dedicated tab-bar component test | unverified |
| D24 | Search field is reusable with idle, text-entry, and results-browsing states | designspecification.md:159-183 | binding | SearchScreen currently uses full-width field; no three-state sizing contract. | Search | SearchPanelRestorationTest | unverified |
| D25 | Idle field smaller than active and rests normally; entry grows/moves above IME without jumps | designspecification.md:163-174 | binding + unverified device measure | No measured sizes/travel approved. | Search | SearchPanelRestorationTest | unverified device measure |
| D26 | Closing entry preserves query, shrinks/returns, and keeps text visible | designspecification.md:176-183 | binding | Search state restoration exists; visual transition remains. | Search | SearchPanelRestorationTest | unverified |
| D27 | Reuse bubble styling and animation for initial sign-in text field | designspecification.md:185-187 | binding | The sign-in screen lives in `ui/setup/SignInScreen.kt`; its `SetupServerField` input lives in `ui/setup/SetupScreens.kt`. | Setup/sign-in | SignInScreenTest | unverified |
| D28 | Shared motion vocabulary: tap, state changes, button/card expand/collapse, bubbles, position, size, pin/unpin | designspecification.md:189-205 | binding | MotionTokens.kt and SpringyInteractions.kt exist; coverage is not complete for each primitive. | Shared interaction surfaces | MotionTokensTest, SpringyInteractionsTest | unverified |
| D29 | Large layouts use the same floating navigation vertically; contextual action bottom-right; six independent destinations above | designspecification.md:207 | binding + unverified device measure | Compact tall keeps four grouped positions (Home, Search/Photo Grid, Notifications/Direct Messages, Profile) plus a separate contextual action. Wide layouts use six direct destinations plus a separate contextual action. The 0.4.0 plan corrects the earlier four-button wide register to six destinations. | Compact and wide/folding shell | LargeLayoutModeTest, WideNavigationTest | unverified |
| U01 | Quiet content-first visual hierarchy, density varies by Home/thread, Photo Grid, and DM | ui_upgrades.md:32-36 | proposal | Existing theme/tokens are not screenshot validation. | Home, Photo Grid, DM | HomeFeedTest, PhotoGridScreenTest, DirectMessageScreenTest | unverified |
| U02 | Paired-circle/path selection motif | ui_upgrades.md:38 | proposal | No approved asset requirement. | Navigation, bubbles | NavigationTest | unverified |
| U03 | Stable primary navigation; one moving selection indicator; unread state does not grow bar | ui_upgrades.md:40-44 | proposal; nav mode persistence binding (D03 restated) | CompactAppNavigation mounts one indicator per selected item; mode persistence exists. | Four destinations | NavigationTest, WideNavigationTest | unverified |
| U04 | Vertical wide controls, bottom-right action, safe-pane placement at hinge | ui_upgrades.md:44 | proposal; physical placement binding under D29 | Existing rail and dock; hinge rendering needs device validation. | Wide/folding shell | WideNavigationTest, LargeLayoutModeTest | unverified device measure |
| U05 | Shared tab pattern, physical-left caret, `<`/`>`, edge travel, feature-owned selection | ui_upgrades.md:46 | binding (restated D20-D23) | Not implemented as specified. | Tabbed screens | NavigationTest and feature tests | unverified device measure |
| U06 | Back closes top surface first, preserves drafts and returns to origin with fallback | ui_upgrades.md:48 | proposal | Existing back policy; broader continuity acceptance remains. | Overlays, detail, viewer | ShellBackPolicyTest, ShellNavigatorTest | unverified |
| U07 | Post text/media/actions stay together; count semantics; contextual secondary actions | ui_upgrades.md:50-52 | proposal | Existing presentation; audit proposed visual hierarchy. | Posts and detail | HomeFeedTest, SinglePostScreenTest | unverified |
| U08 | Pointer-down press feedback, distinct optimistic transitions, rollback on failure | ui_upgrades.md:54 | proposal | Some interaction animation exists; full failure and frame behavior needs checks. | Posts and detail | PostInteractionMutationOwnerTest, HomeFeedTest | unverified |
| U09 | Anchored Repost/Quote pop-out, no immediate action, sheet at edge/high font | ui_upgrades.md:56 | binding (repost choice restated D17) | Quote is not currently in tap pop-out. | Posts | PostPopupOwnerTest, PostRepostConfirmationStateTest | unverified device measure |
| U10 | Emoji pin/unpin transition, account-scoped, failed write must not look successful | ui_upgrades.md:58 | proposal | Existing pin confirmation/storage; transition gap. | Emoji picker | EmojiPickerTest, EmojiCatalogViewModelTest | unverified |
| U11 | Inline tags, 3–5 bubbles, See all/less, readable links at large text | ui_upgrades.md:60 | proposal; binding clauses D09-D15 restated; 3–5 is device measure | Current summary allows up to six and puts See all first. | Posts, Search | PostTextPresentationTest, SearchPanelRestorationTest | unverified device measure |
| U12 | Post-to-thread continuity and feed return | ui_upgrades.md:62 | proposal | Existing detail routing; continuity not device verified. | Home, detail | SinglePostScreenTest | unverified |
| U13 | Preserve carousel and registered media identity; no list-index identity | ui_upgrades.md:64-70 | proposal; binding clause D15 restated | PostMediaCarousel and transition states exist. | Home, Photo Grid, viewer | PostMediaCarouselTest, MediaTransitionStateTest, MediaViewerScreenTest | unverified |
| U14 | Viewer drag fades backdrop, valid thumbnail return, reduced motion retains direct manipulation | ui_upgrades.md:70 | proposal | Existing velocity-plus-distance settle; backdrop currently opaque during drag. | Media viewer | MediaViewerScreenTest | unverified device measure |
| U15 | Origin-aware composer/editor/share transitions, preserve feature state and IME | ui_upgrades.md:72-78 | proposal; binding clauses D08 restated | Separate sheets and popup; no shared presentation contract. | Composer, profile editor, share | ComposerOwnerTest, ProfileScreenTest, PostShareSheetTest | unverified |
| U16 | Search idle/entry/results states; account-bound state; shared sign-in presentation | ui_upgrades.md:80-84 | proposal; binding clauses D24-D27 restated; caret/tab references elsewhere are binding | Search uses full-width field. Sign-in screen lives in `SignInScreen.kt`; `SetupServerField` lives in `SetupScreens.kt`. | Search, sign-in | SearchPanelRestorationTest, SignInScreenTest | unverified device measure |
| U17 | Emoji picker uses shared tile and identity, account scope, accessibility and both sizes | ui_upgrades.md:86-94 | proposal | Existing compact/full picker and PickerCell; transition/semantics need review. | Emoji picker | EmojiPickerTest, EmojiCatalogViewModelTest | unverified |
| U18 | RTL mirrors logical flow while fixed caret/action anchors remain physical; mixed-direction text | ui_upgrades.md:96-104 | proposal; physical-left caret and physical-bottom-right action bind to D22/D29 | No dedicated RTL UI tests; forced preview is not real-locale evidence. | All shared screens | Current screen tests; no dedicated RTL suite | unverified device measure |
| U19 | Notification states distinguish unread/seen/acknowledged; DM remains explicitly not encrypted | ui_upgrades.md:106-112 | proposal | Existing notification and DM owners; retain protocol and privacy truth. | Notifications, DM, settings | NotificationsScreenTest, DirectMessageScreenTest, SettingsDisplayTest | unverified |
| U20 | Stable loading skeletons, offline/retry/stale states, account isolation | ui_upgrades.md:114-120 | proposal | Must verify each owner and failure state. | Home, Search, profile, inbox, media | HomeFeedTest, SearchPanelRestorationTest, ProfileScreenTest, DirectMessageScreenTest | unverified |
| U21 | Optimistic actions reconcile on response; destructive actions remain explicit | ui_upgrades.md:122-124 | proposal | Existing action owners; no visual signoff. | Posts, moderation | PostInteractionMutationOwnerTest, ModerationViewModelTest | unverified |
| U22 | Shared haptic events; one haptic at commit/threshold; respect system settings | ui_upgrades.md:126-128 | proposal | Event map in `ui/motion/HapticEvents.kt`; no device haptic evidence. | Shared gestures | HapticEventsTest, SpringyInteractionsTest | unverified device measure |
| U23 | Cover sensitive media until permitted reveal; no thumbnail in transition before reveal | ui_upgrades.md:130-132 | proposal | Validate transition and semantics in source tests. | Home, Photo Grid, notifications, detail, viewer | SinglePostScreenTest, MediaTransitionStateTest | unverified |
| U24 | Localization, 48 dp targets, focus, state beyond color | ui_upgrades.md:134-140 | proposal | Existing semantics and resources; no comprehensive device audit. | All screens | SettingsDisplayTest, NavigationTest, EmojiPickerTest | unverified device measure |
| U25 | Implementation order and acceptance checks | ui_upgrades.md:142-159 | proposal | Plan only; device and release traces pending. | All screens | Listed owner tests | unverified |
| U26 | Keep protocol/account owners; prototype floating dimensions, tab collapse, media threshold | ui_upgrades.md:161-165 | proposal + unverified device measure | Do not derive values from research. | Navigation, tabs, viewer | LargeLayoutModeTest, NavigationTest, MediaViewerScreenTest | unverified device measure |
| P01 | Research continuity, immediate feedback, stable navigation, state completeness, performance, accessibility | polishreport.md:18-35 | proposal/reference | Research principles, not Beeline acceptance values. | All screens | Not a direct test contract | unverified |
| P02 | Custom controls and state machines; pointer-down feedback | polishreport.md:88-145 | proposal/reference | Recommendations and example scale ranges are not approved. | Posts, navigation, emoji | MotionTokensTest, SpringyInteractionsTest | unverified device measure |
| P03 | Motion explains origin, destination, hierarchy, state, and direct manipulation | polishreport.md:148-194 | proposal/reference | Research principles; no dimensions approved. | Detail, sheets, viewer | MediaViewerScreenTest, PostShareSheetTest | unverified |
| P04 | Platform examples and regional patterns | polishreport.md:198-590 | proposal/reference | Comparative research only; not product requirements. | Product-wide | None | unverified |
| P05 | Press compression and navigation indicator examples | polishreport.md:593-630 | proposal/reference | Example scale and haptic sequences are not approved tokens. | Buttons, navigation | SpringyInteractionsTest, NavigationTest | unverified device measure |
| P06 | Sensitive reveal and media dismissal geometry examples | polishreport.md:641-663 | proposal/reference | Research examples are not approved measures. | Media, sensitive posts | MediaViewerScreenTest, MediaTransitionStateTest | unverified device measure |
| P07 | Composer expansion examples | polishreport.md:665-675 | proposal/reference | Preserve actual feature state owners. | Composer | ComposerOwnerTest | unverified |
| P08 | Floating versus edge-to-edge bar tradeoffs | polishreport.md:679-724 | proposal/reference | Does not override Beeline specification. | Navigation | NavigationTest | unverified |
| P09 | Sheet quality principles | polishreport.md:728-765 | proposal/reference | No new sheet values approved. | Sheets | ReplyComposerTest, PostShareSheetTest | unverified |
| P10 | Suggested typography roles, spacing scale, icon geometry | polishreport.md:769-809 | proposal/reference | Illustrative roles and example dimensions are not approved values. | All screens | SettingsDisplayTest | unverified device measure |
| P11 | Loading/failure, performance budgets, accessibility/reduced motion | polishreport.md:813-905 | proposal/reference | Research examples do not prove runtime performance. | All screens | MotionTokensTest, HomeFeedTest | unverified device measure |
| P12 | Continuity priorities and shared motion vocabulary | polishreport.md:909-1007 | proposal/reference | No additional specification. | All screens | Existing motion and feature tests | unverified |
| P13 | Hypothetical UI module, motion token values, pressable/reaction examples, shared transitions | polishreport.md:1011-1153 | proposal/reference | Hypothetical architecture and values are not adopted; Beeline uses one app module and existing motion owner. | Shared UI | MotionTokensTest, SpringyInteractionsTest | unverified device measure |

## 3. Contradictions and decisions

1. **Wide layout:** polish research lines 1220-1235 proposes a rail; designspecification line 207 requires floating vertical controls. The specification is the target. `LargeNavigationRail.kt` currently implements a rail.
2. **Research dimensions:** research examples are not approved measures. Do not copy them into implementation.
3. **Media:** preserve and extend `PostMediaCarousel` and `MediaTransitionState`. Do not build a new generic viewer.
4. **Sign-in ownership:** `ui/setup/SignInScreen.kt` owns the sign-in screen and route. `ui/setup/SetupScreens.kt` defines and renders the `SetupServerField` input. The original `ui_upgrades.md:26` claim that `SetupScreens.kt` owns the field is correct; it is not the screen owner.
5. **Chips and indicators:** current chips lack the specified caret and edge-to-edge behavior. Compact navigation mounts one indicator per selected item, not one traveling indicator.
6. **Theme:** `AppColorSchemes.kt` implements theme selection. It does not provide screenshot evidence.
7. **Wiki status:** both relevant wiki pages carry `Status: current, partial coverage` after slice 0A. This record does not change that status.
8. **Approval:** the emulator-observed values listed in section 6 are approved as prototype baselines. Media dismissal threshold, physical-left caret, and physical-bottom-right wide action remain pending. Keep the remaining screenshot and device-verification gates open.
9. **Review input:** `docs/260926_current_state.md` does not exist. `docs/260923_current_state.md` is the active review input.

## 4. Screen, owner, and test inventory

The four primary destinations are **Home**, **Search**, **Notifications**, and **Profile**. Photo Grid is a Search mode. Direct Messages is a Notifications mode. Overlays and wide panes are not destinations.

| Surface | Source owner files | Relevant existing tests |
| --- | --- | --- |
| Home | `ui/feed/HomeFeed.kt`, `FeedHost.kt`, `ui/navigation/HomeTimelineTabs.kt` | `ui/feed/HomeFeedTest.kt`, `ui/navigation/NavigationTest.kt` |
| Search | `ui/search/SearchScreen.kt`, `SearchController.kt` | `ui/search/SearchPanelRestorationTest.kt` |
| Photo Grid (inside Search) | `ui/photogrid/PhotoGridScreen.kt`, `PhotoGridController.kt`, `PhotoPagerSizing.kt` | `ui/photogrid/PhotoGridScreenTest.kt`, `PhotoGridFeedViewModelTest.kt` |
| Notifications | `ui/notifications/NotificationsScreen.kt`, `NotificationsHost.kt` | `ui/notifications/NotificationsScreenTest.kt`, `ui/NotificationsViewModelTest.kt` |
| Direct Messages (inside Notifications) | `ui/directmessages/DirectMessageInboxScreen.kt`, `DirectMessageConversationScreen.kt`, `DirectMessagesHost.kt` | `ui/directmessages/DirectMessageScreenTest.kt`, `DirectMessageViewModelTest.kt` |
| Profile + profile editor | `ui/profile/ProfileScreen.kt`, `EditProfileScreen.kt`, `EditProfileSheet.kt`, `ProfileViewModel.kt` | `ui/profile/ProfileScreenTest.kt`, `ProfileViewModelTest.kt` |
| Post/thread detail | `ui/posts/SinglePostScreen.kt`, `ui/thread/ThreadHost.kt`, `ThreadedReplies.kt` | `ui/SinglePostScreenTest.kt`, `ui/thread/PostThreadViewModelTest.kt` |
| Media viewer | `ui/media/MediaViewerScreen.kt`, `PostMediaCarousel.kt`, `MediaTransitionState.kt` | `ui/media/MediaViewerScreenTest.kt`, `PostMediaCarouselTest.kt`, `MediaTransitionStateTest.kt` |
| Saved posts, drafts, About | `ui/saved/SavedPostsScreen.kt`, `ui/composer/DraftsScreen.kt`, `ui/settings/SettingsScreen.kt` | `ui/saved/SavedPostsScreenTest.kt`, `ui/composer/ComposerOwnerTest.kt`, `ui/settings/SettingsDisplayTest.kt` |
| Composer overlay | `ui/composer/ComposerOverlayHost.kt`, `ComposerSheet.kt`, `ComposerOwner.kt` | `ui/composer/ReplyComposerTest.kt`, `ComposerOwnerTest.kt` |
| Setup/sign-in | `ui/setup/SetupScreens.kt`, `ui/setup/SignInScreen.kt` | `ui/SignInScreenTest.kt` |
| Settings tree + children | `ui/settings/SettingsScreen.kt`, `SettingsHost.kt`, child screens under `ui/settings/` | `ui/settings/SettingsDisplayTest.kt`, `SettingsRouteRestorationTest.kt`, `LanguageSettingsScreenTest.kt` |
| Notification detail | `ui/notifications/NotificationDetailScreen.kt`, `ui/shell/AppNotificationDetailContent.kt` | `ui/SinglePostScreenTest.kt`, `ui/notifications/NotificationRouteResolverTest.kt` |

Baseline test classes:

- `me.foxtails.palustris.ui.motion.MotionTokensTest`
- `me.foxtails.palustris.ui.motion.SpringyInteractionsTest`
- `me.foxtails.palustris.ui.large.LargeLayoutModeTest`
- `me.foxtails.palustris.ui.large.WideNavigationTest`
- `me.foxtails.palustris.ui.navigation.NavigationTest`
- `me.foxtails.palustris.ui.feed.HomeFeedTest`
- `me.foxtails.palustris.ui.shell.AppShellStateTest`
- `me.foxtails.palustris.ui.settings.SettingsDisplayTest`

No dedicated `CompactOverlayMetricsTest` exists. Adjacent coverage is `HomeFeedTest`, `NavigationTest`, and `AppShellStateTest`. No dedicated `AppColorSchemesTest` or screenshot test exists.

## 5. Capture matrix — partial emulator captures

Expand each row across these axes: theme = light/dark/pure-black; geometry = compact/wide/folding with separating hinge; locale = LTR plus a real RTL locale and mixed-direction content; font = normal/200%; animator = normal/0; state = loading/populated/empty/failed/selected/overlay-open/sensitive-covered/IME-open where applicable. Capture both Mastodon and Misskey account states on shared screens. Use test accounts and redact before publication. Folded and unfolded posture captures for both protocol accounts exist from the 2026-09-23 emulator pass. RTL remains unverified.

| Screen | Account identity + protocol | Theme | Size/posture | Locale/direction | Font scale | Animator scale | State | Screenshot path | Date/device/build | Reviewer | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Home | test account; Mastodon + Misskey | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/empty/failed/selected/overlay/sensitive/IME as applicable | docs/ (git-ignored posture captures, see sections 7–8) | 2026-09-23; Pixel Fold emulator (emulator-5554, API 36); versionCode 2008 | Beeline agent device pass | Theme light/dark/pure-black both postures; font normal/200% both postures (200% failure evidence, section 8); animator 0 captured; IME-open captured; hinge and narrow landscape captured for Home/Search; RTL unverified; account `ctr@dvd.chat` (Misskey) for these axis runs; both accounts in posture pass. |
| Search + Photo Grid | test account; Mastodon + Misskey | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/empty/failed/selected/overlay/IME as applicable | docs/ (git-ignored posture captures, see sections 7–8) | 2026-09-23; Pixel Fold emulator (emulator-5554, API 36); versionCode 2008 | Beeline agent device pass | Theme light/dark/pure-black both postures; font normal/200% both postures (200% failure evidence, section 8); animator 0 captured; IME-open captured where applicable; hinge and narrow landscape captured for Home/Search; RTL unverified; account `ctr@dvd.chat` (Misskey) for these axis runs; both accounts in posture pass. |
| Notifications + Direct Messages | test account; Mastodon + Misskey | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/empty/failed/selected/overlay/IME as applicable | docs/ (git-ignored posture captures, see sections 7–8) | 2026-09-23; Pixel Fold emulator (emulator-5554, API 36); versionCode 2008 | Beeline agent device pass | Theme light/dark/pure-black both postures; font normal/200% both postures (200% failure evidence, section 8); animator 0 captured; IME-open captured where applicable; hinge captured; RTL unverified; account `ctr@dvd.chat` (Misskey) for these axis runs; both accounts in posture pass. |
| Profile + editor | test account; Mastodon + Misskey | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/empty/failed/selected/IME as applicable | docs/ (git-ignored posture captures, see sections 7–8) | 2026-09-23; Pixel Fold emulator (emulator-5554, API 36); versionCode 2008 | Beeline agent device pass | Theme light/dark/pure-black both postures; font normal/200% both postures (200% failure evidence, section 8); animator 0 captured; IME-open where applicable; RTL unverified; account `ctr@dvd.chat` (Misskey) for these axis runs; both accounts in posture pass. Profile editor not captured. |
| Post/thread detail | test account; Mastodon + Misskey | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/empty/failed/selected/sensitive-covered/overlay as applicable | not captured | pending | pending | unverified - no capture |
| Media viewer | test account; Mastodon + Misskey | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/failed/selected/sensitive-covered/overlay as applicable | not captured | pending | pending | unverified - no capture |
| Saved posts/drafts/About/settings/notification detail | test account; protocol as applicable | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | loading/populated/empty/failed/selected/overlay/IME as applicable | not captured | pending | pending | unverified - no capture |
| Setup/sign-in | no authenticated account; protocol detection as applicable | light/dark/pure-black | compact/wide/folding + hinge | LTR/real RTL/mixed | normal/200% | normal/0 | idle/entry/failed/selected/IME as applicable | not captured | pending | pending | unverified - no capture |

## 6. Measure decisions

The approved values below are Pixel Fold emulator observations at 420 dpi (density 2.625 px/dp). Physical-device verification remains unverified.

| Measure | Applies to | Proposed prototype method | Owner | Approved value | Status |
| --- | --- | --- | --- | --- | --- |
| High-font compact obstruction and usable content | Compact navigation and scroll content at 200% | Measure overlay hit bounds vs final scroll item with IME open and closed; both postures | UI maintainers | contract approved via decisions 1-3 and 5; numeric targets derived in packet 4E1 from the approved clearance anchors | contract approved 2026-09-23; numeric targets partly derived in 4E1: wide Search dock rises by IME height beyond the system-bar inset (device and `SearchClearanceTest`, 100% and 200%); compact capsule and the other docks not re-measured |
| Compact floating height | Compact navigation | Prototype with content and IME; check reach and overlap | UI maintainers | 212 x 56 dp capsule; four 48 dp targets; 56 dp contextual button (emulator-observed) | approved 2026-09-23 (emulator); physical device unverified |
| Compact edge clearance | Compact navigation and content | Measure system bars, gesture regions, and last-row reach | UI maintainers | 36 dp bottom inset; end and final-item clearance inside scroll content anchored to this inset | approved 2026-09-23 (emulator); physical device unverified |
| Wide/foldable safe-pane clearance | Wide dock/navigation | Test separating hinge and system insets on both panes | UI maintainers | 80 dp rail width; 56 dp rail targets; detail split at 50 percent of 2208 px; wide layout renders at 2208x1840 including half-folded | approved 2026-09-23 (emulator); physical device unverified |
| High-font tab collapse | Universal tab bar | Test translated labels and 200% font with focus and edge gestures | UI maintainers | chip row 48 dp at font 1.0, 56 dp at font 200 (heights approved) | heights approved 2026-09-23; collapse behavior prototype required per 0B decision 1 before 4D1 |
| Media dismissal threshold | Media viewer | Test distance and velocity across compact/wide devices | UI maintainers | pending; media viewer not captured | pending device measurement |
| First-logical-position caret | Universal tab bar | Verify anchor and hit target in a real RTL locale | UI maintainers | decided 2026-10-07: logical-first, flips in RTL (supersedes physical-left) | real RTL locale unverified |
| Physical-bottom-right wide action | Wide navigation | Verify physical anchor, hinge safe pane, and RTL | UI maintainers | pending; dock not built until 4C | pending device measurement |

Decisions 1–3 and 5 from the plan's "0B decisions" section constrain this prototype: horizontal scrolling tabs with a caret; stacked profile stats; Search field and navigation stay visible; content clears overlays.

Approved prototype baseline values (approved 2026-09-23; emulator-observed at 420 dpi, density 2.625 px/dp; physical device unverified):

- Compact bottom capsule: 212 x 56 dp with four 48 dp targets and a 56 dp contextual button — approved 2026-09-23 (emulator-observed).
- Bottom inset: 36 dp — approved 2026-09-23 (emulator-observed).
- Wide left rail: 80 dp wide with 56 dp targets — approved 2026-09-23 (emulator-observed).
- Chip row height: 48 dp at font 1.0 and 56 dp at font 200% — approved 2026-09-23 (emulator-observed); collapse behavior still needs the decision 1 prototype.
- Wide detail split: 50 percent of 2208 px — approved 2026-09-23 (emulator-observed).
- Search field to IME clearance: 47 px (18 dp) at font 1.0 — approved 2026-09-23 (emulator-observed).
- IME failure evidence: nav capsule 136/147 px under the IME frame. This fails decision 3, which requires navigation to stay visible with the IME open; packet 4E1 targets it. Source now uses the greater of IME and navigation-bar insets; this capsule value has not been re-measured on device. This is not an approved design value.

Motion timings belong to existing `ui/motion/MotionTokens.kt` in a later slice. Slice 0B writes no timing values.

## 7. Foldable emulator device pass 2026-09-23

Verification: device verified (emulator) for folded and opened posture rendering only. This evidence does not verify physical foldable behavior or the other capture axes.

### Environment and method

- Device: Pixel Fold AVD (`ro.boot.qemu.avd_name=Pixel_Fold`), serial `emulator-5554`, API 36, density 420 dpi.
- Folded cover display: 1080x2092. Opened inner display: 2208x1840. Use `adb shell cmd device_state state 0` to fold and `state 2` to unfold.
- The installed debug APK was `app-debug.apk`, versionCode 2008, versionName 0.2.8. The previous installed copy was removed first. Installation date: 2026-09-23.
- Both live test accounts signed in through Chrome: `jmjmjm` on mstdn.ca (Mastodon) and `ctr` on dvd.chat (Misskey). The flows used Mastodon authorize and Misskey MiAuth accept.
- Navigation identity was checked in the accessibility hierarchy at each step. Live-server evidence includes sign-in, timelines, notifications, profiles, and Photo Grid media from both servers.
- A fold transition can show the Android keyguard over the app. On this emulator, dismiss it with `wm dismiss-keyguard`; this is environment behavior, not an app defect.

### Verified outcomes

- Folded posture shows compact navigation: a floating bottom capsule with grouped positions, a separate compose action, and a chips row over content.
- Opened posture shows a left rail with six direct destinations and a two-pane layout with the `Select a post` detail placeholder.
- Selecting the Search group restored Photo Grid with its previous `Local` feed chip.
- Photo Grid and its `Local` chip stayed selected across fold, unfold, and a second fold. All three checks passed.
- Home, Search/Photo Grid, Notifications/Direct Messages, and Profile navigation worked in both postures.

### Capture inventory and tooling limits

- Git-ignored screenshots under `docs/` cover the `folded-01` through `folded-17` family, the `unfolded-10` through `unfolded-19` family, and `step-*.png` sign-in flow evidence. These are evidence for this pass, not published artifacts.
- `adb exec-out screencap -p > file` produced stale or undecodable captures. Use `adb shell screencap -p /sdcard/shot.png` followed by `adb pull` instead.
- `tools/scripts/adb_control.py`, `adb_flow.py`, `adb_screenshot.py`, and `adb_inspect.py` failed with `error: adb not found: adb` because `adb` is not on PATH. These helpers were unusable in this pass.
- The helper-script failures were fixed on 2026-09-23: scripts now auto-resolve adb, strip multi-display PNG warnings, and support optional `--display`. The historical limitation above no longer holds.

### Still unverified

- Physical-device rendering, a real RTL locale, mixed-direction content, TalkBack, API 29 physical hardware, and signed-release behavior remain unverified. Dark and pure-black themes, 200% font scale, animator scale 0, and IME-open captures now exist on the emulator; these captures do not approve geometry or demonstrate fixes.
- Approved emulator values are listed in section 6. Media dismissal threshold, physical-left caret, and physical-bottom-right wide action remain pending for lack of device evidence. High-font tab collapse heights are approved, but collapse behavior needs the decision 1 prototype before 4D1.
- Foldable evidence is emulator-only. Physical foldable rendering remains unverified.

## 8. Capture axis inventory and 200% failure evidence (2026-09-23)

Capture inventory (all files are git-ignored under `docs/`):

- Theme dark: `folded-dark-01..06` and `unfolded-dark-01..06`.
- Theme pure-black: `folded-black-01..06` and `unfolded-black-01..06`; the captures were visually verified as true black.
- Font 200%: `folded-font200-01..06` and `unfolded-font200-01..06`.
- Animator-0: `folded-animator0-01/02` and `unfolded-animator0-01`; navigation was verified under animator 0.
- IME: `folded-ime-search-open`.
- Hinge: `hinge-half-01/02`.
- Narrow landscape: `landscape-home-01` and `landscape-search-02`.
- RTL attempt: `rtl-attempt-notmirrored` and `rtl-step-*` process shots.
- PNG header dimensions were verified against the claimed posture for every file.

The RTL axis is blocked. Beeline's in-app language catalog has 18 locales and none are RTL. `cmd locale set-app-locales me.foxtails.palustris --locales ar-XB` stored the locale, but the running app stayed `en_US/ldltr`. The override was reset to `[]`. RTL remains unverified.

Observed 200% failure evidence, grouped by failure class:

- Tiny-width text clips instead of wrapping or ellipsizing. `Federated` and `Reposts` labels render at 19 px; `#misskey +2` clips to `#micek`; Photo Grid displays `Fec`; a notification `@ctr` mention node is 3 px high.
- Floating chrome overlaps scroll content. Timeline chips and filter rows cover posts, grid tiles, and notification cards. Search subtitle overlaps. Post actions clip to 7 px behind the navigation capsule.
- Chip rows do not adapt. Category chips overflow past the right edge. Rows do not wrap, collapse, or re-measure. Cells exceed parent bounds.
- Single-line assumptions break. Profile stats wrap character by character in both postures. Notification rows grow past their cards. Post action rows clip behind navigation.
- IME coverage hides navigation. Search field stays visible with about 18 dp clearance, but the bottom capsule is about 92 percent covered by the IME frame.

Source owner hints for later repair:

- `ProfileStats` in `ProfileScreen.kt` uses a `Row` of three unrestricted `Text` nodes.
- Inspect `NotificationRow.kt` before changing its text geometry.
- `CategoryChips.kt` `FilterChipRow` uses a fixed 48 dp height with no large-text policy.
- `HomeTimelineTabs.kt` is separate and uses single-line ellipsis.
- `CompactOverlayMetrics.kt` derives clearance from fixed heights. `ShellContent.kt` pins Home tabs to `CompactTimelineTabsHeight`.
- Search measures its wide dock but not its compact dock. `SearchField` sets `singleLine = true` while a 306 px two-line field was observed; investigate before attributing this behavior.

This evidence schedules repair as plan packets 3C1–3C3, amended 4D1, and 4E1–4E2. No fix is implemented yet.
