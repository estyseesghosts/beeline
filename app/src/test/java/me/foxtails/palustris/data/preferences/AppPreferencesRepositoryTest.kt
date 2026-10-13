package me.foxtails.palustris.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileAppPreferencesRepository
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppNavigationAnchor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesRepositoryTest {
    private lateinit var file: File

    @Before
    fun clearPreferencesFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.noBackupFilesDir, "app-preferences.json")
        file.delete()
        File("${file.path}.new").delete()
    }

    @Test
    fun malformedFileKeepsTheLoadErrorWithSafeDefaults() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("not json")

        val state = FileAppPreferencesRepository(ApplicationProvider.getApplicationContext())
            .observe()
            .first { it.loaded }

        assertEquals(AppColorScheme.System, state.preferences.colorScheme)
        assertNotNull(state.error)
    }

    @Test
    fun everySelectionPersistsAndRestoresByTag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLanguage.entries.forEach { language ->
            val repository = FileAppPreferencesRepository(context)
            repository.observe().first { it.loaded }
            repository.update { it.copy(language = language) }

            val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }
            assertEquals(language, restored.preferences.language)
            assertEquals(language.tag, restored.preferences.language.tag)
        }
    }

    @Test
    fun unknownStoredLanguageFallsBackToSystemDefault() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("""{"language":"Klingon"}""")

        val state = FileAppPreferencesRepository(ApplicationProvider.getApplicationContext())
            .observe()
            .first { it.loaded }

        assertEquals(AppLanguage.SystemDefault, state.preferences.language)
    }

    @Test
    fun navigationAnchorsUseIndependentPhysicalDefaultsAndPersistSeparately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val defaults = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppNavigationAnchor.Left, defaults.tabletNavigationAnchor)
        assertEquals(AppNavigationAnchor.Right, defaults.compactWideNavigationAnchor)

        file.writeText("""{"colorScheme":"Palette"}""")
        val legacy = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppNavigationAnchor.Left, legacy.tabletNavigationAnchor)
        assertEquals(AppNavigationAnchor.Right, legacy.compactWideNavigationAnchor)

        val repository = FileAppPreferencesRepository(context)
        repository.update {
            it.copy(
                tabletNavigationAnchor = AppNavigationAnchor.Right,
                compactWideNavigationAnchor = AppNavigationAnchor.Left,
            )
        }
        val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppNavigationAnchor.Right, restored.tabletNavigationAnchor)
        assertEquals(AppNavigationAnchor.Left, restored.compactWideNavigationAnchor)

        file.writeText("""{"tabletNavigationAnchor":"Unknown"}""")
        val safe = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppNavigationAnchor.Left, safe.tabletNavigationAnchor)
        assertEquals(AppNavigationAnchor.Right, safe.compactWideNavigationAnchor)
    }

    @Test
    fun alwaysTranslatePostsIsOffByDefaultAndRoundTripsTrue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(false, FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.alwaysTranslatePosts)

        // A file written by an earlier build has no key and keeps manual translation.
        file.writeText("""{"colorScheme":"Palette"}""")
        val legacy = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(false, legacy.alwaysTranslatePosts)

        FileAppPreferencesRepository(context).update { it.copy(alwaysTranslatePosts = true) }
        val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(true, restored.alwaysTranslatePosts)
    }

    @Test
    fun autoplayVideosDefaultsToTrueAndRoundTripsFalse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(true, FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.autoplayVideos)

        file.writeText("""{"colorScheme":"Palette"}""")
        val legacy = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(true, legacy.autoplayVideos)

        FileAppPreferencesRepository(context).update { it.copy(autoplayVideos = false) }
        val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(false, restored.autoplayVideos)
    }

    @Test
    fun combineRelatedHashtagsDefaultsToTrueAndRoundTripsFalse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(true, FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.combineRelatedHashtags)

        file.writeText("""{"colorScheme":"Palette"}""")
        val legacy = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(true, legacy.combineRelatedHashtags)

        FileAppPreferencesRepository(context).update { it.copy(combineRelatedHashtags = false) }
        val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(false, restored.combineRelatedHashtags)
    }

    @Test
    fun fontChoiceDefaultsToNoChoiceAndRoundTripsSans() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNull(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.font)

        // A file written by an earlier build has no key. No choice follows the language default.
        file.writeText("""{"colorScheme":"Palette"}""")
        val legacy = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertNull(legacy.font)

        FileAppPreferencesRepository(context).update { it.copy(font = AppFont.Sans) }
        val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppFont.Sans, restored.font)

        // Stored names from earlier builds still load under the renamed labels.
        file.writeText("""{"font":"Serif"}""")
        val serif = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppFont.Serif, serif.font)

        file.writeText("""{"font":"OpenDyslexic"}""")
        val openDyslexic = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertEquals(AppFont.OpenDyslexic, openDyslexic.font)

        // Stored names for the new bundled fonts round-trip.
        listOf(
            AppFont.Inter,
            AppFont.ZcoolKuaiLe,
            AppFont.ChocolateClassicalSans,
            AppFont.ChironHeiHk,
            AppFont.KiwiMaru,
            AppFont.Sarala,
        ).forEach { font ->
            FileAppPreferencesRepository(context).update { it.copy(font = font) }
            val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
            assertEquals(font, restored.font)
        }
    }

    @Test
    fun aVersionOneDeviceFontIsNoChoiceAndAnExplicitDeviceRoundTrips() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file.parentFile?.mkdirs()

        // Version 1 wrote Device on every save, also for a font the user never selected.
        file.writeText("""{"version":1,"font":"Device","colorScheme":"Palette"}""")
        val versionOne = FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences
        assertNull(versionOne.font)
        assertEquals(AppColorScheme.Palette, versionOne.colorScheme)

        file.writeText("""{"font":"Device"}""")
        assertNull(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.font)

        // Every other version 1 name is an explicit choice.
        file.writeText("""{"version":1,"font":"Inter"}""")
        assertEquals(AppFont.Inter, FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.font)

        // An unrelated save keeps no choice and writes no font key.
        file.writeText("""{"version":1,"font":"Device"}""")
        FileAppPreferencesRepository(context).update { it.copy(request60Hz = true) }
        assertFalse(JSONObject(file.readText()).has("font"))
        assertNull(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.font)

        // The user selects Device default. The current version stores it as an explicit choice.
        FileAppPreferencesRepository(context).update { it.copy(font = AppFont.Device) }
        assertEquals(AppFont.Device, FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.font)

        // An unknown stored name is no choice.
        file.writeText("""{"version":2,"font":"Papyrus"}""")
        assertNull(FileAppPreferencesRepository(context).observe().first { it.loaded }.preferences.font)
    }

    @Test
    fun successfulUpdateClearsTheLoadError() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("not json")
        val repository = FileAppPreferencesRepository(ApplicationProvider.getApplicationContext())
        repository.observe().first { it.loaded }

        repository.update { it.copy(colorScheme = AppColorScheme.SystemMonochrome) }

        val state = repository.observe().first { it.preferences.colorScheme == AppColorScheme.SystemMonochrome }
        assertEquals(null, state.error)
    }
}
