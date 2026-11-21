package com.mediaflow.universal.extraction

import com.mediaflow.universal.core.AppException
import com.mediaflow.universal.core.ErrorKind
import com.mediaflow.universal.core.UrlDetector.Platform
import com.mediaflow.universal.domain.MediaInfo
import com.mediaflow.universal.extraction.engines.NewPipeEngine
import com.mediaflow.universal.extraction.engines.YtDlpEngine
import timber.log.Timber

interface PlatformProvider {
    val platform: Platform
    suspend fun analyze(url: String): MediaInfo
}

/** Each platform has its own provider and its own choice/fallback of extraction engine. */
abstract class BaseProvider : PlatformProvider {
    protected suspend fun withFallback(
        url: String,
        preferFallbackWhenLowQuality: Boolean = false,
        primary: suspend () -> MediaInfo
    ): MediaInfo = try {
        val primaryInfo = primary()
        if (preferFallbackWhenLowQuality && maxQuality(primaryInfo) < 720) {
            // NewPipe sometimes exposes only progressive (<=360p) YouTube streams. Ask yt-dlp
            // for the full format list, but keep the NewPipe result if it is not better.
            val fallbackInfo = runCatching { YtDlpEngine.analyze(url, platform) }.getOrNull()
            if (fallbackInfo != null && maxQuality(fallbackInfo) > maxQuality(primaryInfo)) {
                fallbackInfo
            } else {
                primaryInfo
            }
        } else {
            primaryInfo
        }
    } catch (e: AppException) {
        when (e.kind) {
            ErrorKind.DELETED, ErrorKind.PRIVATE, ErrorKind.RESTRICTED, ErrorKind.UNSUPPORTED_LINK -> throw e
            else -> fallback(url, e)
        }
    } catch (e: Exception) {
        fallback(url, e)
    }

    private fun maxQuality(info: MediaInfo): Int =
        info.videoOptions.maxOfOrNull { it.qualityRank } ?: 0

    private suspend fun fallback(url: String, cause: Throwable): MediaInfo = try {
        YtDlpEngine.analyze(url, platform)
    } catch (fallbackError: Exception) {
        Timber.w(cause, "Primary extractor failed for ${platform.id}")
        Timber.w(fallbackError, "Fallback extractor failed for ${platform.id}")
        throw fallbackError
    }
}

class YouTubeProvider : BaseProvider() {
    override val platform = Platform.YOUTUBE
    override suspend fun analyze(url: String): MediaInfo = withFallback(
        url,
        preferFallbackWhenLowQuality = true
    ) {
        NewPipeEngine.analyze(url, platform)
    }
}

class SoundCloudProvider : BaseProvider() {
    override val platform = Platform.SOUNDCLOUD
    override suspend fun analyze(url: String): MediaInfo = withFallback(url) {
        NewPipeEngine.analyze(url, platform)
    }
}

class FacebookProvider : BaseProvider() {
    override val platform = Platform.FACEBOOK
    override suspend fun analyze(url: String): MediaInfo = withFallback(url) {
        YtDlpEngine.analyze(url, platform)
    }
}

class InstagramProvider : BaseProvider() {
    override val platform = Platform.INSTAGRAM
    override suspend fun analyze(url: String): MediaInfo = withFallback(url) {
        YtDlpEngine.analyze(url, platform)
    }
}

class TikTokProvider : BaseProvider() {
    override val platform = Platform.TIKTOK
    override suspend fun analyze(url: String): MediaInfo = withFallback(url) {
        YtDlpEngine.analyze(url, platform)
    }
}

/** Single entry point for the UI: takes a pasted URL and returns rich media metadata. */
class MediaAnalyzer {
    private val providers = listOf(
        YouTubeProvider(), SoundCloudProvider(), FacebookProvider(), InstagramProvider(), TikTokProvider()
    )

    suspend fun analyze(url: String, platform: Platform): MediaInfo {
        val provider = providers.firstOrNull { it.platform == platform }
            ?: throw AppException(ErrorKind.UNSUPPORTED_LINK)
        return provider.analyze(url)
    }
}
