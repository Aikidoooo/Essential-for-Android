package jp.essential.app.feature.downloader

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

internal object InstagramImageParser {
    fun parse(html: String, code: String): List<ImageCandidate> {
        var result = emptyList<ImageCandidate>()
        // 埋め込みJSONのラッパーだけを探索し、対象投稿と一致したデータを採用する。
        fun visit(value: Any?, depth: Int) {
            if (depth > 48) return
            when (value) {
                is JSONObject -> {
                    if (value.optString("shortcode") == code &&
                        (value.has("display_url") || value.has("edge_sidecar_to_children"))) {
                        val photos = photos(value)
                        if (photos.size > result.size) result = photos
                    }
                    for (key in value.keys()) {
                        val child = value.opt(key)
                        if (key == "contextJSON" && child is String) {
                            visit(runCatching { JSONObject(child) }.getOrNull(), depth + 1)
                        } else visit(child, depth + 1)
                    }
                }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index), depth + 1)
            }
        }
        for (script in Jsoup.parse(html).select("script")) {
            val data = script.data()
            if (script.attr("type") == "application/json") {
                visit(runCatching { JSONObject(data) }.getOrNull(), 0)
            }
            // JavaScriptは実行せず、ServerJS内のJSON文字列だけを安全に復号する。
            for (match in Regex("\"contextJSON\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")").findAll(data)) {
                val context = runCatching {
                    JSONObject(JSONObject("{\"value\":${match.groupValues[1]}}").getString("value"))
                }.getOrNull()
                visit(context, 0)
            }
        }
        return result
    }

    private fun photos(post: JSONObject): List<ImageCandidate> {
        val edges = post.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        val nodes = if (edges != null) (0 until edges.length()).mapNotNull {
            edges.optJSONObject(it)?.optJSONObject("node")
        } else {
            // 複数写真の本体が欠けた場合、表紙だけを全件取得済みとして返さない。
            if (post.optString("__typename") == "GraphSidecar") return emptyList()
            listOf(post)
        }
        return nodes.mapIndexedNotNull { index, node ->
            if (node.optBoolean("is_video") || node.optString("__typename") == "GraphVideo") return@mapIndexedNotNull null
            val resources = node.optJSONArray("display_resources") ?: JSONArray()
            val variants = (0 until resources.length()).mapNotNull { resources.optJSONObject(it) }
                .sortedByDescending { it.optLong("config_width") * it.optLong("config_height") }
            val urls = (variants.map { it.optString("src") } + node.optString("display_url"))
                .filter { raw -> runCatching {
                    val uri = URI(raw)
                    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
                }.getOrDefault(false) }.distinct()
            if (urls.isEmpty()) error("Instagramの写真情報が不足しています。画像一覧を再読み込みしてください")
            val dimensions = node.optJSONObject("dimensions")
            ImageCandidate(
                "instagram-${node.optString("id", index.toString())}", urls.first(),
                dimensions?.optInt("width")?.takeIf { it > 0 },
                dimensions?.optInt("height")?.takeIf { it > 0 },
                "画像 ${index + 1}", urls.drop(1),
            )
        }.distinctBy { it.id }
    }
}
