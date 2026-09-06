package jp.essential.app.feature.minigame

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.core.view.WindowCompat
import jp.essential.app.ui.progressiveItem

/** ミニゲームの入口。ゲーム選択と各ゲーム画面を一つの戻る階層で管理する。 */
@Composable
internal fun MiniGameScreen(
    onBack: () -> Unit,
    onDosukoiVisibilityChange: (Boolean) -> Unit,
    motionFps: Int = 60,
) {
    var selectedGame by remember { mutableStateOf(GameSelection.Menu) }
    DisposableEffect(selectedGame) {
        onDosukoiVisibilityChange(selectedGame == GameSelection.Dosukoi)
        onDispose {
            if (selectedGame == GameSelection.Dosukoi) onDosukoiVisibilityChange(false)
        }
    }
    BackHandler {
        if (selectedGame == GameSelection.Menu) onBack() else selectedGame = GameSelection.Menu
    }
    when (selectedGame) {
        GameSelection.Menu -> MiniGameMenu(
            onBack = onBack,
            onDosukoi = { selectedGame = GameSelection.Dosukoi },
            onMinesweeper = { selectedGame = GameSelection.Minesweeper },
        )
        GameSelection.Dosukoi -> DosukoiWebViewScreen(
            onBack = { selectedGame = GameSelection.Menu },
            motionFps = motionFps,
        )
        GameSelection.Minesweeper -> MinesweeperScreen(onBack = { selectedGame = GameSelection.Menu })
    }
}

private enum class GameSelection { Menu, Dosukoi, Minesweeper }

@Composable
private fun MiniGameMenu(
    onBack: () -> Unit,
    onDosukoi: () -> Unit,
    onMinesweeper: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        progressiveItem(0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ミニゲーム", style = MaterialTheme.typography.headlineLarge)
                    Text("すきま時間に遊べるゲーム", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        progressiveItem(1) {
            GameMenuCard(
                title = "どすこい",
                description = "言葉遊び",
                icon = "🌀",
                accent = Color(0xFFFF8A3D),
                onClick = onDosukoi,
            )
        }
        progressiveItem(2) {
            GameMenuCard(
                title = "マインスイーパー",
                description = "地雷を避けてすべてのマスを開こう",
                icon = "💣",
                accent = Color(0xFF5A8DEE),
                onClick = onMinesweeper,
            )
        }
    }
}

@Composable
private fun GameBackButton(onClick: () -> Unit, darkSurface: Boolean = false) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (darkSurface) Color.White.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            )
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = if (darkSurface) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun GameMenuCard(
    title: String,
    description: String,
    icon: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.13f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.62f), shape)
            .combinedClickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Text(icon, style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class MineCell(
    val isMine: Boolean,
    val adjacentMines: Int,
    val revealed: Boolean = false,
    val flagged: Boolean = false,
)

private enum class MinesweeperStatus { Playing, Won, Lost }

@Composable
private fun MinesweeperScreen(onBack: () -> Unit) {
    var cells by remember { mutableStateOf(createMineBoard()) }
    var status by remember { mutableStateOf(MinesweeperStatus.Playing) }
    var flagMode by remember { mutableStateOf(false) }
    val flags = cells.count { it.flagged }
    val revealedSafe = cells.count { it.revealed && !it.isMine }
    val safeCells = cells.count { !it.isMine }

    fun restart() {
        cells = createMineBoard()
        status = MinesweeperStatus.Playing
        flagMode = false
    }

    fun toggleFlag(index: Int) {
        if (status == MinesweeperStatus.Playing && !cells[index].revealed) {
            cells = cells.toMutableList().also { it[index] = it[index].copy(flagged = !it[index].flagged) }
        }
    }

    fun open(index: Int) {
        if (flagMode) {
            toggleFlag(index)
            return
        }
        if (status != MinesweeperStatus.Playing || cells[index].revealed || cells[index].flagged) return
        if (cells[index].isMine) {
            cells = cells.map { if (it.isMine) it.copy(revealed = true) else it }
            status = MinesweeperStatus.Lost
            return
        }
        cells = revealSafeCells(cells, index)
        if (cells.count { it.revealed && !it.isMine } == safeCells) status = MinesweeperStatus.Won
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        progressiveItem(0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("マインスイーパー", style = MaterialTheme.typography.headlineLarge)
                    Text("地雷を避けてすべてのマスを開こう", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        progressiveItem(1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when (status) {
                        MinesweeperStatus.Playing -> "地雷 10個　・　旗 $flags　・　開いたマス $revealedSafe/$safeCells"
                        MinesweeperStatus.Won -> "クリア！すべての安全なマスを開きました"
                        MinesweeperStatus.Lost -> "ゲームオーバー　地雷を踏みました"
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { flagMode = !flagMode },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (flagMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (flagMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(if (flagMode) "旗モード ON" else "旗モード OFF")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = ::restart,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text(if (status == MinesweeperStatus.Playing) "最初から" else "もう一度遊ぶ") }
                }
            }
        }
        progressiveItem(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(MINE_BOARD_SIZE) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(MINE_BOARD_SIZE) { column ->
                            val index = row * MINE_BOARD_SIZE + column
                            MineCellView(cells[index], index, ::open, ::toggleFlag)
                        }
                    }
                }
            }
        }
        progressiveItem(3) {
            Text(
                if (flagMode) "旗モード中：タップで旗を立てる／外す"
                else "タップ：開く　／　長押し：旗を立てる",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowScope.MineCellView(
    cell: MineCell,
    index: Int,
    onOpen: (Int) -> Unit,
    onFlag: (Int) -> Unit,
) {
    val label = when {
        cell.revealed && cell.isMine -> "地雷"
        cell.revealed -> if (cell.adjacentMines == 0) "空白" else "隣接地雷${cell.adjacentMines}"
        cell.flagged -> "旗"
        else -> "未開封"
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (cell.revealed) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primaryContainer,
            )
            .border(
                1.dp,
                if (cell.revealed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(7.dp),
            )
            .semantics { contentDescription = label }
            .combinedClickable(onClick = { onOpen(index) }, onLongClick = { onFlag(index) }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                cell.revealed && cell.isMine -> "💣"
                cell.revealed && cell.adjacentMines > 0 -> cell.adjacentMines.toString()
                cell.flagged -> "⚑"
                else -> ""
            },
            color = when {
                cell.flagged -> MaterialTheme.colorScheme.error
                cell.adjacentMines == 1 -> Color(0xFF1769AA)
                cell.adjacentMines == 2 -> Color(0xFF2E7D32)
                cell.adjacentMines == 3 -> Color(0xFFB45309)
                cell.adjacentMines >= 4 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
    }
}

private const val MINE_BOARD_SIZE = 9
private const val MINE_COUNT = 10

private fun createMineBoard(): List<MineCell> {
    val mineIndexes = (0 until MINE_BOARD_SIZE * MINE_BOARD_SIZE).shuffled().take(MINE_COUNT).toSet()
    return (0 until MINE_BOARD_SIZE * MINE_BOARD_SIZE).map { index ->
        MineCell(index in mineIndexes, adjacentMineCount(index, mineIndexes))
    }
}

private fun adjacentMineCount(index: Int, mineIndexes: Set<Int>): Int = neighbors(index).count { it in mineIndexes }

private fun neighbors(index: Int): List<Int> {
    val row = index / MINE_BOARD_SIZE
    val column = index % MINE_BOARD_SIZE
    return buildList {
        for (rowOffset in -1..1) for (columnOffset in -1..1) {
            if (rowOffset == 0 && columnOffset == 0) continue
            val nextRow = row + rowOffset
            val nextColumn = column + columnOffset
            if (nextRow in 0 until MINE_BOARD_SIZE && nextColumn in 0 until MINE_BOARD_SIZE) {
                add(nextRow * MINE_BOARD_SIZE + nextColumn)
            }
        }
    }
}

private fun revealSafeCells(source: List<MineCell>, start: Int): List<MineCell> {
    val result = source.toMutableList()
    val pending = ArrayDeque<Int>()
    pending.add(start)
    while (pending.isNotEmpty()) {
        val index = pending.removeFirst()
        val cell = result[index]
        if (cell.revealed || cell.flagged || cell.isMine) continue
        result[index] = cell.copy(revealed = true)
        if (cell.adjacentMines == 0) neighbors(index).forEach { pending.add(it) }
    }
    return result
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DosukoiWebViewScreen(onBack: () -> Unit, motionFps: Int) {
    val context = LocalContext.current
    val activity = context as? Activity
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var connectionState by remember { mutableStateOf("Firebase連携を初期化中…") }
    var pageVisible by remember { mutableStateOf(false) }
    BackHandler {
        val webView = webViewRef
        if (webView?.canGoBack() == true) webView.goBack() else onBack()
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousStatusBarColor = window?.statusBarColor
        val previousNavigationBarColor = window?.navigationBarColor
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        val darkBackground = AndroidColor.rgb(15, 15, 26)
        window?.statusBarColor = darkBackground
        window?.navigationBarColor = darkBackground
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (window != null) {
                window.statusBarColor = previousStatusBarColor ?: window.statusBarColor
                window.navigationBarColor = previousNavigationBarColor ?: window.navigationBarColor
            }
            if (controller != null) {
                previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
                previousLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy() }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0F0F1A)).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameBackButton(onClick = onBack, darkSurface = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("どすこい", color = Color(0xFFF1EBFF), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("言葉遊び", color = Color(0xFFBFC7D9))
            }
            Text(connectionState, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFC7D9))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                val html = context.assets.open("dosukoi.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
                var fallbackLoaded = false
                var pageFinished = false
                var loadBundledPage: ((WebView) -> Unit)? = null
                WebView(context).apply {
                    webViewRef = this
                    // 透明なHardware Layerは一部WebViewでbackdrop-filterを全消去するため使用しない。
                    setBackgroundColor(AndroidColor.rgb(15, 15, 26))
                    overScrollMode = View.OVER_SCROLL_NEVER
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.setSupportMultipleWindows(false)
                    // 同梱アセットはHTTPSのWebViewAssetLoaderだけで提供し、不要なファイル経路を閉じる。
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    if (Build.VERSION.SDK_INT >= 35) requestedFrameRate = motionFps.toFloat()
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        // アプリ内HTTPSオリジンのメインフレームだけに、貼り付け要求を許可する。
                        WebViewCompat.addWebMessageListener(
                            this,
                            "essentialClipboard",
                            setOf("https://appassets.androidplatform.net"),
                        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
                            if (
                                isMainFrame &&
                                sourceOrigin.toString() == "https://appassets.androidplatform.net" &&
                                message.data == "paste-room-code"
                            ) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clipboard.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    .orEmpty()
                                replyProxy.postMessage(text)
                            }
                        }
                    }
                    loadBundledPage = { view: WebView ->
                        if (!fallbackLoaded) {
                            fallbackLoaded = true
                            view.loadDataWithBaseURL(
                                "https://appassets.androidplatform.net/assets/",
                                html,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                            assetLoader.shouldInterceptRequest(request.url)

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: android.webkit.WebResourceError,
                        ) {
                            if (request.isForMainFrame) loadBundledPage?.invoke(view)
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: android.webkit.WebResourceResponse,
                        ) {
                            if (request.isForMainFrame && errorResponse.statusCode >= 400) loadBundledPage?.invoke(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            // DOMが描画可能になったことを確認してからローディングを外す。
                            pageFinished = true
                            connectionState = "Firebase連携対応"
                            view.evaluateJavascript(
                                """(function(){
                                    var s=document.getElementById('essential-android-fix');
                                    if(!s){
                                        s=document.createElement('style');
                                        s.id='essential-android-fix';
                                        s.textContent='html,body{width:100%!important;height:100vh!important;min-height:100vh!important;opacity:1!important;transform:none!important;background:#0f0f1a!important;color:#fff!important}.background{background:#0f0f1a!important;background-image:none!important}.background::after{background:transparent!important}.dosukoi-container{display:flex!important;width:100%!important;height:100vh!important;min-height:100vh!important;padding-bottom:0!important;position:relative!important;overflow:hidden!important;background:#0f0f1a!important}.screen-section.active{display:flex!important;visibility:visible!important;opacity:1!important;pointer-events:auto!important;z-index:20!important}';
                                        document.head.appendChild(s);
                                    }
                                    var applyAndroidViewport=function(){
                                        var height=Math.max(window.innerHeight,document.documentElement.clientHeight,1)+'px';
                                        [document.documentElement,document.body,document.querySelector('.dosukoi-container')].forEach(function(element){
                                            if(!element)return;
                                            element.style.setProperty('height',height,'important');
                                            element.style.setProperty('min-height',height,'important');
                                        });
                                        var container=document.querySelector('.dosukoi-container');
                                        if(container)container.style.setProperty('padding-bottom','0px','important');
                                        document.querySelectorAll('.screen-section').forEach(function(section){
                                            section.style.setProperty('height',height,'important');
                                        });
                                    };
                                    applyAndroidViewport();
                                    if(!window.__essentialViewportListener){
                                        window.__essentialViewportListener=true;
                                        window.addEventListener('resize',applyAndroidViewport,{passive:true});
                                    }
                                    document.documentElement.style.backgroundColor='#0f0f1a';
                                    document.body.style.backgroundColor='#0f0f1a';
                                    document.body.style.overflowX='hidden';
                                    document.documentElement.dataset.motionFps='${motionFps.coerceIn(30, 120)}';
                                    document.documentElement.style.setProperty('--essential-motion-fps','${motionFps.coerceIn(30, 120)}');
                                    var menu=document.getElementById('dosukoiMenuScreen');
                                    if(menu){menu.classList.remove('hidden');menu.classList.add('active');}
                                    var create=document.getElementById('createRoomBtn');
                                    var join=document.getElementById('joinRoomPanel');
                                    if(!menu||!create||!join){return false;}
                                    var rect=menu.getBoundingClientRect();
                                    return rect.width>0&&rect.height>=window.innerHeight*0.8&&getComputedStyle(menu).opacity!=='0';
                                })();""",
                            ) { rendered ->
                                if (rendered == "true") {
                                    pageVisible = true
                                } else if (!fallbackLoaded) {
                                    loadBundledPage?.invoke(view)
                                }
                            }
                            // Firebase SDKの読み込みと匿名認証は通信状況で遅れるため、短時間だけ再確認する。
                            fun checkFirebaseConnection(attempt: Int) {
                                view.evaluateJavascript("Boolean(window.irohClient && window.irohClient.isConnected)") { result ->
                                    if (result == "true") connectionState = "Firebase接続済み"
                                    else if (attempt < 8) view.postDelayed({ checkFirebaseConnection(attempt + 1) }, 600L)
                                }
                            }
                            checkFirebaseConnection(0)
                        }
                    }
                    // 添付された実ゲームHTMLをアプリ内オリジンから直接読み込む。
                    loadUrl("https://appassets.androidplatform.net/assets/dosukoi.html")
                    // WebViewが完了通知を返さない場合は、同じHTMLを直接渡して再試行する。
                    postDelayed({
                        if (!pageFinished && !fallbackLoaded) {
                            loadBundledPage?.invoke(this)
                        }
                    }, 8_000L)
                }
                },
                update = { view ->
                    // Android 15以降はWebView自身へ希望fpsを伝え、DOSUKOIのCSSモーションを同じ描画レートへ揃える。
                    if (Build.VERSION.SDK_INT >= 35) view.requestedFrameRate = motionFps.toFloat()
                    view.evaluateJavascript(
                        "(function(){document.documentElement.dataset.motionFps='${motionFps.coerceIn(30, 120)}';document.documentElement.style.setProperty('--essential-motion-fps','${motionFps.coerceIn(30, 120)}');})();",
                        null,
                    )
                },
            )
            if (!pageVisible) {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Text("どすこいを読み込み中…", color = MaterialTheme.colorScheme.onSurface)
                    Text("Firebaseへ接続しています", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
