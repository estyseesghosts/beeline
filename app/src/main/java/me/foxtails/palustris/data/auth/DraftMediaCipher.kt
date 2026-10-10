package me.foxtails.palustris.data.auth

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The file format for draft media: AES-GCM over fixed-size chunks.
 *
 * One AES-GCM message over a whole file cannot be streamed on decrypt, because the platform buffers all of it
 * before it returns a byte. A 25 MB video then fills the heap and stalls the app in garbage collection. This format
 * seals each chunk of [CHUNK_BYTES] on its own, so memory stays at two chunks however large the file is.
 *
 * Layout: [MAGIC] (12 bytes), the chunk size (4 bytes), then one record for each chunk: the IV (12 bytes), the
 * ciphertext, and the tag (16 bytes). Every record but the last holds a full chunk. The associated data of a chunk is
 * its index (8 bytes) and a final flag (1 byte), so a reordered, dropped, or truncated chunk fails authentication.
 * A file that does not start with [MAGIC] is the older single-message format, which [isChunked] tells apart.
 */
internal object DraftMediaCipher {
    /** Twelve bytes, so the older format's random 12-byte IV matches it with probability 2^-96. */
    private val MAGIC = "BLNDRAFT-v2\u0000".toByteArray(Charsets.ISO_8859_1)
    const val CHUNK_BYTES = 1 shl 20
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val RECORD_BYTES = IV_BYTES + CHUNK_BYTES + TAG_BYTES

    /** True when [head], the first 12 bytes of a file, is the chunked format's marker. */
    fun isChunked(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    /** Encrypts [source] into [out] and returns the plain byte count. [out] is not closed. */
    fun encrypt(source: InputStream, out: OutputStream, key: SecretKey): Long {
        out.write(MAGIC)
        out.write(ByteBuffer.allocate(4).putInt(CHUNK_BYTES).array())
        var current = ByteArray(CHUNK_BYTES)
        var currentLength = fill(source, current)
        var index = 0L
        var total = 0L
        while (true) {
            // The next chunk is read first so the current one knows whether it is the last.
            val next = ByteArray(CHUNK_BYTES)
            val nextLength = if (currentLength == CHUNK_BYTES) fill(source, next) else 0
            val final = nextLength == 0
            out.write(seal(key, index, final, current, currentLength))
            total += currentLength
            if (final) return total
            current = next
            currentLength = nextLength
            index++
        }
    }

    /** Decrypts a chunked file whose first 12 bytes were already read from [input]. Closing it closes [input]. */
    fun decryptingStream(input: InputStream, key: SecretKey): InputStream = DecryptingStream(input, key)

    private fun fill(source: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = source.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private fun associatedData(index: Long, final: Boolean): ByteArray =
        ByteBuffer.allocate(9).putLong(index).put(if (final) 1 else 0).array()

    private fun seal(key: SecretKey, index: Long, final: Boolean, plain: ByteArray, length: Int): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData(index, final))
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV length." }
        val sealed = cipher.doFinal(plain, 0, length)
        return iv + sealed
    }

    private fun open(key: SecretKey, index: Long, final: Boolean, record: ByteArray, length: Int): ByteArray {
        if (length < IV_BYTES + TAG_BYTES) throw IOException("Draft media chunk is truncated.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, record, 0, IV_BYTES))
        cipher.updateAAD(associatedData(index, final))
        return try {
            cipher.doFinal(record, IV_BYTES, length - IV_BYTES)
        } catch (error: AEADBadTagException) {
            throw IOException("Draft media is damaged.", error)
        }
    }

    private class DecryptingStream(private val input: InputStream, private val key: SecretKey) : InputStream() {
        private var index = 0L
        private var plain = ByteArray(0)
        private var position = 0
        private var pending: ByteArray? = null
        private var pendingLength = 0
        private var started = false
        private var finished = false

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            while (position >= plain.size) {
                if (finished) return -1
                nextChunk()
            }
            val count = minOf(length, plain.size - position)
            System.arraycopy(plain, position, buffer, offset, count)
            position += count
            return count
        }

        private fun nextChunk() {
            if (!started) {
                val chunk = ByteArray(4)
                var read = 0
                while (read < chunk.size) {
                    val step = input.read(chunk, read, chunk.size - read)
                    if (step < 0) throw EOFException("Draft media header is truncated.")
                    read += step
                }
                if (ByteBuffer.wrap(chunk).int != CHUNK_BYTES) throw IOException("Unsupported draft media chunk size.")
                started = true
                pending = ByteArray(RECORD_BYTES)
                pendingLength = fill(input, pending!!)
            }
            val current = pending ?: throw IOException("Draft media is truncated.")
            val currentLength = pendingLength
            // Read ahead so the current record knows whether it is the last.
            val ahead = ByteArray(RECORD_BYTES)
            val aheadLength = if (currentLength == RECORD_BYTES) fill(input, ahead) else 0
            val final = aheadLength == 0
            plain = open(key, index, final, current, currentLength)
            position = 0
            index++
            if (final) {
                finished = true
                pending = null
            } else {
                pending = ahead
                pendingLength = aheadLength
            }
        }

        override fun close() = input.close()
    }
}
