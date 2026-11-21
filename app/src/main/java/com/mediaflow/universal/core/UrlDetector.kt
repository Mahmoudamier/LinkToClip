package com.mediaflow.universal.core

import java.net.URI

/**
 * Detects the source platform from a pasted link. The user never has to choose a platform.
 * Detection only inspects the host/path; it does not fetch any remote content.
 */
object UrlDetector {

    enum class Platform(val id: String, val newPipeServiceId: Int?) {
        YOUTUBE("youtube", 0),
        SOUNDCLOUD("soundcloud", 1),
        FACEBOOK("facebook", null),
        INSTAGRAM("instagram", null),
        TIKTOK("tiktok", null);

        val displayName: String
            get() = when (this) {
                YOUTUBE -> "يوتيوب"
                SOUNDCLOUD -> "ساوندكلاود"
                FACEBOOK -> "فيسبوك"
                INSTAGRAM -> "إنستغرام"
                TIKTOK -> "تيك توك"
            }
    }

    sealed interface Result {
        data class Detected(
            val platform: Platform,
            val url: String,
            val isPlaylist: Boolean = false
        ) : Result
        data object Invalid : Result
        data object Unsupported : Result
    }

    private val youtubeHosts = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtu.be", "www.youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com"
    )
    private val soundCloudHosts = setOf(
        "soundcloud.com", "www.soundcloud.com", "on.soundcloud.com", "m.soundcloud.com"
    )
    private val facebookHosts = setOf(
        "facebook.com", "www.facebook.com", "m.facebook.com", "fb.me", "fb.com", "www.fb.com"
    )
    private val instagramHosts = setOf(
        "instagram.com", "www.instagram.com", "instagr.am", "www.instagr.am"
    )
    private val tiktokHosts = setOf(
        "tiktok.com", "www.tiktok.com", "m.tiktok.com",
        "vm.tiktok.com", "vt.tiktok.com", "www.vm.tiktok.com", "www.vt.tiktok.com"
    )
    private val tiktokShortHosts = setOf("vm.tiktok.com", "vt.tiktok.com", "www.vm.tiktok.com", "www.vt.tiktok.com")

    /** Normalises common "copy paste" variants (missing scheme, extra whitespace, mobile links). */
    fun detect(raw: String?): Result {
        if (raw.isNullOrBlank()) return Result.Invalid
        var value = raw.trim().trim('"', '\'', '<', '>')
        if (whitespaceOnlyRegex.containsMatchIn(value)) return Result.Invalid
        if (!value.contains("://")) value = "https://$value"

        val uri = runCatching { URI(value) }.getOrNull() ?: return Result.Invalid
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return Result.Invalid
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return Result.Invalid
        if (!host.matches(hostRegex)) return Result.Invalid
        val path = uri.path.orEmpty()
        val query = uri.query.orEmpty()

        val platform = when (host) {
            in youtubeHosts -> Platform.YOUTUBE
            in soundCloudHosts -> Platform.SOUNDCLOUD
            in facebookHosts -> Platform.FACEBOOK
            in instagramHosts -> Platform.INSTAGRAM
            in tiktokHosts -> Platform.TIKTOK
            else -> return Result.Unsupported
        }

        if (!hasMediaPath(platform, host, path, query)) return Result.Unsupported
        return Result.Detected(platform, value, isPlaylist(platform, path, query))
    }

    /** True for YouTube playlists and SoundCloud sets (including watch links with a list id). */
    private fun isPlaylist(platform: Platform, path: String, query: String): Boolean {
        val lower = path.lowercase()
        return when (platform) {
            Platform.YOUTUBE -> lower.startsWith("/playlist") ||
                (lower.startsWith("/watch") && query.split('&')
                    .any { it.startsWith("list=", ignoreCase = true) && it.length > 5 })
            Platform.SOUNDCLOUD -> lower.contains("/sets/")
            else -> false
        }
    }

    /** Used for the clipboard suggestion. Never triggers a download by itself. */
    fun isSupportedLink(raw: String?): Boolean = detect(raw) is Result.Detected

    private fun hasMediaPath(platform: Platform, host: String, path: String, query: String): Boolean {
        val lower = path.lowercase()
        return when (platform) {
            Platform.YOUTUBE -> {
                if (query.split('&').any { it.startsWith("v=", ignoreCase = true) && it.length > 2 }) {
                    true
                } else if (host in setOf("youtu.be", "www.youtu.be")) {
                    lower.trim('/').isNotBlank()
                } else {
                    listOf("/watch", "/shorts/", "/embed/", "/live/", "/v/", "/e/", "/playlist")
                        .any { lower.startsWith(it) }
                }
            }
            Platform.SOUNDCLOUD -> lower.trim('/').isNotBlank()
            Platform.FACEBOOK -> lower.trim('/').isNotBlank()
            Platform.INSTAGRAM -> listOf("/p/", "/reel/", "/reels/", "/tv/")
                .any { lower.startsWith(it) }
            Platform.TIKTOK -> {
                if (host in tiktokShortHosts) lower.trim('/').isNotBlank()
                else listOf("/video/", "/reel/", "/@").any { lower.startsWith(it) }
            }
        }
    }

    private val hostRegex = Regex("^[a-z0-9.-]+$")
    private val whitespaceOnlyRegex = Regex("\\s")
}
