package com.mediaflow.universal.domain

import com.mediaflow.universal.core.UrlDetector.Platform

enum class MediaKind { VIDEO, AUDIO }

enum class ExtractionEngine { NEW_PIPE, YT_DLP }

/**
 * One selectable download quality. [id] is stable inside one analysis result; [downloadKey]
 * is the engine specific selector used when the worker starts the download.
 */
data class MediaOption(
    val id: String,
    val label: String,
    val kind: MediaKind,
    val extension: String,
    val qualityRank: Int = 0,
    val sizeBytes: Long? = null,
    val bitrateBps: Long? = null,
    val engine: ExtractionEngine,
    val downloadKey: String,
    val directUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requiresAudioMux: Boolean = false,
    val audioPartnerKey: String? = null,
    val notes: String? = null
) {
    val isAudioOnly: Boolean get() = kind == MediaKind.AUDIO
}

data class MediaInfo(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val engine: ExtractionEngine,
    val videoOptions: List<MediaOption>,
    val audioOptions: List<MediaOption>
) {
    val hasVideo: Boolean get() = videoOptions.isNotEmpty()
    val hasAudio: Boolean get() = audioOptions.isNotEmpty()
}
