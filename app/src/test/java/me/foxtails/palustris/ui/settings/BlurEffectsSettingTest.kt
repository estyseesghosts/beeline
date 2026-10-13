package me.foxtails.palustris.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileAppPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryAppPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.ui.components.LocalBlurEffectsEnabled
import me.foxtails.palustris.ui.components.rememberChromeBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "Disable blur effects" setting: storage, command, switch, and the effect on the blur backdrop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BlurEffectsSettingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var file: File

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.noBackupFilesDir, "app-preferences.json")
        file.delete()
        File("${file.path}.new").delete()
    }

    @Test
    fun theSettingDefaultsToOnAndAFileWithoutTheKeyDisablesBlur() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(AppPreferences().disableBlurEffects)
        assertTrue(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.disableBlurEffects)

        file.parentFile?.mkdirs()
        file.writeText("""{"colorScheme":"Palette"}""")
        assertTrue(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.disableBlurEffects)
    }

    @Test
    fun theSettingPersistsAcrossRepositoryInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FileAppPreferencesRepository(context).update { it.copy(disableBlurEffects = false) }

        assertFalse(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.disableBlurEffects)
    }

    @Test
    fun theCommandStoresOnlyItsOwnField() {
        val repository = InMemoryAppPreferencesRepository(AppPreferences(background = AppBackground.Dark))
        val model = SettingsViewModel(repository, InMemoryPostPreferencesRepository())

        model.setDisableBlurEffects(false)
        compose.waitForIdle()

        val preferences = runBlocking { repository.observe().first().preferences }
        assertFalse(preferences.disableBlurEffects)
        assertEquals(AppBackground.Dark, preferences.background)
    }

    @Test
    fun theSwitchSitsOnTheAppearancePageAndReportsItsState() {
        var disabled by mutableStateOf(false)
        compose.setContent {
            DisplaySettingsScreen(
                preferences = AppPreferences(disableBlurEffects = disabled),
                onColorScheme = {},
                onColorPalette = {},
                onBackground = {},
                onTextSize = {},
                onFont = {},
                onRequest60Hz = {},
                onDisableBlurEffects = { disabled = it },
            )
        }

        compose.onNodeWithTag("disable_blur_effects").performScrollTo().assertIsOff().performClick()

        compose.onNodeWithTag("disable_blur_effects").assertIsOn()
        assertTrue(disabled)
    }

    @Test
    fun theBackdropExistsWhileBlurIsAllowedAndIsAbsentWhenTheSettingDisablesIt() {
        var allowed by mutableStateOf(true)
        var present: Boolean? = null
        compose.setContent {
            CompositionLocalProvider(LocalBlurEffectsEnabled provides allowed) {
                present = rememberChromeBackdrop() != null
            }
        }

        compose.runOnIdle { assertEquals(true, present) }
        allowed = false
        // Without a backdrop every floating band draws nothing, so no band needs its own check.
        compose.runOnIdle { assertEquals(false, present) }
    }
}
