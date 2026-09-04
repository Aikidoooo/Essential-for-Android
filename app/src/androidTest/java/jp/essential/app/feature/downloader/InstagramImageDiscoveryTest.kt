package jp.essential.app.feature.downloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class InstagramImageDiscoveryTest {
    private fun photo(index: Int) = """{"id":"$index","is_video":false,"display_url":"https://example.com/$index.jpg?sig=a%2Fb","dimensions":{"width":1080,"height":1350}}"""
    private fun page(post: String): String {
        val context = JSONObject("""{"context":{"shortcode":"abc","type":"GraphSidecar"},"gql_data":{"shortcode_media":$post}}""")
        val wrapper = JSONObject().put("contextJSON", context.toString())
        return "<img src='https://example.com/avatar.jpg'><script type='application/json'>$wrapper</script>"
    }

    @Test fun extractsEveryPhotoInOrderWithoutCoverOrVideo() {
        val edges = (1..20).joinToString(",") { """{"node":${photo(it)}}""" }
        val post = """{"shortcode":"abc","__typename":"GraphSidecar","display_url":"https://example.com/cover.jpg","edge_sidecar_to_children":{"edges":[$edges,{"node":{"id":"video","is_video":true,"display_url":"https://example.com/video.jpg"}}]}}"""
        val images = InstagramImageParser.parse(page(post), "abc")
        assertEquals(20, images.size)
        assertEquals("画像 20", images.last().label)
        assertEquals("https://example.com/1.jpg?sig=a%2Fb", images.first().url)
        assertEquals(1350, images.first().height)
        assertTrue(InstagramImageParser.parse(page(post), "different").isEmpty())
    }

    @Test fun supportsSinglePhotoAndRejectsMissingCarouselData() {
        assertEquals(1, InstagramImageParser.parse(page("""{"shortcode":"abc","display_url":"https://example.com/photo.jpg"}"""), "abc").size)
        assertTrue(InstagramImageParser.parse(page("""{"shortcode":"abc","__typename":"GraphSidecar","display_url":"https://example.com/cover.jpg"}"""), "abc").isEmpty())
        assertTrue(InstagramImageParser.parse("<meta property='og:image' content='https://example.com/cover.jpg'>", "abc").isEmpty())
    }

    @Test fun readsServerJsContextWithoutExecutingJavaScript() {
        val html = page("""{"shortcode":"abc","display_url":"https://example.com/photo.jpg?sig=x%2Fy&next=1"}""")
            .replace("<script type='application/json'>", "<script>requireLazy([],function(){s.handle(")
            .replace("</script>", ");});</script>")
        val images = InstagramImageParser.parse(html, "abc")
        assertEquals(1, images.size)
        assertEquals("https://example.com/photo.jpg?sig=x%2Fy&next=1", images.single().url)
    }

    @Test fun providedPublicPostLoadsAllTenPreviews() = runBlocking {
        // 実通信は明示指定時だけ実行し、ログイン情報は使用しない。
        assumeTrue(InstrumentationRegistry.getArguments().getString("instagramLive") == "true")
        val engine = YtDlpDownloadEngine(ApplicationProvider.getApplicationContext<Context>())
        val images = engine.analyzeImages("https://www.instagram.com/p/DcqT5raE6-9/?igsi=azRmOTZjdWtxeG9n").getOrThrow()
        assertEquals(10, images.size)
        for (image in images) {
            val bitmap = engine.preview(image)
            assertNotNull(image.label, bitmap)
            assertTrue(bitmap!!.width > 100 && bitmap.height > 100)
        }
    }
}
