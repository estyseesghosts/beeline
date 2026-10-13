package me.foxtails.palustris.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostScript
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.posts.BubblePlacement
import me.foxtails.palustris.ui.posts.PostHashtagBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A post with a known script renders in one font, and so do its labels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostFontStyleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)

    private fun post(text: String, contentWarning: String? = null): Post = Post(
        id = EntityId("https://example.org", "1"),
        author = Account(AccountId(connection, "person"), "Person", "@person@example.org"),
        text = text,
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
        contentWarning = contentWarning,
    )

    private fun show(appFont: AppFont, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme(AppPreferences(font = appFont)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().layoutInput.style
    }

    @Test
    fun textOutsideAPostDecidesForItself() {
        var japanese: TextStyle? = null
        var english: TextStyle? = null
        show(AppFont.Inter) {
            japanese = TextStyle().forPostText("今日はいい天気")
            english = TextStyle().forPostText("Good weather today")
        }

        // An Inter user reads a Japanese text in the system font with Japanese glyph forms.
        assertEquals(FontFamily.Default, japanese!!.fontFamily)
        assertEquals(LocaleList("ja-JP"), japanese!!.localeList)
        // A text without a script keeps its style, so it keeps the font of the application.
        assertNull(english!!.fontFamily)
        assertNull(english!!.localeList)
    }

    @Test
    fun aPostKeepsTheApplicationFontWhenItIsTheFontOfTheScript() {
        var japanese: TextStyle? = null
        var chinese: TextStyle? = null
        show(AppFont.KiwiMaru) {
            japanese = TextStyle().forPostText("今日はいい天気")
            chinese = TextStyle().forPostText("這是我們的時間")
        }

        assertEquals(appFontFamily(AppFont.KiwiMaru), japanese!!.fontFamily)
        // A Kiwi Maru user reads Chinese in the system font, not in Kiwi Maru with fallback glyphs.
        assertEquals(FontFamily.Default, chinese!!.fontFamily)
        assertEquals(LocaleList("zh-Hant-TW"), chinese!!.localeList)
    }

    @Test
    fun eachScriptUsesItsBundledFontOnlyForItsOwnUser() {
        val expected = mapOf(
            "佢喺屋企" to (AppFont.ChironHeiHk to "zh-Hant-HK"),
            "這是我們的時間" to (AppFont.ChocolateClassicalSans to "zh-Hant-TW"),
            "这是我们的时间" to (AppFont.ZcoolKuaiLe to "zh-Hans-CN"),
        )
        expected.forEach { (text, fontAndLocale) ->
            val (font, locale) = fontAndLocale
            var own: TextStyle? = null
            show(font) { own = TextStyle().forPostText(text) }
            assertEquals(text, appFontFamily(font), own!!.fontFamily)
            assertEquals(text, LocaleList(locale), own!!.localeList)

            var other: TextStyle? = null
            show(AppFont.Inter) { other = TextStyle().forPostText(text) }
            assertEquals(text, FontFamily.Default, other!!.fontFamily)
            assertEquals(text, LocaleList(locale), other!!.localeList)
        }
    }

    @Test
    fun thePostScopeDecidesForEveryTextAndLabelOfThePost() {
        var warning: TextStyle? = null
        var label: TextStyle? = null
        var outside: TextStyle? = null
        show(AppFont.Inter) {
            outside = TextStyle().forPostLabel()
            // The warning alone proves no script. The body of the post does.
            PostScriptProvider(post(text = "今日はいい天気", contentWarning = "天気")) {
                warning = TextStyle().forPostText("天気")
                label = TextStyle().forPostLabel()
            }
        }

        assertEquals(LocaleList("ja-JP"), warning!!.localeList)
        assertEquals(FontFamily.Default, label!!.fontFamily)
        assertEquals(LocaleList("ja-JP"), label!!.localeList)
        // A label outside every post keeps its style.
        assertNull(outside!!.localeList)
    }

    @Test
    fun aPostWithoutAScriptKeepsTheApplicationFontForItsTexts() {
        var body: TextStyle? = null
        show(AppFont.KiwiMaru) {
            // The scope of an unclassified post wins over the text, so a Japanese word in a label
            // or a warning does not split the post into two fonts.
            PostScriptProvider(post(text = "Good weather today")) {
                body = TextStyle().forPostText("今日は")
            }
        }

        assertNull(body!!.fontFamily)
        assertNull(body!!.localeList)
    }

    @Test
    fun theRendererDrawsTheBodyAndItsHashtagBubbleInTheFontOfThePost() {
        var bodyStyle: TextStyle? = null
        show(AppFont.Inter) {
            PostScriptProvider(post(text = "今日はいい天気 #写真")) {
                InlineEmojiText(
                    text = "今日はいい天気 #写真",
                    emoji = emptyMap(),
                    enableInlineEntities = true,
                    onTextLayout = { bodyStyle = it.layoutInput.style },
                )
            }
        }

        assertEquals(FontFamily.Default, bodyStyle!!.fontFamily)
        assertEquals(LocaleList("ja-JP"), bodyStyle!!.localeList)
        val labels = compose.onAllNodesWithTag("entity_label", useUnmergedTree = true)
        assertEquals(1, labels.fetchSemanticsNodes().size)
        // The hashtag holds only shared characters. The post gives it the Japanese font.
        assertEquals(LocaleList("ja-JP"), labels[0].textStyle().localeList)
        assertEquals(FontFamily.Default, labels[0].textStyle().fontFamily)
    }

    @Test
    fun theHashtagBubbleOfAPostRendersItsPillsInTheFontOfThePost() {
        show(AppFont.KiwiMaru) {
            PostHashtagBubble(
                script = PostScript.Japanese,
                hashtags = listOf("#写真", "#photo"),
                maxHeight = 600.dp,
                openingKey = 1L,
                side = BubblePlacement.Below,
                onSelected = {},
            )
        }

        listOf("写真", "photo").forEach { label ->
            val style = compose.onNodeWithText(label, useUnmergedTree = true).textStyle()
            assertEquals(label, appFontFamily(AppFont.KiwiMaru), style.fontFamily)
            assertEquals(label, LocaleList("ja-JP"), style.localeList)
        }
    }
}
