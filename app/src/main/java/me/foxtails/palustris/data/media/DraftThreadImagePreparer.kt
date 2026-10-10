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
import me.foxtails.palustris.domain.ThreadImageLimits
import me.foxtails.palustris.domain.ThreadImagePreparer
import me.foxtails.palustris.domain.ThreadPublicationMedia

/**
 * Prepares draft thread images for upload.
 *
 * Responsibility: decrypt one draft copy into a plain temporary file, then run the shared
 * [UploadImagePreparer] over it. Lifetime: the publisher owns one call for each image. The
 * publisher releases the result after the upload, which deletes the prepared copy and the plain
 * temporary file, never the draft's own encrypted file.
 */
@Singleton
class DraftThreadImagePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: DraftMediaStore,
) : ThreadImagePreparer {
    override suspend fun prepare(
        accountId: AccountId?,
        draftId: String,
        media: ThreadPublicationMedia,
        compress: Boolean,
        limits: ThreadImageLimits,
    ): PreparedThreadImage {
        val owner = requireNotNull(accountId) { "A thread image belongs to an account before it can be prepared." }
        val plain = withContext(Dispatchers.IO) {
            val file = File(workDirectory(), "plain-${media.id}.img")
            mediaStore.open(owner, draftId, media.id).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        }
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

    private fun workDirectory(): File = File(context.cacheDir, "thread-prep").apply { mkdirs() }

    private class FilePreparedThreadImage(
        private val file: File,
        override val mimeType: String,
        override val fileName: String,
        private val releasePrepared: () -> Unit,
        private val plain: File,
    ) : PreparedThreadImage {
        override fun open(): InputStream = file.inputStream()
        override fun release() {
            releasePrepared()
            plain.delete()
        }
    }
}
