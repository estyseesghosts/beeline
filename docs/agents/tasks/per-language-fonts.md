# Per-language display fonts

Status: current.
Owner: Orchestrator (single implementation owner for all slices).
Last reviewed: 2026-10-13.
Stale when: The font preference contract, the per-language default mapping, or the setup typography changes.

Authority: `importantdocs/add_font_guide.md`, `domain/AppPreferences.kt`, `domain/EffectiveFont.kt`, `ui/theme/AppTypography.kt`, `ui/theme/EffectiveAppFont.kt`, `ui/settings/DisplaySettingsScreen.kt`, `ui/setup/SetupTypography.kt`, `ui/localization/AppLocaleController.kt`, `data/preferences/FileAppPreferencesRepository.kt`.

## Objective

Bundle six fonts (Inter, ZCOOL KuaiLe, Chocolate Classical Sans, Chiron Hei HK, Kiwi Maru, Sarala) as Appearance options. Give each language one default font for the whole application.

- Latin-script languages use Inter.
- Mainland Chinese uses ZCOOL KuaiLe.
- Taiwanese Mandarin uses Chocolate Classical Sans.
- Hong Kong Cantonese uses Chiron Hei HK.
- Japanese uses Kiwi Maru.
- Hindi uses Sarala.
- Generic Chinese, Korean, Russian, and unsupported languages keep the system font.

The default applies to the setup screens, the theme, and the selected option on the Appearance page. A clean install in English renders Inter everywhere.

## Invariants

- Never rename an existing `AppFont` entry. Stored files persist the enum name.
- `AppPreferences.font` is null until the user selects a font. Null means no choice.
- `effectiveFont` is the only rule that turns no choice into a font. The theme and the Appearance page read it through `effectiveAppFont`.
- The stored language decides the default. The device language decides it while the stored language is `SystemDefault`.
- An explicit font, including `Device`, never changes on a language switch.
- No `Noto` entry is added to `AppFont`. Noto means the Android system family.
- `ChoiceGroup` gains no parameters.
- Existing test cases are extended, never weakened.

## Decisions

- S4 replaces the S2 rule. S2 wrote the language default only when the user changed the language. A user on `SystemDefault` therefore kept the system font after sign-in.
- `SettingsViewModel.setLanguage` stores the language only. The font follows through `effectiveFont`.
- `AppLocaleController.languageForLocale` owns the device locale fallback: the exact tag, then language plus region without script, then the bare language. `setupLanguage` delegates to it.
- The preference file is version 2. No choice writes no `font` key. Version 1 wrote `Device` on every save, so a version 1 `Device` loads as no choice. Every other stored name is an explicit choice.
- `effectiveFont` and `effectiveAppFont` live in new files. The audit file-growth rule rejects more lines in `AppPreferences.kt` and `AppTypography.kt`.
- Variable font files are used where they exist (Inter, Chiron Hei HK). Kiwi Maru ships Regular only; Bold synthesizes.
- CJK binaries add about 48 MB to the APK. The user accepts the growth.

## Completed

- S1 (bundle six fonts as Appearance options): commit `012eba3d`.
- S2 (per-language default on language change): commit `32694a9d`.
- S3 (setup screens use the defaults; `noto_sans.ttf` deleted): commit `c3f5b61a`.
- S4 (the language default is the global default): the commit that carries this record, "Make the per-language font the global default font".

## Current slice

None. The task is complete with S4.

## Files involved

S4 production: `domain/AppPreferences.kt`, new `domain/EffectiveFont.kt`, `data/preferences/FileAppPreferencesRepository.kt`, `ui/localization/AppLocaleController.kt`, new `ui/theme/EffectiveAppFont.kt`, `ui/Theme.kt`, `ui/settings/DisplaySettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `ui/setup/SetupTypography.kt`.
S4 tests: `domain/AppFontDefaultsTest.kt`, `data/preferences/AppPreferencesRepositoryTest.kt`, `ui/localization/AppLocaleControllerTest.kt`, `ui/settings/SettingsDisplayTest.kt`, `ui/settings/SettingsViewModelTest.kt`.
S4 documents: `importantdocs/add_font_guide.md`, `docs/wiki/ui-and-navigation.md`.

## Verification

- S4 focused suites pass: `AppFontDefaultsTest`, `AppPreferencesRepositoryTest`, `AppLocaleControllerTest`, `SettingsDisplayTest`, `SettingsViewModelTest`, `SignInScreenTest`, `SetupTypographyTest`, `AppTypographyTest`.
- S4 Python checks: 52 tool tests pass. The audit reports no new `regression:` line. It reports two new `dependency-direction` warnings for `effectiveAppFont`.
- S4 full gate green: `testDebugUnitTest` (2559 tests), `lintDebug`, `ktlintCheck`, `assembleDebug`, and `assembleRelease`. The first run hit the known lint crash in `lintAnalyzeDebugUnitTest`; the rerun passed. See `logs/261013-140640.txt`.
- No independent review ran. The owner reviewed the staged diff.

## Limits

- No device check ran. Rendering of the bundled fonts on a device is unverified.
- A version 1 `Device` that the user selected on purpose also loads as no choice. Version 1 cannot tell the two apart.
- A build from S2 that switched language stored the language default as an explicit name. That name stays an explicit choice.
- A `zh-HK` device locale has no entry. It resolves to generic Chinese and keeps the system font. Only `yue-HK` gives Chiron Hei HK.
- The setup screens always render the language default. They do not follow an explicit font choice.

## Blockers

- None.

## Last safe commit

`c3f5b61a` Render setup screens in the per-language default font.
