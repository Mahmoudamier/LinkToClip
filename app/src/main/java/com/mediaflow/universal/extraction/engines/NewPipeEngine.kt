package com.mediaflow.universal.extraction.engines

import com.mediaflow.universal.core.UrlDetector.Platform
import com.mediaflow.universal.domain.ExtractionEngine
import com.mediaflow.universal.domain.MediaInfo
import com.mediaflow.universal.domain.MediaKind
import com.mediaflow.universal.domain.MediaOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber

/**
 * YouTube and SoundCloud extraction. NewPipeExtractor reads public stream metadata without an
 * account or API key; all formats it exposes are the ones the platform itself serves publicly.
 */
object NewPipeEngine {

    init {
        runCatching { NewPipe.init(OkHttpNewPipeDownloader()) }
            .onFailure { Timber.e(it, "NewPipe initialisation failed") }
    }

    fun supports(platform: Platform): Boolean = platform.newPipeServiceId != null

    suspend fun analyze(url: String, platform: Platform): MediaInfo = withContext(Dispatchers.IO) {
        val serviceId = requireNotNull(platform.newPipeServiceId)
        val info = StreamInfo.getInfo(NewPipe.getService(serviceId), url)

        val progressive = info.videoStreams.filter { it.isUrl }
        val adaptive = info.videoOnlyStreams.filter { it.isUrl }

        val videoOptions = progressive.map(::videoOption).toMutableList()
        val existingLabels = videoOptions.map { it.label }.toSet()
        // Video-only streams are offered through yt-dlp so video and audio can be merged locally.
        adaptive.forEach { stream ->
            val label = resolutionLabel(stream)
            if (label !in existingLabels && label.isNotBlank()) {
                videoOptions += MediaOption(
                    id = "v-only-${stream.id}",
                    label = label,
                    kind = MediaKind.VIDEO,
                    extension = "mp4",
                    qualityRank = parseResolution(label),
                    sizeBytes = estimateSize(stream.bitrate.toLong(), info.duration),
                    bitrateBps = stream.bitrate.takeIf { it > 0 }?.toLong(),
                    engine = ExtractionEngine.YT_DLP,
                    downloadKey = "-f ${stream.id}+bestaudio/best",
                    requiresAudioMux = true,
                    notes = "عالية الوضوح"
                )
            }
        }

        val audioOptions = info.audioStreams
            .filter { it.isUrl }
            .map { audioOption(it, info.duration) }
            .sortedByDescending { it.bitrateBps ?: 0 }
            .distinctBy { "${it.label}-${it.extension}" }
            .take(5)
            .toMutableList()

        // MP3 is produced locally by ffmpeg because platforms rarely serve public MP3 streams.
        audioOptions += MediaOption(
            id = "mp3-conversion",
            label = "صوت MP3 · 320 كيلوبت/ث",
            kind = MediaKind.AUDIO,
            extension = "mp3",
            qualityRank = 320,
            sizeBytes = estimateSize(320_000L, info.duration),
            bitrateBps = 320_000L,
            engine = ExtractionEngine.YT_DLP,
            downloadKey = "-f bestaudio -x --audio-format mp3 --audio-quality 0",
            notes = "صوت MP3"
        )

        val thumbnail = info.thumbnails.maxByOrNull { it.height }?.url ?: info.thumbnails.firstOrNull()?.url

        MediaInfo(
            sourceUrl = url,
            platform = platform,
            title = info.name.ifBlank { url },
            author = info.uploaderName.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnail,
            durationSeconds = info.duration.takeIf { it > 0 },
            engine = ExtractionEngine.NEW_PIPE,
            videoOptions = videoOptions.sortedByDescending { it.qualityRank },
            audioOptions = audioOptions
        )
    }

    private fun videoOption(stream: VideoStream): MediaOption {
        val label = resolutionLabel(stream)
        val suffix = stream.format?.suffix?.takeIf { it.isNotBlank() } ?: "mp4"
        return MediaOption(
            id = "v-${stream.id}",
            label = label.ifBlank { stream.id },
            kind = MediaKind.VIDEO,
            extension = suffix,
            qualityRank = parseResolution(label),
            sizeBytes = null,
            bitrateBps = stream.bitrate.takeIf { it > 0 }?.toLong(),
            engine = ExtractionEngine.NEW_PIPE,
            downloadKey = stream.id,
            directUrl = stream.content.takeIf { stream.isUrl },
            notes = if (stream.format?.name?.contains("mpeg", true) == true) "فيديو MP4" else null
        )
    }

    private fun audioOption(stream: AudioStream, duration: Long): MediaOption {
        val kbps = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate.takeIf { it > 0 } ?: 128_000
        val suffix = stream.format?.suffix?.takeIf { it.isNotBlank() } ?: "m4a"
        return MediaOption(
            id = "a-${stream.id}",
            label = "${kbps / 1000} كيلوبت/ث",
            kind = MediaKind.AUDIO,
            extension = suffix,
            qualityRank = kbps / 1000,
            sizeBytes = estimateSize(kbps.toLong(), duration),
            bitrateBps = kbps.toLong(),
            engine = ExtractionEngine.NEW_PIPE,
            downloadKey = stream.id,
            directUrl = stream.content.takeIf { stream.isUrl }
        )
    }

    private fun resolutionLabel(stream: VideoStream): String {
        val fromResolution = stream.resolution?.trim().orEmpty()
        if (fromResolution.isNotBlank()) return fromResolution
        val height = stream.height.takeIf { it > 0 }
        return if (height != null) "${height}p" else ""
    }

    private fun parseResolution(label: String): Int =
        Regex("(\\d+)").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun estimateSize(bitrate: Long, durationSeconds: Long): Long? =
        if (bitrate > 0 && durationSeconds > 0) bitrate * durationSeconds / 8 else null
}
