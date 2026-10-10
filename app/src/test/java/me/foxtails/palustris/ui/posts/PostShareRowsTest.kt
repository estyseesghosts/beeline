package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnPostCapabilities
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.TranslationCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class PostShareRowsTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val me = AccountId(connection, "me")
    private val other = AccountId(connection, "other")

    private fun post(author: AccountId, language: String? = "fr") = Post(
        id = EntityId(connection.origin, "p"),
        author = Account(author, "A", "@a@example.org"),
        text = "bonjour",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        language = language,
    )

    private fun capabilities(
        delete: CapabilityStatus = CapabilityStatus.Supported,
        edit: CapabilityStatus = CapabilityStatus.Unknown,
        translation: CapabilityStatus = CapabilityStatus.Unknown,
    ) = ServerCapabilities(
        ownPosts = OwnPostCapabilities(delete = delete, edit = edit),
        translation = TranslationCapability(translation),
    )

    @Test
    fun ownPostShowsDeleteWhenSupportedAndHidesEditUntilSupported() {
        val rows = postShareRows(post(me), me, capabilities(), "en")

        assertEquals(PostShareRows(own = true, edit = false, delete = true, translate = false), rows)
    }

    @Test
    fun ownPostWithNoSupportedRowsLeavesShareAlone() {
        val rows = postShareRows(post(me), me, capabilities(delete = CapabilityStatus.Unsupported), "en")

        assertEquals(PostShareRows(own = true, edit = false, delete = false, translate = false), rows)
    }

    @Test
    fun editShowsOnlyWhenSupported() {
        val rows = postShareRows(post(me), me, capabilities(edit = CapabilityStatus.Supported), "en")

        assertEquals(true, rows.edit)
    }

    @Test
    fun someoneElsesPostNeverOffersOwnPostRows() {
        val rows = postShareRows(
            post(other),
            me,
            capabilities(edit = CapabilityStatus.Supported, translation = CapabilityStatus.Supported),
            "en",
        )

        assertEquals(PostShareRows(own = false, edit = false, delete = false, translate = true), rows)
    }

    @Test
    fun translateHidesWhenThePostAlreadyMatchesTheAppLanguage() {
        val rows = postShareRows(post(other, language = "en"), me, capabilities(translation = CapabilityStatus.Supported), "en")

        assertEquals(false, rows.translate)
    }
}
