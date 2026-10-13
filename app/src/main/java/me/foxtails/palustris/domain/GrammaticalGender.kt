package me.foxtails.palustris.domain

/**
 * The grammatical gender that the application uses to address the user.
 *
 * `Masculine` is the form of every base catalog, so it is always available and it is the value for
 * a file that predates this preference. `Feminine` and `Neutral` apply only where a catalog holds
 * such a form. Every other string keeps the base form.
 */
enum class AppGrammaticalGender {
    Masculine,
    Feminine,
    Neutral,
}

/** Whether the catalog of the language holds feminine and neutral forms. Only Arabic does. */
val AppLanguage.hasGrammaticalGenderForms: Boolean
    get() = this == AppLanguage.Arabic
