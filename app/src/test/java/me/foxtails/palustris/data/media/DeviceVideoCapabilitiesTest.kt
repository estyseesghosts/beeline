package me.foxtails.palustris.data.media

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceVideoCapabilitiesTest {
    private val fixture = File("clip.mp4")

    private class Encoder(override val buildId: String?, var factor: Double, var fails: Boolean = false) : WebmEncoder {
        var benchmarks = 0

        override suspend fun benchmark(fixture: File, maxHeight: Int): Double {
            benchmarks++
            if (fails) throw VideoTranscodeException("no encoder")
            return factor
        }

        override suspend fun transcode(input: File, output: File, maxHeight: Int, videoBitrate: Int?, onProgress: (Float) -> Unit) = Unit
    }

    private class Store : VideoBenchmarkStore {
        val values = mutableMapOf<String, Double>()
        override fun read(key: String): Double? = values[key]
        override fun write(key: String, realtimeFactor: Double) {
            values[key] = realtimeFactor
        }
    }

    private fun capabilities(encoder: Encoder, store: Store = Store(), sdk: Int = 35, abi: String? = "arm64-v8a") =
        DeviceVideoCapabilities(DeviceVideoIdentity("Pixel 9", sdk, abi), encoder, store)

    @Test
    fun thresholdIsOnePointFiveTimesRealtime() = runBlocking {
        assertTrue(capabilities(Encoder("b1", 1.5)).canEncodeWebm(fixture))
        assertFalse(capabilities(Encoder("b1", 1.49)).canEncodeWebm(fixture))
    }

    @Test
    fun nonArm64OrMissingEncoderNeverRunsTheBenchmark() = runBlocking {
        val x86 = Encoder("b1", 9.0)
        assertFalse(capabilities(x86, abi = "x86_64").canEncodeWebm(fixture))
        assertFalse(capabilities(x86, abi = null).canEncodeWebm(fixture))
        val absent = Encoder(null, 9.0)
        assertFalse(capabilities(absent).canEncodeWebm(fixture))
        assertEquals(0, x86.benchmarks + absent.benchmarks)
    }

    @Test
    fun theResultIsCachedUntilTheKeyChanges() = runBlocking {
        val encoder = Encoder("b1", 2.0)
        val store = Store()
        assertTrue(capabilities(encoder, store).canEncodeWebm(fixture))
        assertTrue(capabilities(encoder, store).canEncodeWebm(fixture))
        assertEquals(1, encoder.benchmarks)

        // A new SDK level, then a new encoder build, each run the benchmark again.
        assertTrue(capabilities(encoder, store, sdk = 36).canEncodeWebm(fixture))
        assertEquals(2, encoder.benchmarks)
        val rebuilt = Encoder("b2", 2.0)
        assertTrue(capabilities(rebuilt, store, sdk = 36).canEncodeWebm(fixture))
        assertEquals(1, rebuilt.benchmarks)
        assertEquals(setOf("Pixel 9|35|b1", "Pixel 9|36|b1", "Pixel 9|36|b2"), store.values.keys)
    }

    @Test
    fun aFailedBenchmarkCountsAsSlowAndIsCached() = runBlocking {
        val encoder = Encoder("b1", 9.0, fails = true)
        val store = Store()
        assertFalse(capabilities(encoder, store).canEncodeWebm(fixture))
        encoder.fails = false
        assertFalse(capabilities(encoder, store).canEncodeWebm(fixture))
        assertEquals(1, encoder.benchmarks)
    }
}
