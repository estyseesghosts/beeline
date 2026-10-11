package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PostingVisibilityPolicyTest {
    private val all = ServerCapabilities(audiences = Audience.entries.toSet())

    private fun reply(target: Audience, repliesUnlisted: Boolean) = PostingVisibilityPolicy.forReply(
        PostPreferences(repliesUnlisted = repliesUnlisted),
        all,
        target,
    )

    @Test
    fun replyKeepsTheTargetAudienceByDefault() {
        Audience.entries.forEach { assertEquals(it, reply(it, repliesUnlisted = false)) }
    }

    @Test
    fun repliesUnlistedNarrowsOnlyAPublicTarget() {
        assertEquals(Audience.Unlisted, reply(Audience.Public, repliesUnlisted = true))
        assertEquals(Audience.Unlisted, reply(Audience.Unlisted, repliesUnlisted = true))
        assertEquals(Audience.Followers, reply(Audience.Followers, repliesUnlisted = true))
        assertEquals(Audience.Direct, reply(Audience.Direct, repliesUnlisted = true))
    }
}
