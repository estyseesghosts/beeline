package me.foxtails.palustris.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostingCapabilitiesVideoTest {
    @Test
    fun aServerWithoutATypeListMayTakeVideo() {
        assertTrue(PostingCapabilities(uploadTypes = null).acceptsVideo)
    }

    @Test
    fun aServerListingAVideoTypeAcceptsVideo() {
        assertTrue(PostingCapabilities(uploadTypes = setOf("image/png", "video/mp4")).acceptsVideo)
    }

    @Test
    fun aServerListingOnlyImagesDoesNotAcceptVideo() {
        assertFalse(PostingCapabilities(uploadTypes = setOf("image/png", "image/webp")).acceptsVideo)
    }
}
