package jp.essential.app.feature.downloader

import org.junit.Assert.*
import org.junit.Test

class VideoDownloadPolicyTest {
    @Test fun twitterPrefersDirectMp4AndUsesStrictVideoOnlyFallback() {
        val value = VideoDownloadPolicy.format(DownloaderSelection("https://x.com/user/status/123", DownloadMediaType.Video), true)
        assertTrue(value.startsWith("b[height<=?1080][fps<=?60][ext=mp4][protocol=https]/"))
        assertFalse(value.contains("bv*"))
        assertTrue(value.contains(",ba)"))
    }

    @Test fun otherSitesRetainSeparateHighQualityStreams() {
        val selection = DownloaderSelection("https://youtube.com/watch?v=test", DownloadMediaType.Video)
        assertTrue(VideoDownloadPolicy.format(selection, true).startsWith("(bv"))
        assertTrue(VideoDownloadPolicy.format(selection, false).contains("+ba"))
        assertFalse(VideoDownloadPolicy.isTwitter("https://x.com.example.org/status/1"))
    }
}
