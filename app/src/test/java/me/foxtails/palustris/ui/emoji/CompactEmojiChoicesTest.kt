package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactEmojiChoicesTest {
    private fun custom(shortcode: String, visible: Boolean = true) = CustomEmoji(
        shortcode = shortcode,
        animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
        submissionValue = ":$shortcode:",
        visibleInPicker = visible,
    )

    private fun build(
        catalog: List<CustomEmoji> = emptyList(),
        additional: List<EmojiChoice> = emptyList(),
        recents: List<String> = emptyList(),
        selected: Set<String> = emptySet(),
        preferences: EmojiPickerPreferences = EmojiPickerPreferences(),
    ) = buildCompactEmojiChoices(catalog, additional, recents, selected, preferences).map { it.submissionValue }

    @Test
    fun sectionsFollowSelectedFavoriteRecentPostSpecificStandardServerOrder() {
        val catalog = (1..6).map { custom("s$it") }
        val postSpecific = EmojiChoice(":remote:", ":remote:")
        val choices = build(
            catalog = catalog,
            additional = listOf(postSpecific),
            recents = listOf("🔥"),
            selected = setOf(":s6:"),
            preferences = EmojiPickerPreferences(pinnedEmoji = listOf(":s5:")),
        )

        assertEquals(listOf(":s6:", ":s5:", "🔥", ":remote:"), choices.take(4))
        val standardStart = choices.indexOf(":remote:") + 1
        assertTrue(choices[standardStart] in DefaultUnicodeEmojis)
        assertEquals(listOf(":s1:", ":s2:", ":s3:", ":s4:"), choices.takeLast(4))
    }

    @Test
    fun everySectionIsBoundedAndNoEmojiRepeats() {
        val catalog = (1..40).map { custom("s$it") }
        val postSpecific = (1..20).map { EmojiChoice(":remote$it:", ":remote$it:") }
        val recents = DefaultUnicodeEmojis.take(30)
        val pinned = catalog.map { it.submissionValue }.take(30)

        val choices = build(
            catalog = catalog,
            additional = postSpecific,
            recents = recents,
            preferences = EmojiPickerPreferences(pinnedEmoji = pinned),
        )

        assertEquals(choices.distinct(), choices)
        val limit = COMPACT_FAVORITE_LIMIT + COMPACT_RECENT_LIMIT + COMPACT_POST_SPECIFIC_LIMIT +
            COMPACT_STANDARD_LIMIT + COMPACT_SERVER_LIMIT
        assertTrue(choices.size <= limit)
        assertEquals(COMPACT_FAVORITE_LIMIT, choices.count { it.startsWith(":s") })
        assertEquals(COMPACT_POST_SPECIFIC_LIMIT, choices.count { it.startsWith(":remote") })
    }

    @Test
    fun selectedReactionsStayEvenWhenEveryLimitIsFull() {
        val catalog = (1..40).map { custom("s$it") }

        val choices = build(catalog = catalog, selected = setOf(":s40:"))

        assertEquals(":s40:", choices.first())
    }

    @Test
    fun hiddenServerEmojiAndUnknownSelectionsAreLeftOut() {
        val choices = build(
            catalog = listOf(custom("hidden", visible = false), custom("shown")),
            selected = setOf(":ghost:"),
        )

        assertTrue(":hidden:" !in choices)
        assertTrue(":ghost:" !in choices)
        assertTrue(":shown:" in choices)
    }

    @Test
    fun emptyCatalogStillOffersStandardEmoji() {
        val choices = build()

        assertEquals(COMPACT_STANDARD_LIMIT, choices.size)
        assertTrue(choices.all { it in DefaultUnicodeEmojis })
    }
}
