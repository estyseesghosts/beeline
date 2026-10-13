package me.foxtails.palustris.domain

/** Global presentation and local content preferences. Secrets never belong here. */
data class AppPreferences(
    val colorScheme: AppColorScheme = AppColorScheme.System,
    val colorPalette: AppColorPalette = AppColorPalette.PastelIndigo,
    val background: AppBackground = AppBackground.Default,
    val textSize: AppTextSize = AppTextSize.Device,
    /** The explicit font choice. Null means no choice, so the font follows the language default. */
    val font: AppFont? = null,
    /** Draws Latin characters in Inter for a language that is not written in Latin script. */
    val latinFontOverride: Boolean = true,
    val request60Hz: Boolean = false,
    /** Turns off every blur effect in the application. Off by default, so blur shows where the device supports it. */
    val disableBlurEffects: Boolean = true,
    val language: AppLanguage = AppLanguage.SystemDefault,
    val grammaticalGender: AppGrammaticalGender = AppGrammaticalGender.Masculine,
    val layoutDirection: AppLayoutDirection = AppLayoutDirection.System,
    val tabletNavigationAnchor: AppNavigationAnchor = AppNavigationAnchor.Left,
    val compactWideNavigationAnchor: AppNavigationAnchor = AppNavigationAnchor.Right,
    val cleanTrackingParameters: Boolean = false,
    val combineRelatedHashtags: Boolean = true,
    val autoplayVideos: Boolean = true,
    /** Shows the server translation of every translatable post without a tap. Off by default. */
    val alwaysTranslatePosts: Boolean = false,
    val contentWarningRules: ContentWarningRules = ContentWarningRules(),
    val hiddenContentPresentation: HiddenContentPresentation = HiddenContentPresentation.Placeholder,
)

enum class AppColorScheme {
    System,
    SystemMonochrome,
    Palette,
}

enum class AppColorPalette {
    PastelGold,
    PastelRed,
    PastelPink,
    PastelIndigo,
    PastelBlue,
    PastelGreen,
    VibrantGold,
    VibrantRed,
    VibrantPink,
    VibrantIndigo,
    VibrantBlue,
    VibrantGreen,
}

enum class HiddenContentPresentation { Remove, Placeholder }

enum class AppBackground {
    Default,
    Dark,
    PureBlack,
}

enum class AppTextSize {
    Device,
    Smaller,
    Larger,
}

enum class AppFont {
    Device,
    Serif,
    Sans,
    OpenDyslexic,
    Inter,
    ZcoolKuaiLe,
    ChocolateClassicalSans,
    ChironHeiHk,
    KiwiMaru,
    Sarala,
}

/**
 * The font that a language uses when the user has made no font choice.
 * Latin-script languages use Inter. Mainland Chinese uses ZCOOL KuaiLe, Taiwanese Mandarin uses
 * Chocolate Classical Sans, Hong Kong Cantonese uses Chiron Hei HK, Japanese uses Kiwi Maru, and
 * Hindi uses Sarala. Generic Chinese, Korean, Russian, and Arabic keep the system font because the
 * Android Noto family covers every script, so no character renders as tofu. SystemDefault keeps
 * the system font. There is no Noto entry: Noto means the Android system family.
 */
fun defaultFontForLanguage(language: AppLanguage): AppFont = when (language) {
    AppLanguage.English,
    AppLanguage.German,
    AppLanguage.Spanish,
    AppLanguage.SpanishSpain,
    AppLanguage.SpanishLatinAmerica,
    AppLanguage.French,
    AppLanguage.PortugueseBrazil,
    AppLanguage.PortuguesePortugal,
    AppLanguage.Indonesian,
    -> AppFont.Inter
    AppLanguage.ChineseMainland -> AppFont.ZcoolKuaiLe
    AppLanguage.ChineseTaiwan -> AppFont.ChocolateClassicalSans
    AppLanguage.CantoneseHongKong -> AppFont.ChironHeiHk
    AppLanguage.Japanese -> AppFont.KiwiMaru
    AppLanguage.Hindi -> AppFont.Sarala
    AppLanguage.SystemDefault,
    AppLanguage.Chinese,
    AppLanguage.Korean,
    AppLanguage.Russian,
    AppLanguage.Arabic,
    -> AppFont.Device
}

enum class AppLanguage(val tag: String?) {
    SystemDefault(null),
    English("en"),
    German("de"),
    Spanish("es"),
    SpanishSpain("es-ES"),
    SpanishLatinAmerica("es-419"),
    French("fr"),
    Hindi("hi"),
    Japanese("ja"),
    Korean("ko"),
    PortugueseBrazil("pt-BR"),
    PortuguesePortugal("pt-PT"),
    CantoneseHongKong("yue-HK"),
    Chinese("zh"),
    ChineseMainland("zh-CN"),
    ChineseTaiwan("zh-TW"),
    Russian("ru"),
    Indonesian("in-ID"),
    Arabic("ar"),
    ;

    companion object {
        /** Stored preferences keep the enum name. Unknown names fall back safely. */
        fun fromNameOrDefault(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: SystemDefault
    }
}

/**
 * The stored layout direction override.
 *
 * `System` keeps the device direction and is the value for a user whose stored file predates this
 * preference, so an upgrade never flips an existing user. `ForceRtl` and `ForceLtr` name an explicit
 * direction. The values are presentation only. They do not change the application locale, and they
 * never appear as a user-facing label.
 */
enum class AppLayoutDirection {
    System,
    ForceRtl,
    ForceLtr,
}

/** A physical display edge. The value never follows layout direction. */
enum class AppNavigationAnchor {
    Left,
    Right,
}

data class AppPreferencesState(
    val loaded: Boolean = false,
    val preferences: AppPreferences = AppPreferences(),
    val error: String? = null,
)
