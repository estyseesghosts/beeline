package me.foxtails.palustris.domain

/**
 * Whether the Latin font override can apply to the language. It applies to a language whose
 * script Inter does not hold, so Inter draws only the Latin characters. A Latin-script language
 * needs no override. Russian is absent, because Inter also holds Cyrillic and would then replace
 * the font of the whole language.
 */
val AppLanguage.supportsLatinFontOverride: Boolean
    get() = when (this) {
        AppLanguage.Hindi,
        AppLanguage.Japanese,
        AppLanguage.Korean,
        AppLanguage.CantoneseHongKong,
        AppLanguage.Chinese,
        AppLanguage.ChineseMainland,
        AppLanguage.ChineseTaiwan,
        AppLanguage.Arabic,
        -> true
        else -> false
    }

/**
 * Whether Inter draws the Latin characters while the font in effect draws every other character.
 *
 * The stored switch is on by default. It has an effect only for a language that supports it; a
 * `SystemDefault` language uses [deviceLanguage]. A font that the user selected for its Latin
 * design keeps its Latin characters: the device serif, the device sans, OpenDyslexic, and Inter
 * itself never take the override.
 */
fun AppPreferences.latinFontOverrideActive(deviceLanguage: AppLanguage): Boolean {
    if (!latinFontOverride) return false
    val shown = if (language == AppLanguage.SystemDefault) deviceLanguage else language
    if (!shown.supportsLatinFontOverride) return false
    return when (effectiveFont(deviceLanguage)) {
        AppFont.Serif, AppFont.Sans, AppFont.OpenDyslexic, AppFont.Inter -> false
        else -> true
    }
}
