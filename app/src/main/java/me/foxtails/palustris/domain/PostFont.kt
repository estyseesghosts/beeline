package me.foxtails.palustris.domain

/** The bundled font that the language of this script uses by default. */
val PostScript.bundledFont: AppFont
    get() = when (this) {
        PostScript.Japanese -> AppFont.KiwiMaru
        PostScript.Cantonese -> AppFont.ChironHeiHk
        PostScript.TraditionalChinese -> AppFont.ChocolateClassicalSans
        PostScript.SimplifiedChinese -> AppFont.ZcoolKuaiLe
    }

/**
 * The locale that selects the regional glyph forms of this script in the system font. Chinese
 * characters share code points across regions, so the system font needs the region to draw them.
 */
val PostScript.systemLocaleTag: String
    get() = when (this) {
        PostScript.Japanese -> "ja-JP"
        PostScript.Cantonese -> "zh-Hant-HK"
        PostScript.TraditionalChinese -> "zh-Hant-TW"
        PostScript.SimplifiedChinese -> "zh-Hans-CN"
    }

/**
 * The one font of a post with a known script. The post keeps the font of the application when
 * that font is the bundled font of the script. Every other application font cannot be trusted to
 * hold the characters, so the post uses the system font, which holds them all. A glyph that the
 * chosen font lacks falls back to the system font in both cases.
 */
fun postFont(script: PostScript, appFont: AppFont): AppFont =
    if (appFont == script.bundledFont) appFont else AppFont.Device
