package me.foxtails.palustris.domain

import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.ui.posts.postHashtags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentWarningPolicyTest {
    @Test
    fun bodyTextAndInlineHashtagsParticipateInWarningRules() {
        val rules = ContentWarningRules(
            hideKeywords = listOf("unsafe"),
            hideHashtags = listOf("blocked"),
        )

        assertEquals(
            ContentWarningDecision.Hidden,
            ContentWarningPolicy.decide("spoiler", listOf("#blocked"), rules, bodyText = "safe text"),
        )
        assertEquals(
            ContentWarningDecision.Hidden,
            ContentWarningPolicy.decide("spoiler", emptyList(), rules, bodyText = "unsafe text"),
        )
    }

    @Test
    fun semanticHashtagExtractionKeepsInlineTagsForRules() {
        assertEquals(listOf("#inline", "#terminal"), postHashtags("Text #inline\n#terminal"))
        assertTrue(ContentWarningPolicy.matchesHashtagMute(listOf("#inline"), listOf("inline")))
    }

    @Test
    fun mutedHashtagMatchNormalizesTagsAndKeepsQuoteOrder() {
        assertEquals(" #Tag ", ContentWarningPolicy.firstMatchingMutedHashtag(listOf(" #Tag "), setOf("tag")))
        assertEquals("tag", ContentWarningPolicy.firstMatchingMutedHashtag(listOf("tag"), setOf("#Tag")))
        assertEquals("#coturn", ContentWarningPolicy.firstMatchingMutedHashtag(listOf("#coturn"), setOf("CoTuRn")))
        assertEquals("FOO", ContentWarningPolicy.firstMatchingMutedHashtag(listOf("FOO"), setOf("#foo")))
        assertEquals("#first", ContentWarningPolicy.firstMatchingMutedHashtag(listOf("#first", "#Second"), setOf("second", "first")))
        assertEquals(null, ContentWarningPolicy.firstMatchingMutedHashtag(listOf("#tagged"), setOf("tag")))
        assertEquals(null, ContentWarningPolicy.firstMatchingMutedHashtag(listOf("#alpha"), setOf("beta")))
        assertEquals(null, ContentWarningPolicy.firstMatchingMutedHashtag(listOf("#tag"), emptySet()))
        assertEquals(null, ContentWarningPolicy.firstMatchingMutedHashtag(emptyList(), setOf("tag")))
        assertEquals("#Second", ContentWarningPolicy.firstMatchingMutedHashtag(listOf("#Second", "#first"), setOf("first", "second")))
    }
}
