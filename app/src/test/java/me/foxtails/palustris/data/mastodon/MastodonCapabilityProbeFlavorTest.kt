package me.foxtails.palustris.data.mastodon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import javax.crypto.spec.SecretKeySpec
import me.foxtails.palustris.data.auth.AccountFileStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.BubbleRoute
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.LocalOnlyRoute
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReactionRoute
import me.foxtails.palustris.domain.ReportRoute
import me.foxtails.palustris.domain.ScheduleRoute
import me.foxtails.palustris.domain.SchedulingCapability
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ServerFlavor
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonCapabilityProbeFlavorTest {
    @Test
    fun parseFlavorReadsTheCompatibilityMarkerFirst() {
        assertEquals(ServerFlavor.Pleroma, MastodonCapabilityProbe.parseFlavor(instance("3.5.3 (compatible; Pleroma 2.6.50)")))
        assertEquals(ServerFlavor.Akkoma, MastodonCapabilityProbe.parseFlavor(instance("2.7.2 (compatible; Akkoma 3.10.4)")))
        assertEquals(ServerFlavor.Pixelfed, MastodonCapabilityProbe.parseFlavor(instance("3.5.3 (compatible; Pixelfed 0.12.5)")))
    }

    @Test
    fun parseFlavorDetectsGlitchAndHometown() {
        val glitch = instance("4.2.0")
            .put("api_versions", JSONObject().put("mastodon", 2).put("glitch", "4.2.0"))
        assertEquals(ServerFlavor.Glitch, MastodonCapabilityProbe.parseFlavor(glitch))

        assertEquals(
            ServerFlavor.Hometown,
            MastodonCapabilityProbe.parseFlavor(instance("4.0.0+hometown-1.1.0")),
        )
    }

    @Test
    fun parseFlavorFallsBackToMastodonOrUnknown() {
        assertEquals(ServerFlavor.Mastodon, MastodonCapabilityProbe.parseFlavor(instance("4.6.0")))
        assertEquals(ServerFlavor.Unknown, MastodonCapabilityProbe.parseFlavor(instance("")))
        assertEquals(ServerFlavor.Unknown, MastodonCapabilityProbe.parseFlavor(JSONObject()))
        val machineOnly = JSONObject().put("api_versions", JSONObject().put("mastodon", 8))
        assertEquals(ServerFlavor.Mastodon, MastodonCapabilityProbe.parseFlavor(machineOnly))
    }

    @Test
    fun pixelfedProbeYieldsThePixelfedCapabilitySet() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(instance("3.5.3 (compatible; Pixelfed 0.12.5)"))

        assertEquals(ServerFlavor.Pixelfed, capabilities.flavor)
        assertEquals(14, capabilities.capabilitySchemaVersion)
        assertEquals(CapabilityStatus.Unsupported, capabilities.notifications.webPush)
        assertEquals(CapabilityStatus.Unsupported, capabilities.notifications.streaming)
        assertEquals(CapabilityStatus.Unsupported, capabilities.notifications.dismissal)
        assertEquals(CapabilityStatus.Unsupported, capabilities.notifications.unread)
        assertEquals(CapabilityStatus.Unknown, capabilities.notifications.listing)
        assertEquals(CapabilityStatus.Unsupported, capabilities.feedChips.lists)
        assertEquals(CapabilityStatus.Unknown, capabilities.feedChips.followedTags)
        assertNull(capabilities.posting.scheduling)
        assertNull(capabilities.posting.poll)
        assertEquals(CapabilityStatus.Unsupported, capabilities.ownPosts.edit)
        assertEquals(CapabilityStatus.Unknown, capabilities.search.posts)
        assertEquals(CapabilityStatus.Supported, capabilities.timelineStatus(Timeline.Home))
        assertEquals(CapabilityStatus.Unsupported, capabilities.timelineStatus(Timeline.Bubble))
        assertEquals(ReportRoute.V11Report, capabilities.reportRoute)
        assertEquals(ReactionRoute.None, capabilities.reactionRoute)
        assertEquals(BubbleRoute.None, capabilities.bubbleRoute)
        assertEquals(CapabilityStatus.Unknown, capabilities.supportsReactions)
        assertFalse(PostAction.React in capabilities.actions)
    }

    @Test
    fun pixelfedCompatibilityNumberNeverEnablesEditing() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(instance("3.5.3 (compatible; Pixelfed 0.12.5)"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.ownPosts.edit)
    }

    @Test
    fun pleromaProbeYieldsTopLevelLimitsAndReactionSupport() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance())

        assertEquals(ServerFlavor.Pleroma, capabilities.flavor)
        assertEquals(LocalOnlyRoute.PleromaVisibility, capabilities.posting.localOnly)
        assertEquals(8, capabilities.posting.maxAttachments)
        assertEquals(CapabilityStatus.Unsupported, capabilities.timelineStatus(Timeline.Bubble))
        assertEquals(CapabilityStatus.Supported, capabilities.timelineStatus(Timeline.Federated))
        assertEquals(CapabilityStatus.Supported, capabilities.supportsReactions)
        assertTrue(PostAction.React in capabilities.actions)
        assertEquals(CapabilityStatus.Unknown, capabilities.supportsCustomReactions)
        assertEquals(listOf("text/plain", "text/markdown"), capabilities.posting.postFormats)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
        assertEquals(CapabilityStatus.Supported, capabilities.ownPosts.edit)
        assertEquals(SchedulingCapability(ScheduleRoute.Mastodon, 300L), capabilities.posting.scheduling)
        assertEquals(20, requireNotNull(capabilities.posting.poll).maxOptions)
        assertEquals(ReactionRoute.Pleroma, capabilities.reactionRoute)
        assertEquals(ReportRoute.V1Reports, capabilities.reportRoute)
        assertEquals(BubbleRoute.None, capabilities.bubbleRoute)
    }

    @Test
    fun akkomaProbeEnablesTheBubbleTimeline() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(
            instance("2.9.0 (compatible; Akkoma 3.13.0)").put(
                "pleroma",
                JSONObject().put(
                    "metadata",
                    JSONObject().put("features", JSONArray().put("pleroma_emoji_reactions").put("editing")),
                ),
            ),
        )

        assertEquals(ServerFlavor.Akkoma, capabilities.flavor)
        assertEquals(CapabilityStatus.Supported, capabilities.timelineStatus(Timeline.Bubble))
        assertEquals(BubbleRoute.Akkoma, capabilities.bubbleRoute)
        assertEquals(CapabilityStatus.Supported, capabilities.timelineStatus(Timeline.Federated))
        assertEquals(LocalOnlyRoute.PleromaVisibility, capabilities.posting.localOnly)
        assertEquals(ReactionRoute.Pleroma, capabilities.reactionRoute)
    }

    @Test
    fun glitchAndHometownKeepTheMastodonTableWithALocalOnlyFlag() {
        val glitch = MastodonCapabilityProbe.parseCapabilities(
            instance("4.2.0").put("api_versions", JSONObject().put("mastodon", 2).put("glitch", "4.2.0")),
        )
        assertEquals(ServerFlavor.Glitch, glitch.flavor)
        assertEquals(LocalOnlyRoute.MastodonFlag, glitch.posting.localOnly)
        assertEquals(CapabilityStatus.Supported, glitch.feedChips.lists)
        assertEquals(SchedulingCapability(ScheduleRoute.Mastodon, 300L), glitch.posting.scheduling)

        val hometown = MastodonCapabilityProbe.parseCapabilities(instance("4.0.0+hometown-1.1.0"))
        assertEquals(ServerFlavor.Hometown, hometown.flavor)
        assertEquals(LocalOnlyRoute.MastodonFlag, hometown.posting.localOnly)
    }

    @Test
    fun unknownFlavorKeepsOptimisticDefaults() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(JSONObject())

        assertEquals(ServerFlavor.Unknown, capabilities.flavor)
        assertEquals(CapabilityStatus.Supported, capabilities.feedChips.lists)
        assertEquals(CapabilityStatus.Unknown, capabilities.feedChips.followedTags)
        assertEquals(SchedulingCapability(ScheduleRoute.Mastodon, 300L), capabilities.posting.scheduling)
        assertTrue(capabilities.posting.poll != null)
        assertEquals(CapabilityStatus.Unknown, capabilities.notifications.webPush)
        assertEquals(CapabilityStatus.Unknown, capabilities.notifications.streaming)
        assertEquals(CapabilityStatus.Unknown, capabilities.notifications.dismissal)
        assertEquals(CapabilityStatus.Unknown, capabilities.notifications.unread)
        assertEquals(CapabilityStatus.Unknown, capabilities.search.posts)
        assertEquals(CapabilityStatus.Supported, capabilities.ownPosts.delete)
        assertEquals(CapabilityStatus.Unknown, capabilities.ownPosts.edit)
        assertEquals(ReactionRoute.Unknown, capabilities.reactionRoute)
        assertEquals(ReportRoute.V1Reports, capabilities.reportRoute)
        assertEquals(BubbleRoute.None, capabilities.bubbleRoute)
        assertNull(capabilities.posting.postFormats)
    }

    @Test
    fun flavorAndNewFieldsSurviveAnAccountRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AccountFileStore(context, SecretKeySpec(ByteArray(16) { 21 }, "AES"))
        store.clear()
        val accountId = AccountId(Connection("https://flavor.example", Protocol.MASTODON), "account")
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance())
        store.write(accountId, Session(accountId, "session-token", capabilities))

        val restored = store.read(accountId) ?: error("Session was not restored")

        assertEquals(capabilities, restored.capabilities)
        assertEquals(ServerFlavor.Pleroma, restored.capabilities.flavor)
        assertEquals(listOf("text/plain", "text/markdown"), restored.capabilities.posting.postFormats)
        assertEquals(CapabilityStatus.Unsupported, restored.capabilities.timelineStatus(Timeline.Bubble))
    }

    @Test
    fun schemaThirteenSnapshotForcesAFreshProbe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AccountFileStore(context, SecretKeySpec(ByteArray(16) { 22 }, "AES"))
        store.clear()
        val accountId = AccountId(Connection("https://stale.example", Protocol.MASTODON), "account")
        val stale = MastodonCapabilityProbe.parseCapabilities(pleromaInstance()).copy(
            capabilitySchemaVersion = 13,
            capabilitiesLastUpdated = 123L,
        )
        store.write(accountId, Session(accountId, "session-token", stale))

        val restored = store.read(accountId) ?: error("Session was not restored")

        assertEquals(ServerCapabilities(), restored.capabilities)
        assertEquals(ServerFlavor.Unknown, restored.capabilities.flavor)
    }

    private fun instance(version: String): JSONObject {
        val json = JSONObject()
        if (version.isNotEmpty()) json.put("version", version)
        return json
    }

    private fun pleromaInstance(): JSONObject = instance("2.7.2 (compatible; Pleroma 2.6.0)")
        .put("max_media_attachments", 8)
        .put(
            "poll_limits",
            JSONObject().put("max_options", 20).put("max_option_chars", 200)
                .put("min_expiration", 10).put("max_expiration", 31536000),
        )
        .put(
            "pleroma",
            JSONObject().put(
                "metadata",
                JSONObject()
                    .put("features", JSONArray().put("pleroma_emoji_reactions").put("editing").put("quote_posting"))
                    .put("post_formats", JSONArray().put("text/plain").put("text/markdown")),
            ),
        )
}
