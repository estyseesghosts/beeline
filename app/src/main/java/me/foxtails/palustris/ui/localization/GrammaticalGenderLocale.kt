package me.foxtails.palustris.ui.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import me.foxtails.palustris.domain.AppGrammaticalGender
import me.foxtails.palustris.domain.hasGrammaticalGenderForms
import org.json.JSONObject

/**
 * Selects the feminine or the neutral catalog of a language through a private-use region.
 *
 * The platform has its own grammatical gender qualifier only from Android 14, and the resource
 * compiler rejects a locale variant without a region. A region qualifier selects resources on
 * every supported version. ISO 3166 reserves the codes `QM` to `QZ` for private use, so no real
 * region takes them. The locale `ar-QF` reads `values-ar-rQF` first and falls back to `values-ar`
 * for each string without a feminine form. Formatting falls back to plain Arabic in the same way.
 * The region never reaches the stored language or the platform application locale.
 */
object GrammaticalGenderLocale {
    /** The private-use region of a gender, or null for the base catalog. */
    fun region(gender: AppGrammaticalGender): String? = when (gender) {
        AppGrammaticalGender.Masculine -> null
        AppGrammaticalGender.Feminine -> "QF"
        AppGrammaticalGender.Neutral -> "QN"
    }

    /** Read-only early bridge for base-context setup. The repository stays the source of truth. */
    fun persistedGender(context: Context): AppGrammaticalGender = runCatching {
        val file = java.io.File(context.noBackupFilesDir, "app-preferences.json")
        enumValueOf<AppGrammaticalGender>(JSONObject(file.readText(Charsets.UTF_8)).optString("grammaticalGender"))
    }.getOrDefault(AppGrammaticalGender.Masculine)

    /**
     * Returns [context] with the gender region on its first locale. The context stays unchanged
     * for the masculine form and for a language without gender catalogs. Apply this after
     * [AppLocaleController.localizedContext], so it also covers a device language under
     * `SystemDefault`.
     */
    fun genderedContext(context: Context, gender: AppGrammaticalGender): Context {
        val region = region(gender) ?: return context
        val locale = context.resources.configuration.locales.get(0) ?: return context
        if (!AppLocaleController.languageForLocale(locale).hasGrammaticalGenderForms) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.Builder().setLocale(locale).setRegion(region).build())
        return context.createConfigurationContext(configuration)
    }
}
