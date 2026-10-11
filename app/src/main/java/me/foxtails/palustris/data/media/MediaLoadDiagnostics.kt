package me.foxtails.palustris.data.media

import android.util.Log
import coil.EventListener
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.util.Logger
import me.foxtails.palustris.domain.MediaRequestRole
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a media request asked for. Attached to the request so the debug listener can report it. */
internal data class MediaLoadTag(
    val role: MediaRequestRole,
    /** True when the server sent no thumbnail, so the preview request fell back to the original. */
    val thumbnailMissing: Boolean,
    val videoFrame: Boolean,
)

/**
 * Debug-only report of media request outcomes, one line per finished request. It logs the host, the role,
 * the file extension, the HTTP status, the exception class and the duration. It never logs a full URL,
 * a token or a response body. The loader installs it only when `BuildConfig.DEBUG` is true, so a release
 * build never constructs it.
 */
internal class MediaLoadDiagnostics(private val request: ImageRequest) : EventListener {
    private var startedAt = 0L

    override fun onStart(request: ImageRequest) {
        startedAt = System.nanoTime()
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        report("ok", result.dataSource.name, null, null)
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        val failure = result.throwable
        val status = (failure as? HttpException)?.response?.code
        report("fail", null, status, failure.javaClass.simpleName)
    }

    override fun onCancel(request: ImageRequest) {
        report("cancel", null, null, null)
    }

    private fun report(outcome: String, source: String?, status: Int?, exception: String?) {
        val tag = request.tags.tag<MediaLoadTag>()
        val url = (request.data as? String) ?: (request.data as? VideoFrameRequest)?.url
        val parsed = url?.toHttpUrlOrNull()
        val millis = (System.nanoTime() - startedAt) / 1_000_000
        val extension = parsed?.pathSegments?.lastOrNull()?.substringAfterLast('.', "")?.take(5).orEmpty()
        Log.d(
            LOG_TAG,
            "$outcome host=${parsed?.host} role=${tag?.role} ext=$extension videoFrame=${tag?.videoFrame} " +
                "thumbnailMissing=${tag?.thumbnailMissing} source=$source status=$status exception=$exception ms=$millis",
        )
    }

    companion object {
        const val LOG_TAG = "MediaLoad"
    }
}

/**
 * Coil's own log sink for debug builds. Coil messages can hold full URLs, so this logger drops the message
 * and reports only the priority and the exception class.
 */
internal class RedactingImageLogger : Logger {
    override var level: Int = Log.WARN

    override fun log(tag: String, priority: Int, message: String?, throwable: Throwable?) {
        if (throwable != null) Log.println(priority, MediaLoadDiagnostics.LOG_TAG, "coil error ${throwable.javaClass.simpleName}")
    }
}
