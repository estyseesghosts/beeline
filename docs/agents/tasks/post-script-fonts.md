# Fonts by script

Status: current.
Owner: Orchestrator (single implementation owner for all slices).
Last reviewed: 2026-10-13.
Stale when: The post font rule, the script tables, or the Latin font override changes.

Authority: `domain/PostScript.kt`, `domain/PostFont.kt`, `ui/theme/PostFont.kt`, `ui/emoji/InlineEmojiText.kt`, `ui/posts/PostRow.kt`, `ui/posts/SinglePostScreen.kt`.

## Objective

1. A post renders in one font. Its script decides the font.
2. For a language that is not written in Latin script, an option draws Latin characters in Inter. The option is on by default.

## User decisions (2026-10-13)

- A post with kana or Japanese punctuation is Japanese. A post with Cantonese-only characters is Cantonese. A post with traditional characters and no kana is traditional Chinese. Simplified Chinese follows the same rule.
- The post uses the bundled font of its script only when that font is the font of the application. Otherwise it uses a font that can render the text, which is the system font (Noto).
- A glyph that the font lacks falls back to the system font. No other mix of fonts is allowed in a post.
- The hashtag bubbles of a post use the font of the post.
- The Latin override applies to non-Latin languages. It uses Inter for Latin characters and the existing or selected font for the language.

## Decisions of the owner

- Kana wins over the Chinese rules. Cantonese wins over the traditional rule. Traditional evidence wins over simplified evidence.
- Only Japan-only punctuation proves Japanese. `、`, `。` and the corner brackets do not, because Chinese uses them.
- A post of shared characters only has no script. It keeps the font of the application.
- The scope is the body, the content warning, and the hashtag labels. The author name, the handle and the time keep the font of the application.
- A quoted post has its own scope.
- The system font takes the locale of the script (`ja-JP`, `zh-Hant-HK`, `zh-Hant-TW`, `zh-Hans-CN`) for the regional glyph forms.

## Slices

- F1 One font for one post.
- F2 Latin font override.

## Completed

- F1 One font for one post: the commit "Render a post in one font by its script". Full gate green (2597 tests). Two earlier runs stopped at ktlint on an unused import and on an import order.

## Limits

- The script tables hold frequent characters only. A short post can miss its script.
- Japanese text without kana classifies as Chinese or as no script.
- The system font draws Latin with its own Latin face and Chinese or Japanese with Noto. Both belong to the system font.
- A hashtag bubble in a notification row keeps the font of the application.
- No device check ran. Glyph forms and fallback need a device.

## Last safe commit

`752adcb1` Add a grammatical gender setting for Arabic.
