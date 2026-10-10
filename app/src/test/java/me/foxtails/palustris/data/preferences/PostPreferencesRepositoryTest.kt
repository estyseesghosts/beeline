package me.foxtails.palustris.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FilePostPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.UploadCompression
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostPreferencesRepositoryTest {
    private lateinit var file: File
    private val first = AccountId(Connection("https://one.example", Protocol.MISSKEY), "first")
    private val second = AccountId(Connection("https://two.example", Protocol.MISSKEY), "second")

    @Before
    fun clearPreferencesFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.noBackupFilesDir, "post-preferences.json")
        file.delete()
        File("${file.path}.new").delete()
        File("${file.path}.bak").delete()
    }

    @Test
    fun valuesAreAccountScopedAndSurviveReload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = FilePostPreferencesRepository(context)
        assertEquals(DEFAULT_FAVOURITE_EMOJI, repository.observe(first).first().favouriteEmoji)

        repository.update(first) { PostPreferences(":blobcat:", contentWarningRules = ContentWarningRules(hideKeywords = listOf("spoiler")), localMutedHashtags = listOf("#cats")) }
        repository.update(second) { PostPreferences("🎉") }
        assertEquals(":blobcat:", repository.observe(first).first().favouriteEmoji)
        assertEquals("🎉", repository.observe(second).first().favouriteEmoji)
        assertTrue(file.exists())
        assertFalse(file.readText().contains("token", ignoreCase = true))

        val reloaded = FilePostPreferencesRepository(context)
        assertEquals(":blobcat:", reloaded.observe(first).first().favouriteEmoji)
        assertEquals(listOf("spoiler"), reloaded.observe(first).first().contentWarningRules.hideKeywords)
        assertEquals(listOf("cats"), reloaded.observe(first).first().localMutedHashtags)
        assertEquals("🎉", reloaded.observe(second).first().favouriteEmoji)
    }

    @Test
    fun invalidValuesFallBackAndRemovalDoesNotAffectOtherAccounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = FilePostPreferencesRepository(context)
        repository.update(first) { PostPreferences("\u0000invalid") }
        repository.update(second) { PostPreferences("👍") }

        assertEquals(DEFAULT_FAVOURITE_EMOJI, repository.observe(first).first().favouriteEmoji)
        repository.remove(first)
        assertEquals(DEFAULT_FAVOURITE_EMOJI, repository.observe(first).first().favouriteEmoji)
        assertEquals("👍", repository.observe(second).first().favouriteEmoji)
        assertNotEquals(first, second)
    }

    @Test
    fun fileAndMemoryRepositoriesApplyTheSameNormalization() = runBlocking {
        val input = PostPreferences(
            favouriteEmoji = "  :blobcat:  ",
            contentWarningRules = ContentWarningRules(
                hideKeywords = listOf(" Spoiler ", "spoiler"),
                hideHashtags = listOf("#Cats", "cats"),
            ),
            localMutedHashtags = listOf("#Dogs", " dogs "),
        )
        val fileRepository = FilePostPreferencesRepository(ApplicationProvider.getApplicationContext())
        val memoryRepository = InMemoryPostPreferencesRepository()

        fileRepository.update(first) { input }
        memoryRepository.update(first) { input }

        assertEquals(memoryRepository.observe(first).first(), fileRepository.observe(first).first())
    }

    @Test
    fun uploadCompressionDefaultsToAlways() = runBlocking {
        val repository = FilePostPreferencesRepository(ApplicationProvider.getApplicationContext())

        assertEquals(UploadCompression.Always, repository.observe(first).first().uploadCompression)
    }

    @Test
    fun uploadCompressionRoundTripsThroughTheFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilePostPreferencesRepository(context).update(first) { it.copy(uploadCompression = UploadCompression.Ask) }
        FilePostPreferencesRepository(context).update(second) { it.copy(uploadCompression = UploadCompression.Never) }

        val reloaded = FilePostPreferencesRepository(context)
        assertEquals(UploadCompression.Ask, reloaded.observe(first).first().uploadCompression)
        assertEquals(UploadCompression.Never, reloaded.observe(second).first().uploadCompression)
    }

    @Test
    fun aFileWrittenBeforeTheSettingExistedLoadsWithTheDefault() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilePostPreferencesRepository(context).update(first) { it.copy(repliesUnlisted = true) }
        val old = JSONObject(file.readText())
        old.getJSONObject("accounts").keys().forEach { key ->
            old.getJSONObject("accounts").getJSONObject(key).remove("uploadCompression")
        }
        file.writeText(old.toString())

        val reloaded = FilePostPreferencesRepository(context).observe(first).first()

        assertTrue(reloaded.repliesUnlisted)
        assertEquals(UploadCompression.Always, reloaded.uploadCompression)
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FilePostPreferencesRepository(context).update(first) { it }
        val stored = JSONObject(file.readText())
        stored.getJSONObject("accounts").keys().forEach { key ->
            stored.getJSONObject("accounts").getJSONObject(key).put("uploadCompression", "Sometimes")
        }
        file.writeText(stored.toString())

        assertEquals(
            UploadCompression.Always,
            FilePostPreferencesRepository(context).observe(first).first().uploadCompression,
        )
    }
}
