package jp.essential.app.feature.downloader

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ImagePreviewQualityTest {
    @Test fun expandedPreviewUsesSelectedResolutionAndFormat() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "essential-preview-test.png")
        val original = Bitmap.createBitmap(4000, 2000, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { original.recycle() }
        try {
            val engine = YtDlpDownloadEngine(context)
            for ((quality, width) in listOf(ImageQuality.Fhd to 1920, ImageQuality.P4K to 3840, ImageQuality.P8K to 4000)) {
                for (format in ImageFormat.entries) {
                    val preview = engine.renderExpandedPreview(source, quality, format)
                    assertNotNull(preview)
                    try {
                        assertEquals(width, preview!!.width)
                        assertEquals(width / 2, preview.height)
                    } finally { preview?.recycle() }
                }
            }
        } finally { source.delete() }
    }

    @Test fun videoHtmlPrefersPosterOverPageLogos() {
        val images = ImageDiscovery.parseHtml("<video poster='/cover.jpg'></video><img src='/logo.png'>", "https://example.com/video")
        assertEquals(1, images.size)
        assertEquals("https://example.com/cover.jpg", images.single().url)
        assertEquals("動画のサムネイル", images.single().label)
    }

    @Test fun tiktokVideoUsesOriginalCover() {
        val html = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"id":"123","video":{"originCover":"https://example.com/original.jpg","cover":"https://example.com/small.jpg"}}}}}}</script>"""
        val images = ImageDiscovery.parseTikTok(html, "https://www.tiktok.com/@user/video/123")
        assertEquals("https://example.com/original.jpg", images.single().url)
        assertEquals(listOf("https://example.com/small.jpg"), images.single().alternateUrls)
    }
}
