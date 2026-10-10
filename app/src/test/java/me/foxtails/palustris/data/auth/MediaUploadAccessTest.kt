package me.foxtails.palustris.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.domain.AccessGrant
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol as AppProtocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.effectiveMediaUpload
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaUploadAccessTest {
    private val user = """{"id":"user-1","username":"alice","name":"Alice","host":null,"avatarUrl":null}"""

    @Test
    fun newMisskeySignInRequestsDriveAccessAndRecordsIt() = runBlocking {
        val bodies = ArrayDeque(listOf("""{"version":"2026.1.0"}""", """{"ok":true,"token":"t","user":$user}"""))
        // Sign-in requires an https origin, so a canned client stands in for the server.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(bodies.removeFirst().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val auth = MisskeyAuth(AuthenticatedHttpClient(client))

        val pending = auth.prepare("https://misskey.test")
        val session = auth.complete(pending)

        assertTrue(AccessScope.MediaUpload in pending.requestedAccess)
        val permissions = auth.browserUrl(pending).toHttpUrl().queryParameter("permission").orEmpty().split(",")
        assertTrue("write:drive" in permissions)
        assertEquals(AccessStatus.Granted, session.access.status(AccessScope.MediaUpload))
    }

    @Test
    fun mastodonWriteScopeGrantsUploadAndReadOnlyDoesNot() = runBlocking {
        listOf("read write push" to AccessStatus.Granted, "read push" to AccessStatus.Denied, "read write:media" to AccessStatus.Granted)
            .forEach { (scope, expected) ->
                MockWebServer().use { server ->
                    server.enqueue(MockResponse().setBody("""{"access_token":"tok","scope":"$scope"}"""))
                    server.enqueue(
                        MockResponse().setBody(
                            """{"id":"m","username":"alice","acct":"alice","display_name":"Alice","avatar":"https://example.org/a.png","note":""}""",
                        ),
                    )
                    val origin = server.url("/").toString().removeSuffix("/")
                    val pending = PendingLogin(
                        origin, "state", System.currentTimeMillis(), AppProtocol.MASTODON,
                        clientId = "id", clientSecret = "secret", codeVerifier = "v", codeChallenge = "c", authorizationCode = "code",
                    )

                    val session = MastodonAuth(AuthenticatedHttpClient(OkHttpClient()), AppRegistrationCache()).complete(pending)

                    assertEquals(scope, expected, session.access.status(AccessScope.MediaUpload))
                }
            }
    }

    @Test
    fun grantWithoutARecordResolvesToDenied() {
        val supported = ServerCapabilities(posting = PostingCapabilities(mediaUpload = CapabilityStatus.Supported))

        assertEquals(AccessStatus.Denied, AccessGrant().mediaUploadStatus())
        assertEquals(CapabilityStatus.Denied, supported.effectiveMediaUpload(AccessGrant()))
    }

    @Test
    fun grantedAccessOnASupportingServerIsSupported() {
        val grant = AccessGrant(known = mapOf(AccessScope.MediaUpload to AccessStatus.Granted))

        assertEquals(CapabilityStatus.Supported, supported().effectiveMediaUpload(grant))
        assertEquals(
            CapabilityStatus.Unsupported,
            ServerCapabilities(posting = PostingCapabilities(mediaUpload = CapabilityStatus.Unsupported)).effectiveMediaUpload(grant),
        )
    }

    @Test
    fun storedMisskeySessionWithoutTheRecordStaysDenied() {
        val store = store(21)
        val accountId = AccountId(Connection("https://legacy-misskey.example", AppProtocol.MISSKEY), "user")
        val legacy = AccessGrant(known = mapOf(AccessScope.NotificationsWrite to AccessStatus.Granted))
        store.write(accountId, Session(accountId, "token", ServerCapabilities(), legacy))

        val restored = requireNotNull(store.read(accountId))

        assertEquals(AccessStatus.Denied, restored.access.mediaUploadStatus())
    }

    @Test
    fun storedMastodonSessionWithWriteScopeGainsTheRecord() {
        val store = store(22)
        val accountId = AccountId(Connection("https://legacy-mastodon.example", AppProtocol.MASTODON), "user")
        val legacy = AccessGrant(known = mapOf(AccessScope.NotificationsWrite to AccessStatus.Granted))
        store.write(accountId, Session(accountId, "token", ServerCapabilities(), legacy))
        val readOnlyId = AccountId(Connection("https://readonly-mastodon.example", AppProtocol.MASTODON), "user")
        store.write(readOnlyId, Session(readOnlyId, "token", ServerCapabilities(), AccessGrant()))

        assertEquals(AccessStatus.Granted, requireNotNull(store.read(accountId)).access.mediaUploadStatus())
        assertEquals(AccessStatus.Denied, requireNotNull(store.read(readOnlyId)).access.mediaUploadStatus())
    }

    private fun supported() = ServerCapabilities(posting = PostingCapabilities(mediaUpload = CapabilityStatus.Supported))

    private fun store(seed: Int): AccountFileStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AccountFileStore(context, SecretKeySpec(ByteArray(16) { seed.toByte() }, "AES")).also { it.clear() }
    }
}
