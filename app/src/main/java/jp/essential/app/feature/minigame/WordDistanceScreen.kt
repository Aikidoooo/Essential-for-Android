package jp.essential.app.feature.minigame

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import jp.essential.app.device.DeviceOptimizer
import kotlinx.coroutines.delay
import org.json.JSONTokener

private const val WORD_DISTANCE_LANDING_URL = "https://unityroom.com/games/word-distance"
private const val UNITYROOM_PLAYER_HOST_SUFFIX = ".play.unityroom.com"

/** unityroomの実ゲームをWebViewで表示し、Essentialの画面枠から操作できるようにする。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WordDistanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deviceOptimization = remember { DeviceOptimizer.current() }
    val lowMemoryDevice = remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
    val restrainedMotion = lowMemoryDevice && deviceOptimization.isXiaomiFamily
    val motionDuration = if (restrainedMotion) 170 else 230
    val landscapeMode = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var pageReady by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var playerNavigationRequested by remember { mutableStateOf(false) }
    var chromeStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chromeStarted = true }

    fun loadGame() {
        pageReady = false
        loadError = false
        playerNavigationRequested = false
        webViewRef?.loadUrl(WORD_DISTANCE_LANDING_URL)
    }

    fun navigateBack() {
        val webView = webViewRef
        if (webView?.canGoBack() == true) webView.goBack() else onBack()
    }
    BackHandler(onBack = ::navigateBack)
    DisposableEffect(lifecycleOwner, webViewRef) {
        val webView = webViewRef
        if (webView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        webView.resumeTimers()
                        webView.onResume()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        webView.onPause()
                        // ミニゲーム内ではWebViewを一つだけ使うため、背面時はJavaScriptタイマーも止める。
                        webView.pauseTimers()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                webView.resumeTimers()
                webView.onResume()
            }
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { webView ->
                webView.stopLoading()
                // タイマー停止は全WebViewへ適用されるため、画面を閉じる時に共有状態を元へ戻す。
                webView.resumeTimers()
                webView.destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151622))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ProgressiveChrome(visible = chromeStarted, index = 0, duration = motionDuration) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (landscapeMode) 10.dp else 18.dp,
                        top = if (landscapeMode) 2.dp else 10.dp,
                        end = if (landscapeMode) 10.dp else 18.dp,
                        bottom = if (landscapeMode) 2.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassActionButton(contentDescription = "戻る", onClick = ::navigateBack) {
                    Text("‹", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(if (landscapeMode) 8.dp else 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "一番遠い言葉",
                        color = Color(0xFFF3F1FF),
                        style = if (landscapeMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (!landscapeMode) {
                        Text("公式ゲームをEssential内でプレイ", color = Color(0xFFBFC2D3), style = MaterialTheme.typography.bodySmall)
                    }
                }
                GlassActionButton(contentDescription = "再読み込み", onClick = ::loadGame) {
                    Text("↻", color = Color.White, fontSize = 23.sp)
                }
            }
        }

        if (!landscapeMode) ProgressiveChrome(visible = chromeStarted, index = 1, duration = motionDuration) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color(0xFFB9D7FF).copy(alpha = 0.06f)),
                        ),
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.08f))),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (loadError) Color(0xFFFF817F) else if (pageReady) Color(0xFF89E0B4) else Color(0xFFFFD27A)),
                )
                Text(
                    when {
                        loadError -> "接続できませんでした。再読み込みしてください。"
                        pageReady -> "unityroomのゲーム"
                        else -> "ゲームを読み込み中…"
                    },
                    color = Color(0xFFDAE5F3),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    horizontal = if (landscapeMode) 8.dp else 12.dp,
                    vertical = if (landscapeMode) 3.dp else 8.dp,
                )
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF101723), Color(0xFF080B12))),
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), Color(0xFFB9D7FF).copy(alpha = 0.08f))),
                    RoundedCornerShape(26.dp),
                ),
        ) {
            key(webViewGeneration) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                        webViewRef = this
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        // 前面のWebGLは優先し、画面外では描画器を休止可能にしてメモリを返しやすくする。
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                        if (deviceOptimization.isXiaomiFamily && Build.VERSION.SDK_INT >= 35) {
                            // Xiaomi版はWebGLゲームを安定した60Hzに揃え、HyperOS端末の発熱と描画負荷を抑える。
                            requestedFrameRate = 60f
                        }
                        setBackgroundColor(android.graphics.Color.rgb(9, 10, 16))
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.setSupportMultipleWindows(false)
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                MUTE_WORD_DISTANCE_AUDIO_SCRIPT,
                                setOf("https://*.play.unityroom.com"),
                            )
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                return request.url.scheme !in setOf("http", "https")
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                pageReady = false
                                loadError = false
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                val finishedUri = Uri.parse(url)
                                if (
                                    finishedUri.host == "unityroom.com" &&
                                    finishedUri.path == "/games/word-distance" &&
                                    !playerNavigationRequested
                                ) {
                                    view.evaluateJavascript(EXTRACT_PLAYER_URL_SCRIPT) { result ->
                                        val playerUrl = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                                        val playerUri = playerUrl?.let(Uri::parse)
                                        if (
                                            playerUri?.scheme == "https" &&
                                            playerUri.host?.endsWith(UNITYROOM_PLAYER_HOST_SUFFIX) == true
                                        ) {
                                            playerNavigationRequested = true
                                            view.loadUrl(playerUri.toString())
                                        } else {
                                            playerNavigationRequested = true
                                            pageReady = true
                                        }
                                    }
                                } else {
                                    pageReady = true
                                    loadError = false
                                    if (finishedUri.host?.endsWith(UNITYROOM_PLAYER_HOST_SUFFIX) == true) {
                                        view.evaluateJavascript(MUTE_WORD_DISTANCE_AUDIO_SCRIPT, null)
                                        view.postDelayed({ view.evaluateJavascript(FIT_UNITY_PLAYER_SCRIPT, null) }, 250)
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    pageReady = true
                                    loadError = true
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: android.webkit.WebResourceResponse,
                            ) {
                                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                    pageReady = true
                                    loadError = true
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: android.webkit.RenderProcessGoneDetail,
                            ): Boolean {
                                pageReady = true
                                loadError = true
                                playerNavigationRequested = false
                                (view.parent as? android.view.ViewGroup)?.removeView(view)
                                view.stopLoading()
                                view.destroy()
                                if (webViewRef === view) webViewRef = null
                                // Xiaomi端末の強いメモリ回収などで描画器が終了しても、画面全体を落とさず再生成する。
                                webViewGeneration += 1
                                return true
                            }
                        }
                            loadUrl(WORD_DISTANCE_LANDING_URL)
                        }
                    },
                    update = { webViewRef = it },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = loadError || !pageReady,
                enter = androidx.compose.animation.fadeIn(tween(motionDuration)) +
                    androidx.compose.animation.scaleIn(initialScale = 0.985f, animationSpec = tween(motionDuration)),
                exit = androidx.compose.animation.fadeOut(tween(motionDuration)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xE61B2635), Color(0xD9121925), Color(0xE6090A10)),
                            ),
                            RoundedCornerShape(26.dp),
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f))),
                            RoundedCornerShape(26.dp),
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (loadError) {
                        Text("ゲームを読み込めませんでした", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("通信状態を確認して、もう一度お試しください。", color = Color(0xFFDAE5F3), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = ::loadGame,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34455E)),
                            shape = RoundedCornerShape(18.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
                        ) {
                            Text("再読み込み")
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFFD7E8FF), strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("公式ゲームを読み込んでいます", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("初回は数秒かかる場合があります", color = Color(0xFFDAE5F3), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (!landscapeMode) ProgressiveChrome(visible = chromeStarted, index = 2, duration = motionDuration) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.13f), Color(0xFFB9D7FF).copy(alpha = 0.06f))),
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.07f))),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Essential", color = Color(0xFFF3F1FF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("提供元 unityroom", color = Color(0xFFDAE5F3), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProgressiveChrome(
    visible: Boolean,
    index: Int,
    duration: Int,
    content: @Composable BoxScope.() -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "画面要素の段階表示",
    )
    LaunchedEffect(visible, index) {
        if (visible) {
            delay(index * 42L)
            revealed = true
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 9.dp.toPx()
            scaleX = 0.99f + progress * 0.01f
            scaleY = scaleX
        },
        content = content,
    )
}

@Composable
private fun GlassActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 520f),
        label = "ガラスボタンの押下反応",
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (pressed) 0.3f else 0.2f),
                        Color(0xFFB9D7FF).copy(alpha = if (pressed) 0.2f else 0.08f),
                    ),
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.12f))),
                CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = if (pressed) 0.56f else 0.34f)),
            )
            content()
        },
    )
}

private const val EXTRACT_PLAYER_URL_SCRIPT = """(function(){var links=Array.from(document.querySelectorAll('a[href]'));var link=links.find(function(item){try{return new URL(item.href,location.href).hostname.endsWith('.play.unityroom.com')}catch(error){return false}});return link?link.href:''})()"""
private const val MUTE_WORD_DISTANCE_AUDIO_SCRIPT = """(function(){
if(window.__essentialWordDistanceAudioMuted)return;
window.__essentialWordDistanceAudioMuted=true;
var muteElement=function(media){if(media&&typeof media.muted==='boolean'){media.muted=true;media.volume=0}};
var inspectAddedNode=function(node){if(node.nodeType!==1)return;if(node.matches&&node.matches('audio,video'))muteElement(node);if(node.querySelectorAll)node.querySelectorAll('audio,video').forEach(muteElement)};
document.addEventListener('play',function(event){muteElement(event.target)},true);
new MutationObserver(function(records){records.forEach(function(record){record.addedNodes.forEach(inspectAddedNode)})}).observe(document,{childList:true,subtree:true});
document.querySelectorAll('audio,video').forEach(muteElement);
if(window.AudioNode&&AudioNode.prototype&&typeof AudioNode.prototype.connect==='function'&&window.WeakMap){
var originalConnect=AudioNode.prototype.connect;
var muteGates=new WeakMap();
AudioNode.prototype.connect=function(destination){
var context=this.context;
if(context&&destination===context.destination&&typeof context.createGain==='function'){
var gate=muteGates.get(context);
if(!gate){gate=context.createGain();gate.gain.value=0;originalConnect.call(gate,context.destination);muteGates.set(context,gate);context.__essentialWordDistanceMuteGate=gate}
var argumentsList=Array.prototype.slice.call(arguments);argumentsList[0]=gate;originalConnect.apply(this,argumentsList);return destination
}
return originalConnect.apply(this,arguments)
}
}
})()"""
private const val FIT_UNITY_PLAYER_SCRIPT = """(function(){
var fit=function(){
var html=document.documentElement;
var body=document.body;
var canvas=document.querySelector('#unity-canvas,canvas');
var root=document.querySelector('#unity-container')||body;
if(!html||!body)return;
var width=Math.max(1,html.clientWidth||innerWidth);
var height=Math.max(1,html.clientHeight||innerHeight);
var set=function(node,name,value){if(node)node.style.setProperty(name,value,'important')};
[html,body].forEach(function(node){set(node,'width',width+'px');set(node,'height',height+'px');set(node,'margin','0');set(node,'padding','0');set(node,'overflow','hidden')});
set(root,'position','fixed');set(root,'left','0');set(root,'top','0');set(root,'transform','none');set(root,'width',width+'px');set(root,'height',height+'px');set(root,'margin','0');set(root,'padding','0');set(root,'overflow','hidden');set(root,'display','block');
if(canvas){var parent=canvas.parentElement;if(parent&&parent!==root){set(parent,'position','absolute');set(parent,'left','0');set(parent,'top','0');set(parent,'width','100%');set(parent,'height','100%');set(parent,'margin','0');set(parent,'padding','0');set(parent,'overflow','hidden')};set(canvas,'position','absolute');set(canvas,'left','0');set(canvas,'top','0');set(canvas,'width','100%');set(canvas,'height','100%');set(canvas,'max-width','none');set(canvas,'max-height','none');set(canvas,'margin','0');set(canvas,'display','block');var fitScale=Math.min(1,(width*0.96)/Math.max(1,canvas.offsetWidth),(height*0.92)/Math.max(1,canvas.offsetHeight));set(canvas,'transform-origin','center center');set(canvas,'transform','scale('+fitScale+')')}
var loading=document.querySelector('#unity-loading-bar');if(loading){set(loading,'position','absolute');set(loading,'left','50%');set(loading,'top','50%');set(loading,'transform','translate(-50%,-50%)')}
};
if(!window.__essentialUnityFit){window.__essentialUnityFit=true;window.addEventListener('resize',fit,{passive:true});var observer=new MutationObserver(function(){requestAnimationFrame(fit)});var bindObserver=function(){var canvas=document.querySelector('#unity-canvas,canvas');if(canvas)observer.observe(canvas,{attributes:true,attributeFilter:['width','height']});else setTimeout(bindObserver,250)};bindObserver();[0,250,750,1500,3000].forEach(function(delay){setTimeout(function(){fit();window.dispatchEvent(new Event('resize'));fit()},delay)})}
fit()
})()"""
