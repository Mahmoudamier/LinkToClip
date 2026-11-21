package com.mediaflow.universal.download

import com.mediaflow.universal.core.ErrorKind
import kotlinx.coroutines.sync.Semaphore

object DownloadWork {
    const val KEY_ID = "download_id"
    const val KEY_URL = "source_url"
    const val KEY_DIRECT_URL = "direct_url"
    const val KEY_HEADERS = "headers_json"
    const val KEY_DOWNLOAD_KEY = "download_key"
    const val KEY_ENGINE = "engine"
    const val KEY_RESUME = "resume"
    const val KEY_TOTAL = "total_bytes"
    const val KEY_PROGRESS = "progress"

    /** Hard cap: at most this many downloads run at once on the whole device. */
    const val MAX_CONCURRENT_DOWNLOADS = 3

    /** Transient network failures retried by WorkManager with exponential backoff. */
    const val MAX_NETWORK_RETRIES = 4
    const val RETRY_BACKOFF_SECONDS = 20L

    private val slots = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    /**
     * Holds one of the three global download slots for the entire lifetime of a worker.
     * WorkManager itself has no public concurrency limit, so waiting here is what guarantees
     * "at most 3 files are really downloading" - the rest stay QUEUED and start one-by-one
     * as a slot is released. The permit is released automatically on pause/cancel (coroutine
     * cancellation), success and failure.
     */
    suspend fun <T> withSlot(block: suspend () -> T): T {
        slots.acquire()
        try {
            return block()
        } finally {
            slots.release()
        }
    }

    fun uniqueName(id: String) = "mediaflow_download_$id"
    fun tag(id: String) = "mediaflow:$id"

    /**
     * True for DNS/connection/5xx failures that are worth retrying instead of failing.
     * yt-dlp phrases these in many different ways depending on the site and Android version,
     * so this list is intentionally broad — a false retry only costs a few seconds, while a
     * missed retry is what produces the confusing "check your connection" failure.
     */
    fun isNetworkFailure(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        if (Regex("http\\s*error\\s*(4(29|44|56)|5\\d\\d|7\\d\\d)").containsMatchIn(t)) return true
        if (Regex("\\[errno\\s*(7|101|104|110|113|115)\\b").containsMatchIn(t)) return true
        return listOf(
            "no address associated with hostname",
            "temporary failure in name resolution",
            "could not resolve host",
            "name or service not known",
            "name resolution",
            "no such host",
            "network is unreachable",
            "connection timed out",
            "connection aborted",
            "connection refused",
            "connection reset",
            "connection was reset",
            "failed to connect",
            "unable to connect",
            "unable to download",
            "could not download",
            "download error",
            "got error:",
            "giving up after",
            "err_name_not_resolved",
            "err_connection",
            "eof occurred",
            "unexpected eof",
            "remote end closed",
            "broken pipe",
            "read timed out",
            "socket timeout",
            "timed out",
            "ssl handshake",
            "tls handshake",
            "ssl: ",
            "certificate verify failed",
            "service unavailable",
            "internal server error",
            "bad gateway",
            "gateway timeout",
            "try again later",
            "temporarily unavailable",
            "fragment",
            "interrupted",
            "aborted"
        ).any { t.contains(it) }
    }

    /** Storage/MediaStore failures are reported separately from connection problems. */
    fun isStorageFailure(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return listOf(
            "no space left",
            "not enough space",
            "quota exceeded",
            "storage full",
            "could not create mediastore entry",
            "could not open mediastore output",
            "read-only file system",
            "permission denied",
            "failed to create file",
            "cannot create file"
        ).any { t.contains(it) }
    }

    /** Failures that happen after the bytes arrived (ffmpeg merge / audio conversion). */
    fun isProcessingFailure(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return listOf(
            "postprocessing",
            "post-processing",
            "[merger]",
            "[ffmpeg]",
            "ffmpeg",
            "merge",
            "muxing",
            "no output file was produced",
            "audio conversion",
            "audioformat"
        ).any { t.contains(it) }
    }

    /** One place that turns a low-level failure into the message the user actually sees. */
    fun classifyFailure(detail: String?): ErrorKind = when {
        isNetworkFailure(detail) -> ErrorKind.NETWORK
        isStorageFailure(detail) -> ErrorKind.STORAGE
        isProcessingFailure(detail) -> ErrorKind.PROCESSING
        detail.orEmpty().contains("private", true) ||
            detail.orEmpty().contains("sign in", true) ||
            detail.orEmpty().contains("login", true) -> ErrorKind.PRIVATE
        else -> ErrorKind.DOWNLOAD
    }
}
