package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.ServerFlavor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Posting-limit precedence for the instance document. `configuration.statuses` wins when both it
 * and the Pleroma-family top level report a cap; the top level is the fallback. Byte and alt-text
 * limits stay configuration-only: no documented top-level key exists for them, so unknown
 * top-level keys must not leak into the parsed limits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonPostingLimitsTest {
    @Test
    fun configurationStatusesWinsWhenBothReportMaxAttachments() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(
            pleromaInstance()
                .put("max_media_attachments", 8)
                .put("configuration", JSONObject().put("statuses", JSONObject().put("max_media_attachments", 4))),
        )

        assertEquals(ServerFlavor.Pleroma, capabilities.flavor)
        assertEquals(4, capabilities.posting.maxAttachments)
    }

    @Test
    fun topLevelMaxAttachmentsAppliesWithoutConfiguration() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(pleromaInstance().put("max_media_attachments", 8))

        assertEquals(8, capabilities.posting.maxAttachments)
    }

    @Test
    fun byteAndAltTextLimitsComeFromConfigurationMediaAttachments() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(
            pleromaInstance().put(
                "configuration",
                JSONObject().put(
                    "media_attachments",
                    JSONObject()
                        .put("description_limit", 2000)
                        .put("image_size_limit", 8_000_000)
                        .put("image_matrix_limit", 10_000_000)
                        .put("video_size_limit", 40_000_000),
                ),
            ),
        )

        assertEquals(2000, capabilities.posting.maxAltTextLength)
        assertEquals(8_000_000L, capabilities.posting.maxImageBytes)
        assertEquals(10_000_000L, capabilities.posting.maxImagePixels)
        assertEquals(40_000_000L, capabilities.posting.maxVideoBytes)
    }

    @Test
    fun unknownTopLevelSizeKeysDoNotLeakIntoLimits() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(
            pleromaInstance()
                .put("image_size_limit", 1)
                .put("video_size_limit", 1)
                .put("description_limit", 1),
        )

        assertEquals(1500, capabilities.posting.maxAltTextLength)
        assertEquals(16L * 1024 * 1024, capabilities.posting.maxImageBytes)
        assertEquals(99L * 1024 * 1024, capabilities.posting.maxVideoBytes)
    }

    private fun pleromaInstance(): JSONObject = JSONObject().put("version", "2.7.2 (compatible; Pleroma 2.6.0)")
}
