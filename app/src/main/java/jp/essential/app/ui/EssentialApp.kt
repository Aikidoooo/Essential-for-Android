package jp.essential.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.essential.app.AppIconManager
import jp.essential.app.AppIconOption
import jp.essential.app.feature.routine.calculateRoutineLevel
import jp.essential.app.BuildConfig
import jp.essential.app.R
import jp.essential.app.core.EssentialCore
import jp.essential.app.feature.downloader.DownloaderScreen
import jp.essential.app.feature.downloader.YtDlpUpdateSettingsCard
import jp.essential.app.feature.files.FileReferenceScreen
import jp.essential.app.feature.minigame.MiniGameScreen
import jp.essential.app.feature.qr.QrScannerScreen
import jp.essential.app.feature.routine.RoutineScreen
import jp.essential.app.feature.schedule.ScheduleGeneratorScreen
import jp.essential.app.ui.theme.EssentialLime
import jp.essential.app.ui.theme.EssentialOrange
import jp.essential.app.ui.theme.EssentialRed
import jp.essential.app.ui.theme.EssentialTheme
import jp.essential.app.ui.theme.LocalEssentialDark
import jp.essential.app.ui.theme.EssentialYellow
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val symbol: EssentialSymbol) {
    Home("ホーム", EssentialSymbol.Home),
    Features("機能一覧", EssentialSymbol.Grid),
    Settings("設定", EssentialSymbol.Settings),
}

private enum class EssentialSymbol {
    Home,
    Grid,
    Settings,
    Spark,
    Media,
    Device,
    Ai,
    Arrow,
    Check,
    Download,
    Qr,
    Calendar,
    Game,
    Routine,
}

private enum class FeatureRoute(val requestId: String) {
    Downloader("downloader"),
    QrScanner("qr_scanner"),
    Schedule("schedule"),
    Files("files"),
    MiniGame("mini_game"),
    Routine("routine"),
}

private data class FeatureItem(
    val title: String,
    val description: String,
    val symbol: EssentialSymbol,
    val accent: Color,
    val route: FeatureRoute?,
)

@Composable
fun EssentialRoot(
    sharedUrl: String? = null,
    requestedFeature: String? = null,
    initialDarkTheme: Boolean? = null,
    onDarkThemeApplied: (Boolean) -> Unit = {},
    initialAppIconId: String = AppIconManager.defaultLight.id,
    onAppIconChange: (String) -> Unit = {},
    motionFps: Int = 60,
    onMotionFpsChange: (Int) -> Unit = {},
    initialHomeShortcut: String = FeatureRoute.Downloader.requestId,
    initialSetupRequired: Boolean = false,
    onHomeShortcutChange: (String) -> Unit = {},
) {
    val systemDarkTheme = isSystemInDarkTheme()
    var darkTheme by rememberSaveable { mutableStateOf(initialDarkTheme ?: systemDarkTheme) }
    val view = LocalView.current
    LaunchedEffect(darkTheme) { onDarkThemeApplied(darkTheme) }
    LaunchedEffect(motionFps, view) {
        // Android 15以降はComposeを描画するViewへも希望fpsを伝える。
        if (android.os.Build.VERSION.SDK_INT >= 35) view.requestedFrameRate = motionFps.toFloat()
    }

    EssentialTheme(darkTheme = darkTheme) {
        jp.essential.app.update.AppUpdateHost()
        StartupGate {
            EssentialApp(
                darkTheme = darkTheme,
                onDarkThemeChange = { darkTheme = it },
                initialAppIconId = initialAppIconId,
                onAppIconChange = { option ->
                    onAppIconChange(option.id)
                    darkTheme = option.prefersDarkTheme
                },
                sharedUrl = sharedUrl,
                requestedFeature = requestedFeature,
                motionFps = motionFps,
                onMotionFpsChange = onMotionFpsChange,
                initialHomeShortcut = initialHomeShortcut,
                initialSetupRequired = initialSetupRequired,
                onHomeShortcutChange = onHomeShortcutChange,
            )
        }
    }
}

@Composable
private fun EssentialApp(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    initialAppIconId: String,
    onAppIconChange: (AppIconOption) -> Unit,
    sharedUrl: String?,
    requestedFeature: String?,
    motionFps: Int,
    onMotionFpsChange: (Int) -> Unit,
    initialHomeShortcut: String,
    initialSetupRequired: Boolean,
    onHomeShortcutChange: (String) -> Unit,
) {
    var destination by rememberSaveable {
        mutableStateOf(if (initialSetupRequired) Destination.Settings else Destination.Home)
    }
    var activeFeature by rememberSaveable { mutableStateOf<FeatureRoute?>(null) }
    var dosukoiActive by rememberSaveable { mutableStateOf(false) }
    var homeShortcut by rememberSaveable {
        mutableStateOf(FeatureRoute.entries.firstOrNull { it.requestId == initialHomeShortcut } ?: FeatureRoute.Downloader)
    }
    var appIconId by rememberSaveable { mutableStateOf(initialAppIconId) }
    LaunchedEffect(darkTheme) {
        if (appIconId == AppIconManager.defaultLight.id || appIconId == AppIconManager.defaultDark.id) {
            appIconId = if (darkTheme) AppIconManager.defaultDark.id else AppIconManager.defaultLight.id
        }
    }
    val destinationState = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onComingSoon = remember(snackbarHostState, scope) {
        {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("この機能は、これからEssentialに追加されます")
            }
            Unit
        }
    }

    LaunchedEffect(requestedFeature, sharedUrl) {
        FeatureRoute.entries.firstOrNull { it.requestId == requestedFeature }?.let {
            activeFeature = it
        }
        if (!sharedUrl.isNullOrBlank()) {
            activeFeature = FeatureRoute.Downloader
        }
    }

    BackHandler(enabled = activeFeature != null || destination != Destination.Home) {
        if (activeFeature != null) {
            activeFeature = null
        } else {
            destination = Destination.Home
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (dosukoiActive) {
            // DOSUKOIはWebView自身がFluid Gradientを描画するため、背面のCanvasを止めて二重描画を避ける。
            Box(Modifier.fillMaxSize().background(Color(0xFF0F0F1A)))
        } else {
            AnimatedBackdrop(AppIconManager.optionForId(appIconId))
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            // どすこいは暗色の背景をステータス／ナビゲーションバーまで連続させる。
            contentWindowInsets = if (dosukoiActive) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (activeFeature == null) {
                    EssentialNavigationBar(
                        selected = destination,
                        onSelected = { destination = it },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AnimatedContent(
                    targetState = activeFeature,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState != null) 1 else -1
                        // 入場は各機能内のまとまりに任せ、画面全体の横スライドは行わない。
                        fadeIn(tween(if (targetState != null) 90 else 180)) togetherWith fadeOut(tween(100))
                    },
                    label = "機能を開く段階的モーション",
                ) { feature ->
                when (feature) {
                    FeatureRoute.Downloader -> DownloaderScreen(
                        initialUrl = sharedUrl,
                        onBack = { activeFeature = null },
                        onOpenSettings = {
                            activeFeature = null
                            destination = Destination.Settings
                        },
                    )
                    FeatureRoute.QrScanner -> QrScannerScreen { activeFeature = null }
                    FeatureRoute.Schedule -> ScheduleGeneratorScreen { activeFeature = null }
                    FeatureRoute.Files -> FileReferenceScreen { activeFeature = null }
                    FeatureRoute.MiniGame -> MiniGameScreen(
                        onBack = { activeFeature = null },
                        onDosukoiVisibilityChange = { dosukoiActive = it },
                        motionFps = motionFps,
                    )
                    FeatureRoute.Routine -> RoutineScreen { activeFeature = null }
                    null -> AnimatedContent(
                        targetState = destination,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val direction = if (forward) 1 else -1
                            // 押下、選択移動、画面出現を順に重ねる段階的モーション。
                            (fadeIn(tween(240, delayMillis = 70)) +
                                slideInHorizontally(tween(340, delayMillis = 50, easing = FastOutSlowInEasing)) { it * direction / 18 } +
                                scaleIn(tween(340, delayMillis = 50, easing = FastOutSlowInEasing), initialScale = 0.985f)) togetherWith
                                (fadeOut(tween(110)) + slideOutHorizontally(tween(160)) { -it * direction / 28 })
                        },
                        label = "画面切り替え",
                    ) { current ->
                        destinationState.SaveableStateProvider(current.name) {
                        when (current) {
                            Destination.Home -> HomeScreen(
                                onOpenFeature = { activeFeature = it },
                                onOpenAll = { destination = Destination.Features },
                                onComingSoon = onComingSoon,
                                shortcut = homeShortcut,
                            )
                            Destination.Features -> FeaturesScreen(
                                onOpenFeature = { activeFeature = it },
                                onComingSoon = onComingSoon,
                            )
                            Destination.Settings -> SettingsScreen(
                                darkTheme = darkTheme,
                                onDarkThemeChange = onDarkThemeChange,
                                appIcon = AppIconManager.optionForId(appIconId),
                                routineLevel = calculateRoutineLevel(
                                    LocalContext.current.getSharedPreferences("routine", android.content.Context.MODE_PRIVATE)
                                        .getInt("total_points", 0),
                                ),
                                onAppIconChange = { option ->
                                    appIconId = option.id
                                    onAppIconChange(option)
                                },
                                motionFps = motionFps,
                                onMotionFpsChange = onMotionFpsChange,
                                homeShortcut = homeShortcut,
                                onHomeShortcutChange = {
                                    homeShortcut = it
                                    onHomeShortcutChange(it.requestId)
                                },
                            )
                        }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun AnimatedBackdrop(appIcon: AppIconOption = AppIconManager.defaultLight) {
    val dark = LocalEssentialDark.current
    val backdropSpec = tween<Color>(360, easing = FastOutSlowInEasing)
    val iconStart by animateColorAsState(Color(appIcon.backgroundStart), backdropSpec, label = "アイコン背景開始色")
    val iconEnd by animateColorAsState(Color(appIcon.backgroundEnd), backdropSpec, label = "アイコン背景終了色")
    // 下端色を二重に補間すると追従が遅れるため、同じ補間値を直接使用する。
    val bottom = iconEnd
    val firstGlow = iconEnd.copy(alpha = if (dark) 0.34f else 0.26f)
    val secondGlow = iconStart.copy(alpha = if (dark) 0.24f else 0.18f)
    val horizontal = remember { androidx.compose.animation.core.Animatable(0.18f) }
    val vertical = remember { androidx.compose.animation.core.Animatable(0.16f) }
    LaunchedEffect(dark) {
        // 起動とテーマ変更時だけ光彩を動かし、停止中の全画面再描画をなくす。
        launch { horizontal.animateTo(if (dark) 0.72f else 0.30f, tween(1_200)) }
        launch { vertical.animateTo(if (dark) 0.36f else 0.24f, tween(1_200)) }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(iconStart, bottom)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(firstGlow.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(size.width * horizontal.value, size.height * vertical.value),
                radius = size.maxDimension * 0.64f,
            ),
            radius = size.maxDimension * 0.64f,
            center = Offset(size.width * horizontal.value, size.height * vertical.value),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondGlow.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * (1f - horizontal.value), size.height * 0.76f),
                radius = size.maxDimension * 0.56f,
            ),
            radius = size.maxDimension * 0.56f,
            center = Offset(size.width * (1f - horizontal.value), size.height * 0.76f),
        )
    }
}

@Composable
private fun HomeScreen(
    onOpenFeature: (FeatureRoute) -> Unit,
    onOpenAll: () -> Unit,
    onComingSoon: () -> Unit,
    shortcut: FeatureRoute,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        progressiveItem(0) { AppHeader() }
        progressiveItem(1) { HeroCard(shortcut) { onOpenFeature(shortcut) } }
        progressiveItem(2) { SectionHeader(title = "使える機能", action = "すべて見る", onAction = onOpenAll) }
        progressiveItem(3) {
            FeatureGrid(
                features = listOf(
                    FeatureItem("ダウンローダー", "動画・音声・画像を端末へ", EssentialSymbol.Download, EssentialOrange, FeatureRoute.Downloader),
                    FeatureItem("QRスキャナー", "純正カメラ経路で高速読取", EssentialSymbol.Qr, EssentialLime, FeatureRoute.QrScanner),
                    FeatureItem("行程表", "経由地を含めて3形式へ", EssentialSymbol.Calendar, EssentialYellow, FeatureRoute.Schedule),
                    FeatureItem("ファイル参照", "圧縮・変換・背景透過", EssentialSymbol.Media, EssentialRed, FeatureRoute.Files),
                    FeatureItem("ミニゲーム", "言葉遊び・マインスイーパー", EssentialSymbol.Game, Color(0xFF9C6BFF), FeatureRoute.MiniGame),
                    FeatureItem("日課", "毎日の目標をポイントに", EssentialSymbol.Routine, Color(0xFF27B99A), FeatureRoute.Routine),
                ),
                onClick = { feature ->
                    feature.route?.let(onOpenFeature) ?: onComingSoon()
                },
            )
        }
        progressiveItem(4) { RustCoreBanner() }
    }
}

@Composable
private fun EssentialLogo(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Crossfade(LocalEssentialDark.current, modifier = modifier, animationSpec = tween(500), label = "Essentialロゴの切り替え") { dark ->
        Image(painterResource(if (dark) R.drawable.essential_icon_dark else R.drawable.essential_icon),
            contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EssentialLogo(
            contentDescription = "Essentialのアイコン",
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(15.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Essential",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "必要なものを、もっと身近に",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, Color.White.copy(alpha = 0.32f), CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                EssentialSymbol(
                    symbol = EssentialSymbol.Spark,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                    description = "Essentialの状態",
                )
            }
        }
    }
}

@Composable
private fun HeroCard(shortcut: FeatureRoute, onClick: () -> Unit) {
    val dark = LocalEssentialDark.current
    val heroLight by animateColorAsState(if (dark) Color(0xFF814AFF) else EssentialYellow, tween(700), label = "カードの紫")
    val heroShade by animateColorAsState(if (dark) Color(0xFF225AFF) else EssentialOrange, tween(700), label = "カードの青")
    val heroAccent by animateColorAsState(if (dark) Color(0xFF665EFF) else EssentialLime, tween(700), label = "カードの光彩")
    PressableGlassCard(
        onClick = onClick,
        radius = 36.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(heroLight.copy(alpha = 0.72f), heroShade.copy(alpha = 0.10f)),
                    ),
                    radius = size.width * 0.58f,
                    center = Offset(size.width * 0.94f, size.height * 0.17f),
                )
                drawCircle(
                    color = heroAccent.copy(alpha = 0.24f),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.08f, size.height * 0.92f),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "はじめよう",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    EssentialLogo(
                        contentDescription = null,
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column {
                    Text(
                        text = "毎日に必要なものを、\nひとつに。",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${shortcut.displayName()}を開く",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        EssentialSymbol(
                            symbol = EssentialSymbol.Arrow,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                            description = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun FeatureGrid(
    features: List<FeatureItem>,
    onClick: (FeatureItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        features.chunked(2).forEachIndexed { rowIndex, rowFeatures ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowFeatures.forEachIndexed { columnIndex, feature ->
                    ProgressiveWidget(rowIndex * 2 + columnIndex, Modifier.weight(1f)) {
                        FeatureCard(
                            feature = feature,
                            onClick = { onClick(feature) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (rowFeatures.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableGlassCard(
        onClick = onClick,
        radius = 28.dp,
        modifier = modifier.height(164.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(17.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(feature.accent.copy(alpha = 0.20f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    EssentialSymbol(
                        symbol = feature.symbol,
                        tint = if (feature.accent == EssentialYellow) Color(0xFF785900) else feature.accent,
                        modifier = Modifier.size(23.dp),
                        description = null,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (feature.route == null) "準備中" else "利用可能",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Column {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RustCoreBanner() {
    val coreVersion = remember { EssentialCore.version() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(24.dp)
            .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (coreVersion > 0) EssentialLime else EssentialRed,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Rust Core", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (coreVersion > 0) "接続済み・Core v$coreVersion" else "ライブラリ未接続",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EssentialSymbol(
                symbol = if (coreVersion > 0) EssentialSymbol.Check else EssentialSymbol.Settings,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
                description = null,
            )
        }
    }
}

@Composable
private fun FeaturesScreen(onOpenFeature: (FeatureRoute) -> Unit, onComingSoon: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        progressiveItem(0) {
            Text("機能一覧", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                "利用可能な機能と、これから追加する機能です。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        progressiveItem(1) {
            CategoryPanel(
                title = "ダウンローダー",
                description = "yt-dlpとFFmpegで動画・音声・画像を保存",
                symbol = EssentialSymbol.Download,
                accent = EssentialOrange,
                onClick = { onOpenFeature(FeatureRoute.Downloader) },
            )
        }
        progressiveItem(2) {
            CategoryPanel(
                title = "QRスキャナー",
                description = "Camera HALを通る高性能カメラ経路で読み取り",
                symbol = EssentialSymbol.Qr,
                accent = EssentialLime,
                onClick = { onOpenFeature(FeatureRoute.QrScanner) },
            )
        }
        progressiveItem(3) {
            CategoryPanel(
                title = "行程表ジェネレーター",
                description = "経由地を含む行程をPDF・文章・画像へ出力",
                symbol = EssentialSymbol.Calendar,
                accent = EssentialYellow,
                onClick = { onOpenFeature(FeatureRoute.Schedule) },
            )
        }
        progressiveItem(4) {
            CategoryPanel(
                title = "ファイル参照",
                description = "画像・動画・音声の圧縮、変換、切り取り",
                symbol = EssentialSymbol.Media,
                accent = EssentialRed,
                onClick = { onOpenFeature(FeatureRoute.Files) },
            )
        }
        progressiveItem(5) {
            CategoryPanel(
                title = "ミニゲーム",
                description = "言葉遊び・マインスイーパー",
                symbol = EssentialSymbol.Game,
                accent = Color(0xFF9C6BFF),
                onClick = { onOpenFeature(FeatureRoute.MiniGame) },
            )
        }
        progressiveItem(6) {
            CategoryPanel(
                title = "日課",
                description = "デイリー・ウィークリー目標とポイントを管理",
                symbol = EssentialSymbol.Routine,
                accent = Color(0xFF27B99A),
                onClick = { onOpenFeature(FeatureRoute.Routine) },
            )
        }
        progressiveItem(7) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
            ) {
                Text(
                    "ダウンローダーは公開コンテンツ専用。QR解析、行程表生成、ファイル加工は端末内で実行します。",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CategoryPanel(
    title: String,
    description: String,
    symbol: EssentialSymbol,
    accent: Color,
    available: Boolean = true,
    onClick: () -> Unit,
) {
    PressableGlassCard(
        onClick = onClick,
        radius = 30.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(accent.copy(alpha = 0.20f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                EssentialSymbol(symbol, accent, Modifier.size(28.dp), null)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(5.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (available) {
                EssentialSymbol(EssentialSymbol.Arrow, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(20.dp), null)
            } else {
                Text("準備中", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    appIcon: AppIconOption,
    routineLevel: jp.essential.app.feature.routine.RoutineLevel,
    onAppIconChange: (AppIconOption) -> Unit,
    motionFps: Int,
    onMotionFpsChange: (Int) -> Unit,
    homeShortcut: FeatureRoute,
    onHomeShortcutChange: (FeatureRoute) -> Unit,
) {
    val coreVersion = remember { EssentialCore.version() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        progressiveItem(0) {
            Text("設定", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                "Essentialを、あなたに合う見た目へ。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        progressiveItem(1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(28.dp)
                    .padding(19.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EssentialSymbol(
                            EssentialSymbol.Spark,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            Modifier.size(23.dp),
                            null,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ダークテーマ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (darkTheme) "落ち着いた暗い配色" else "明るく軽やかな配色",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ThemeToggle(dark = darkTheme, onToggle = { onDarkThemeChange(!darkTheme) })
                }
            }
        }
        progressiveItem(2) {
            AppIconSettingsCard(
                selected = appIcon,
                currentLevel = routineLevel.level,
                onSelect = onAppIconChange,
            )
        }
        progressiveItem(3) {
            Column(Modifier.fillMaxWidth().glassSurface(28.dp).padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("モーションfps", style = MaterialTheme.typography.titleLarge)
                Text("初期値60fps・選択すると適用されます", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120).forEach { fps ->
                        val selected = fps == motionFps
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onMotionFpsChange(fps) })
                            .padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("${fps}fps", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text("端末へ希望fpsを要求します。実際のfpsは対応Hz・省電力設定・OSに依存します。アニメーションの所要時間は変わりません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        progressiveItem(4) {
            Column(
                Modifier.fillMaxWidth().glassSurface(28.dp).padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ホームのショートカット", style = MaterialTheme.typography.titleLarge)
                Text("ホームのカードをタップしたときに開く機能", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureRoute.entries.forEach { route ->
                        val selected = route == homeShortcut
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onHomeShortcutChange(route) })
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EssentialSymbol(route.symbol(), if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                Modifier.size(24.dp), null)
                            Spacer(Modifier.width(12.dp))
                            Text(route.displayName(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
        }
        progressiveItem(5) {
            YtDlpUpdateSettingsCard()
        }
        item(key = "app-updates") {
            ProgressiveWidget(6) { jp.essential.app.update.AppUpdateSettingsCard() }
        }
        progressiveItem(7) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(28.dp)
                    .padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                SettingValue("アプリ", "Essential ${BuildConfig.VERSION_NAME}")
                SettingValue("UI", "Material 3 Expressive")
                SettingValue("コア", if (coreVersion > 0) "Rust Core v$coreVersion・接続済み" else "未接続")
                SettingValue("状態", "3機能を搭載")
            }
        }
        progressiveItem(8) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EssentialLogo(
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Essential · Built with Kotlin + Rust",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppIconSettingsCard(
    selected: AppIconOption,
    currentLevel: Int,
    onSelect: (AppIconOption) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(28.dp)
            .padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(selected.iconRes),
                contentDescription = selected.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)),
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("アプリアイコン", style = MaterialTheme.typography.titleLarge)
                Text("${selected.label}・設定からいつでも変更", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            ChevronIndicator(expanded = expanded)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(110)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "報酬で解放されたアイコンを選ぶと、アイコンの雰囲気に合わせて背景とテーマを調整します。DOSUKOIの画面色は変更しません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppIconManager.options.forEach { option ->
                    val available = option.unlockLevel == 0 || currentLevel >= option.unlockLevel
                    val isSelected = option.id == selected.id
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface.copy(alpha = if (available) 0.62f else 0.30f),
                            )
                            .clickable(enabled = available) { onSelect(option) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .graphicsLayer(alpha = if (available) 1f else 0.48f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(option.iconRes),
                            contentDescription = option.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)),
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, fontWeight = FontWeight.Bold)
                            Text(
                                if (available) option.description else "LV${option.unlockLevel}で解放",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.RadioButton(selected = isSelected, onClick = null)
                    }
                }
            }
        }
    }
}

/** テーマ切替用のMaterial 3 Expressiveピル。月と太陽を同じ軌道で滑らかに移動させる。 */
@Composable
private fun ThemeToggle(
    dark: Boolean,
    onToggle: () -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (dark) Color(0xFF272D4D) else Color(0xFFE8C995),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "テーマ切替トラック",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (dark) Color(0xFFE8EEFF) else Color(0xFFFFF1C9),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "テーマ切替サム",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (dark) 38.dp else 4.dp,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "テーマ切替位置",
    )
    val rotation by animateFloatAsState(
        targetValue = if (dark) 180f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "テーマ切替回転",
    )
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 38.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(role = Role.Switch, onClick = onToggle)
            .semantics { contentDescription = if (dark) "ダークテーマ" else "ライトテーマ" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (dark) {
                listOf(
                    Offset(size.width * 0.20f, size.height * 0.32f),
                    Offset(size.width * 0.38f, size.height * 0.66f),
                    Offset(size.width * 0.52f, size.height * 0.25f),
                ).forEach { center ->
                    drawCircle(Color.White.copy(alpha = 0.82f), radius = 1.35.dp.toPx(), center = center)
                }
            } else {
                val center = Offset(size.width * 0.26f, size.height * 0.5f)
                val rayRadius = 11.dp.toPx()
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val start = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * (rayRadius - 3.dp.toPx()),
                        center.y + kotlin.math.sin(angle).toFloat() * (rayRadius - 3.dp.toPx()),
                    )
                    val end = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * rayRadius,
                        center.y + kotlin.math.sin(angle).toFloat() * rayRadius,
                    )
                    drawLine(Color(0xFFFFF1C9).copy(alpha = 0.76f), start, end, 1.4.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(Color(0xFFFFF1C9).copy(alpha = 0.48f), radius = 8.dp.toPx(), center = center)
            }
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(30.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(thumbColor)
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.31f
                if (dark) {
                    drawCircle(Color(0xFFF6F8FF), radius = radius, center = center)
                    drawCircle(trackColor, radius = radius, center = Offset(center.x + radius * 0.52f, center.y - radius * 0.38f))
                } else {
                    drawCircle(Color(0xFFFFC94A), radius = radius * 0.72f, center = center)
                    repeat(8) { index ->
                        val angle = Math.toRadians(index * 45.0)
                        val inner = radius * 1.05f
                        val outer = radius * 1.48f
                        drawLine(
                            Color(0xFFFFB52E),
                            Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                            Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                            1.35.dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/** 折りたたみ状態を広いV字で示すインジケーター。 */
@Composable
private fun ChevronIndicator(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "折りたたみ矢印",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = 3.6.dp.toPx()
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.35f), Offset(size.width * 0.5f, size.height * 0.65f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.65f), Offset(size.width * 0.88f, size.height * 0.35f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EssentialNavigationBar(
    selected: Destination,
    onSelected: (Destination) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val glassShape = RoundedCornerShape(28.dp)
    val selectionPosition by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 480f),
        label = "ガラスの選択位置",
    )
    // 背景だけを透過させる。文字・アイコンに親の透明度や消失アニメーションを適用しない。
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(glassShape)
            .background(colors.surface.copy(alpha = 0.90f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), colors.outlineVariant.copy(alpha = 0.32f))), glassShape),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val cellWidth = size.width / Destination.entries.size
            val width = minOf(72.dp.toPx(), cellWidth - 16.dp.toPx())
            val height = 36.dp.toPx()
            val left = cellWidth * (selectionPosition + 0.5f) - width / 2f
            val top = 7.dp.toPx()
            val radius = androidx.compose.ui.geometry.CornerRadius(height / 2f)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(colors.secondaryContainer.copy(alpha = 0.94f), colors.primaryContainer.copy(alpha = 0.65f)), Offset(left, top), Offset(left + width, top + height)),
                topLeft = Offset(left, top), size = Size(width, height), cornerRadius = radius,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.52f), topLeft = Offset(left, top),
                size = Size(width, height), cornerRadius = radius, style = Stroke(1.dp.toPx()),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            Destination.entries.forEach { destination ->
                val isSelected = destination == selected
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.92f else if (isSelected) 1.04f else 1f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 650f),
                    label = "ナビゲーションアイコン",
                )
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                        .selectable(selected = isSelected, role = Role.Tab, interactionSource = interaction, indication = null) {
                        if (!isSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelected(destination)
                        }
                    }.padding(top = 7.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                        BadgedBox(
                            badge = {
                                if (destination == Destination.Features) {
                                    Badge(containerColor = EssentialOrange)
                                }
                            },
                        ) {
                            EssentialSymbol(
                                symbol = destination.symbol,
                                tint = colors.onSurface,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale),
                                description = destination.label,
                            )
                        }
                    }
                    Text(destination.label, color = colors.onSurface, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PressableGlassCard(
    onClick: () -> Unit,
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 620f),
        label = "カードの押下",
    )
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .glassSurface(radius)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
    ) {
        content()
    }
}

private fun Modifier.glassSurface(radius: Dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.36f),
                Color.White.copy(alpha = 0.13f),
            ),
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.12f)),
        ),
        shape = RoundedCornerShape(radius),
    )

@Composable
private fun EssentialSymbol(
    symbol: EssentialSymbol,
    tint: Color,
    modifier: Modifier,
    description: String?,
) {
    val semanticsModifier = if (description != null) {
        modifier.semantics { contentDescription = description }
    } else {
        modifier
    }
    Canvas(modifier = semanticsModifier) {
        val stroke = Stroke(width = size.minDimension * 0.095f, cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        when (symbol) {
            EssentialSymbol.Home -> {
                val path = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.48f)
                    lineTo(size.width * 0.50f, size.height * 0.18f)
                    lineTo(size.width * 0.84f, size.height * 0.48f)
                    lineTo(size.width * 0.77f, size.height * 0.48f)
                    lineTo(size.width * 0.77f, size.height * 0.82f)
                    lineTo(size.width * 0.23f, size.height * 0.82f)
                    lineTo(size.width * 0.23f, size.height * 0.48f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            EssentialSymbol.Grid -> {
                val itemSize = size.minDimension * 0.25f
                listOf(0.18f to 0.18f, 0.57f to 0.18f, 0.18f to 0.57f, 0.57f to 0.57f).forEach { (x, y) ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(size.width * x, size.height * y),
                        size = Size(itemSize, itemSize),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(itemSize * 0.28f),
                        style = stroke,
                    )
                }
            }
            EssentialSymbol.Settings -> {
                drawCircle(tint, size.minDimension * 0.18f, center, style = stroke)
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val start = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.29f,
                        center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.29f,
                    )
                    val end = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.40f,
                        center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.40f,
                    )
                    drawLine(tint, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            EssentialSymbol.Spark -> {
                val path = Path().apply {
                    moveTo(center.x, size.height * 0.08f)
                    lineTo(size.width * 0.59f, size.height * 0.40f)
                    lineTo(size.width * 0.92f, center.y)
                    lineTo(size.width * 0.59f, size.height * 0.60f)
                    lineTo(center.x, size.height * 0.92f)
                    lineTo(size.width * 0.41f, size.height * 0.60f)
                    lineTo(size.width * 0.08f, center.y)
                    lineTo(size.width * 0.41f, size.height * 0.40f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            EssentialSymbol.Media -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
                    size = Size(size.width * 0.76f, size.height * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.14f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.70f)
                    lineTo(size.width * 0.42f, size.height * 0.48f)
                    lineTo(size.width * 0.56f, size.height * 0.62f)
                    lineTo(size.width * 0.68f, size.height * 0.50f)
                    lineTo(size.width * 0.80f, size.height * 0.70f)
                }
                drawPath(path, tint, style = stroke)
            }
            EssentialSymbol.Device -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.25f, size.height * 0.08f),
                    size = Size(size.width * 0.50f, size.height * 0.84f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.16f),
                    style = stroke,
                )
                drawCircle(tint, size.minDimension * 0.025f, Offset(center.x, size.height * 0.82f))
            }
            EssentialSymbol.Ai -> {
                drawCircle(tint, size.minDimension * 0.34f, center, style = stroke)
                drawCircle(tint, size.minDimension * 0.035f, Offset(size.width * 0.40f, size.height * 0.45f))
                drawCircle(tint, size.minDimension * 0.035f, Offset(size.width * 0.60f, size.height * 0.45f))
                drawArc(
                    color = tint,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.37f, size.height * 0.48f),
                    size = Size(size.width * 0.26f, size.height * 0.20f),
                    style = stroke,
                )
            }
            EssentialSymbol.Download -> {
                drawLine(
                    tint,
                    Offset(center.x, size.height * 0.12f),
                    Offset(center.x, size.height * 0.64f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                // 矢印先端を一体の面として描き、スクロール中の縮小レイヤーでも欠けないようにする。
                val arrowHead = Path().apply {
                    moveTo(size.width * 0.23f, size.height * 0.47f)
                    lineTo(center.x, size.height * 0.73f)
                    lineTo(size.width * 0.77f, size.height * 0.47f)
                    lineTo(size.width * 0.69f, size.height * 0.40f)
                    lineTo(center.x, size.height * 0.59f)
                    lineTo(size.width * 0.31f, size.height * 0.40f)
                    close()
                }
                drawPath(arrowHead, tint)
                drawLine(
                    tint,
                    Offset(size.width * 0.18f, size.height * 0.84f),
                    Offset(size.width * 0.82f, size.height * 0.84f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            EssentialSymbol.Qr -> {
                val block = size.minDimension * 0.26f
                listOf(0.12f to 0.12f, 0.62f to 0.12f, 0.12f to 0.62f).forEach { (x, y) ->
                    drawRect(
                        color = tint,
                        topLeft = Offset(size.width * x, size.height * y),
                        size = Size(block, block),
                        style = stroke,
                    )
                }
                drawRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.60f, size.height * 0.60f),
                    size = Size(block * 0.42f, block * 0.42f),
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.78f, size.height * 0.74f),
                    size = Size(block * 0.42f, block * 0.42f),
                )
            }
            EssentialSymbol.Calendar -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.13f, size.height * 0.20f),
                    size = Size(size.width * 0.74f, size.height * 0.66f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f),
                    style = stroke,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.13f, size.height * 0.40f),
                    Offset(size.width * 0.87f, size.height * 0.40f),
                    strokeWidth = stroke.width,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.33f, size.height * 0.10f),
                    Offset(size.width * 0.33f, size.height * 0.28f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.67f, size.height * 0.10f),
                    Offset(size.width * 0.67f, size.height * 0.28f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            EssentialSymbol.Game -> {
                drawRoundRect(tint, Offset(size.width * 0.10f, size.height * 0.30f),
                    Size(size.width * 0.80f, size.height * 0.48f),
                    androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.22f), style = stroke)
                drawLine(tint, Offset(size.width * 0.25f, center.y), Offset(size.width * 0.43f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.34f, size.height * 0.41f), Offset(size.width * 0.34f, size.height * 0.59f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(tint, size.minDimension * 0.045f, Offset(size.width * 0.66f, size.height * 0.47f))
                drawCircle(tint, size.minDimension * 0.045f, Offset(size.width * 0.77f, size.height * 0.59f))
            }
            EssentialSymbol.Routine -> {
                drawCircle(tint, size.minDimension * 0.38f, center, style = stroke)
                drawLine(tint, center, Offset(center.x, size.height * 0.27f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, center, Offset(size.width * 0.68f, size.height * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.35f, size.height * 0.08f), Offset(size.width * 0.65f, size.height * 0.08f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EssentialSymbol.Arrow -> {
                drawLine(tint, Offset(size.width * 0.16f, center.y), Offset(size.width * 0.82f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.60f, size.height * 0.28f), Offset(size.width * 0.82f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.60f, size.height * 0.72f), Offset(size.width * 0.82f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            EssentialSymbol.Check -> {
                drawLine(tint, Offset(size.width * 0.14f, size.height * 0.52f), Offset(size.width * 0.40f, size.height * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.40f, size.height * 0.76f), Offset(size.width * 0.86f, size.height * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

private fun FeatureRoute.displayName(): String = when (this) {
    FeatureRoute.Downloader -> "ダウンローダー"
    FeatureRoute.QrScanner -> "QRスキャナー"
    FeatureRoute.Schedule -> "行程表ジェネレーター"
    FeatureRoute.Files -> "ファイル参照"
    FeatureRoute.MiniGame -> "ミニゲーム"
    FeatureRoute.Routine -> "日課"
}

private fun FeatureRoute.symbol(): EssentialSymbol = when (this) {
    FeatureRoute.Downloader -> EssentialSymbol.Download
    FeatureRoute.QrScanner -> EssentialSymbol.Qr
    FeatureRoute.Schedule -> EssentialSymbol.Calendar
    FeatureRoute.Files -> EssentialSymbol.Media
    FeatureRoute.MiniGame -> EssentialSymbol.Game
    FeatureRoute.Routine -> EssentialSymbol.Routine
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun EssentialHomePreview() {
    EssentialTheme(darkTheme = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedBackdrop()
            HomeScreen(
                onOpenFeature = {},
                onOpenAll = {},
                onComingSoon = {},
                shortcut = FeatureRoute.Downloader,
            )
        }
    }
}
