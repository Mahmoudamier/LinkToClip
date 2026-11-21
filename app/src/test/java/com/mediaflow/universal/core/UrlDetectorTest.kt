package com.mediaflow.universal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDetectorTest {

    private fun platformOf(raw: String) = (UrlDetector.detect(raw) as? UrlDetector.Result.Detected)?.platform

    @Test
    fun detectsYouTubeWatchLinks() {
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("https://m.youtube.com/watch?v=abc123"))
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("https://music.youtube.com/watch?v=abc123"))
    }

    @Test
    fun detectsYouTubeShortLinksAndShorts() {
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("https://www.youtube.com/shorts/abc123"))
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("https://www.youtube.com/embed/abc123"))
    }

    @Test
    fun detectsSoundCloudFacebookInstagram() {
        assertEquals(UrlDetector.Platform.SOUNDCLOUD, platformOf("https://soundcloud.com/artist/track"))
        assertEquals(UrlDetector.Platform.SOUNDCLOUD, platformOf("https://on.soundcloud.com/abc123"))
        assertEquals(UrlDetector.Platform.FACEBOOK, platformOf("https://www.facebook.com/watch/?v=123456"))
        assertEquals(UrlDetector.Platform.INSTAGRAM, platformOf("https://www.instagram.com/reel/ABC123/"))
        assertEquals(UrlDetector.Platform.INSTAGRAM, platformOf("https://instagr.am/p/ABC123/"))
    }

    @Test
    fun detectsTikTokLinks() {
        assertEquals(
            UrlDetector.Platform.TIKTOK,
            platformOf("https://www.tiktok.com/@user/video/7123456789012345678")
        )
        assertEquals(UrlDetector.Platform.TIKTOK, platformOf("https://vm.tiktok.com/ZMabc123/"))
        assertEquals(UrlDetector.Platform.TIKTOK, platformOf("https://vt.tiktok.com/ZSabc123"))
        assertEquals(UrlDetector.Platform.TIKTOK, platformOf("tiktok.com/@user/reel/123456"))
    }

    @Test
    fun detectsPlaylistLinks() {
        val youtube = UrlDetector.detect("https://www.youtube.com/playlist?list=PL123456") as UrlDetector.Result.Detected
        assertEquals(UrlDetector.Platform.YOUTUBE, youtube.platform)
        assertTrue(youtube.isPlaylist)

        val watchList = UrlDetector.detect("https://www.youtube.com/watch?v=abc&list=PL123") as UrlDetector.Result.Detected
        assertTrue(watchList.isPlaylist)

        val soundCloud = UrlDetector.detect("https://soundcloud.com/user/sets/my-mix") as UrlDetector.Result.Detected
        assertEquals(UrlDetector.Platform.SOUNDCLOUD, soundCloud.platform)
        assertTrue(soundCloud.isPlaylist)

        val single = UrlDetector.detect("https://www.youtube.com/watch?v=abc") as UrlDetector.Result.Detected
        assertFalse(single.isPlaylist)
    }

    @Test
    fun addsMissingSchemeAndTrimsCopyPasteArtifacts() {
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("  youtube.com/watch?v=abc123  "))
        assertEquals(UrlDetector.Platform.YOUTUBE, platformOf("\"https://youtu.be/abc123\""))
    }

    @Test
    fun rejectsInvalidAndUnsupportedLinks() {
        assertTrue(UrlDetector.detect(null) is UrlDetector.Result.Invalid)
        assertTrue(UrlDetector.detect("   ") is UrlDetector.Result.Invalid)
        assertTrue(UrlDetector.detect("ftp://youtube.com/watch?v=abc") is UrlDetector.Result.Invalid)
        assertTrue(UrlDetector.detect("https://not-a-platform.example/watch?v=abc") is UrlDetector.Result.Unsupported)
        assertTrue(UrlDetector.detect("https://youtube.com/") is UrlDetector.Result.Unsupported)
        assertTrue(UrlDetector.detect("https://instagram.com/") is UrlDetector.Result.Unsupported)
    }

    @Test
    fun isSupportedLinkMatchesDetectedResult() {
        assertTrue(UrlDetector.isSupportedLink("https://youtu.be/abc123"))
        assertFalse(UrlDetector.isSupportedLink("https://example.com/video"))
        assertFalse(UrlDetector.isSupportedLink(null))
    }

    @Test
    fun normalisedUrlKeepsOriginalSchemeAndHost() {
        val result = UrlDetector.detect("youtube.com/watch?v=abc123") as UrlDetector.Result.Detected
        assertEquals("https://youtube.com/watch?v=abc123", result.url)
    }
}
