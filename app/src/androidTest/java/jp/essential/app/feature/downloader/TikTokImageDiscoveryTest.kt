package jp.essential.app.feature.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TikTokImageDiscoveryTest {
    private fun html(key: String) = """
        <img src="https://example.com/black.png"><img alt="TikTok logo" src="https://example.com/logo.png">
        <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">
        {"__DEFAULT_SCOPE__":{"$key":{"statusCode":0,"itemInfo":{"itemStruct":{"id":"123","imagePost":{
          "images":[
            {"imageWidth":1086,"imageHeight":1448,"imageURL":{"urlList":["https://example.com/one.jpeg?x-signature=a%2Fb&x-expires=123","https://backup.example.com/one.jpeg"]}},
            {"imageWidth":1086,"imageHeight":1448,"imageURL":{"urlList":["https://example.com/two.jpeg"]}}
          ],"cover":{"imageURL":{"urlList":["https://example.com/cover.jpeg"]}}
        }}}}}}</script>
    """.trimIndent()

    @Test fun shareAndDesktopPagesContainOnlyTwoPostPhotos() {
        for (key in listOf("webapp.reflow.video.detail", "webapp.video-detail")) {
            val images = ImageDiscovery.parseTikTok(html(key), "https://www.tiktok.com/@user/photo/123")
            assertEquals(2, images.size)
            assertEquals("画像 1", images[0].label)
            assertEquals(1086, images[0].width)
            assertEquals("https://example.com/one.jpeg?x-signature=a%2Fb&x-expires=123", images[0].url)
            assertEquals(listOf("https://backup.example.com/one.jpeg"), images[0].alternateUrls)
            assertFalse(images.any { it.url.contains("logo") || it.url.contains("black") || it.url.contains("cover") })
        }
    }

    @Test fun missingOrDifferentPostNeverFallsBackToLogos() {
        assertTrue(ImageDiscovery.parseTikTok("<img src='https://example.com/logo.png'>", "https://www.tiktok.com/@user/photo/123").isEmpty())
        assertTrue(ImageDiscovery.parseTikTok(html("webapp.reflow.video.detail"), "https://www.tiktok.com/@user/photo/999").isEmpty())
    }

    @Test fun providedPublicPostLoadsTwoDecodablePreviews() = runBlocking {
        // 公開ネットワーク検査は明示指定時のみ実行する。
        assumeTrue(InstrumentationRegistry.getArguments().getString("tiktokLive") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = YtDlpDownloadEngine(context)
        val images = engine.analyzeImages("https://lite.tiktok.com/t/ZSq8hgqfA/").getOrThrow()
        assertEquals(2, images.size)
        for (image in images) {
            val bitmap = engine.preview(image)
            assertNotNull("投稿画像のプレビューを復号できること", bitmap)
            assertTrue(bitmap!!.width > 100 && bitmap.height > 100)
            println("${image.label}: ${image.width}x${image.height} プレビュー成功")
        }
    }
}
