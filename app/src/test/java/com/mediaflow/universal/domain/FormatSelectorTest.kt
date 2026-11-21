package com.mediaflow.universal.domain

import com.mediaflow.universal.data.DefaultFormat
import com.mediaflow.universal.data.DefaultQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSelectorTest {

    private fun option(label: String, rank: Int, extension: String = "mp4", kind: MediaKind = MediaKind.VIDEO) =
        MediaOption(
            id = label,
            label = label,
            kind = kind,
            extension = extension,
            qualityRank = rank,
            engine = ExtractionEngine.NEW_PIPE,
            downloadKey = label
        )

    private val video = listOf(option("144p", 144), option("480p", 480), option("720p", 720), option("1080p", 1080))
    private val audio = listOf(
        option("opus", 0, "opus", MediaKind.AUDIO),
        option("m4a", 128, "m4a", MediaKind.AUDIO),
        option("mp3", 320, "mp3", MediaKind.AUDIO)
    )

    @Test
    fun returnsNullForEmptyOptions() {
        assertNull(FormatSelector.choose(emptyList(), DefaultQuality.HIGHEST, DefaultFormat.AUTO, MediaKind.VIDEO))
    }

    @Test
    fun selectsHighestAndLowestVideoQuality() {
        assertEquals("1080p", FormatSelector.choose(video, DefaultQuality.HIGHEST, DefaultFormat.AUTO, MediaKind.VIDEO)?.id)
        assertEquals("1080p", FormatSelector.choose(video, DefaultQuality.AUTO, DefaultFormat.AUTO, MediaKind.VIDEO)?.id)
        assertEquals("144p", FormatSelector.choose(video, DefaultQuality.LOWEST, DefaultFormat.AUTO, MediaKind.VIDEO)?.id)
    }

    @Test
    fun selectsClosestRequestedResolution() {
        assertEquals("720p", FormatSelector.choose(video, DefaultQuality.P720, DefaultFormat.AUTO, MediaKind.VIDEO)?.id)
        assertEquals("480p", FormatSelector.choose(video, DefaultQuality.P480, DefaultFormat.AUTO, MediaKind.VIDEO)?.id)
    }

    @Test
    fun respectsPreferredAudioFormat() {
        assertEquals("mp3", FormatSelector.choose(audio, DefaultQuality.HIGHEST, DefaultFormat.MP3, MediaKind.AUDIO)?.id)
        assertEquals("m4a", FormatSelector.choose(audio, DefaultQuality.HIGHEST, DefaultFormat.M4A, MediaKind.AUDIO)?.id)
    }

    @Test
    fun fallsBackToBestAudioWhenFormatUnavailable() {
        val onlyOpus = listOf(option("opus", 64, "opus", MediaKind.AUDIO))
        assertEquals("opus", FormatSelector.choose(onlyOpus, DefaultQuality.HIGHEST, DefaultFormat.MP3, MediaKind.AUDIO)?.id)
    }
}
