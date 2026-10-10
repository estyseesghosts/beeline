package me.foxtails.palustris.domain.hashtags

import java.util.Locale
import me.foxtails.palustris.domain.AppLanguage

/**
 * Decides which hashtag names the app may show for a display language. Stateless and pure.
 * It applies to names that the app shows, such as suggestions and related chips. It never
 * applies to merged search results.
 */
class HashtagLanguagePolicy private constructor(
    private val allowedCodes: Set<String>,
    private val allowedScripts: Set<Character.UnicodeScript>,
) {
    /** Whether a catalog member passes: any language of the member is allowed. */
    fun allows(member: CatalogMember): Boolean = member.languages.any { it in allowedCodes }

    /** The script check for a name that the catalog does not know. Digits and marks never decide. */
    fun allowsUnknown(name: String): Boolean {
        var index = 0
        while (index < name.length) {
            val codePoint = name.codePointAt(index)
            index += Character.charCount(codePoint)
            if (!Character.isLetter(codePoint)) continue
            val script = Character.UnicodeScript.of(codePoint)
            // Common and inherited letters, such as the Japanese prolonged sound mark, belong to no script.
            if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED) continue
            if (script !in allowedScripts) return false
        }
        return true
    }

    companion object {
        private const val UNDETERMINED = "und"
        private const val FALLBACK = "en"

        /**
         * The policy for a stored language. [systemLocale] resolves `SystemDefault` to the language
         * the app uses for its strings.
         */
        fun forLanguage(
            language: AppLanguage,
            systemLocale: Locale = Locale.getDefault(),
        ): HashtagLanguagePolicy {
            val resolved = if (language == AppLanguage.SystemDefault) resolveSystem(systemLocale) else language
            return HashtagLanguagePolicy(
                allowedCodes = setOf(catalogCode(resolved) ?: FALLBACK, UNDETERMINED),
                allowedScripts = scripts(resolved),
            )
        }

        /** The catalog code of a display language, or null when the catalog has no coverage. */
        fun catalogCode(language: AppLanguage): String? = when (language) {
            AppLanguage.English -> "en"
            AppLanguage.German -> "de"
            AppLanguage.Spanish, AppLanguage.SpanishSpain, AppLanguage.SpanishLatinAmerica -> "es"
            AppLanguage.French -> "fr"
            AppLanguage.Japanese -> "ja"
            AppLanguage.ChineseTaiwan, AppLanguage.CantoneseHongKong -> "zh-TW"
            else -> null
        }

        /**
         * Maps a device locale to the display language whose strings the app resolves. Android
         * reports Indonesian as `in`, so `in` and `id` both map to Indonesian. An unknown locale
         * gives English, which holds the fallback strings.
         */
        fun resolveSystem(locale: Locale): AppLanguage {
            val language = if (locale.language == "id") "in" else locale.language
            val regional = "$language-${locale.country}"
            AppLanguage.entries.firstOrNull { it.tag.equals(regional, ignoreCase = true) }?.let { return it }
            return when (language) {
                "es" -> AppLanguage.Spanish
                "pt" -> AppLanguage.PortugueseBrazil
                "yue" -> AppLanguage.CantoneseHongKong
                "zh" -> AppLanguage.Chinese
                "in" -> AppLanguage.Indonesian
                else -> AppLanguage.entries.firstOrNull { it.tag == language } ?: AppLanguage.English
            }
        }

        private fun scripts(language: AppLanguage): Set<Character.UnicodeScript> {
            val latin = Character.UnicodeScript.LATIN
            return when (language) {
                AppLanguage.Japanese -> setOf(
                    Character.UnicodeScript.HAN,
                    Character.UnicodeScript.HIRAGANA,
                    Character.UnicodeScript.KATAKANA,
                    latin,
                )
                AppLanguage.Chinese,
                AppLanguage.ChineseMainland,
                AppLanguage.ChineseTaiwan,
                AppLanguage.CantoneseHongKong,
                -> setOf(Character.UnicodeScript.HAN, latin)
                AppLanguage.Korean -> setOf(Character.UnicodeScript.HANGUL, latin)
                AppLanguage.Russian -> setOf(Character.UnicodeScript.CYRILLIC, latin)
                AppLanguage.Hindi -> setOf(Character.UnicodeScript.DEVANAGARI, latin)
                else -> setOf(latin)
            }
        }
    }
}
