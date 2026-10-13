package me.foxtails.palustris.domain

/**
 * The font that the application renders and that the Appearance page shows as selected.
 * An explicit choice wins, and that includes an explicit Device. Without a choice the font is the
 * default of the stored language. A SystemDefault language has no default of its own, so it uses
 * [deviceLanguage], the supported language of the device locale.
 */
fun AppPreferences.effectiveFont(deviceLanguage: AppLanguage): AppFont =
    font ?: defaultFontForLanguage(if (language == AppLanguage.SystemDefault) deviceLanguage else language)
