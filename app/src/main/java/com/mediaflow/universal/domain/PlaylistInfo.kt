package com.mediaflow.universal.domain

import com.mediaflow.universal.core.UrlDetector.Platform

/** One entry of a remote playlist/set. Each entry is analysed as its own media item. */
data class PlaylistItem(
    val url: String,
    val title: String,
    val durationSeconds: Long? = null
)

/** Public metadata for a playlist link, before any item is downloaded. */
data class PlaylistInfo(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val items: List<PlaylistItem>
)
