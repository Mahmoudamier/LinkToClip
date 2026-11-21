package com.mediaflow.universal.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DownloadWorkTest {

    @Test
    fun detectsRealYtDlpDnsErrors() {
        assertTrue(
            DownloadWork.isNetworkFailure(
                "ERROR: [youtube] xTbUgysqvMI: Unable to download API page: " +
                    "[Errno 7] No address associated with hostname"
            )
        )
        assertTrue(
            DownloadWork.isNetworkFailure(
                "[download] Got error: [Errno 7] No address associated with hostname. " +
                    "Giving up after 10 retries"
            )
        )
        assertTrue(DownloadWork.isNetworkFailure("Could not resolve host: www.youtube.com"))
        assertTrue(DownloadWork.isNetworkFailure("HTTP 503 Service Unavailable"))
        assertFalse(DownloadWork.isNetworkFailure("Video unavailable"))
        assertFalse(DownloadWork.isNetworkFailure("Private video"))
        assertFalse(DownloadWork.isNetworkFailure(null))
    }

    @Test
    fun detectsOtherTransientNetworkPhrases() {
        assertTrue(DownloadWork.isNetworkFailure("ERROR: unable to download webpage: HTTP Error 502"))
        assertTrue(DownloadWork.isNetworkFailure("ERROR: [download] Got error: Connection aborted"))
        assertTrue(DownloadWork.isNetworkFailure("Read timed out"))
        assertTrue(DownloadWork.isNetworkFailure("[Errno 104] Connection reset by peer"))
        assertTrue(DownloadWork.isNetworkFailure("ERROR: fragment 3 failed, retrying"))
        assertFalse(DownloadWork.isNetworkFailure("Video unavailable"))
        assertFalse(DownloadWork.isNetworkFailure("Sign in to confirm you are not a bot"))
    }

    @Test
    fun classifiesPostDownloadFailuresSeparately() {
        assertEquals(
            com.mediaflow.universal.core.ErrorKind.STORAGE,
            DownloadWork.classifyFailure("Could not create MediaStore entry")
        )
        assertEquals(
            com.mediaflow.universal.core.ErrorKind.STORAGE,
            DownloadWork.classifyFailure("java.io.IOException: No space left on device")
        )
        assertEquals(
            com.mediaflow.universal.core.ErrorKind.PROCESSING,
            DownloadWork.classifyFailure("ERROR: Postprocessing: ffmpeg exited with status 1")
        )
        assertEquals(
            com.mediaflow.universal.core.ErrorKind.PRIVATE,
            DownloadWork.classifyFailure("This video is private")
        )
        assertEquals(
            com.mediaflow.universal.core.ErrorKind.DOWNLOAD,
            DownloadWork.classifyFailure("Some unexpected failure")
        )
    }

    @Test
    fun neverRunsMoreThanThreeDownloadsAtOnce() = runBlocking {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val jobs = (1..10).map {
            async(Dispatchers.Default) {
                DownloadWork.withSlot {
                    val current = active.incrementAndGet()
                    peak.updateAndGet { max -> maxOf(max, current) }
                    delay(25)
                    active.decrementAndGet()
                }
            }
        }
        jobs.awaitAll()
        assertEquals(3, peak.get())
        assertEquals(0, active.get())
    }
}
