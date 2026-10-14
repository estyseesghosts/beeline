package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONObject

internal class MastodonPushService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
    private val accountId: AccountId,
    private val capabilities: () -> ServerCapabilities = { ServerCapabilities() },
) {
    /**
     * Server push availability from the probed capability. Pixelfed serves no v1 push
     * subscription routes, so a probed Unsupported result reports Unsupported without network
     * and the registration manager stays off. Denied stays Denied so the caller keeps its
     * reauthorization path. Unknown and temporarily-unavailable states keep the optimistic
     * Supported answer; only a verified absence or denial changes it. There is no v1.1 push
     * client in this slice.
     */
    suspend fun providerInfo(): PushProviderInfo {
        val probed = capabilities().notifications.webPush
        val status = when (probed) {
            CapabilityStatus.Unsupported, CapabilityStatus.Denied -> probed
            else -> CapabilityStatus.Supported
        }
        return PushProviderInfo(status = status)
    }

    suspend fun query(knownEndpoint: ValidatedUrl?): PushSubscription? {
        // With no v1 subscription route there is no server subscription to read.
        if (capabilities().notifications.webPush == CapabilityStatus.Unsupported) return null
        val subscription = readOwned()
        if (knownEndpoint != null && subscription != null && subscription.endpoint != knownEndpoint) throw SourceError.ServerError("notifications.push.identity-changed")
        return subscription
    }

    suspend fun createOrReplace(spec: PushSubscriptionSpec, previous: PushSubscription?): PushSubscription {
        validateSpec(spec); previous?.let(::validateSubscription)
        requirePush("notifications.push.create")
        return confirmed(api.postForm(origin, "api/v1/push/subscription", createFields(spec), token, MASTODON_MAX_RESPONSE_BYTES).body, spec.endpoint)
    }

    suspend fun updatePolicy(subscription: PushSubscription, alerts: Set<NotificationCategory>): PushSubscription {
        validateSubscription(subscription)
        requirePush("notifications.push.update")
        val confirmed = confirmed(api.putForm(origin, "api/v1/push/subscription", alertFields(alerts, includeReactions = capabilities().supportsReactionNotifications()), token, MASTODON_MAX_RESPONSE_BYTES).body, subscription.endpoint)
        if (!sameIdentity(confirmed, subscription)) throw SourceError.ServerError("notifications.push.identity-changed")
        return confirmed
    }

    suspend fun remove(subscription: PushSubscription) {
        validateSubscription(subscription)
        requirePush("notifications.push.remove")
        val current = readOwned() ?: return
        if (!sameIdentity(current, subscription)) throw SourceError.ServerError("notifications.push.identity-changed")
        api.delete(origin, "api/v1/push/subscription", token, MASTODON_MAX_RESPONSE_BYTES)
    }

    /** Throws before network when the probe verified the v1 push routes absent. */
    private fun requirePush(operation: String) {
        if (capabilities().notifications.webPush == CapabilityStatus.Unsupported) throw SourceError.Unsupported(operation)
    }

    private fun validateSpec(spec: PushSubscriptionSpec) {
        if (spec.accountId != accountId || spec.publicKey.isBlank() || spec.authSecret.isBlank()) throw SourceError.Unsupported("notifications.push.spec")
    }
    private suspend fun readOwned(): PushSubscription? = try {
        confirmed(api.get(origin, "api/v1/push/subscription", token, MASTODON_MAX_RESPONSE_BYTES).body)
    } catch (error: HttpStatusFailure) { if (error.status == 404) null else throw error }
    private fun confirmed(body: String, expectedEndpoint: ValidatedUrl? = null): PushSubscription {
        val json = JSONObject(body); val endpoint = ValidatedUrl.https(json.optString("endpoint")) ?: throw SourceError.ServerError("notifications.push.confirmation")
        if (expectedEndpoint != null && endpoint != expectedEndpoint) throw SourceError.ServerError("notifications.push.confirmation")
        val remoteId = json.optString("id").takeIf { it.isNotBlank() } ?: throw SourceError.ServerError("notifications.push.confirmation")
        return PushSubscription(accountId, endpoint, remoteId)
    }
    private fun validateSubscription(subscription: PushSubscription) {
        if (subscription.accountId != accountId || subscription.endpoint.value.isBlank()) throw SourceError.AccountMismatch
    }
    private fun sameIdentity(first: PushSubscription, second: PushSubscription) = first.accountId == second.accountId && first.endpoint == second.endpoint && first.remoteId == second.remoteId
    private fun createFields(spec: PushSubscriptionSpec) = buildList {
        add("subscription[endpoint]" to spec.endpoint.value); add("subscription[keys][p256dh]" to spec.publicKey); add("subscription[keys][auth]" to spec.authSecret)
        add("subscription[standard]" to spec.standardWebPush.toString()); addAll(alertFields(spec.alerts, includeReactions = capabilities().supportsReactionNotifications()))
    }
    internal fun alertFields(alerts: Set<NotificationCategory>, includeReactions: Boolean = false): List<Pair<String, String>> {
        val all = NotificationCategory.All in alerts; fun enabled(category: NotificationCategory) = (all || category in alerts).toString()
        return buildList {
            add("data[alerts][mention]" to enabled(NotificationCategory.Mentions))
            add("data[alerts][quote]" to enabled(NotificationCategory.Quotes))
            add("data[alerts][reblog]" to enabled(NotificationCategory.Social))
            add("data[alerts][follow]" to enabled(NotificationCategory.Social))
            add("data[alerts][follow_request]" to enabled(NotificationCategory.Social))
            add("data[alerts][favourite]" to enabled(NotificationCategory.Social))
            add("data[alerts][poll]" to enabled(NotificationCategory.Polls))
            add("data[alerts][status]" to enabled(NotificationCategory.Social))
            add("data[alerts][update]" to enabled(NotificationCategory.Social))
            add("data[alerts][quoted_update]" to enabled(NotificationCategory.Quotes))
            if (includeReactions) add("data[alerts][pleroma:emoji_reaction]" to enabled(NotificationCategory.Social))
        }
    }
}
