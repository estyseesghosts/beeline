package me.foxtails.palustris.data.auth

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.DetectingAuthGateway
import me.foxtails.palustris.data.auth.LoginSession
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.domain.Protocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGatewayTest {
    @Test fun callbackDiagnosticsKeepProtocolMatchingStrict() {
        val pending = PendingLogin(
            "https://example.org",
            "state",
            System.currentTimeMillis(),
            protocol = Protocol.MASTODON,
        )

        assertTrue(AuthCallback.diagnose("palustris://auth/mastodon?state=state&code=returned", pending, System.currentTimeMillis()) is AuthCallback.Result.Accepted)
        val invalid = listOf(
            "https://auth/mastodon?state=state&code=returned" to AuthCallback.InvalidReason.SCHEME,
            "palustris://wrong/mastodon?state=state&code=returned" to AuthCallback.InvalidReason.HOST,
            "palustris://auth/other?state=state&code=returned" to AuthCallback.InvalidReason.PATH,
            "palustris://auth/mastodon?code=returned" to AuthCallback.InvalidReason.STATE,
            "palustris://auth/mastodon?state=other&code=returned" to AuthCallback.InvalidReason.STATE,
            "palustris://auth/mastodon?state=state&state=state&code=returned" to AuthCallback.InvalidReason.STATE,
            "palustris://auth/mastodon?state=state" to AuthCallback.InvalidReason.CODE_COUNT,
            "palustris://auth/mastodon?state=state&code=" to AuthCallback.InvalidReason.CODE_COUNT,
            "palustris://auth/mastodon?state=state&code=one&code=two" to AuthCallback.InvalidReason.CODE_COUNT,
        )
        invalid.forEach { (value, reason) ->
            assertEquals(reason, (AuthCallback.diagnose(value, pending, System.currentTimeMillis()) as AuthCallback.Result.Invalid).reason)
        }
        assertEquals(
            AuthCallback.InvalidReason.FRESHNESS,
            (AuthCallback.diagnose(
                "palustris://auth/mastodon?state=state&code=returned",
                pending,
                pending.createdAt + 15 * 60 * 1000L + 1,
            ) as AuthCallback.Result.Invalid).reason,
        )
    }

    @Test fun misskeyDetectionDispatchesPrepareAndProtocolOperations() = runBlocking {
        val misskey = RecordingGateway(Protocol.MISSKEY)
        val mastodon = RecordingGateway(Protocol.MASTODON)
        val gateway = DetectingAuthGateway(misskey, mastodon) { true }

        val pending = gateway.prepare(" https://misskey.example/ ")

        assertEquals(1, misskey.prepareCalls)
        assertEquals(0, mastodon.prepareCalls)
        assertEquals("misskey-browser", gateway.browserUrl(pending))
        assertSame(misskey.login, gateway.complete(pending))
    }

    @Test fun failedOrNegativeDetectionFallsBackToMastodon() = runBlocking {
        val misskey = RecordingGateway(Protocol.MISSKEY)
        val mastodon = RecordingGateway(Protocol.MASTODON)
        val gateway = DetectingAuthGateway(misskey, mastodon) { error("not a Misskey response") }

        val pending = gateway.prepare("https://mastodon.example")

        assertEquals(0, misskey.prepareCalls)
        assertEquals(1, mastodon.prepareCalls)
        assertEquals("mastodon-browser", gateway.browserUrl(pending))
        assertSame(mastodon.login, gateway.complete(pending))
    }

    private class RecordingGateway(private val protocol: Protocol) : AuthGateway {
        var prepareCalls = 0
        val login = LoginSession("https://example.org", "token", JSONObject("{}"), protocol)

        override suspend fun prepare(input: String): PendingLogin {
            prepareCalls++
            return PendingLogin("https://example.org", "state", 1, protocol = protocol)
        }

        override fun browserUrl(pending: PendingLogin): String = "${protocol.name.lowercase()}-browser"

        override suspend fun complete(pending: PendingLogin): LoginSession = login
    }
}
