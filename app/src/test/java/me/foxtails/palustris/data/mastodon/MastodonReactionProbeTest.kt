package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ReactionRoute
import me.foxtails.palustris.domain.ServerFlavor
import me.foxtails.palustris.domain.allowsCustomReactions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 4 reaction flags: `pleroma_emoji_reactions` enables reactions, while custom
 * reactions need `pleroma_custom_emoji_reactions`. Akkoma without the custom flag stays
 * optimistic until a custom `PUT` rejection demotes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonReactionProbeTest {
    @Test
    fun reactionsWithoutCustomFlagStayUnicodeOnlyOnPleroma() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance(
            "2.7.2 (compatible; Pleroma 2.6.0)",
            features = listOf("pleroma_emoji_reactions"),
        ))

        assertEquals(ServerFlavor.Pleroma, capabilities.flavor)
        assertEquals(CapabilityStatus.Supported, capabilities.supportsReactions)
        assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
        assertTrue(PostAction.React in capabilities.actions)
        assertEquals(ReactionRoute.Pleroma, capabilities.reactionRoute)
        assertEquals(CapabilityStatus.Unknown, capabilities.supportsCustomReactions)
        assertFalse(capabilities.allowsCustomReactions())
    }

    @Test
    fun bothFlagsEnableCustomReactions() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance(
            "2.7.2 (compatible; Pleroma 2.6.0)",
            features = listOf("pleroma_emoji_reactions", "pleroma_custom_emoji_reactions"),
        ))

        assertEquals(CapabilityStatus.Supported, capabilities.supportsReactions)
        assertEquals(CapabilityStatus.Supported, capabilities.supportsCustomReactions)
        assertTrue(capabilities.allowsCustomReactions())
    }

    @Test
    fun akkomaWithoutCustomFlagStaysOptimistic() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance(
            "2.9.0 (compatible; Akkoma 3.13.0)",
            features = listOf("pleroma_emoji_reactions"),
        ))

        assertEquals(ServerFlavor.Akkoma, capabilities.flavor)
        assertEquals(CapabilityStatus.Supported, capabilities.supportsReactions)
        // The probe records no custom evidence, so the flag stays Unknown rather than
        // guessing. The picker still offers custom emoji until a custom PUT rejection
        // demotes the session to unicode-only.
        assertEquals(CapabilityStatus.Unknown, capabilities.supportsCustomReactions)
        assertTrue(capabilities.allowsCustomReactions())
    }

    @Test
    fun demotedCustomSupportClosesThePicker() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance(
            "2.9.0 (compatible; Akkoma 3.13.0)",
            features = listOf("pleroma_emoji_reactions"),
        )).copy(supportsCustomReactions = CapabilityStatus.Unsupported)

        assertEquals(CapabilityStatus.Supported, capabilities.supportsReactions)
        assertFalse(capabilities.allowsCustomReactions())
    }

    @Test
    fun mastodonWithoutAdvertisementStaysUnknown() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", "4.6.0"))

        assertEquals(ServerFlavor.Mastodon, capabilities.flavor)
        assertEquals(CapabilityStatus.Unknown, capabilities.supportsReactions)
        assertEquals(CapabilityStatus.Unknown, capabilities.supportsCustomReactions)
        assertFalse(capabilities.allowsCustomReactions())
        assertFalse(PostAction.React in capabilities.actions)
    }

    private fun pleromaInstance(version: String, features: List<String>): JSONObject = JSONObject()
        .put("version", version)
        .put(
            "pleroma",
            JSONObject().put(
                "metadata",
                JSONObject().put("features", features.fold(JSONArray()) { array, feature -> array.put(feature) }),
            ),
        )
}
