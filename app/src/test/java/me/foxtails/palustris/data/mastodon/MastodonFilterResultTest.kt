package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.PostFilterPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonFilterResultTest {
    private fun status(filtered: JSONArray?, reblog: JSONObject? = null) = JSONObject()
        .put("id", "s1")
        .put("created_at", "2026-10-13T10:00:00Z")
        .put("account", JSONObject().put("id", "a1").put("username", "author").put("acct", "author"))
        .put("content", "<p>Spoilers for the finale</p>")
        .put("spoiler_text", "TV")
        .put("visibility", "public")
        .put("media_attachments", JSONArray().put(JSONObject().put("id", "m1").put("type", "image").put("url", "https://example.org/m.png")))
        .apply { filtered?.let { put("filtered", it) } }
        .apply { reblog?.let { put("reblog", it) } }

    private fun result(title: String, action: String?, vararg contexts: String) = JSONObject()
        .put("filter", JSONObject()
            .put("id", "f-$title")
            .put("title", title)
            .put("context", JSONArray(contexts.toList()))
            .apply { action?.let { put("filter_action", it) } })
        .put("keyword_matches", JSONArray().put("finale"))

    @Test
    fun filteredStatusKeepsItsBodyAndCarriesTheMatch() {
        val post = MastodonMapper.post(status(JSONArray().put(result("Spoilers", "warn", "home", "public"))), "https://example.org")

        assertEquals("Spoilers for the finale", post.text.trim())
        assertEquals("TV", post.contentWarning)
        assertEquals(1, post.attachments.size)
        val match = post.serverFilters.single()
        assertEquals("Spoilers", match.title)
        assertEquals(PostFilterAction.Collapse, match.action)
        assertEquals(setOf(FilterContext.Home, FilterContext.Public), match.contexts)
    }

    @Test
    fun hideRemovesAndBlurAndUnknownActionsCollapse() {
        assertEquals(PostFilterAction.Remove, MastodonFilterMapper.action("hide"))
        assertEquals(PostFilterAction.Collapse, MastodonFilterMapper.action("warn"))
        assertEquals(PostFilterAction.Collapse, MastodonFilterMapper.action("blur"))
        assertEquals(PostFilterAction.Collapse, MastodonFilterMapper.action("future-action"))
        assertEquals(PostFilterAction.Collapse, MastodonFilterMapper.action(""))
    }

    @Test
    fun contextLimitsWhereTheFilterApplies() {
        val post = MastodonMapper.post(status(JSONArray().put(result("Home only", "hide", "home"))), "https://example.org")

        assertEquals(PostFilterAction.Remove, PostFilterPolicy.evaluate(post, FilterContext.Home)?.action)
        assertNull(PostFilterPolicy.evaluate(post, FilterContext.Thread))
        assertNull(PostFilterPolicy.evaluate(post, FilterContext.Notifications))
    }

    @Test
    fun removeWinsAndTheNoticeNamesTheWinningFilters() {
        val post = MastodonMapper.post(
            status(JSONArray().put(result("Spoilers", "warn", "home")).put(result("Politics", "hide", "home")).put(result("Sports", "hide", "home"))),
            "https://example.org",
        )

        val verdict = PostFilterPolicy.evaluate(post, FilterContext.Home)!!
        assertEquals(PostFilterAction.Remove, verdict.action)
        assertEquals("Politics, Sports", verdict.reason)
    }

    @Test
    fun missingOrUnknownContextsApplyEverywhereAndEntriesWithoutFilterAreSkipped() {
        val post = MastodonMapper.post(
            status(JSONArray().put(result("Any", null, "somewhere-new")).put(JSONObject().put("keyword_matches", JSONArray()))),
            "https://example.org",
        )

        val match = post.serverFilters.single()
        assertEquals(FilterContext.entries.toSet(), match.contexts)
        assertEquals(PostFilterAction.Collapse, PostFilterPolicy.evaluate(post, FilterContext.Account)?.action)
    }

    @Test
    fun boostCarriesTheFilterOfTheBoostedStatus() {
        val inner = status(JSONArray().put(result("Spoilers", "warn", "home"))).put("id", "inner")
        val outer = status(null, reblog = inner).put("id", "outer")

        val post = MastodonMapper.post(outer, "https://example.org")

        assertTrue(post.resharedBy != null)
        assertEquals("Spoilers", post.serverFilters.single().title)
    }

    @Test
    fun unfilteredStatusHasNoVerdict() {
        val post = MastodonMapper.post(status(null), "https://example.org")

        assertTrue(post.serverFilters.isEmpty())
        FilterContext.entries.forEach { assertNull(PostFilterPolicy.evaluate(post, it)) }
    }
}
