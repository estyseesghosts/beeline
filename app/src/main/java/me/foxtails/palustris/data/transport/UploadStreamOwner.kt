package me.foxtails.palustris.data.transport

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Owns one upload input stream for a single multipart upload call.
 *
 * Responsibility: exactly-once closure of the caller-supplied stream and a single permitted
 * network write from that stream.
 * Lifetime: the upload entry creates this owner, and the same call releases it on every
 * terminal path: successful body write, body read failure, request failure before writing,
 * HTTP failure, oversized response, and coroutine cancellation.
 */
internal class UploadStreamOwner(stream: InputStream) {
    private val inner: InputStream = stream
    private val consumed = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    /**
     * Builds the one-shot streaming body for the owned stream.
     *
     * Throws when the MIME type is invalid. Callers build the body inside the upload
     * ownership boundary so this failure still releases the stream.
     */
    fun body(mimeType: String): RequestBody = UploadRequestBody(this, mimeType.toMediaType())

    /**
     * Marks the single permitted body write. Returns false when the input is consumed,
     * so a repeated write fails instead of sending empty or partial bytes.
     */
    fun markConsumed(): Boolean = consumed.compareAndSet(false, true)

    internal fun openStream(): InputStream = inner

    /** Closes the owned stream once. Safe to call on every terminal path. */
    fun release() {
        if (released.compareAndSet(false, true)) {
            runCatching { inner.close() }
        }
    }
}

/**
 * Streams the owned upload input to the network without buffering the whole input.
 *
 * The body reports unknown length, so OkHttp never consumes input to measure it.
 * Framing follows the negotiated protocol. The body is one-shot: a second write
 * fails with an IOException because the consumed input cannot be replayed. The write
 * holds no lock while reading, so closing the stream from the cancellation path
 * unblocks a blocked read.
 */
internal class UploadRequestBody(
    private val owner: UploadStreamOwner,
    private val mediaType: MediaType,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType

    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        if (!owner.markConsumed()) {
            throw IOException("Upload input is one-shot and was already consumed. Reopen the input for an explicit retry.")
        }
        try {
            sink.writeAll(owner.openStream().source())
        } finally {
            owner.release()
        }
    }
}

/**
 * Presents a completed multipart body as one-shot to OkHttp.
 *
 * OkHttp 4.12 checks one-shot state only on the enclosing request body when it
 * decides whether a failure may resend. The stream parts are consume-once, so a
 * resend could never replay them. This wrapper reports that honestly: a failure
 * after sending starts propagates as the original failure instead of triggering
 * a doomed resend. Length, headers, and bytes delegate unchanged, so the wire
 * format stays identical to the wrapped multipart body. Replayable bodies (for
 * example a field-only PATCH call) must not use this wrapper.
 */
internal class OneShotRequestBody(private val delegate: MultipartBody) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
}
