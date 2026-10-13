package me.foxtails.palustris.ui.localization

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import java.io.File
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppGrammaticalGender
import me.foxtails.palustris.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GrammaticalGenderLocaleTest {
    private lateinit var context: Context
    private lateinit var file: File

    @Before
    fun clearPreferencesFile() {
        context = ApplicationProvider.getApplicationContext()
        file = File(context.noBackupFilesDir, "app-preferences.json")
        file.delete()
    }

    private fun arabic(gender: AppGrammaticalGender): Context =
        GrammaticalGenderLocale.genderedContext(
            AppLocaleController.localizedContext(context, AppLanguage.Arabic),
            gender,
        )

    @Test
    fun eachGenderSelectsItsFormOfACommand() {
        assertEquals("احذف", arabic(AppGrammaticalGender.Masculine).getString(R.string.draft_delete))
        assertEquals("احذفي", arabic(AppGrammaticalGender.Feminine).getString(R.string.draft_delete))
        assertEquals("حذف", arabic(AppGrammaticalGender.Neutral).getString(R.string.draft_delete))

        assertEquals("تابع", arabic(AppGrammaticalGender.Masculine).getString(R.string.profile_follow))
        assertEquals("تابعي", arabic(AppGrammaticalGender.Feminine).getString(R.string.profile_follow))
        assertEquals("متابعة", arabic(AppGrammaticalGender.Neutral).getString(R.string.profile_follow))
    }

    @Test
    fun aStringWithoutAGenderFormKeepsTheBaseCatalog() {
        AppGrammaticalGender.entries.forEach { gender ->
            val localized = arabic(gender)
            assertEquals(gender.name, "الرئيسية", localized.getString(R.string.nav_home))
            // The glossary gives one fixed phrase for Unfollow.
            assertEquals(gender.name, "إلغاء المتابعة؟", localized.getString(R.string.profile_unfollow_confirm))
        }
    }

    @Test
    fun aGenderCatalogKeepsTheLanguageAndTheRightToLeftLayout() {
        listOf(AppGrammaticalGender.Feminine, AppGrammaticalGender.Neutral).forEach { gender ->
            val configuration = arabic(gender).resources.configuration
            assertEquals(View.LAYOUT_DIRECTION_RTL, configuration.layoutDirection)
            assertEquals("ar", configuration.locales.get(0).language)
            assertEquals(AppLanguage.Arabic, AppLocaleController.languageForLocale(configuration.locales.get(0)))
        }
    }

    @Test
    fun aGenderCatalogKeepsArabicNumberFormatting() {
        // A count from 3 to 10 takes the plural form, with Arabic-Indic digits as in plain Arabic.
        val feminine = arabic(AppGrammaticalGender.Feminine)
        assertEquals(
            "#صور: ٥ منشورات",
            feminine.resources.getQuantityString(R.plurals.search_trending_tag_posts, 5, "#صور", 5),
        )
    }

    @Test
    fun theBaseFormAndALanguageWithoutGenderCatalogsKeepTheContext() {
        val arabicContext = AppLocaleController.localizedContext(context, AppLanguage.Arabic)
        assertSame(arabicContext, GrammaticalGenderLocale.genderedContext(arabicContext, AppGrammaticalGender.Masculine))

        val japanese = AppLocaleController.localizedContext(context, AppLanguage.Japanese)
        assertSame(japanese, GrammaticalGenderLocale.genderedContext(japanese, AppGrammaticalGender.Feminine))
        // The device language of this test is English.
        assertSame(context, GrammaticalGenderLocale.genderedContext(context, AppGrammaticalGender.Neutral))
    }

    @Test
    fun onlyTheFeminineAndNeutralFormsHaveAPrivateUseRegion() {
        assertNull(GrammaticalGenderLocale.region(AppGrammaticalGender.Masculine))
        assertEquals("QF", GrammaticalGenderLocale.region(AppGrammaticalGender.Feminine))
        assertEquals("QN", GrammaticalGenderLocale.region(AppGrammaticalGender.Neutral))
    }

    @Test
    fun persistedGenderBridgesTheStoredNameAndFallsBackSafely() {
        assertEquals(AppGrammaticalGender.Masculine, GrammaticalGenderLocale.persistedGender(context))

        file.parentFile?.mkdirs()
        file.writeText("""{"grammaticalGender":"Feminine"}""")
        assertEquals(AppGrammaticalGender.Feminine, GrammaticalGenderLocale.persistedGender(context))

        file.writeText("""{"grammaticalGender":"Neutral"}""")
        assertEquals(AppGrammaticalGender.Neutral, GrammaticalGenderLocale.persistedGender(context))

        file.writeText("""{"grammaticalGender":"Unknown"}""")
        assertEquals(AppGrammaticalGender.Masculine, GrammaticalGenderLocale.persistedGender(context))
    }
}
