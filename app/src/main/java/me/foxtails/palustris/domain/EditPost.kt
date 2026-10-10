package me.foxtails.palustris.domain

/** One image that stays on an edited post. [description] is its alt text after the edit. */
data class EditAttachment(val id: String, val description: String?)

/**
 * An edit of one post. A null field leaves that part as it is. [text] is always sent, because
 * both server families take the full text. [contentWarning] is the new warning; an empty string
 * clears it. [attachments] lists the images that remain, with their alt text. [poll] replaces
 * the poll, which resets its votes.
 */
data class EditPostRequest(
    val text: String,
    val contentWarning: String? = null,
    val attachments: List<EditAttachment>? = null,
    val poll: PollRequest? = null,
)

/** The raw text and warning of a post, as the author wrote them. Rendered text cannot be edited. */
data class PostEditSource(val text: String, val contentWarning: String?)

/**
 * The poll as an editable request: the same choices, and the time still left as its length. A poll
 * with no end time keeps no length, and the server default applies if the author changes it.
 */
fun Poll.toPollRequest(nowMillis: Long = System.currentTimeMillis()): PollRequest = PollRequest(
    choices = options.map(PollOption::text),
    multiple = multiple,
    expiresInSeconds = expiresAtEpochMillis?.let { ((it - nowMillis) / 1000L).coerceAtLeast(1L) },
)
