# Per-language display fonts

Status: current.
Owner: Orchestrator (single implementation owner for all slices).
Last reviewed: 2026-10-13.
Stale when: The font preference contract, the per-language default mapping, or the setup typography changes.

Authority: `importantdocs/add_font_guide.md`, `domain/AppPreferences.kt`, `ui/theme/AppTypography.kt`, `ui/settings/DisplaySettingsScreen.kt`, `ui/setup/SetupTypography.kt`.

## Objective

Bundle six new fonts (Inter, ZCOOL KuaiLe, Chocolate Classical Sans, Chiron Hei HK, Kiwi Maru, Sarala) as Appearance options. Store an explicit per-language default display font: Latin-script languages use Inter, mainland Chinese uses ZCOOL KuaiLe, Taiwanese Mandarin uses Chocolate Classical Sans, Hong Kong Cantonese uses Chiron Hei HK, Japanese uses Kiwi Maru, Hindi uses Sarala. Generic Chinese, Korean, and Russian keep the system font (Android Noto covers every script, so there is no tofu). SystemDefault keeps the system font. First-launch setup screens render the same per-language defaults instead of the bundled Noto Sans.

## Invariants

- Never rename an existing `AppFont` entry. Stored files persist the enum name.
- A file without the `font` key keeps `Device`.
- An explicitly chosen font never changes on language switch.
- No `Noto` entry is added to `AppFont`. Noto means the Android system family.
- `ChoiceGroup` gains no parameters.
- Existing test cases are extended, never weakened.

## Decisions

- Stored default is explicit (user-confirmed). `Device` keeps rendering as the system font.
- `defaultFontForLanguage` lives in `domain/AppPreferences.kt` beside the enums it maps.
- `SettingsViewModel.setLanguage` moves a still-`Device` font to the new language default. Explicit choices stay.
- Setup resolves the effective app language from the current configuration locale and renders `defaultFontForLanguage` of it. Unmapped languages render `FontFamily.Default`.
- Variable font files are used where they exist (Inter, Chiron Hei HK). Single Regular elsewhere except Sarala (Regular plus Bold). Kiwi Maru ships Regular only; Bold synthesizes.
- `noto_sans.ttf` is deleted in S3 once setup no longer references it.
- CJK binaries add about 48 MB to the APK (Chiron variable 29 MB, Chocolate 12 MB, Kiwi 5 MB). The user accepts the growth.

## Completed

- S1 (bundle six fonts as Appearance options) — commit 012eba3d.
- S2 (per-language stored default plus propagation) — commit follows this record update.

## Current slice

S3: setup screens use the new defaults; delete `noto_sans.ttf`.

## Files involved

S1 production: `domain/AppPreferences.kt`, `res/values/strings.xml`, `ui/settings/DisplaySettingsScreen.kt`, `ui/theme/AppTypography.kt`, `ui/settings/AboutSettingsScreen.kt`, seven new binaries under `res/font/`.
S1 tests: `ui/theme/AppTypographyTest.kt`, `ui/settings/SettingsDisplayTest.kt`, `data/preferences/AppPreferencesRepositoryTest.kt`.

## Verification

- S1 focused suites pass (AppTypographyTest, SettingsDisplayTest, AppPreferencesRepositoryTest).
- S1 python checks green (52 tool tests). No new audit regression for S1 files.
- S1 full gate: lintDebug, ktlintCheck, assembleDebug, assembleRelease green. Release APK verified to hold all 10 TTFs byte-identical. testDebugUnitTest re-run pending after one unrelated load-induced MediaRetryInterceptorTest flake (passes standalone).

## Next

Commit S1 after the green gate and problem_solver review, then start S2.

## Blockers

- None. Open questions on mechanism, unmapped languages, variable files, Noto, and propagation are answered above.

## Last safe commit

`181c9352` Replace the generated palettes with twelve hand-picked palettes.
