package com.mediaflow.universal.extraction

import com.mediaflow.universal.core.AppException
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.core.UrlDetector.Platform
import com.mediaflow.universal.domain.PlaylistInfo
import com.mediaflow.universal.domain.PlaylistItem
import com.mediaflow.universal.extraction.engines.NewPipeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.playlist.PlaylistInfo as NewPipePlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber

/** Reads public YouTube playlists and SoundCloud sets without an account or API key. */
object PlaylistExtractor {

    suspend fun analyze(url: String, platform: Platform): PlaylistInfo = withContext(Dispatchers.IO) {
        val serviceId = platform.newPipeServiceId
            ?: throw AppException(ErrorKind.UNSUPPORTED_LINK, "Playlists are supported for YouTube and SoundCloud")

        // Touching NewPipeEngine guarantees NewPipe.init() has run with our downloader.
        runCatching { NewPipeEngine.supports(platform) }
            .onFailure { Timber.e(it, "NewPipe init check failed") }

        val info = runCatching { NewPipePlaylistInfo.getInfo(NewPipe.getService(serviceId), url) }
            .getOrElse { error ->
                Timber.w(error, "NewPipe playlist extraction failed for ${platform.id}")
                if (platform == Platform.SOUNDCLOUD) return@withContext fromSoundCloudHtml(url)
                throw error
            }
        val items = info.relatedItems.orEmpty().mapNotNull { item ->
            val itemUrl = item.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val duration = (item as? StreamInfoItem)?.duration?.takeIf { it > 0 }
            PlaylistItem(
                url = itemUrl,
                title = item.name.ifBlank { itemUrl },
                durationSeconds = duration
            )
        }
        if (items.isEmpty()) {
            throw AppException(ErrorKind.EXTRACT, "This playlist has no downloadable items")
        }
        PlaylistInfo(
            sourceUrl = url,
            platform = platform,
            title = info.name.ifBlank { url },
            items = items
        )
    }

    /**
     * NewPipe often cannot resolve SoundCloud's rotating client id. SoundCloud still publishes
     * the set's track list in the page HTML, so read that public data directly as a fallback.
     */
    private fun fromSoundCloudHtml(url: String): PlaylistInfo {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val html = OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AppException(ErrorKind.EXTRACT, "SoundCloud returned ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        val start = html.indexOf("\"tracks\":[")
        if (start < 0 || html.isBlank()) {
            throw AppException(ErrorKind.EXTRACT, "Could not read the tracks in this SoundCloud set")
        }
        val chunk = html.substring(start, (start + 200_000).coerceAtMost(html.length))
        val items = Regex("\"permalink_url\":\"([^\"]+)\"").findAll(chunk)
            .map { it.groupValues[1].replace("\\/", "/") }
            .filter { candidate ->
                val path = runCatching { java.net.URI(candidate).path.orEmpty() }.getOrDefault("")
                val segments = path.trim('/').split('/').filter { it.isNotBlank() }
                segments.size >= 2 && !path.contains("/sets/", ignoreCase = true)
            }
            .distinct()
            .map { link ->
                PlaylistItem(
                    url = link,
                    title = link.substringAfterLast('/').replace('-', ' ').replace('_', ' ')
                )
            }
            .toList()
        if (items.isEmpty()) {
            throw AppException(ErrorKind.EXTRACT, "This SoundCloud set has no downloadable tracks")
        }
        val title = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replace("| SoundCloud", "", ignoreCase = true)?.trim()
            ?.takeIf { it.isNotBlank() } ?: url
        return PlaylistInfo(url, Platform.SOUNDCLOUD, title, items)
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Mobile Safari/537.36"
}
