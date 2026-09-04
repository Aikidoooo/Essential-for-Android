package jp.essential.app.feature.downloader

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONObject
import org.jsoup.Jsoup

internal class ImageDiscovery {
    fun discover(url: String): List<ImageCandidate> {
        val host = URI(url).host.orEmpty().lowercase()
        if (host == "instagram.com" || host.endsWith(".instagram.com")) {
            val code = Regex("^/(?:p|reel|tv)/([A-Za-z0-9_-]+)(?:/|$)")
                .find(URI(url).path)?.groupValues?.get(1)
                ?: error("Instagramの投稿URLを入力してください")
            // 公開埋め込みページだけを読み、ログイン情報は使用しない。
            val connection = open("https://www.instagram.com/p/$code/embed/captioned/")
            return try {
                check(connection.responseCode == 200) { "Instagramの公開投稿を取得できません（HTTP ${connection.responseCode}）" }
                InstagramImageParser.parse(readText(connection), code).ifEmpty {
                    error("Instagramの公開写真を取得できませんでした。時間をおいて再読み込みしてください。ログイン必須・非公開・埋め込み禁止の投稿には対応していません")
                }
            } finally { connection.disconnect() }
        }
        if (VideoDownloadPolicy.isTwitter(url)) {
            val id = Regex("/(?:status|statuses)/(\\d+)").find(URI(url).path)?.groupValues?.get(1)
                ?: error("Xの投稿URLを入力してください")
            // 公開埋め込みデータのみを使用し、ログイン情報やCookieは送信しない。
            val endpoint = "https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=ja&token=0"
            val connection = open(endpoint)
            return try {
                check(connection.responseCode == 200) { "Xの公開画像を取得できません（HTTP ${connection.responseCode}）" }
                parseTwitter(JSONObject(readText(connection))).ifEmpty { error("公開画像が見つかりません。画像のある公開投稿を指定してください") }
            } finally { connection.disconnect() }
        }
        val connection = open(url)
        return try {
            check(connection.responseCode == 200) { "ページを読み込めません（HTTP ${connection.responseCode}）" }
            if (connection.contentType.orEmpty().startsWith("image/")) {
                listOf(candidate(url, "画像 1"))
            } else if (connection.contentType.orEmpty().contains("html")) {
                val html = readText(connection)
                val finalUrl = connection.url.toString()
                if (isTikTok(url) || isTikTok(finalUrl)) {
                    parseTikTok(html, finalUrl).ifEmpty {
                        error("TikTokの投稿画像を取得できませんでした。時間をおいて画像一覧を再読み込みしてください。ログインが必要な投稿には対応していません")
                    }
                } else parseHtml(html, finalUrl)
            } else emptyList()
        } finally { connection.disconnect() }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Essential/1.0")
    }

    private fun readText(connection: HttpURLConnection): String = connection.inputStream.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size() + count <= 4 * 1024 * 1024) { "ページ情報が大きすぎます" }
            output.write(buffer, 0, count)
        }
        output.toString("UTF-8")
    }

    companion object {
        private fun isTikTok(url: String): Boolean {
            val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
            return host == "tiktok.com" || host.endsWith(".tiktok.com")
        }

        internal fun parseTikTok(html: String, pageUrl: String): List<ImageCandidate> {
            val document = Jsoup.parse(html, pageUrl)
            val expectedId = Regex("/(?:photo|video)/(\\d+)").find(URI(pageUrl).path)?.groupValues?.get(1)
            val script = document.getElementById("__UNIVERSAL_DATA_FOR_REHYDRATION__")?.data().orEmpty()
            val scope = runCatching { JSONObject(script).optJSONObject("__DEFAULT_SCOPE__") }.getOrNull()
            // おすすめ投稿・カバー・ロゴは混ぜず、表示中の投稿の写真配列だけを読む。
            for (key in listOf("webapp.reflow.video.detail", "webapp.video-detail")) {
                val detail = scope?.optJSONObject(key) ?: continue
                if (detail.optInt("statusCode", 0) != 0) continue
                val item = detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct") ?: continue
                if (expectedId != null && item.optString("id") != expectedId) continue
                val images = item.optJSONObject("imagePost")?.optJSONArray("images") ?: continue
                return buildList {
                    for (index in 0 until images.length()) {
                        val image = images.optJSONObject(index) ?: continue
                        val list = image.optJSONObject("imageURL")?.optJSONArray("urlList") ?: continue
                        val urls = (0 until list.length()).map { list.optString(it) }.filter { value ->
                            val uri = runCatching { URI(value) }.getOrNull()
                            uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
                        }.distinct()
                        if (urls.isEmpty()) continue
                        add(ImageCandidate("tiktok-${item.optString("id")}-${index + 1}", urls.first(),
                            image.optInt("imageWidth").takeIf { it > 0 }, image.optInt("imageHeight").takeIf { it > 0 },
                            "画像 ${index + 1}", urls.drop(1)))
                    }
                }
            }
            return emptyList()
        }

        internal fun parseTwitter(json: JSONObject): List<ImageCandidate> {
            val media = json.optJSONArray("mediaDetails")
            val urls = buildList {
                if (media != null) for (index in 0 until media.length()) {
                    val item = media.optJSONObject(index) ?: continue
                    if (item.optString("type") != "photo") continue
                    val size = item.optJSONObject("original_info")
                    val raw = item.optString("media_url_https")
                    if (raw.isNotBlank()) add(candidate(originalTwitterImage(raw), "画像 ${index + 1}", size?.optInt("width"), size?.optInt("height")))
                }
                if (isEmpty()) {
                    val photos = json.optJSONArray("photos")
                    if (photos != null) for (index in 0 until photos.length()) {
                        val item = photos.optJSONObject(index) ?: continue
                        val raw = item.optString("url")
                        if (raw.isNotBlank()) add(candidate(originalTwitterImage(raw), "画像 ${index + 1}", item.optInt("width"), item.optInt("height")))
                    }
                }
            }
            return urls.distinctBy { it.url }
        }

        private fun originalTwitterImage(raw: String): String {
            val uri = URI(raw)
            return if (uri.host == "pbs.twimg.com" && uri.path.startsWith("/media/")) {
                val format = uri.path.substringAfterLast('.', "jpg")
                "https://pbs.twimg.com${uri.path.substringBeforeLast('.')}?format=$format&name=orig"
            } else raw
        }

        internal fun parseHtml(html: String, pageUrl: String): List<ImageCandidate> {
            val document = Jsoup.parse(html, pageUrl)
            val images = linkedMapOf<String, ImageCandidate>()
            fun add(raw: String, label: String) {
                val resolved = runCatching { URI(pageUrl).resolve(raw).toString() }.getOrNull() ?: return
                val uri = runCatching { URI(resolved) }.getOrNull() ?: return
                if (uri.scheme !in setOf("http", "https") || uri.host == null || uri.userInfo != null) return
                images.putIfAbsent(resolved, candidate(resolved, label.ifBlank { "画像 ${images.size + 1}" }))
            }
            for (element in document.select("img")) {
                val sourceSet = element.attr("data-srcset").ifBlank { element.attr("srcset") }
                val largest = sourceSet.split(',').map { it.trim().split(Regex("\\s+")) }
                    .filter { it.firstOrNull()?.isNotBlank() == true }
                    .maxByOrNull { it.getOrNull(1)?.dropLast(1)?.toDoubleOrNull() ?: 0.0 }?.firstOrNull()
                val raw = largest ?: element.attr("data-src").ifBlank { element.attr("src") }
                if (raw.isNotBlank()) add(raw, element.attr("alt"))
            }
            for (element in document.select("meta[property=og:image], meta[name=twitter:image]")) {
                if (element.attr("content").isNotBlank()) add(element.attr("content"), "ページの画像")
            }
            return images.values.toList()
        }

        private fun candidate(url: String, label: String, width: Int? = null, height: Int? = null) =
            ImageCandidate(url, url, width?.takeIf { it > 0 }, height?.takeIf { it > 0 }, label)
    }
}
