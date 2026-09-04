package jp.essential.app.feature.downloader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ImageDiscoveryTest {
    @Test fun allPhotosAreDistinctAndVideoThumbnailsAreExcluded() {
        val json = JSONObject("""{"mediaDetails":[
            {"type":"photo","media_url_https":"https://pbs.twimg.com/media/one.jpg"},
            {"type":"video","media_url_https":"https://pbs.twimg.com/video.jpg"},
            {"type":"photo","media_url_https":"https://pbs.twimg.com/media/two.png"}
        ]}""")
        val images = ImageDiscovery.parseTwitter(json)
        assertEquals(2, images.size)
        assertEquals(2, images.map { it.id }.toSet().size)
        assertTrue(images.all { it.url.endsWith("name=orig") })
    }

    @Test fun htmlResolvesLazyImagesAndDoesNotTruncateAtFifty() {
        val html = (1..60).joinToString("") { "<img data-src='/photo$it.jpg'>" } +
            "<img srcset='/small.jpg 100w, /large.jpg 900w'><img src='data:image/png;base64,abc'>"
        val images = ImageDiscovery.parseHtml(html, "https://example.com/post")
        assertEquals(61, images.size)
        assertEquals("https://example.com/large.jpg", images.last().url)
        assertEquals(61, images.map { it.id }.toSet().size)
    }
}
