# Handoff prompt: related hashtags, slices 4 to 6

You continue the implementation of `docs/related-tags.md` in the Beeline repository
(`C:\Users\julie\Documents\newmisskeyclientprojectdirectory\misskeyclient`).
Slices 1 to 3 are committed. You finish slices 4, 5, and 6, and commit each slice.

## Start here

1. Read `AGENTS.md` and every page it requires: `docs/agents/workflow.md`, `agent-control.md`, `engineering-rules.md`, `documentation-rules.md`, `operation-rules.md`, and `importantdocs/writing_style.md`.
2. Read `docs/related-tags.md` completely. It is the specification. The slice sections 4, 5, and 6 are your scope.
3. Read `docs/agents/tasks/related-hashtags.md` (task state), `docs/agents/related-hashtags.md` (owners and invariants), and `docs/agents/handoff.md`.
4. Run `git status`, `git log --oneline -6`, and read the three commits below.
5. The Git user wants one commit for each slice. Never push. The user has said: test as you go, no lint or ktlint violations, install on the emulator, commit after every slice.

## What is done

| Slice | Commit | Content |
|---|---|---|
| 1 | `2b551c12` | Catalog asset, `HashtagCatalog`, `HashtagExpander`, `HashtagLanguagePolicy`, the "Combine related hashtags" setting |
| 2 | `c8771358` | `HashtagQuery`, `searchHashtags`, Mastodon and Misskey adapters, Search and Photo Grid merging, the "Includes ... / Show only" header |
| 3 | `0829f3d4` | `trendingHashtags`, `suggestHashtags`, `popularAccounts` on `SocialSource` with both adapters, and `HashtagSuggestionService` |

The catalog has 74 groups and 1,814 members. The plan text says 62 and 1,370. The plan is stale on this point. Tests assert the real numbers.

## Facts you need

Code locations:

- Domain: `app/src/main/java/me/foxtails/palustris/domain/hashtags/`. Types: `HashtagCatalog`, `HashtagExpander`, `HashtagExpansion` (`applied`, `related`), `HashtagExpansionInput`, `HashtagLanguagePolicy`, `HashtagQuery`, `HashtagSuggestion(name, weight)`, `TrendingHashtag(name, accounts, uses)`, `HashtagSuggestionService`.
- `HashtagSuggestionService` is not constructed anywhere yet. Its constructor takes the catalog, an account key, a policy provider, a `fetchServer(prefix, limit)` lambda, and optionally a clock and a debounce. `suggestions(prefixes: Flow<String>, limit)` debounces 250 ms and cancels the earlier request. `suggest(typed, limit)` is the suspend form. `release()` clears the cache. Use `HashtagSuggestionService.SEARCH_LIMIT` (8) and `COMPOSER_LIMIT` (5).
- Wiring: `MainActivity` injects `HashtagExpander` (Hilt, `StorageModule` in `di/AppModule.kt`). `ConnectedApp` builds one `HashtagExpansionInput` from the setting and display language. It passes it through `ConnectedSessionHost` to `SearchHost` and `PhotoGridHost`. The hosts give the owners a provider (`rememberUpdatedState`). The catalog itself comes from `HashtagCatalogRepository` (`catalog` property). You need the catalog and the policy for the suggestion service and for related chips. Extend this wiring. Do not construct a second catalog.
- Search: `ui/search/SearchController.kt` (state in `AccountSearchState`, fields `combinedTags` and `relatedTags`), `SearchOwner.kt`, `SearchHost.kt`, `SearchScreen.kt` (large file), `CombinedHashtagHeader.kt`, `ui/shell/SearchContract.kt`, `ShellSearchDestination.kt`.
- `AccountSearchState.relatedTags` already holds the related hashtags for chips. `HashtagExpander` already applies the language policy to them. The chips only render and tap them.
- Composer: `ui/composer/` (`ComposerEntryRow`, `ComposerEntryActions`, `ComposerToolbar`, `pendingEmojiInsertion`, `CursorField`, `PostTextPresentation`). The plan's "Composer" section lists the exact behavior.

Decisions already made (keep them):

- The head ranks first in `HashtagExpander`, and it covers its first language. The plan's ranking text omits the head. This is documented in `docs/agents/tasks/related-hashtags.md`.
- Setting off returns no expansion and no related hashtags. An ambiguous (`amb`) hashtag returns neither.
- `SearchController.search(query)` follows the setting. `searchWithoutRelated(query)` ignores it. Chips and the "Show only" button use these.
- Related chips: the plan says to show the header only when extras exist. It also says chips follow the language policy. Show the chip row whenever `relatedTags` is not empty, so `#caturday` (which merges nothing) still suggests `#cats`. Show the "Includes" line only when `combinedTags` is not empty. Record this choice.
- The plan's decisions D1 to D5 follow the recommended defaults.

## Slice 4: Hashtags tab

Follow "Slice 4" and "Screens, Hashtags tab" in the plan. In short:

- `SearchExploreController` in `ui/search/`, owned by `SearchOwner`. It holds trending hashtags (via `source.trendingHashtags(20)`), popular accounts for slice 5, and the typed suggestions (through `HashtagSuggestionService`). One owner, one release rule: `SearchOwner.release()` must stop it and call `HashtagSuggestionService.release()`.
- Blank query on tab 1: a list headed "Trending hashtags". Each row shows `#tag` and "N people". Tapping a row runs the existing hashtag search (`navigator::openHashtagSearch` is what hashtag bubbles use). A failed or empty list keeps the old prompt and shows no error.
- Typed but not submitted: replace the "ready" empty state in `HashtagSearchResults` with the suggestion list. A tap runs the search. A submit replaces the list with results.
- Related chips under the results header. Taps run that search.
- Keep the clearance rules. `SearchClearanceTest` must cover the new rows and chips on both sides. `SearchPanelRestorationTest` must still pass.
- Strings go in `values` and all 14 `values-*` folders.
- Update `docs/wiki/ui-and-navigation.md` and `docs/agents/related-hashtags.md`.

## Slice 5: Profiles tab

Follow "Slice 5". The blank state of `AccountSearchResults` shows "Popular accounts" using the same row as search results. A tap opens the profile. An empty or failed list keeps the old prompt. Typing replaces the list with search results. The Misskey list omits the signed-in account (the adapter already does this). Do not add a follow button. `SearchExploreController` from slice 4 loads the accounts.

## Slice 6: composer autocomplete

Follow "Slice 6" and "Composer" in the plan.

- A pure token helper that finds the hashtag token ending at the cursor. Use the boundary rules of `PostTextPresentation`. Require at least one character after `#`.
- `ComposerEntryRow` reports the token through `ComposerEntryActions`. The body shows at most 5 chips directly above `ComposerToolbar`. A tap replaces the token with `#tag ` and moves the cursor after the space. Reuse the `pendingEmojiInsertion` pattern: the body sends the replacement, the row owns `CursorField` and applies it.
- Each chip is a button with the description "Insert hashtag #tag". No chips in the content warning field. No chips when the cursor is not in a hashtag.
- A test must assert that only the fragment reaches the server.
- Update `docs/wiki/data-and-privacy.md`: the typed fragment goes to the account server.
- This is the last slice. Also update `docs/wiki/changelog.md` with a new unreleased entry (see the format of earlier entries; do not set a version number or release).
- Finish by rewriting `docs/agents/tasks/related-hashtags.md` and `docs/agents/handoff.md` for the completed task. State the release gate: the owner must review the catalog before a release.

## Process for every slice

1. Write the code and the tests. Use the real catalog (`app/src/test/.../data/hashtags/RealHashtagCatalog.kt`) where the plan says so.
2. Run focused tests, for example `./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests "*Name*"`.
3. Run the architecture audit and compare. The tree has about 40 `regression:` lines from before this work. Save the list (`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`, then `grep ^regression | sort`) and make sure your change adds none. Do not edit `tools/architecture-baseline.json`. The audit flags function growth of 5 lines and 20 percent, one more parameter, or one more nesting level. It also flags a local `LinkedHashMap` or similar "unretained long-lived map". Extract helpers or split functions instead.
4. Run the complete gate, with nothing else running Gradle:
   `python -m unittest discover -s tools/tests`, the audit above, then
   `./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease --continue`
   with `GRADLE_OPTS=-Dorg.gradle.daemon=false`, an explicit timeout, `< /dev/null`, and output to `logs/rt-sN-gate.out`. It takes 4 to 6 minutes.
5. Install the debug build on the emulator and check the slice by hand (see "Device"). Record what you could not verify.
6. Update the wiki, the agent page, the task state, and the handoff in the same commit. Stage explicit paths only. Never run `git add docs/agents` or `git add .`: the tree has many unrelated untracked files (screenshots, logs, `docs/agents/tasks/*-prompt.md`). Do not stage them. Commit with the attribution line from your system reminder. Never push.

## Device

The user asked for installation on the simulator after each slice. `adb` is at `C:\Users\julie\Documents\platform-tools\adb.exe` and is not on PATH. Use `adb devices` first and select the device explicitly. Install with `adb -s <id> install -r app/build/outputs/apk/debug/app-debug.apk` (the gate builds it). The earlier slices were not yet installed or checked on a device: do that for slices 1 to 3 as well when you first connect, if the emulator has an account. Useful checks: Settings, Display shows the new switch; search `#foto` on a Mastodon account and on dvd.chat (Misskey) shows the "Includes" line and page 2 still loads; the Hashtags tab shows trending rows; the Profiles tab shows popular accounts. Do not capture credentials or full API bodies. If no emulator or account is available, say so in your report. Do not claim device verification you did not do.

## Pitfalls found so far

- Bash heredocs with code often fail with `unexpected EOF while looking for matching`. Create files with the Write tool.
- Many source files use CRLF line endings, and some are mixed. A plain string replace of a multi-line block fails on CRLF files. Normalize in a script, or use the Edit tool. Keep each file's existing line endings.
- Two Gradle runs at once corrupt each other. Wait for one to finish before you start another. Do not run a second Gradle while a monitor waits on the first.
- ktlint enforces lexicographic import order (uppercase before lowercase, so `...domain.PhotoGridPreferencesRepository` comes before `...domain.hashtags...`). It also uses a baseline keyed by line. If you edit a file that has an old violation, the baseline entry stops matching and the gate fails. Fix the violation.
- A new parameter with a default, placed last, steals a trailing lambda from existing call sites. Put it before a trailing lambda parameter.
- JUnit test methods written as `= runBlocking { ... }` must end in a `Unit` expression. A final `assertThrows(...)` makes the method return a value and JUnit rejects the class.
- `isExactHashtag("alice")` is true: a bare word counts as a hashtag. An account search must start with `@`.
- `SettingsDisplayTest` counts the switches on the Display page. It expects three now.
- Gradle test tasks that leave a gated call pending inside `runTest` hang until timeout. Complete every gate before the test ends.
- Do not run `tools/hashtag-catalog/scripts`. They are research scripts that need files the repo lacks.
- Use "Beeline" in all user text. Never put "Palustris" in user-facing strings.

## Report

When all three slices are committed, report: the three commit hashes, the gate results, what you verified on a device and what you did not, and the remaining risks (unreviewed catalog, fork behavior, Mastodon limit of 3, `v2/suggestions` unverified with a token).
