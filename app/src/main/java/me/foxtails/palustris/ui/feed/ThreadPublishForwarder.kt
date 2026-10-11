package me.foxtails.palustris.ui.feed

import androidx.compose.runtime.getValue
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator

/** Publishes threads through the feed model and fans accepted posts out to the other surfaces. */
internal fun threadPublish(
    feedModel: FeedViewModel,
    feedSink: PostProjectionCoordinator.Sink,
    coordinator: PostProjectionCoordinator,
): (ThreadPublication, ThreadPublishListener) -> Unit = { publication, listener ->
    feedModel.create(publication, ThreadPublishForwarder(feedModel, feedSink, coordinator, listener))
}

/** Forwards each accepted post to the projection coordinator before the composer listener runs. */
internal class ThreadPublishForwarder(
    private val feedModel: FeedViewModel,
    private val origin: PostProjectionCoordinator.Sink,
    private val coordinator: PostProjectionCoordinator,
    private val listener: ThreadPublishListener,
) : ThreadPublishListener {
    override fun onAccepted(created: List<OwnedPost>, requests: List<CreatePostRequest>) {
        created.forEachIndexed { index, post ->
            feedModel.applyPublishedPost(requests[index])
            coordinator.forwardPublishedPost(origin, requests[index], post)
        }
        listener.onAccepted(created, requests)
    }

    override fun onProgress(posted: Int, total: Int) = listener.onProgress(posted, total)

    override fun onError(failure: ThreadPublishFailure) = listener.onError(failure)

    override fun onScheduled(items: List<ScheduledPost>) = listener.onScheduled(items)
}
