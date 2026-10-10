package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostLengthRule
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ServerCapabilities
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonCapabilityProbeTest {
    @Test
    fun apiVersionEightEnablesProfileApiButNotImageDescriptions() {
        val capabilities = parse(instanceWithApiVersion(8))

        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.update)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageUpload)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun apiVersionNineEnablesImageDescriptions() {
        val capabilities = parse(instanceWithApiVersion(9))

        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.read)
    }

    @Test
    fun apiVersionSevenDisablesTheProfileApiAndKeepsQuotes() {
        val capabilities = parse(instanceWithApiVersion(7))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.update)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun machineApiVersionWinsOverConflictingHumanVersion() {
        val capabilities = parse(instanceWithApiVersion(7).put("version", "4.6.0"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun missingApiVersionsUsesReleaseFourSixFallback() {
        val capabilities = parse(instance("4.6.0"))

        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.update)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageUpload)
        assertEquals(CapabilityStatus.Unknown, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun malformedHumanVersionLeavesAdvancedCapabilitiesUnknown() {
        listOf("", "glitch", "v4.2.0-rc1", "4", "latest").forEach { version ->
            val capabilities = parse(instance(version))

            assertEquals(version, CapabilityStatus.Unknown, capabilities.profile.editable.read)
            assertEquals(version, CapabilityStatus.Unknown, capabilities.profile.editable.advancedSettings)
            assertEquals(version, CapabilityStatus.Unknown, capabilities.profile.editable.imageDeletion)
            assertEquals(version, CapabilityStatus.Unknown, capabilities.quotes)
        }
    }

    @Test
    fun forkStyleVersionDoesNotCombineUnrelatedNumbers() {
        val capabilities = parse(instance("3.5.3 (compatible; Pleroma 2.6.50)"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Unsupported, capabilities.quotes)
    }

    @Test
    fun releaseVersionBelowFourSixDisablesTheProfileApi() {
        val capabilities = parse(instance("4.2.1"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Unsupported, capabilities.quotes)
    }

    @Test
    fun everyProbeResultCarriesTheCurrentSchemaVersion() {
        assertEquals(
            ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
            parse(instance("4.6.0")).capabilitySchemaVersion,
        )
        assertEquals(
            ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
            parse(instance("")).capabilitySchemaVersion,
        )
    }

    @Test
    fun ordinaryMastodonSupportsCatalogWithUnknownReactionsAndNoReactAction() {
        val capabilities = parse(instance("4.6.0"))

        assertEquals(CapabilityStatus.Supported, capabilities.emoji.catalog)
        assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionListing)
        assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
        assertEquals(ReactionSelectionMode.Unknown, capabilities.emoji.selectionMode)
        assertFalse(PostAction.React in capabilities.actions)
        assertTrue(PostAction.Favorite in capabilities.actions)
        assertEquals(CapabilityStatus.Supported, capabilities.likedPosts)
    }

    @Test
    fun verifiedExtensionAdvertisementAddsReactIndependentAndMutation() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(extensionInstance())

        assertEquals(CapabilityStatus.Supported, capabilities.emoji.catalog)
        assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionListing)
        assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
        assertEquals(ReactionSelectionMode.Independent, capabilities.emoji.selectionMode)
        assertTrue(PostAction.React in capabilities.actions)
    }

    @Test
    fun missingOrMalformedAdvertisementKeepsReactionSupportUnknown() {
        val absent = extensionInstance().put("pleroma", JSONObject().put("metadata", JSONObject()))
        val wrongFeature = extensionInstance().put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", org.json.JSONArray().put("pleroma_other"))),
        )
        val featuresNotAnArray = extensionInstance().put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", "pleroma_emoji_reactions")),
        )
        listOf(instance("4.6.0"), absent, wrongFeature, featuresNotAnArray).forEach { instance ->
            val capabilities = MastodonCapabilityProbe.parseCapabilities(instance)

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionListing)
            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertEquals(ReactionSelectionMode.Unknown, capabilities.emoji.selectionMode)
            assertFalse(PostAction.React in capabilities.actions)
        }
    }

    @Test
    fun metadataAbsenceAddsOneNodeInfoDiscoveryWithoutAMutationProbe() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody("""{"links":[]}"""))
            val origin = server.url("/").toString().removeSuffix("/")
            val capabilities = MastodonCapabilityProbe(mastodonTestClient())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertFalse(PostAction.React in capabilities.actions)
            assertEquals(2, server.requestCount)
            assertEquals("/api/v2/instance", server.takeRequest().path)
            assertEquals("/.well-known/nodeinfo", server.takeRequest().path)
        }
    }

    @Test
    fun extensionAdvertisementNeedsNoMutationProbe() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(extensionInstance().toString()))
            val origin = server.url("/").toString().removeSuffix("/")
            val capabilities = MastodonCapabilityProbe(mastodonTestClient())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertTrue(PostAction.React in capabilities.actions)
            assertEquals(1, server.requestCount)
            assertEquals("/api/v2/instance", server.takeRequest().path)
        }
    }

    @Test
    fun absentV2InstanceFallsBackToV1() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Record not found"}"""))
            server.enqueue(MockResponse().setBody(extensionInstance().toString()))
            val origin = server.url("/").toString().removeSuffix("/")
            val capabilities = MastodonCapabilityProbe(mastodonTestClient())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertEquals("/api/v2/instance", server.takeRequest().path)
            assertEquals("/api/v1/instance", server.takeRequest().path)
        }
    }

    @Test
    fun metadataFailurePropagatesWithoutPublishingDefaults() = runBlocking {
        listOf(401, 429, 500).forEach { status ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
                val origin = server.url("/").toString().removeSuffix("/")

                assertThrows(me.foxtails.palustris.data.transport.HttpStatusFailure::class.java) {
                    runBlocking {
                        MastodonCapabilityProbe(mastodonTestClient())
                            .probeCapabilities(Connection(origin, Protocol.MASTODON))
                    }
                }
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun leadingVersionParserRejectsMalformedValues() {
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(4, 2, 0),
            MastodonCapabilityProbe.parseLeadingVersion("4.2.0"),
        )
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(4, 6, 1),
            MastodonCapabilityProbe.parseLeadingVersion("4.6.1+glitch"),
        )
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(3, 5, 3),
            MastodonCapabilityProbe.parseLeadingVersion("3.5.3 (compatible; Pleroma 2.6.50)"),
        )
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(4, 2, 0),
            MastodonCapabilityProbe.parseLeadingVersion("4.2"),
        )
        assertNull(MastodonCapabilityProbe.parseLeadingVersion("v4.2.0"))
        assertNull(MastodonCapabilityProbe.parseLeadingVersion("glitch"))
        assertNull(MastodonCapabilityProbe.parseLeadingVersion(""))
    }

    @Test
    fun v2ConfigurationSuppliesPostingLimits() {
        val configuration = JSONObject()
            .put(
                "statuses",
                JSONObject().put("max_characters", 1500).put("max_media_attachments", 8)
                    .put("characters_reserved_per_url", 20),
            )
            .put(
                "media_attachments",
                JSONObject()
                    .put("supported_mime_types", org.json.JSONArray().put("image/JPEG").put("image/avif"))
                    .put("image_size_limit", 5_000_000L).put("image_matrix_limit", 4_000_000L)
                    .put("description_limit", 2000),
            )
        val capabilities = parse(instance("4.4.0").put("configuration", configuration))
        val posting = capabilities.posting

        assertEquals(1500, capabilities.maxPostLength)
        assertEquals(PostLengthRule.MastodonCombined, posting.lengthRule)
        assertNull(posting.maxWarningLength)
        assertEquals(8, posting.maxAttachments)
        assertEquals(2000, posting.maxAltTextLength)
        assertEquals(5_000_000L, posting.maxImageBytes)
        assertEquals(4_000_000L, posting.maxImagePixels)
        assertEquals(setOf("image/jpeg", "image/avif"), posting.uploadTypes)
        assertEquals(20, posting.charactersReservedPerUrl)
        assertEquals(CapabilityStatus.Supported, posting.mediaUpload)
        assertFalse(posting.clientCompression)
    }

    @Test
    fun responseWithoutConfigurationUsesDocumentedDefaults() {
        val capabilities = parse(instance("3.5.0"))
        val posting = capabilities.posting

        assertEquals(500, capabilities.maxPostLength)
        assertEquals(4, posting.maxAttachments)
        assertEquals(1500, posting.maxAltTextLength)
        assertEquals(16L * 1024 * 1024, posting.maxImageBytes)
        assertEquals(33_177_600L, posting.maxImagePixels)
        assertEquals(setOf("image/jpeg", "image/png", "image/gif", "image/webp"), posting.uploadTypes)
        assertEquals(23, posting.charactersReservedPerUrl)
    }

    @Test
    fun legacyMaxTootCharsIsUsedWhenConfigurationIsAbsent() {
        assertEquals(5000, parse(instance("2.7.0").put("max_toot_chars", 5000)).maxPostLength)
    }

    @Test
    fun malformedConfigurationFallsBackToDefaults() {
        val configuration = JSONObject()
            .put("statuses", JSONObject().put("max_characters", "many").put("max_media_attachments", -2))
            .put("media_attachments", JSONObject().put("supported_mime_types", org.json.JSONArray()))
        val capabilities = parse(instance("4.4.0").put("configuration", configuration))

        assertEquals(500, capabilities.maxPostLength)
        assertEquals(4, capabilities.posting.maxAttachments)
        assertTrue("image/png" in capabilities.posting.uploadTypes.orEmpty())
    }

    private fun parse(instance: JSONObject): ServerCapabilities =
        MastodonCapabilityProbe.parseCapabilities(instance)

    private fun instance(version: String): JSONObject {
        val json = JSONObject()
        if (version.isNotEmpty()) json.put("version", version)
        return json
    }

    private fun instanceWithApiVersion(version: Int): JSONObject =
        instance("4.6.0").put("api_versions", JSONObject().put("mastodon", version))

    private fun extensionInstance(): JSONObject = instance("4.6.0").put(
        "pleroma",
        JSONObject().put(
            "metadata",
            JSONObject().put("features", org.json.JSONArray().put("pleroma_emoji_reactions")),
        ),
    )
}
