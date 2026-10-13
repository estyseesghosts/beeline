package me.foxtails.palustris.data.preferences

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppGrammaticalGender
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppLayoutDirection
import me.foxtails.palustris.domain.AppNavigationAnchor
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.AppTextSize
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation
import me.foxtails.palustris.di.IoDispatcher
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, non-secret global preferences stored outside device backup. */
class FileAppPreferencesRepository(
    context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppPreferencesRepository {
    private val file = File(context.noBackupFilesDir, "app-preferences.json")
    private val messages = AppMessages.from(context)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val values = MutableStateFlow(AppPreferencesState())

    init {
        scope.launch {
            val loaded = runCatching { read() }
            values.value = loaded.fold(
                onSuccess = { AppPreferencesState(loaded = true, preferences = it) },
                onFailure = { error ->
                    AppPreferencesState(
                        loaded = true,
                        preferences = AppPreferences(),
                        error = error.message ?: messages.preferencesLoadFailed(),
                    )
                },
            )
            ready.complete(Unit)
        }
    }

    override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        ready.await()
        mutex.withLock {
            val current = values.value.preferences
            val next = transform(current)
            try {
                withContext(ioDispatcher) { persist(next) }
                values.value = AppPreferencesState(loaded = true, preferences = next)
            } catch (error: Throwable) {
                // Cancellation asks the write to stop; it is not a save failure.
                if (error is kotlinx.coroutines.CancellationException) throw error
                values.value = values.value.copy(
                    loaded = true,
                    error = error.message ?: messages.preferencesSaveFailed(),
                )
                throw error
            }
        }
    }

    private fun read(): AppPreferences {
        if (!file.exists()) return AppPreferences()
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val warning = json.optJSONObject("contentWarningRules")
        val storedColorScheme = json.optString("colorScheme")
        return AppPreferences(
            colorScheme = when (storedColorScheme) {
                "SystemMonochrome", "Monochrome" -> AppColorScheme.SystemMonochrome
                "Palette", "Pastel", "Vibrant" -> AppColorScheme.Palette
                else -> AppColorScheme.System
            },
            colorPalette = when {
                json.has("colorPalette") -> paletteOrDefault(json, "colorPalette", AppColorPalette.PastelIndigo)
                storedColorScheme == "Vibrant" -> AppColorPalette.VibrantBlue
                else -> AppColorPalette.PastelIndigo
            },
            background = enumOrDefault(json, "background", AppBackground.Default),
            textSize = enumOrDefault(json, "textSize", AppTextSize.Device),
            font = storedFontChoice(json),
            request60Hz = json.optBoolean("request60Hz", false),
            // A missing key reads false, so a file from an earlier build keeps its blur.
            disableBlurEffects = json.optBoolean("disableBlurEffects", true),
            language = AppLanguage.fromNameOrDefault(json.optString("language")),
            // A missing key keeps Masculine, the form of every base catalog.
            grammaticalGender = enumOrDefault(json, "grammaticalGender", AppGrammaticalGender.Masculine),
            // A missing key keeps System, so the device direction survives an upgrade unchanged.
            layoutDirection = enumOrDefault(json, "layoutDirection", AppLayoutDirection.System),
            tabletNavigationAnchor = enumOrDefault(json, "tabletNavigationAnchor", AppNavigationAnchor.Left),
            compactWideNavigationAnchor = enumOrDefault(json, "compactWideNavigationAnchor", AppNavigationAnchor.Right),
            cleanTrackingParameters = json.optBoolean("cleanTrackingParameters", false),
            // A missing key reads true, so existing users get the default behavior.
            combineRelatedHashtags = json.optBoolean("combineRelatedHashtags", true),
            // A missing key reads true, so existing users get the default behavior.
            autoplayVideos = json.optBoolean("autoplayVideos", true),
            // A missing key reads false, so a file from an earlier build keeps manual translation.
            alwaysTranslatePosts = json.optBoolean("alwaysTranslatePosts", false),
            contentWarningRules = warning?.toContentWarningRules() ?: ContentWarningRules(),
            hiddenContentPresentation = enumOrDefault(json, "hiddenContentPresentation", HiddenContentPresentation.Placeholder),
        )
    }

    private fun persist(preferences: AppPreferences) {
        file.parentFile?.mkdirs()
        val json = preferences.toJson()
        val temporary = File("${file.path}.new")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private const val FILE_VERSION = 2

/** The stored form of the preferences. A font without a choice writes no key. */
private fun AppPreferences.toJson(): JSONObject = JSONObject()
    .put("version", FILE_VERSION)
    .put("colorScheme", colorScheme.name)
    .put("colorPalette", colorPalette.name)
    .put("background", background.name)
    .put("textSize", textSize.name)
    .putOpt("font", font?.name)
    .put("request60Hz", request60Hz)
    .put("disableBlurEffects", disableBlurEffects)
    .put("language", language.name)
    .put("grammaticalGender", grammaticalGender.name)
    .put("layoutDirection", layoutDirection.name)
    .put("tabletNavigationAnchor", tabletNavigationAnchor.name)
    .put("compactWideNavigationAnchor", compactWideNavigationAnchor.name)
    .put("cleanTrackingParameters", cleanTrackingParameters)
    .put("combineRelatedHashtags", combineRelatedHashtags)
    .put("autoplayVideos", autoplayVideos)
    .put("alwaysTranslatePosts", alwaysTranslatePosts)
    .put("contentWarningRules", contentWarningRules.toJson())
    .put("hiddenContentPresentation", hiddenContentPresentation.name)

/** The first file version in which a stored `Device` font is an explicit choice. */
private const val EXPLICIT_DEVICE_FONT_VERSION = 2

/**
 * The stored font choice, or null for no choice. A missing key or an unknown name is no choice.
 * Version 1 wrote `Device` on every save, also for a font the user never selected. Therefore a
 * version 1 `Device` is no choice, and the font follows the language default. Every other stored
 * name is an explicit choice in each version.
 */
private fun storedFontChoice(json: JSONObject): AppFont? {
    val font = runCatching { enumValueOf<AppFont>(json.optString("font")) }.getOrNull() ?: return null
    val explicitDevice = json.optInt("version", 1) >= EXPLICIT_DEVICE_FONT_VERSION
    return font.takeUnless { it == AppFont.Device && !explicitDevice }
}

/** Deterministic repository for previews and constructor-based tests. */
class InMemoryAppPreferencesRepository(
    initial: AppPreferences = AppPreferences(),
) : AppPreferencesRepository {
    private val values = MutableStateFlow(AppPreferencesState(loaded = true, preferences = initial))

    override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        values.value = values.value.copy(preferences = transform(values.value.preferences), error = null)
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(json: JSONObject, key: String, default: T): T =
    runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(default)

/**
 * A stored palette name, with removed names mapped to their replacement.
 * Removed names must not silently reset to the default.
 */
internal fun migratedPaletteName(stored: String): String = when (stored) {
    "PastelOrange", "PastelYellow" -> AppColorPalette.PastelGold.name
    "VibrantOrange", "VibrantYellow" -> AppColorPalette.VibrantGold.name
    "PastelViolet" -> AppColorPalette.PastelIndigo.name
    "VibrantViolet" -> AppColorPalette.VibrantIndigo.name
    else -> stored
}

private fun paletteOrDefault(json: JSONObject, key: String, default: AppColorPalette): AppColorPalette =
    runCatching { enumValueOf<AppColorPalette>(migratedPaletteName(json.optString(key))) }.getOrDefault(default)

private fun ContentWarningRules.toJson(): JSONObject = JSONObject()
    .put("hideAll", hideAll)
    .put("expandAll", expandAll)
    .put("hideKeywords", JSONArray(hideKeywords))
    .put("hideHashtags", JSONArray(hideHashtags))
    .put("expandKeywords", JSONArray(expandKeywords))
    .put("expandHashtags", JSONArray(expandHashtags))

private fun JSONObject.toContentWarningRules(): ContentWarningRules = ContentWarningRules(
    hideAll = optBoolean("hideAll"),
    expandAll = optBoolean("expandAll"),
    hideKeywords = stringList("hideKeywords"),
    hideHashtags = stringList("hideHashtags"),
    expandKeywords = stringList("expandKeywords"),
    expandHashtags = stringList("expandHashtags"),
)

private fun JSONObject.stringList(key: String): List<String> = optJSONArray(key)?.let { values ->
    (0 until values.length()).mapNotNull { values.optString(it).trim().takeIf(String::isNotEmpty) }
}.orEmpty()
