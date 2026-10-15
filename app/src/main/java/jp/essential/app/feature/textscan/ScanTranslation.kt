package jp.essential.app.feature.textscan

import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.essential.app.feature.scanner.scannerSafeArea
import jp.essential.app.ui.GlassBackButton
import jp.essential.app.ui.GlassFeatureTitle
import kotlinx.coroutines.delay

/** URLの長さを抑え、長文やサロゲートペアも欠けずに順番に渡す。 */
internal fun translationChunks(text: String, maxLength: Int = 1500): List<String> {
    require(maxLength >= 2)
    if (text.isEmpty()) return emptyList()
    return buildList {
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxLength, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            if (end < text.length) {
                val line = text.lastIndexOf('\n', end - 1)
                if (line >= start + maxLength / 2) end = line + 1
            }
            add(text.substring(start, end))
            start = end
        }
    }
}

internal fun googleTranslationUrl(text: String): String = Uri.parse("https://translate.google.com/").buildUpon()
    .appendQueryParameter("sl", "auto").appendQueryParameter("tl", "ja")
    .appendQueryParameter("hl", "ja").appendQueryParameter("text", text)
    .appendQueryParameter("op", "translate").build().toString()

/** 翻訳・同意画面のHTTPSだけをこのWebViewで開き、外部アプリへの遷移を抑える。 */
internal fun isTranslationNavigationAllowed(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme == "https" && (host == "google.com" || host.endsWith(".google.com"))
}

@Composable
internal fun ScanTranslationScreen(text: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val chunks = remember(text) { translationChunks(text) }
    var page by rememberSaveable(text) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val url = remember(chunks, page) { googleTranslationUrl(chunks.getOrElse(page) { "" }) }
    BackHandler(onBack = onBack)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) webView?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) webView?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(url, retry, webView) {
        loading = true
        failure = null
        progress = 0
        webView?.loadUrl(url)
        delay(25000)
        if (loading) failure = "Google翻訳の読み込みに時間がかかっています。接続を確認して再読み込みしてください。"
    }
    Surface(Modifier.fillMaxSize().testTag("text-scan-translation"), color = Color(0xFF131D30), contentColor = Color.White) {
        Column(Modifier.fillMaxSize().scannerSafeArea().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassBackButton(onClick = onBack)
                GlassFeatureTitle("Google翻訳", Modifier.weight(1f))
            }
            Text("読み取った文字をGoogle翻訳で表示しています。翻訳先はページ内で変更できます。",
                style = MaterialTheme.typography.bodySmall)
            if (chunks.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                ScanGlassButton("前へ", page > 0) { page-- }
                Text("${page + 1} / ${chunks.size}")
                ScanGlassButton("次へ", page < chunks.lastIndex) { page++ }
            }
            if (loading && failure == null) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            failure?.let { message ->
                Text(message, color = Color(0xFFFFC8A8), style = MaterialTheme.typography.bodySmall)
                ScanGlassButton("再読み込み") { retry++ }
            }
            AndroidView(factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val blocked = !isTranslationNavigationAllowed(request.url)
                            if (blocked && request.isForMainFrame) failure = "このリンクは翻訳画面内では開けません。"
                            return blocked
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView, url: String?) { loading = false }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                loading = false
                                failure = "Google翻訳を開けませんでした。インターネット接続を確認してください。"
                            }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) {
                                loading = false
                                failure = "Google翻訳を読み込めませんでした。時間を置いて再試行してください。"
                            }
                        }
                    }
                    webView = this
                }
            }, modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White).testTag("text-scan-translation-web"),
                onRelease = { view ->
                    webView = null
                    view.stopLoading()
                    view.webChromeClient = null
                    view.destroy()
                })
        }
    }
}
