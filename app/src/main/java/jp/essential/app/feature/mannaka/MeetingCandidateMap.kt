package jp.essential.app.feature.mannaka

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MeetingCandidateMap(points: List<MeetingPoint>, selected: MeetingPoint?, onPick: (MeetingPoint) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palette = JSONObject().put("marker", "#%06x".format(colors.primary.toArgb() and 0xffffff))
        .put("text", "#%06x".format(colors.onPrimary.toArgb() and 0xffffff))
        .put("first", "#%06x".format(colors.secondary.toArgb() and 0xffffff))
        .put("firstText", "#%06x".format(colors.onSecondary.toArgb() and 0xffffff))
    val currentPalette by rememberUpdatedState(palette)
    val currentPoints by rememberUpdatedState(points)
    val currentSelection by rememberUpdatedState(selected)
    val currentPick by rememberUpdatedState(onPick)
    fun update(view: WebView) {
        val array = JSONArray(currentPoints.map { point -> JSONObject().put("name", point.name)
            .put("latitude", point.latitude).put("longitude", point.longitude) })
        // 駅名をコードに連結せずJSONで渡し、HTMLの区切りもエスケープする。
        view.evaluateJavascript("if(typeof render==='function')render(${array.toString().replace("<", "\\u003c")},${currentPoints.indexOf(currentSelection)},${currentPalette});") { view.invalidate() }
    }
    AndroidView(modifier = Modifier.fillMaxWidth().height(242.dp).clip(RoundedCornerShape(26.dp)),
        factory = { context ->
            val tiles = MeetingTileCache(context)
            val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            WebView(context).apply {
                tag = "mannaka-map"
                // 同梱した地図コードだけを実行し、JavaScriptと端末の接続口は設けない。
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = settings.userAgentString + " Essential-Android-Mannaka/0.6.4"
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        if (jp.essential.app.BuildConfig.DEBUG) android.util.Log.d("MeetingMap", "地図コード：${message.message()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        loader.shouldInterceptRequest(request.url)?.let { return it }
                        if (request.url.scheme == "https" && request.url.host == "tile.openstreetmap.org") {
                            return tiles.load(request.url.toString()) ?: WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun onPageFinished(view: WebView, url: String) { update(view) }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme == "essential" && uri.host == "candidate") {
                            uri.lastPathSegment?.toIntOrNull()?.let { currentPoints.getOrNull(it)?.let(currentPick) }
                        } else if (uri.scheme == "https" && uri.host == "www.openstreetmap.org") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                        return true
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/mannaka/map.html")
            }
        }, onRelease = { it.stopLoading(); it.destroy() }, update = { update(it) })
}
