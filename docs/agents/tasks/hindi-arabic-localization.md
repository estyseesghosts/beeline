# Hindi additions and Arabic localization

Status: current.
Owner: Orchestrator (single implementation owner for all slices).
Last reviewed: 2026-10-13.
Stale when: The supported language list, the grammatical gender setting, or the layout direction rule changes.

Authority: `docs/i18n/hindi.csv`, `docs/i18n/arabic.csv`, `domain/AppPreferences.kt`, `ui/localization/AppLocaleController.kt`, `ui/settings/LanguageSettingsScreen.kt`, `app/src/main/res/values-hi/`, `app/src/main/res/values-ar/`.

## Objective

1. Extend the Hindi catalog with the terms of `docs/i18n/hindi.csv`. Keep every existing Hindi string.
2. Add a Modern Standard Arabic catalog from `docs/i18n/arabic.csv`. Arabic is the first right-to-left language.
3. Add a grammatical gender setting on the Language page: masculine, feminine when possible, neutral when possible.
4. Arabic selects a right-to-left layout. Every other language selects a left-to-right layout.

Both files in `docs/i18n` are glossaries of 33 terms. They are not tables of string keys.

## Decisions

- Hindi uses the polite imperative and neutral nouns. Hindi has no gendered forms (user decision, 2026-10-13).
- The Hindi slice adds the 184 untranslated strings of at most 30 characters that use a glossary term.
- The Arabic catalog covers the keys that the Hindi catalog covers, plus the strings of the gender setting.
- The base Arabic catalog uses the masculine imperative for a command to the user. The feminine catalog replaces it with the feminine imperative. The neutral catalog replaces it with the verbal noun.
- A label, a status, and the name of a setting do not change with grammatical gender.
- Arabic text carries no full vowel marks. It keeps the shadda that the glossary shows.
- The glossary gives two words for hashtag and for mention. The catalog uses `هاشتاغ` and `إشارة`. `وسم` stays the word for tag.

## Slices

- S1 Hindi additions (resources only).
- S2 Arabic language, base catalog, and the layout direction rule.
- S3 Grammatical gender setting with the feminine and neutral Arabic catalogs.

## Completed

- S1 Hindi additions: the commit "Add Hindi strings for the glossary terms". The catalog holds 409 keys. Full gate green on the second run (2559 tests); the first run had one failure in `MastodonIntegrationTest`, a network cancellation test outside this slice.
- S2 Arabic language: the commit "Add Arabic with a right-to-left layout". `AppLanguage.Arabic`, `values-ar` with 409 keys, `locales_config.xml`, the system font, the Arabic hashtag script, and `withLanguage` in `domain/LanguageDirection.kt`. Full gate green on the second run (2565 tests). The first run failed because `LocalizationResourceTest` did not list Arabic yet, and on the known lint crash.

## Limits

- The Arabic locale formats a count with Arabic-Indic digits.
- A notification that the application context builds does not use the stored language below Android 13. This limit is older than this task.
- No native speaker reviewed the Hindi or the Arabic text.
- The glossary marks many Arabic imperative forms as inferred.
- No device check ran yet.

## Last safe commit

`9e0ebe25` Make the per-language font the global default font.
