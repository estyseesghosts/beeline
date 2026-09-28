package me.foxtails.palustris.data.notifications

import android.content.Context
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationLabel
import me.foxtails.palustris.domain.NotificationLabelCode

/** Resolves notification text for Android presentation using the current application locale. */
class NotificationTextResolver(private val context: Context) {
    fun title(activity: NotificationActivity): String = when (activity) {
        NotificationActivity.Mention -> context.getString(R.string.notification_activity_mention)
        NotificationActivity.Reply -> context.getString(R.string.notification_activity_reply)
        NotificationActivity.Reshare -> context.getString(R.string.notification_activity_reshare)
        NotificationActivity.Quote -> context.getString(R.string.notification_activity_quote)
        NotificationActivity.Favourite -> context.getString(R.string.notification_activity_favourite)
        is NotificationActivity.EmojiReaction -> context.getString(
            R.string.notification_activity_reaction,
            label(activity.reaction.fallbackText),
        )
        NotificationActivity.Follow -> context.getString(R.string.notification_activity_follow)
        NotificationActivity.FollowRequest -> context.getString(R.string.notification_activity_follow_request)
        NotificationActivity.AcceptedRequest -> context.getString(R.string.notification_activity_accepted_request)
        NotificationActivity.SubscribedPost -> context.getString(R.string.notification_activity_subscribed_post)
        is NotificationActivity.PollResult -> context.getString(R.string.notification_activity_poll_result)
        NotificationActivity.PostUpdate -> context.getString(R.string.notification_activity_post_update)
        NotificationActivity.QuotedPostUpdate -> context.getString(R.string.notification_activity_quoted_post_update)
        NotificationActivity.DirectMessage -> context.getString(R.string.notification_activity_direct_message)
        is NotificationActivity.System,
        is NotificationActivity.Unknown,
        -> context.getString(R.string.notifications_detail_title)
    }

    fun body(notification: Notification, showPreview: Boolean): String {
        val actorLabel = actorLabel(notification)
        if (!showPreview) return actorLabel
        val post = notification.post
        val warning = post?.contentWarning
        return when {
            warning != null -> warning.takeUnless(String::isNullOrBlank)
                ?.let { context.getString(R.string.notifications_content_warning_with_text, it) }
                ?: context.getString(R.string.notifications_content_warning)
            !post?.text.isNullOrBlank() -> post?.text.orEmpty()
            else -> actorLabel
        }
    }

    private fun actorLabel(notification: Notification): String {
        val actorLabel = notification.group?.actorPreviews?.map { it.displayName }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString()
            ?: notification.actors.map { it.displayName }.filter(String::isNotBlank).joinToString()
        return actorLabel.ifBlank { context.getString(R.string.notifications_actorless) }
    }

    private fun label(value: NotificationLabel): String = when (value) {
        is NotificationLabel.Plain -> value.value
        is NotificationLabel.Coded -> context.getString(value.code.stringRes())
    }

    private fun NotificationLabelCode.stringRes(): Int = when (this) {
        NotificationLabelCode.Reaction -> R.string.notification_label_reaction
        NotificationLabelCode.ScheduledPostFailed -> R.string.notification_label_scheduled_post_failed
        NotificationLabelCode.ApplicationEvent -> R.string.notification_label_application_event
        NotificationLabelCode.AccountAchievement -> R.string.notification_label_account_achievement
        NotificationLabelCode.ModerationEvent -> R.string.notification_label_moderation_event
        NotificationLabelCode.RelationshipChanged -> R.string.notification_label_relationship_changed
        NotificationLabelCode.ChatInvitationUnavailable -> R.string.notification_label_chat_invitation_unavailable
        NotificationLabelCode.ExportCompleted -> R.string.notification_label_export_completed
        NotificationLabelCode.NewSignIn -> R.string.notification_label_new_sign_in
        NotificationLabelCode.AccessTokenCreated -> R.string.notification_label_access_token_created
        NotificationLabelCode.TestNotification -> R.string.notification_label_test_notification
        NotificationLabelCode.NewActivity -> R.string.notification_label_new_activity
        NotificationLabelCode.AccountEvent -> R.string.notification_label_account_event
    }
}
