package me.foxtails.palustris.domain

/** Callbacks for one thread publication. Defaults ignore the event. */
interface ThreadPublishListener {
    fun onProgress(posted: Int, total: Int) = Unit
    fun onAccepted(created: List<OwnedPost>, requests: List<CreatePostRequest>) = Unit
    fun onError(failure: ThreadPublishFailure) = Unit

    companion object {
        val Empty: ThreadPublishListener = object : ThreadPublishListener {}
    }
}
