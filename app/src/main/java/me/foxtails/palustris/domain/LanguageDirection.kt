package me.foxtails.palustris.domain

/** Whether the language reads from right to left. Arabic is the only right-to-left language. */
val AppLanguage.isRightToLeft: Boolean
    get() = this == AppLanguage.Arabic

/**
 * Stores a new language. The layout direction follows the locale of the language, so Arabic gives a
 * right-to-left layout and every other language a left-to-right layout. A forced layout direction
 * would hide that change. Therefore a change to or from a right-to-left language returns the
 * override to `System`. A change between two languages of the same direction keeps the override.
 */
fun AppPreferences.withLanguage(value: AppLanguage): AppPreferences = copy(
    language = value,
    layoutDirection =
        if (language.isRightToLeft != value.isRightToLeft) AppLayoutDirection.System else layoutDirection,
)
