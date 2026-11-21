package com.mediaflow.universal.domain

import com.mediaflow.universal.data.DefaultFormat
import com.mediaflow.universal.data.DefaultQuality

/** Pure, UI-independent logic for choosing the initial download option. */
object FormatSelector {

    fun choose(
        options: List<MediaOption>,
        quality: DefaultQuality,
        format: DefaultFormat,
        kind: MediaKind
    ): MediaOption? {
        if (options.isEmpty()) return null
        if (kind == MediaKind.AUDIO) {
            val extension = when (format) {
                DefaultFormat.MP3 -> "mp3"
                DefaultFormat.M4A -> "m4a"
                else -> null
            }
            if (extension != null) {
                options.firstOrNull { it.extension.equals(extension, true) }?.let { return it }
            }
            return options.maxByOrNull { it.qualityRank }
        }

        return when (quality) {
            DefaultQuality.HIGHEST, DefaultQuality.AUTO -> options.maxByOrNull { it.qualityRank }
            DefaultQuality.LOWEST -> options.minByOrNull { it.qualityRank }
            DefaultQuality.P720 -> options.minByOrNull { kotlin.math.abs(it.qualityRank - 720) }
            DefaultQuality.P480 -> options.minByOrNull { kotlin.math.abs(it.qualityRank - 480) }
        } ?: options.firstOrNull()
    }
}
