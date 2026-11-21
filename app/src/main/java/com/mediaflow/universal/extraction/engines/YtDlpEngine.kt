package com.mediaflow.universal.extraction.engines

import android.content.Context
import com.farimarwat.commons.VideoFormat
import com.farimarwat.commons.VideoInfo
import com.farimarwat.commons.YoutubeDLRequest
import com.farimarwat.library.YoutubeDL
import com.mediaflow.universal.core.AppException
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.core.UrlDetector.Platform
import com.mediaflow.universal.domain.ExtractionEngine
import com.mediaflow.universal.domain.MediaInfo
import com.mediaflow.universal.domain.MediaKind
import com.mediaflow.universal.domain.MediaOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device yt-dlp engine. It is used for Facebook and Instagram (which are not covered by
 * NewPipeExtractor) and as a fallback for YouTube/SoundCloud. It only reads public content;
 * private or login-protected media produces a clear error instead of a fake result.
 */
object YtDlpEngine {

    @Volatile
    private var ready = false
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (ready) return
        runCatching {
            YoutubeDL.init(
                appContext = appContext!!,
                withFfmpeg = true,
                withAria2c = false,
                onSuccess = { ready = true },
                onError = { error -> Timber.e(error, "yt-dlp init failed") }
            )
        }.onFailure { Timber.e(it, "yt-dlp init threw") }
    }

    suspend fun ensureReady(context: Context? = null): Boolean {
        context?.let { initialize(it) }
        val ctx = appContext ?: return false
        if (!ready) initialize(ctx)
        repeat(80) {
            if (ready) return true
            delay(250)
        }
        return ready
    }

    suspend fun analyze(url: String, platform: Platform): MediaInfo = withContext(Dispatchers.IO) {
        if (!ensureReady()) throw AppException(ErrorKind.EXTRACT, "yt-dlp is not ready")

        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--socket-timeout", 20)
            .addOption("--retries", 1)

        val info = suspendCancellableCoroutine<VideoInfo> { cont ->
            YoutubeDL.getInfo(
                request = request,
                onSuccess = { if (cont.isActive) cont.resume(it) },
                onError = { error -> if (cont.isActive) cont.resumeWithException(error) }
            )
        }

        val formats = info.formats.orEmpty().filter { !it.formatId.isNullOrBlank() }
        val duration = info.duration.takeIf { it > 0 }?.toLong()

        val videoOptions = buildVideoOptions(formats, duration)
        val audioOptions = buildAudioOptions(formats, duration).toMutableList()
        audioOptions += MediaOption(
            id = "mp3-conversion",
            label = "صوت MP3 · 320 كيلوبت/ث",
            kind = MediaKind.AUDIO,
            extension = "mp3",
            qualityRank = 320,
            sizeBytes = estimateSize(320_000L, duration),
            bitrateBps = 320_000L,
            engine = ExtractionEngine.YT_DLP,
            downloadKey = "-f bestaudio -x --audio-format mp3 --audio-quality 0"
        )

        MediaInfo(
            sourceUrl = url,
            platform = platform,
            title = (info.title ?: info.fulltitle ?: url).ifBlank { url },
            author = info.uploader?.takeIf { it.isNotBlank() },
            thumbnailUrl = info.thumbnail,
            durationSeconds = duration,
            engine = ExtractionEngine.YT_DLP,
            videoOptions = videoOptions,
            audioOptions = audioOptions
        )
    }

    private fun buildVideoOptions(formats: List<VideoFormat>, duration: Long?): List<MediaOption> {
        val candidates = formats.filter { format ->
            val vcodec = format.vcodec?.lowercase().orEmpty()
            vcodec.isNotBlank() && vcodec != "none" && format.height > 0
        }

        return candidates
            .groupBy { it.height }
            .mapNotNull { (height, group) ->
                val best = group.maxWith(
                    compareBy<VideoFormat> { it.tbr }
                        .thenBy { if (it.ext.equals("mp4", true)) 1 else 0 }
                )
                val id = best.formatId ?: return@mapNotNull null
                val hasAudio = !best.acodec.isNullOrBlank() && best.acodec != "none"
                val selector = if (hasAudio) "-f $id" else "-f $id+bestaudio/best"
                MediaOption(
                    id = "v-${id}",
                    label = "دقة ${height}p",
                    kind = MediaKind.VIDEO,
                    extension = if (hasAudio) best.ext ?: "mp4" else "mp4",
                    qualityRank = height,
                    sizeBytes = knownSize(best, duration, best.tbr.toLong()),
                    bitrateBps = best.tbr.takeIf { it > 0 }?.toLong(),
                    engine = ExtractionEngine.YT_DLP,
                    downloadKey = selector,
                    requiresAudioMux = !hasAudio,
                    notes = best.ext?.let { "صيغة ${it.uppercase()}" }
                )
            }
            .sortedByDescending { it.qualityRank }
            .take(12)
    }

    private fun buildAudioOptions(formats: List<VideoFormat>, duration: Long?): List<MediaOption> {
        val candidates = formats.filter { format ->
            val acodec = format.acodec?.lowercase().orEmpty()
            val vcodec = format.vcodec?.lowercase().orEmpty()
            acodec.isNotBlank() && acodec != "none" && (vcodec.isBlank() || vcodec == "none")
        }

        return candidates
            .groupBy { it.ext ?: "m4a" }
            .mapNotNull { (ext, group) ->
                val best = group.maxByOrNull { it.abr.takeIf { v -> v > 0 } ?: it.tbr } ?: return@mapNotNull null
                val id = best.formatId ?: return@mapNotNull null
                val kbps = best.abr.takeIf { it > 0 } ?: best.tbr
                MediaOption(
                    id = "a-${id}",
                    label = "صوت ${ext.uppercase()} · ${kbps / 1000} كيلوبت/ث",
                    kind = MediaKind.AUDIO,
                    extension = ext,
                    qualityRank = kbps / 1000,
                    sizeBytes = knownSize(best, duration, kbps.toLong()),
                    bitrateBps = kbps.takeIf { it > 0 }?.toLong(),
                    engine = ExtractionEngine.YT_DLP,
                    downloadKey = "-f $id"
                )
            }
            .sortedByDescending { it.qualityRank }
            .take(5)
    }

    private fun knownSize(format: VideoFormat, duration: Long?, bitrate: Long): Long? {
        val exact = format.fileSize.takeIf { it > 0 }
            ?: format.fileSizeApproximate.takeIf { it > 0 }
        return exact ?: estimateSize(bitrate, duration)
    }

    private fun estimateSize(bitrate: Long, duration: Long?): Long? =
        if (bitrate > 0 && duration != null && duration > 0) bitrate * duration / 8 else null
}
