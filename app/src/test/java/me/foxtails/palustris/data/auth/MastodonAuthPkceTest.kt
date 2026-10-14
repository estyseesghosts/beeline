package me.foxtails.palustris.data.auth

import java.io.IOException
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonAuthPkceTest {
    @Test
    fun pkceEnabledOnMastodon43Probe() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val auth = authForInstanceBody("""{"version":"4.3.0"}""", requestedPaths)

        val pending = auth.prepare("https://mastodon.test")

        assertTrue(requestedPaths.contains("/api/v2/instance"))
        assertNotNull(pending.codeVerifier)
        assertNotNull(pending.codeChallenge)
    }

    @Test
    fun pkceDisabledOnMastodon42Probe() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val auth = authForInstanceBody("""{"version":"4.2.0"}""", requestedPaths)

        val pending = auth.prepare("https://mastodon.test")

        assertTrue(requestedPaths.contains("/api/v2/instance"))
        assertNull(pending.codeVerifier)
        assertNull(pending.codeChallenge)
    }

    @Test
    fun pkceDisabledOnNetworkFailureWithoutThrow() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedPaths.add(chain.request().url.encodedPath)
            when (chain.request().url.encodedPath) {
                "/api/v1/apps" -> jsonResponse(chain, """{"client_id":"id","client_secret":"secret"}""")
                "/api/v2/instance" -> throw IOException("unreachable")
                else -> error("unexpected path ${chain.request().url.encodedPath}")
            }
        }.build()
        val auth = MastodonAuth({ AuthenticatedHttpClient(client) }, AppRegistrationCache())

        val pending = auth.prepare("https://mastodon.test")

        assertEquals(listOf("/api/v1/apps", "/api/v2/instance"), requestedPaths)
        assertNull(pending.codeVerifier)
        assertNull(pending.codeChallenge)
    }

    private fun authForInstanceBody(body: String, requestedPaths: MutableList<String>): MastodonAuth {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedPaths.add(chain.request().url.encodedPath)
            when (chain.request().url.encodedPath) {
                "/api/v1/apps" -> jsonResponse(chain, """{"client_id":"id","client_secret":"secret"}""")
                "/api/v2/instance" -> jsonResponse(chain, body)
                else -> error("unexpected path ${chain.request().url.encodedPath}")
            }
        }.build()
        return MastodonAuth({ AuthenticatedHttpClient(client) }, AppRegistrationCache())
    }

    private fun jsonResponse(chain: Interceptor.Chain, body: String): Response =
        Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
}
