package me.foxtails.palustris.ui.links

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource

/** Turns a link or an ID into a post of the connected server, so the app can open it itself. */
interface PostLinkResolver {
    /** True for an address that has the shape of a post. Anything else goes straight to the browser. */
    fun looksLikePost(url: String): Boolean

    /** The post behind [url] on the connected server, or null when the server cannot resolve it in time. */
    suspend fun resolve(url: String): OwnedPost?

    /** The post with [id] on the connected server, or null when it cannot be loaded in time. */
    suspend fun load(id: EntityId): OwnedPost?
}

/** The resolver of the connected session. Null in previews, where every link opens in the browser. */
val LocalPostLinkResolver = staticCompositionLocalOf<PostLinkResolver?> { null }

/** Opens a post in the app's own post view. Null where no shell is wired. */
val LocalPostOpener = staticCompositionLocalOf<((OwnedPost) -> Unit)?> { null }

/**
 * Opens [url] in the app when the session can resolve it to a post, and in the browser otherwise.
 * A lookup that fails or runs past the resolver's time limit falls back to the browser.
 */
@Composable
fun rememberLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    val resolver = LocalPostLinkResolver.current
    val open = LocalPostOpener.current
    val scope = rememberCoroutineScope()
    return remember(context, resolver, open, scope) { linkOpener(context, resolver, open, scope) }
}

/**
 * Opens a quote reference: a loaded post at once, otherwise the post behind its [id] or [url], otherwise the
 * browser at [fallbackUrl].
 */
@Composable
fun rememberQuoteOpener(): (loaded: OwnedPost?, id: EntityId?, url: String?, fallbackUrl: String?) -> Unit {
    val context = LocalContext.current
    val resolver = LocalPostLinkResolver.current
    val open = LocalPostOpener.current
    val scope = rememberCoroutineScope()
    return remember(context, resolver, open, scope) {
        { loaded, id, url, fallbackUrl ->
            val browser = fallbackUrl ?: url
            when {
                loaded != null && open != null -> open(loaded)
                open == null || resolver == null -> browser?.let { ExternalLinkHandler.open(context, it) }
                else -> scope.launch {
                    val post = id?.let { resolver.load(it) } ?: url?.let { resolver.resolve(it) }
                    if (post != null) open(post) else browser?.let { ExternalLinkHandler.open(context, it) }
                }
            }
        }
    }
}

private fun linkOpener(
    context: Context,
    resolver: PostLinkResolver?,
    open: ((OwnedPost) -> Unit)?,
    scope: CoroutineScope,
): (String) -> Unit = { url ->
    if (resolver != null && open != null && resolver.looksLikePost(url)) {
        scope.launch {
            val post = resolver.resolve(url)
            if (post != null) open(post) else ExternalLinkHandler.open(context, url)
        }
    } else {
        ExternalLinkHandler.open(context, url)
    }
}

/**
 * The resolver of one connected session. It asks the account's own server and remembers each answer,
 * hits and misses alike, until the session ends. A lookup that takes longer than [timeoutMillis] counts as a miss.
 */
class SessionPostLinkResolver(
    private val accountId: AccountId,
    private val sessionRevision: Long,
    private val source: SocialSource,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : PostLinkResolver {
    private val resolved = mutableMapOf<String, OwnedPost?>()
    private var retired = false

    override fun looksLikePost(url: String): Boolean = !retired && POST_PATHS.any { it.containsMatchIn(url) }

    override suspend fun resolve(url: String): OwnedPost? {
        if (!looksLikePost(url)) return null
        if (resolved.containsKey(url)) return resolved[url]
        val post = try {
            withTimeoutOrNull(timeoutMillis) { source.resolvePostUrl(url) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val owned = post?.let { OwnedPost(accountId, it, sessionRevision) }
        // A timeout is not an answer from the server, so only real answers are remembered.
        if (!retired && post != null) resolved[url] = owned
        return owned
    }

    override suspend fun load(id: EntityId): OwnedPost? {
        if (retired) return null
        val post = try {
            withTimeoutOrNull(timeoutMillis) { source.post(id) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        return post?.let { OwnedPost(accountId, it, sessionRevision) }
    }

    fun retire() {
        retired = true
        resolved.clear()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 3_000L

        /** `/@user/<id>`, `/users/<u>/statuses/<id>`, `/notes/<id>`, `/notice/<id>` and `/objects/<id>`. */
        internal val POST_PATHS = listOf(
            Regex("^https?://[^/]+/@[^/]+/[A-Za-z0-9]+/?$"),
            Regex("^https?://[^/]+/users/[^/]+/statuses/[A-Za-z0-9]+/?$"),
            Regex("^https?://[^/]+/notes/[A-Za-z0-9]+/?$"),
            Regex("^https?://[^/]+/notice/[A-Za-z0-9]+/?$"),
            Regex("^https?://[^/]+/objects/[A-Za-z0-9-]+/?$"),
        )
    }
}
