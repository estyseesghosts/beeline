package me.foxtails.palustris.data.media

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/** The shared image concurrency policy stays bounded so a grid scroll cannot burst a server. */
class ImageNetworkLimitsTest {
    @Test
    fun dispatchersStayBoundedInTotalAndPerHost() {
        val dispatcher = ImageNetworkLimits.newDispatcher()

        assertEquals(16, dispatcher.maxRequests)
        assertEquals(4, dispatcher.maxRequestsPerHost)
    }

    @Test
    fun eachDispatcherIsIndependent() {
        val first = ImageNetworkLimits.newDispatcher()
        val second = ImageNetworkLimits.newDispatcher()

        assertNotSame(first, second)
        assertEquals(first.maxRequests, second.maxRequests)
        assertEquals(first.maxRequestsPerHost, second.maxRequestsPerHost)
    }

    @Test
    fun aClientBuiltWithThePolicyKeepsItsLimits() {
        val client = OkHttpClient.Builder()
            .dispatcher(ImageNetworkLimits.newDispatcher())
            .build()

        assertEquals(16, client.dispatcher.maxRequests)
        assertEquals(4, client.dispatcher.maxRequestsPerHost)
    }
}
