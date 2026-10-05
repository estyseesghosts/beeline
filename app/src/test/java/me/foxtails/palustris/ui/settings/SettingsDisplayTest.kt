package me.foxtails.palustris.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileAppPreferencesRepository
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppLayoutDirection
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.ui.resolveAgainst
import me.foxtails.palustris.ui.settings.DisplaySettingsScreen
import me.foxtails.palustris.ui.settings.SettingsHost
import me.foxtails.palustris.ui.settings.SettingsRoute
import me.foxtails.palustris.ui.settings.SettingsScreen
import me.foxtails.palustris.ui.theme.resolvedAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsDisplayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.noBackupFilesDir, "app-preferences.json").delete()
        File(context.noBackupFilesDir, "app-preferences.json.new").delete()
    }

    @Test
    fun settingsRootHasOnlyOneDisplayEntry() {
        compose.activity.setContent {
            SettingsScreen(
                preferences = AppPreferences(),
                onDisplay = {},
                onNotifications = {},
                onPrivacy = {},
                onLanguage = {},
            )
        }

        compose.onAllNodesWithText("Display").assertCountEquals(1)
    }

    @Test
    fun displayScreenShowsThreeExclusiveStylesAndFourteenPaletteChoices() {
        compose.activity.setContent {
            DisplaySettingsScreen(
                preferences = AppPreferences(),
                onColorScheme = {},
                onColorPalette = {},
                onBackground = {},
                onTextSize = {},
                onFont = {},
                onRequest60Hz = {},
                layoutDirection = AppLayoutDirection.System,
                onLayoutDirection = {},
            )
        }

        compose.onNodeWithText("System").assertIsDisplayed()
        compose.onNodeWithText("System monochrome").assertIsDisplayed()
        compose.onNodeWithText("Colour palette").assertIsDisplayed()
        listOf(
            "Pastel red", "Pastel orange", "Pastel yellow", "Pastel green", "Pastel blue", "Pastel indigo", "Pastel violet",
            "Vibrant red", "Vibrant orange", "Vibrant yellow", "Vibrant green", "Vibrant blue", "Vibrant indigo", "Vibrant violet",
        ).forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
    }

    @Test
    fun backFromDisplayReturnsToSettingsAndBackFromSettingsDismissesIt() {
        var route by mutableStateOf<SettingsRoute>(SettingsRoute.Display)
        var dismissed = false
        compose.activity.setContent {
            SettingsHost(
                state = AppPreferencesState(loaded = true),
                route = route,
                onRoute = { route = it },
                onBack = { dismissed = true },
            )
        }

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertEquals(SettingsRoute.Main, route)

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun paletteSelectionChangesPersistedPreference() {
        var selected = AppColorPalette.PastelIndigo
        var selectedScheme = AppColorScheme.System
        compose.activity.setContent {
            DisplaySettingsScreen(
                preferences = AppPreferences(colorPalette = selected),
                onColorScheme = { selectedScheme = it },
                onColorPalette = { selected = it },
                onBackground = {},
                onTextSize = {},
                onFont = {},
                onRequest60Hz = {},
                layoutDirection = AppLayoutDirection.System,
                onLayoutDirection = {},
            )
        }

        compose.onNodeWithContentDescription("Vibrant green").performClick()
        compose.runOnIdle {
            assertEquals(AppColorPalette.VibrantGreen, selected)
            assertEquals(AppColorScheme.Palette, selectedScheme)
        }
    }

    @Test
    fun filePreferencesRoundTripPaletteAndMigrateLegacyScheme() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = FileAppPreferencesRepository(context)
        repository.update { it.copy(colorPalette = AppColorPalette.VibrantViolet, colorScheme = AppColorScheme.System) }
        val reloaded = FileAppPreferencesRepository(context)
        assertEquals(AppColorPalette.VibrantViolet, reloaded.observe().first { it.loaded }.preferences.colorPalette)

        File(context.noBackupFilesDir, "app-preferences.json").writeText("{\"colorScheme\":\"Monochrome\"}")
        val legacy = FileAppPreferencesRepository(context)
        assertEquals(AppColorScheme.SystemMonochrome, legacy.observe().first { it.loaded }.preferences.colorScheme)

        File(context.noBackupFilesDir, "app-preferences.json").writeText("{\"colorScheme\":\"Palette\"}")
        val palette = FileAppPreferencesRepository(context)
        assertEquals(AppColorScheme.Palette, palette.observe().first { it.loaded }.preferences.colorScheme)
    }

    // The Display page is a plain column that does not scroll, so its last item can sit outside a
    // short viewport. These tests use a tall viewport so the layout direction item is reachable.
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-420dpi")
    fun layoutDirectionItemNamesTheDirectionItProducesInALtrDevice() {
        var requested: AppLayoutDirection? = null
        setDisplayContent(AppLayoutDirection.System, LayoutDirection.Ltr) { requested = it }

        compose.onAllNodesWithText("Force RTL Layout").assertCountEquals(1)
        compose.onAllNodesWithText("Force LTR Layout").assertCountEquals(0)
        layoutDirectionSwitch().assertIsOff()
        layoutDirectionSwitch().assertHasClickAction()

        layoutDirectionSwitch().performClick()
        compose.runOnIdle { assertEquals(AppLayoutDirection.ForceRtl, requested) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-420dpi")
    fun layoutDirectionItemNamesTheDirectionItProducesInAnRtlDevice() {
        var requested: AppLayoutDirection? = null
        setDisplayContent(AppLayoutDirection.System, LayoutDirection.Rtl) { requested = it }

        compose.onAllNodesWithText("Force LTR Layout").assertCountEquals(1)
        compose.onAllNodesWithText("Force RTL Layout").assertCountEquals(0)
        layoutDirectionSwitch().assertIsOff()

        layoutDirectionSwitch().performClick()
        compose.runOnIdle { assertEquals(AppLayoutDirection.ForceLtr, requested) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-420dpi")
    fun layoutDirectionSwitchIsOffForSystemAndOnForAStoredOverride() {
        setDisplayContent(AppLayoutDirection.System, LayoutDirection.Ltr)
        layoutDirectionSwitch().assertIsOff()

        setDisplayContent(AppLayoutDirection.ForceRtl, LayoutDirection.Ltr)
        layoutDirectionSwitch().assertIsOn()

        setDisplayContent(AppLayoutDirection.ForceLtr, LayoutDirection.Ltr)
        layoutDirectionSwitch().assertIsOn()

        // A stored override is on in either device direction, so the check cannot depend on it.
        setDisplayContent(AppLayoutDirection.ForceRtl, LayoutDirection.Rtl)
        layoutDirectionSwitch().assertIsOn()

        setDisplayContent(AppLayoutDirection.System, LayoutDirection.Rtl)
        layoutDirectionSwitch().assertIsOff()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-420dpi")
    fun theLabelFollowsTheDeviceDirectionAndNotTheForcedDirection() {
        // A forced right-to-left layout does not change the device direction, so the item keeps
        // offering right-to-left in a left-to-right device.
        setDisplayContent(AppLayoutDirection.ForceRtl, LayoutDirection.Ltr)

        compose.onAllNodesWithText("Force RTL Layout").assertCountEquals(1)
        compose.onAllNodesWithText("Force LTR Layout").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-420dpi")
    fun theToggleIsReversibleAndReturnsToTheDeviceDirection() {
        val stored = mutableStateOf(AppLayoutDirection.System)
        setReversibleDisplayContent(stored, LayoutDirection.Ltr)

        compose.onAllNodesWithText("Force RTL Layout").assertCountEquals(1)
        layoutDirectionSwitch().assertIsOff()

        layoutDirectionSwitch().performClick()
        compose.waitForIdle()
        assertEquals(AppLayoutDirection.ForceRtl, stored.value)
        compose.onAllNodesWithText("Force RTL Layout").assertCountEquals(1)
        layoutDirectionSwitch().assertIsOn()

        // Turning the switch off returns to the device direction.
        layoutDirectionSwitch().performClick()
        compose.waitForIdle()
        assertEquals(AppLayoutDirection.System, stored.value)
        compose.onAllNodesWithText("Force RTL Layout").assertCountEquals(1)
        layoutDirectionSwitch().assertIsOff()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-420dpi")
    fun theToggleIsReversibleInAnRtlDevice() {
        val stored = mutableStateOf(AppLayoutDirection.System)
        setReversibleDisplayContent(stored, LayoutDirection.Rtl)

        compose.onAllNodesWithText("Force LTR Layout").assertCountEquals(1)
        layoutDirectionSwitch().assertIsOff()

        layoutDirectionSwitch().performClick()
        compose.waitForIdle()

        // The device direction does not follow the forced direction, so the label does not flip.
        assertEquals(AppLayoutDirection.ForceLtr, stored.value)
        compose.onAllNodesWithText("Force LTR Layout").assertCountEquals(1)
        layoutDirectionSwitch().assertIsOn()

        layoutDirectionSwitch().performClick()
        compose.waitForIdle()
        assertEquals(AppLayoutDirection.System, stored.value)
        compose.onAllNodesWithText("Force LTR Layout").assertCountEquals(1)
        layoutDirectionSwitch().assertIsOff()
    }

    /** Renders the Display page under a fixed stored value over a device direction. */
    private fun setDisplayContent(
        stored: AppLayoutDirection,
        device: LayoutDirection = LayoutDirection.Ltr,
        onLayoutDirection: (AppLayoutDirection) -> Unit = {},
    ) {
        compose.activity.setContent {
            DisplayContent(device, stored, onLayoutDirection)
        }
    }

    /**
     * Renders the Display page over a device direction, with the stored value owned by the test so
     * that a switch press can be observed.
     */
    private fun setReversibleDisplayContent(
        stored: MutableState<AppLayoutDirection>,
        device: LayoutDirection,
    ) {
        compose.activity.setContent {
            DisplayContent(device, stored.value) { stored.value = it }
        }
    }

    @Composable
    private fun DisplayContent(
        device: LayoutDirection,
        stored: AppLayoutDirection,
        onLayoutDirection: (AppLayoutDirection) -> Unit,
    ) {
        CompositionLocalProvider(LocalConfiguration provides configuration(device)) {
            CompositionLocalProvider(
                LocalLayoutDirection provides stored.resolveAgainst(device),
            ) {
                DisplaySettingsScreen(
                    preferences = AppPreferences(),
                    onColorScheme = {},
                    onColorPalette = {},
                    onBackground = {},
                    onTextSize = {},
                    onFont = {},
                    onRequest60Hz = {},
                    layoutDirection = stored,
                    onLayoutDirection = onLayoutDirection,
                )
            }
        }
    }

    /**
     * A configuration whose platform layout direction matches [direction]. The platform derives the
     * layout direction from the locale, so only public API is needed here.
     */
    private fun configuration(direction: LayoutDirection): Configuration =
        Configuration(compose.activity.resources.configuration).apply {
            setLocales(
                LocaleList.forLanguageTags(if (direction == LayoutDirection.Rtl) "ar-SA" else "en-US"),
            )
        }

    /**
     * The Display page has exactly two switches, and the layout direction item follows the refresh
     * rate item. Radio buttons on the page are selectable, not toggleable, so this selects the
     * layout direction switch alone.
     */
    private fun layoutDirectionSwitch(): SemanticsNodeInteraction {
        val switches = compose.onAllNodes(isToggleable())
        switches.assertCountEquals(2)
        return switches[1]
    }

    @Test
    fun filePreferencesPersistTheLayoutDirectionAndKeepSystemForAFileWithoutTheKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // A stored file that predates the preference must not flip an existing user.
        File(context.noBackupFilesDir, "app-preferences.json").writeText("{\"colorScheme\":\"Palette\"}")
        val legacy = FileAppPreferencesRepository(context)
        assertEquals(
            AppLayoutDirection.System,
            legacy.observe().first { it.loaded }.preferences.layoutDirection,
        )

        val repository = FileAppPreferencesRepository(context)
        repository.update { it.copy(layoutDirection = AppLayoutDirection.ForceRtl) }
        val reloaded = FileAppPreferencesRepository(context)
        assertEquals(
            AppLayoutDirection.ForceRtl,
            reloaded.observe().first { it.loaded }.preferences.layoutDirection,
        )

        // An unrecognized stored name falls back to the safe default.
        File(context.noBackupFilesDir, "app-preferences.json")
            .writeText("{\"layoutDirection\":\"Sideways\"}")
        val unknown = FileAppPreferencesRepository(context)
        assertEquals(
            AppLayoutDirection.System,
            unknown.observe().first { it.loaded }.preferences.layoutDirection,
        )
    }

    @Test
    fun systemMonochromeHasNoChroma() {
        lateinit var colors: androidx.compose.material3.ColorScheme
        compose.activity.setContent {
            colors = resolvedAppColorScheme(
                context = LocalContext.current,
                colorScheme = AppColorScheme.SystemMonochrome,
                palette = AppColorPalette.PastelRed,
                background = AppBackground.Default,
                darkTheme = false,
            )
        }

        compose.runOnIdle {
            assertEquals(colors.primary.red, colors.primary.green, 0.0001f)
            assertEquals(colors.primary.green, colors.primary.blue, 0.0001f)
            assertEquals(colors.background.red, colors.background.green, 0.0001f)
            assertNotEquals(0f, colors.primary.red)
        }
    }

    @Test
    fun systemUsesTheDeviceMaterialSchemeInsteadOfTheSelectedPalette() {
        lateinit var colors: androidx.compose.material3.ColorScheme
        lateinit var expected: androidx.compose.material3.ColorScheme
        compose.activity.setContent {
            val context = LocalContext.current
            expected = dynamicLightColorScheme(context)
            colors = resolvedAppColorScheme(
                context = context,
                colorScheme = AppColorScheme.System,
                palette = AppColorPalette.VibrantRed,
                background = AppBackground.Default,
                darkTheme = false,
            )
        }

        compose.runOnIdle {
            assertEquals(expected.primary, colors.primary)
            assertEquals(expected.background, colors.background)
            assertEquals(expected.onSurface, colors.onSurface)
        }
    }

    @Test
    fun paletteKeepsTextAndSurfaceColorsSeparateInBothModes() {
        AppColorPalette.entries.forEach { palette ->
            val light = me.foxtails.palustris.ui.theme.selectedAppColorScheme(palette, darkTheme = false)
            val dark = me.foxtails.palustris.ui.theme.selectedAppColorScheme(palette, darkTheme = true)

            listOf(light, dark).forEach { scheme ->
                listOf(
                    scheme.background to scheme.onBackground,
                    scheme.surface to scheme.onSurface,
                    scheme.surfaceVariant to scheme.onSurfaceVariant,
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondary to scheme.onSecondary,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiary to scheme.onTertiary,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                ).forEach { (background, text) ->
                    assertTrue(colorDistance(background, text) > 0.2f)
                }
            }
        }
    }

    @Test
    fun darkBackgroundUsesNearBlackValue() {
        lateinit var colors: androidx.compose.material3.ColorScheme
        compose.activity.setContent {
            colors = resolvedAppColorScheme(
                context = LocalContext.current,
                colorScheme = AppColorScheme.System,
                palette = AppColorPalette.VibrantBlue,
                background = AppBackground.Dark,
                darkTheme = true,
            )
        }

        compose.runOnIdle { assertEquals(Color(0xFF090909), colors.background) }
    }

    private fun colorDistance(first: Color, second: Color): Float {
        fun brightness(color: Color) = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
        return kotlin.math.abs(brightness(first) - brightness(second))
    }
}
