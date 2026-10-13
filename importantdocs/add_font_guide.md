# Add a Custom Font

Status: current.
Owner: Maintainers.
Last reviewed: 2026-10-13.
Stale when: The font preference contract, the Appearance page, or the font license policy changes.

Authority: `app/src/main/java/me/foxtails/palustris/domain/AppPreferences.kt`, `app/src/main/java/me/foxtails/palustris/domain/EffectiveFont.kt`, `app/src/main/java/me/foxtails/palustris/ui/theme/AppTypography.kt`, `app/src/main/java/me/foxtails/palustris/ui/settings/DisplaySettingsScreen.kt`, `app/src/main/res/values/strings.xml`, and `app/src/main/java/me/foxtails/palustris/ui/settings/AboutSettingsScreen.kt`.
Source example: commit `1b8ead69` added Device sans and the bundled OpenDyslexic font.

## Overview

The application offers its fonts on the Appearance page. The list comes from one enum. Each entry needs four pieces: the enum value, the label, the typeface mapping, and the license attribution. Tests cover each piece.

## Procedure

Follow each step in order.

1. Check the license of the font before bundling it.
2. Copy the font files into `app/src/main/res/font/`.
3. Add one entry to the `AppFont` enum.
4. Add the English label strings.
5. Map the entry in the Appearance page.
6. Map the entry in `appTypography`.
7. Attribute the font on the About page.
8. Extend the three font tests.
9. Run the focused tests.
10. Run the complete local CI-parity gate.

## License Check

- Confirm the license permits bundling in a mobile application.
- OFL fonts permit bundling. Each copy must carry the copyright notice and the license.
- The font binary metadata can carry the notice for a bundled font.
- Mention the font in the About box regardless of the license.
- Record the license name exactly. Do not translate proper nouns.

## Font Files

- Use lowercase letters, digits, and underscores in file names.
- Example: `open_dyslexic3_regular.ttf`.
- Uppercase names break the resource compiler.
- Provide Regular and Bold when both exist.
- One weight is sufficient when only one exists.
- Each file adds about 200 to 500 KB to the APK.
- Reference each file from code. The resource shrinker removes unreferenced files from release builds.
- Verify the release APK contains the font after assembly.

## Enum Entry

- Add the entry to `AppFont` in `domain/AppPreferences.kt`.
- The UI order follows `AppFont.entries`. Place the entry where it must appear.
- Never rename an existing entry. Stored files persist the enum name.
- Never remove an existing entry without a migration.
- `FileAppPreferencesRepository` reads the stored name with `storedFontChoice`. A missing key or an unknown name is no choice.
- A renamed entry orphans existing users. Their stored name no longer matches.

## Default Font

- `AppPreferences.font` is null until the user selects a font. Null means no choice.
- Without a choice, `effectiveFont` in `domain/EffectiveFont.kt` returns the default of the language.
- `defaultFontForLanguage` in `domain/AppPreferences.kt` maps each language to its default.
- The stored language decides the default. The device language decides it while the stored language is `SystemDefault`.
- The theme, the Appearance page, and the setup screens all use this default.
- An explicit `Device` is a choice. It keeps the system font in every language.
- To make a new font the default of a language, change `defaultFontForLanguage` and extend `AppFontDefaultsTest`.

## Label Strings

- Add a dedicated string key for each font label.
- Do not repurpose a shared key. `settings_display_device` also labels the text-size option.
- Keep the font proper name untranslated.
- Add only the English strings. Other locales fall back to English.
- The project disables the `MissingTranslation` lint check for this fallback.
- Translated labels for renamed options stay stale until a translation pass. Record the pass as follow-up work.

## Appearance Page

- Map the new entry in `TextChoices` in `DisplaySettingsScreen.kt`.
- The `when` expression over `AppFont` is exhaustive. The compiler rejects a missing branch.
- Do not add parameters to the generic `ChoiceGroup`. Its parameter count is architecture-baselined.
- New parameters trip a `function-complexity-growth` audit regression.
- A trailing lambda binds to the last function parameter. A parameter added after `label` breaks every call site.
- The font group sits inside a tagged `Column` (`font_choices`). The container adds no spacing and changes no layout.
- Keep the container. Tests locate the font radios through it.

## Typeface Mapping

- Map the new entry in `appTypography` in `ui/theme/AppTypography.kt`.
- Define a private `FontFamily` with `Font(R.font.name, weight = FontWeight.Normal)` and Bold entries.
- `OpenDyslexicFont` in the same file shows the pattern.
- `Font()` construction needs no Android context. Unit tests can assert the mapping directly.
- Missing weights fall back to platform synthesis. This is acceptable for italic when only Regular and Bold ship.
- `unitTests.isIncludeAndroidResources` is true, so `R.font` resolves in JVM tests.

## About Attribution

- Add the font name to `OpenSourceComponents` in `AboutSettingsScreen.kt`.
- The names are proper nouns. Do not translate them.
- Add a component for every bundled font, not only for Gradle dependencies.

## Tests

Extend three suites. Add cases only. Do not weaken existing cases.

- `AppTypographyTest`: assert the new entry maps to its expected family. Assert a bundled family differs from `Default`, `Serif`, and `SansSerif`. The existing loop over `AppFont.entries` covers the new entry automatically.
- `SettingsDisplayTest`: add the new label to the displayed-labels list. Select the radio through the `font_choices` container. Assert the reported selection.
- `AppPreferencesRepositoryTest`: round-trip the new value through `FileAppPreferencesRepository`. Assert old stored names still load. Assert a file without the key loads as no choice.

## Test Selection Pitfalls

- The radio button owns the click. Clicking the label text selects nothing.
- The semantics tree is flat. Sibling and parent matchers match every row on the page.
- Query the group instead: `onAllNodes(hasAnyAncestor(hasTestTag("font_choices")) and isSelectable())`.
- Assert the group count before indexing it. The order follows `AppFont.entries`.
- `onChildren()` does not exist in this Compose test version. `hasParent`, `hasAnyChild`, and `hasAnyAncestor` exist.
- Keep test imports in lexicographic order. `ktlintCheck` rejects `isSelectable` before `isHeading`.

## Verification

- Run the three focused suites first.
- Use `.\gradlew.bat --no-daemon --console=plain` on Windows.
- Set an explicit timeout for every Gradle call.
- Then run the complete gate: `testDebugUnitTest`, `lintDebug`, `ktlintCheck`, `assembleDebug`, `assembleRelease`.
- The architecture audit exits 1 on findings that predate this work. Do not edit the baseline.
- Confirm the audit reports no new `regression:` line for any touched file.

## Unverified Behavior

- Device rendering of a new font stays unverified without a device check.
- Record the missing device check in the handoff.
- Live-server checks do not apply to fonts.
