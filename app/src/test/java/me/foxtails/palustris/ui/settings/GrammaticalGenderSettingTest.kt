package me.foxtails.palustris.ui.settings

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.domain.AppGrammaticalGender
import me.foxtails.palustris.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The grammatical gender row shows only for a language with gender catalogs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h891dp-420dpi")
class GrammaticalGenderSettingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setPage(
        language: AppLanguage,
        deviceLocale: String = "en-US",
        gender: AppGrammaticalGender = AppGrammaticalGender.Masculine,
        onGender: (AppGrammaticalGender) -> Unit = {},
    ) {
        compose.activity.setContent {
            val configuration = Configuration(compose.activity.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(deviceLocale))
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                LanguageSettingsPage(
                    selected = language,
                    onSelected = {},
                    alwaysTranslatePosts = false,
                    onAlwaysTranslatePosts = {},
                    grammaticalGender = gender,
                    onGrammaticalGender = onGender,
                )
            }
        }
    }

    @Test
    fun theRowShowsForArabicAndForAnArabicDeviceUnderSystemDefault() {
        setPage(AppLanguage.Arabic)
        compose.onNodeWithTag("grammatical_gender").assertIsDisplayed()

        setPage(AppLanguage.SystemDefault, deviceLocale = "ar-EG")
        compose.onNodeWithTag("grammatical_gender").assertIsDisplayed()
    }

    @Test
    fun theRowStaysHiddenForALanguageWithoutGenderCatalogs() {
        setPage(AppLanguage.English)
        compose.onAllNodesWithTag("grammatical_gender").assertCountEquals(0)

        setPage(AppLanguage.SystemDefault, deviceLocale = "en-US")
        compose.onAllNodesWithTag("grammatical_gender").assertCountEquals(0)

        // A stored language wins over an Arabic device.
        setPage(AppLanguage.Hindi, deviceLocale = "ar-EG")
        compose.onAllNodesWithTag("grammatical_gender").assertCountEquals(0)
    }

    @Test
    fun theRowNamesTheCurrentFormAndReportsANewChoice() {
        var chosen: AppGrammaticalGender? = null
        setPage(AppLanguage.Arabic, gender = AppGrammaticalGender.Neutral) { chosen = it }

        compose.onNodeWithText("Neutral (when possible)").assertIsDisplayed()
        compose.onNodeWithTag("grammatical_gender").performClick()
        compose.onNodeWithText("Feminine (when possible)").performClick()

        compose.runOnIdle { assertEquals(AppGrammaticalGender.Feminine, chosen) }
    }
}
