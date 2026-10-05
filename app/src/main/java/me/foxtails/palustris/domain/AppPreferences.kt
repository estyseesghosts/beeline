package me.foxtails.palustris.domain

/** Global presentation and local content preferences. Secrets never belong here. */
data class AppPreferences(
    val colorScheme: AppColorScheme = AppColorScheme.System,
    val colorPalette: AppColorPalette = AppColorPalette.PastelIndigo,
    val background: AppBackground = AppBackground.Default,
    val textSize: AppTextSize = AppTextSize.Device,
    val font: AppFont = AppFont.Device,
    val request60Hz: Boolean = false,
    val language: AppLanguage = AppLanguage.SystemDefault,
    val layoutDirection: AppLayoutDirection = AppLayoutDirection.System,
    val cleanTrackingParameters: Boolean = false,
    val contentWarningRules: ContentWarningRules = ContentWarningRules(),
    val hiddenContentPresentation: HiddenContentPresentation = HiddenContentPresentation.Placeholder,
)

enum class AppColorScheme {
    System,
    SystemMonochrome,
    Palette,
}

enum class AppColorPalette {
    PastelRed,
    PastelOrange,
    PastelYellow,
    PastelGreen,
    PastelBlue,
    PastelIndigo,
    PastelViolet,
    VibrantRed,
    VibrantOrange,
    VibrantYellow,
    VibrantGreen,
    VibrantBlue,
    VibrantIndigo,
    VibrantViolet,
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
    OpenDyslexic,
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

data class AppPreferencesState(
    val loaded: Boolean = false,
    val preferences: AppPreferences = AppPreferences(),
    val error: String? = null,
)
