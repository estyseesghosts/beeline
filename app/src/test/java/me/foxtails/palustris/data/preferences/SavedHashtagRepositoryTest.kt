package me.foxtails.palustris.data.preferences

import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileSavedHashtagRepository
import me.foxtails.palustris.data.preferences.InMemorySavedHashtagRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SavedHashtags
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.stripHashtagMarks
import me.foxtails.palustris.domain.validateExactHashtag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedHashtagRepositoryTest {
    @Test
    fun validatorAcceptsOptionalHashAndRejectsInvalidSyntax() {
        assertEquals("#Photography", validateExactHashtag("  #Photography  "))
        assertEquals("Photography", validateExactHashtag("Photography"))
        assertTrue(isExactHashtag("猫_2026"))
        assertFalse(isExactHashtag("#two words"))
        assertFalse(isExactHashtag("#"))
        assertFalse(isExactHashtag("#one\ntwo"))
        assertFalse(isExactHashtag("##one"))
    }

    @Test
    fun inMemoryRepositoryNormalizesDuplicatesWithoutChangingFirstSpelling() = runBlocking {
        val repository = InMemorySavedHashtagRepository()
        val account = account(Protocol.MISSKEY)
        repository.update(account) {
            SavedHashtags(listOf("#Cats", "#cats", "dogs"))
        }

        assertEquals(listOf("Cats", "dogs"), repository.observe(account).first().tags)
        assertEquals(hashtagIdentity("#Cats"), hashtagIdentity("cats"))
    }

    @Test
    fun fileRepositorySurvivesRecreationAndScopesProtocol() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.noBackupFilesDir, "photo-grid-preferences.json").delete()
        val misskey = account(Protocol.MISSKEY)
        val mastodon = account(Protocol.MASTODON)
        FileSavedHashtagRepository(context).update(misskey) {
            SavedHashtags(listOf("#Travel"))
        }

        val recreated = FileSavedHashtagRepository(context)
        assertEquals(listOf("Travel"), recreated.observe(misskey).first { it.tags.isNotEmpty() }.tags)
        assertTrue(recreated.observe(mastodon).first().tags.isEmpty())
    }

    @Test
    fun fileWrittenWithMarksLoadsWithoutThem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.noBackupFilesDir, "photo-grid-preferences.json")
        val misskey = account(Protocol.MISSKEY)
        file.delete()
        FileSavedHashtagRepository(context).update(misskey) { SavedHashtags(listOf("Seed")) }
        file.writeText(file.readText().replace("Seed", "#photo\",\"#Photo\",\"art"))

        val loaded = FileSavedHashtagRepository(context).observe(misskey).first { it.tags.isNotEmpty() }
        assertEquals(listOf("photo", "art"), loaded.tags)
    }

    @Test
    fun stripHashtagMarksRemovesEveryMark() {
        assertEquals("cats", stripHashtagMarks("#cats"))
        assertEquals("cats", stripHashtagMarks("＃c#a＃ts#"))
        assertEquals("cats", stripHashtagMarks("cats"))
    }

    private fun account(protocol: Protocol) = AccountId(
        Connection("https://preferences.example", protocol),
        "account",
    )
}
