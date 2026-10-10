package me.foxtails.palustris.data.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.auth.DraftMediaStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.PreparedThreadImage
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadImageLimits
import me.foxtails.palustris.domain.ThreadImagePreparer
import me.foxtails.palustris.domain.ThreadPublicationMedia
import me.foxtails.palustris.domain.VideoUploadFormat
import me.foxtails.palustris.domain.isVideo

/**
 * Prepares draft thread attachments for upload.
 *
 * Responsibility: decrypt one draft copy into a plain temporary file, then run the shared
 * [UploadImagePreparer] over an image or the [VideoPreparer] over a video. Lifetime: the publisher
 * owns one call for each attachment. The publisher releases the result after the upload, which deletes
 * the prepared copy and the plain temporary file, never the draft's own encrypted file. A server that
 * refuses a WebM upload is recorded in [WebmRejections], so the retry and later uploads use MP4.
 */
@Singleton
class DraftThreadImagePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: DraftMediaStore,
    private val videoPreparer: VideoPreparer,
    private val webmRejections: WebmRejections,
) : ThreadImagePreparer {
    override suspend fun prepare(
        accountId: AccountId?,
        draftId: String,
        media: ThreadPublicationMedia,
        compress: Boolean,
        limits: ThreadImageLimits,
        onProgress: (Float) -> Unit,
    ): PreparedThreadImage {
        val owner = requireNotNull(accountId) { "A thread image belongs to an account before it can be prepared." }
        val plain = withContext(Dispatchers.IO) {
            val file = File(workDirectory(), if (media.isVideo()) "plain-${media.id}.vid" else "plain-${media.id}.img")
            mediaStore.open(owner, draftId, media.id).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        }
        if (media.isVideo()) return prepareVideo(owner, plain, media, limits, onProgress)
        val preparer = UploadImagePreparer(workDirectory())
        return try {
            val prepared = preparer.prepare(
                source = plain,
                mimeType = media.mimeType,
                fileName = media.fileName,
                compress = compress,
                limits = UploadImageLimits(
                    maxBytes = limits.maxBytes,
                    maxPixels = limits.maxPixels,
                    acceptedTypes = limits.acceptedTypes,
                ),
            )
            FilePreparedThreadImage(prepared.file, prepared.mimeType, prepared.fileName, prepared::release, plain)
        } catch (error: Exception) {
            plain.delete()
            throw error
        }
    }

    private suspend fun prepareVideo(
        owner: AccountId,
        plain: File,
        media: ThreadPublicationMedia,
        limits: ThreadImageLimits,
        onProgress: (Float) -> Unit,
    ): PreparedThreadImage = try {
        val target = VideoServerTarget(owner.connection.origin, limits.acceptedTypes, limits.maxVideoBytes)
        val prepared = videoPreparer.prepare(plain, media.fileName, target, onProgress)
        FilePreparedThreadImage(prepared.file, prepared.mimeType, prepared.fileName, prepared::release, plain, prepared.format)
    } catch (error: Throwable) {
        plain.delete()
        throw error
    }

    override fun uploadRejected(accountId: AccountId?, prepared: PreparedThreadImage, error: Exception): Boolean {
        val owner = accountId ?: return false
        val file = prepared as? FilePreparedThreadImage ?: return false
        // Only a server refusal of the container is a reason to retry; a missing allow list on Misskey shows up this way.
        if (file.format != VideoUploadFormat.Webm || error !is SourceError.Unsupported) return false
        webmRejections.record(owner.connection.origin)
        return true
    }

    private fun workDirectory(): File = File(context.cacheDir, "thread-prep").apply { mkdirs() }

    private class FilePreparedThreadImage(
        private val file: File,
        override val mimeType: String,
        override val fileName: String,
        private val releasePrepared: () -> Unit,
        private val plain: File,
        val format: VideoUploadFormat? = null,
    ) : PreparedThreadImage {
        override fun open(): InputStream = file.inputStream()
        override fun release() {
            releasePrepared()
            plain.delete()
        }
    }
}
