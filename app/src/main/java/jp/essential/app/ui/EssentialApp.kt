package jp.essential.app.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.OutlinedButton
import android.content.Context
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.exifinterface.media.ExifInterface
import jp.essential.app.AppIconManager
import jp.essential.app.AppIconOption
import jp.essential.app.BuildConfig
import jp.essential.app.R
import jp.essential.app.core.EssentialCore
import jp.essential.app.feature.downloader.DownloaderScreen
import jp.essential.app.feature.downloader.YtDlpUpdateSettingsCard
import jp.essential.app.storage.StorageMaintenance
import jp.essential.app.feature.files.FileReferenceScreen
import jp.essential.app.feature.minigame.MiniGameScreen
import jp.essential.app.feature.scanner.ScannerScreen
import jp.essential.app.feature.notificationlog.NotificationLogScreen
import jp.essential.app.feature.notificationlog.drawNotificationLogLogo
import jp.essential.app.feature.mannaka.MannakaScreen
import jp.essential.app.feature.mannaka.meetingBackgroundColor
import jp.essential.app.feature.routine.RoutineScreen
import jp.essential.app.feature.schedule.ScheduleGeneratorScreen
import jp.essential.app.profile.AppProgressStore
import jp.essential.app.profile.AppLevel
import jp.essential.app.profile.AppReward
import jp.essential.app.profile.ProfileStore
import jp.essential.app.profile.appRewards
import jp.essential.app.profile.calculateAppLevel
import jp.essential.app.ui.theme.EssentialLime
import jp.essential.app.ui.theme.EssentialOrange
import jp.essential.app.ui.theme.EssentialRed
import jp.essential.app.ui.theme.EssentialTheme
import jp.essential.app.ui.theme.LocalEssentialDark
import jp.essential.app.ui.theme.EssentialYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(val label: String, val symbol: EssentialSymbol) {
    Home("ホーム", EssentialSymbol.Home),
    Features("機能一覧", EssentialSymbol.Grid),
    Profile("プロフィール", EssentialSymbol.Profile),
}

private enum class ProfilePanel {
    Main,
    Arrange,
    Settings,
}

// 最終項目全体が下部タブの影に隠れないための追加スクロール余白。
private val NAVIGATION_CONTENT_CLEARANCE = 12.8.dp

private enum class EssentialSymbol {
    Home,
    Grid,
    Settings,
    Edit,
    Profile,
    Spark,
    Media,
    Device,
    Ai,
    Arrow,
    Check,
    Download,
    Qr,
    Calendar,
    Notification,
    Game,
    Routine,
    TextScan,
}

private enum class FeatureRoute(val requestId: String) {
    Downloader("downloader"),
    Scanner("scanner"),
    Schedule("schedule"),
    Files("files"),
    MiniGame("mini_game"),
    Routine("routine"),
    Mannaka("mannaka"),
    NotificationLog("notification_log"),
}

// 旧ショートカットとクイック設定からも、最後に使った統合モードを開く。
private fun featureRouteFromId(id: String?): FeatureRoute? =
    if (id == "qr_scanner" || id == "text_scan") FeatureRoute.Scanner
    else FeatureRoute.entries.firstOrNull { it.requestId == id }

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
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStore(context.applicationContext) }
    var navigationProfileIcon by remember { mutableStateOf(profileStore.loadIcon()) }
    var navigationProfileImagePath by remember { mutableStateOf(profileStore.loadImagePath()) }
    // 下部タブはActivityの保存状態から復元せず、タスクを開き直したときはホームから始める。
    var destination by remember {
        mutableStateOf(if (initialSetupRequired) Destination.Profile else Destination.Home)
    }
    val tabMotionCycles = remember { mutableStateMapOf(destination to 1L) }
    val tabEntryDirections = remember { mutableStateMapOf(destination to 0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentDestination by rememberUpdatedState(destination)
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stopped = true
                Lifecycle.Event.ON_START -> if (stopped) {
                    val activeDestination = currentDestination
                    tabEntryDirections[activeDestination] = 0
                    tabMotionCycles[activeDestination] = (tabMotionCycles[activeDestination] ?: 0L) + 1L
                    stopped = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun selectDestination(next: Destination) {
        if (next != destination) {
            tabMotionCycles[next] = (tabMotionCycles[next] ?: 0L) + 1L
            tabEntryDirections[next] = if (next.ordinal > destination.ordinal) 1 else -1
            destination = next
        }
    }
    var activeFeature by rememberSaveable { mutableStateOf<FeatureRoute?>(null) }
    var dosukoiActive by rememberSaveable { mutableStateOf(false) }
    var homeShortcut by rememberSaveable {
        mutableStateOf(featureRouteFromId(initialHomeShortcut) ?: FeatureRoute.Downloader)
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
        featureRouteFromId(requestedFeature)?.let {
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
            selectDestination(Destination.Home)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (dosukoiActive) {
            // DOSUKOIはWebView自身がFluid Gradientを描画するため、背面のCanvasを止めて二重描画を避ける。
            Box(Modifier.fillMaxSize().background(Color(0xFF0F0F1A)))
        } else if (activeFeature == FeatureRoute.Mannaka) {
            // まんなかの背景色をシステムバーの背面まで連続させる。
            Box(Modifier.fillMaxSize().background(meetingBackgroundColor()))
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
                        profileIcon = navigationProfileIcon,
                        profileImagePath = navigationProfileImagePath,
                        onSelected = ::selectDestination,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 下部バーの背面まで画面コンテンツを描画し、空いた緑色の帯を作らない。
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                AnimatedContent(
                    targetState = activeFeature,
                    transitionSpec = {
                        val direction = if (targetState != null) 1 else -1
                        // 入場は各機能内のまとまりに任せ、画面全体の横スライドは行わない。
                        fadeIn(tween(if (targetState != null) 90 else 180)) togetherWith fadeOut(tween(100))
                    },
                    label = "機能を開く段階的モーション",
                    modifier = Modifier.fillMaxSize(),
                ) { feature ->
                when (feature) {
                    FeatureRoute.Downloader -> DownloaderScreen(
                        initialUrl = sharedUrl,
                        onBack = { activeFeature = null },
                        onOpenSettings = {
                            activeFeature = null
                            selectDestination(Destination.Profile)
                        },
                    )
                    FeatureRoute.Mannaka -> MannakaScreen { activeFeature = null }
                    FeatureRoute.NotificationLog -> NotificationLogScreen { activeFeature = null }
                    FeatureRoute.Scanner -> ScannerScreen { activeFeature = null }
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
                        modifier = Modifier.fillMaxSize().testTag("main-tabs").tabSwipeNavigation { direction ->
                            Destination.entries.getOrNull(currentDestination.ordinal + direction)?.let(::selectDestination)
                        },
                        transitionSpec = {
                            val direction = tabEntryDirections[targetState] ?: 0
                            // 起動と再表示は縦の項目演出だけにし、タブ移動時だけ横方向へ入れる。
                            val enter = if (direction == 0) {
                                fadeIn(tween(180))
                            } else {
                                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) {
                                        it * direction / 18
                                    }
                            }
                            enter togetherWith fadeOut(tween(120))
                        },
                        label = "画面切り替え",
                    ) { current ->
                        val motionCycle = tabMotionCycles[current] ?: 0L
                        var motionCompleted by remember(current, motionCycle) { mutableStateOf(false) }
                        LaunchedEffect(current, motionCycle) {
                            kotlinx.coroutines.delay(1_000L)
                            motionCompleted = true
                        }
                        CompositionLocalProvider(
                            LocalProgressiveMotionCycle provides motionCycle,
                            LocalProgressiveMotionDirection provides (tabEntryDirections[current] ?: 0),
                            LocalProgressiveMotionCompleted provides motionCompleted,
                            // 画面外のカードの描画レイヤーを毎フレーム更新しない。
                            LocalProgressiveMotionEager provides false,
                        ) {
                        destinationState.SaveableStateProvider(current.name) {
                        when (current) {
                            Destination.Home -> HomeScreen(
                                onOpenFeature = { activeFeature = it },
                                onOpenAll = { selectDestination(Destination.Features) },
                                onComingSoon = onComingSoon,
                                shortcut = homeShortcut,
                                contentBottomPadding = innerPadding.calculateBottomPadding(),
                            )
                            Destination.Features -> FeaturesScreen(
                                onOpenFeature = { activeFeature = it },
                                onComingSoon = onComingSoon,
                                contentBottomPadding = innerPadding.calculateBottomPadding(),
                            )
                            Destination.Profile -> ProfileScreen(
                                darkTheme = darkTheme,
                                onDarkThemeChange = onDarkThemeChange,
                                appIcon = AppIconManager.optionForId(appIconId),
                                appLevel = calculateAppLevel(AppProgressStore(LocalContext.current).loadXp()),
                                initialSetupRequired = initialSetupRequired,
                                onProfileIconChange = { navigationProfileIcon = it },
                                onProfileImagePathChange = { navigationProfileImagePath = it },
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
                                contentBottomPadding = innerPadding.calculateBottomPadding(),
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
    contentBottomPadding: Dp,
) {
    val preferences = LocalContext.current.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var widgetOrder by remember {
        mutableStateOf(preferences.getString("home_widget_order", "").orEmpty().split(",").filter { it.isNotBlank() }
            .map { if (it in setOf("qr_scanner", "text_scan")) "scanner" else it }.distinct())
    }
    val homeListState = rememberLazyListState()
    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 26.dp + contentBottomPadding + NAVIGATION_CONTENT_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 全項目を一緒に構成し、画面外のカードもホーム入場時から演出を進める。
        item(key = "home-content") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ProgressiveWidget(0) { AppHeader() }
                ProgressiveWidget(1) { HeroCard(shortcut) { onOpenFeature(shortcut) } }
                ProgressiveWidget(2) { SectionHeader(title = "使える機能", action = "すべて見る", onAction = onOpenAll) }

                FeatureGrid(
                    features = listOf(
                        FeatureItem("ダウンローダー", "動画・音声・画像を端末へ", EssentialSymbol.Download, EssentialOrange, FeatureRoute.Downloader),
                        FeatureItem("スキャナー", "QRコード・写真の文字を読み取る", EssentialSymbol.Qr, EssentialLime, FeatureRoute.Scanner),
                        FeatureItem("行程表", "経由地を含めて3形式へ", EssentialSymbol.Calendar, EssentialYellow, FeatureRoute.Schedule),
                        FeatureItem("ファイル参照", "圧縮・変換・背景透過", EssentialSymbol.Media, EssentialRed, FeatureRoute.Files),
                        FeatureItem("ミニゲーム", "言葉遊び・マインスイーパー", EssentialSymbol.Game, Color(0xFF9C6BFF), FeatureRoute.MiniGame),
                        FeatureItem("日課", "毎日の目標をポイントに", EssentialSymbol.Routine, Color(0xFF27B99A), FeatureRoute.Routine),
                        FeatureItem("まんなか！", "みんなの駅から集合・遊びを提案", EssentialSymbol.Spark, Color(0xFFFF90B5), FeatureRoute.Mannaka),
                        FeatureItem("通知ログ", "通知を秒単位で記録・keep", EssentialSymbol.Notification, Color(0xFF78D9EF), FeatureRoute.NotificationLog),
                    ).sortedBy { widgetOrder.indexOf(it.route?.requestId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
                    onReorder = { reordered ->
                        widgetOrder = reordered.mapNotNull { it.route?.requestId }
                        preferences.edit().putString("home_widget_order", widgetOrder.joinToString(",")).apply()
                    },
                    scrollState = homeListState,
                    onClick = { feature ->
                        feature.route?.let(onOpenFeature) ?: onComingSoon()
                    },
                )
            }
        }
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
            LiquidGlassHeroBackdrop(
                light = heroLight,
                shade = heroShade,
                accent = heroAccent,
            )
            LiquidGlassHeroFinish()
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

private const val LIQUID_GLASS_SHADER_SOURCE = """
uniform shader contents;
uniform float2 resolution;

half4 main(float2 coordinate) {
    float2 uv = coordinate / resolution;
    float2 radial = uv - float2(0.5, 0.5);
    float radius = length(radial);
    float2 direction = normalize(radial + float2(0.0001, 0.0001));
    float edge = smoothstep(0.18, 0.72, radius);
    float distortion = 0.018 * edge * edge;

    float2 redUv = clamp(uv + direction * (distortion + 0.004), 0.0, 1.0);
    float2 greenUv = clamp(uv + direction * distortion, 0.0, 1.0);
    float2 blueUv = clamp(uv + direction * (distortion - 0.004), 0.0, 1.0);

    half4 redSample = contents.eval(redUv * resolution);
    half4 greenSample = contents.eval(greenUv * resolution);
    half4 blueSample = contents.eval(blueUv * resolution);
    half alpha = max(redSample.a, max(greenSample.a, blueSample.a));
    half4 refracted = half4(redSample.r, greenSample.g, blueSample.b, alpha);

    float rim = smoothstep(0.60, 0.80, radius);
    float centerGlow = 1.0 - smoothstep(0.04, 0.74, radius);
    refracted.rgb += float3(0.035, 0.055, 0.085) * centerGlow;
    refracted.rgb += float3(0.11, 0.025, 0.12) * rim * edge;
    return refracted;
}
"""

@Composable
@SuppressLint("NewApi")
private fun LiquidGlassHeroBackdrop(
    light: Color,
    shade: Color,
    accent: Color,
) {
    val shape = RoundedCornerShape(36.dp)
    val context = LocalContext.current
    val lowMemoryDevice = remember(context) {
        context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true
    }
    val shader = remember {
        if (!lowMemoryDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LIQUID_GLASS_SHADER_SOURCE) }.getOrNull()
        } else {
            null
        }
    }
    // 同じシェーダーの効果を再利用し、レイヤー更新ごとの生成を避ける。
    val glassEffect = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            RenderEffect.createRuntimeShaderEffect(shader, "contents").asComposeRenderEffect()
        } else null
    }
    val lightTransition = rememberInfiniteTransition(label = "液体ガラスの光")
    val lightTravel by lightTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "光の移動",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .graphicsLayer {
                compositingStrategy = if (glassEffect != null) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    shader.setFloatUniform(
                        "resolution",
                        size.width.toFloat().coerceAtLeast(1f),
                        size.height.toFloat().coerceAtLeast(1f),
                    )
                    renderEffect = glassEffect
                } else {
                    renderEffect = null
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x887F69FF),
                        Color(0x664A8DFF),
                        Color(0x6657D7C3),
                    ),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(light.copy(alpha = 0.86f), shade.copy(alpha = 0.06f)),
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.94f, size.height * 0.15f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                ),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.08f, size.height * 0.92f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                ),
                radius = size.width * 0.30f,
                center = Offset(size.width * lightTravel, size.height * 0.12f),
            )
        }
    }
}

@Composable
private fun LiquidGlassHeroFinish() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 1.25f
        val inset = stroke / 2f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(36.dp.toPx())
        drawRoundRect(
            brush = Brush.sweepGradient(
                0.00f to Color.White.copy(alpha = 0.82f),
                0.18f to Color(0xFFFF6EAA).copy(alpha = 0.52f),
                0.34f to Color(0xFF8E7CFF).copy(alpha = 0.70f),
                0.54f to Color(0xFF65E8FF).copy(alpha = 0.56f),
                0.76f to Color.White.copy(alpha = 0.18f),
                1.00f to Color.White.copy(alpha = 0.82f),
            ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = cornerRadius,
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
            ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = cornerRadius,
            style = Stroke(width = 0.8f),
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.64f), Color.Transparent),
                start = Offset(size.width * 0.10f, 2.dp.toPx()),
                end = Offset(size.width * 0.68f, 2.dp.toPx()),
            ),
            start = Offset(size.width * 0.12f, 2.dp.toPx()),
            end = Offset(size.width * 0.70f, 2.dp.toPx()),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round,
        )
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
    onReorder: (List<FeatureItem>) -> Unit,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
) {
    val currentFeatures by rememberUpdatedState(features)
    val currentReorder by rememberUpdatedState(onReorder)
    val bounds = remember { mutableStateMapOf<FeatureRoute, androidx.compose.ui.geometry.Rect>() }
    var dragged by remember { mutableStateOf<FeatureRoute?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val viewHeight = androidx.compose.ui.platform.LocalView.current.height.toFloat()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val edge = with(density) { 100.dp.toPx() }
    fun moveToTarget() {
        val selected = dragged ?: return
        val target = bounds.entries.firstOrNull { it.key != selected && it.value.contains(pointer) }?.key ?: return
        val items = currentFeatures.toMutableList()
        val from = items.indexOfFirst { it.route == selected }
        val to = items.indexOfFirst { it.route == target }
        if (from >= 0 && to >= 0) {
            items.add(to, items.removeAt(from))
            currentReorder(items)
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            val amount = when {
                pointer.y < edge -> -18f
                pointer.y > viewHeight - edge * 2 -> 18f
                else -> 0f
            }
            if (amount != 0f) { scrollState.scrollBy(amount); moveToTarget() }
            kotlinx.coroutines.delay(16)
        }
    }
    Column(
        modifier = Modifier.onGloballyPositioned { gridOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        pointer = gridOrigin + position
                        val hit = bounds.entries.firstOrNull { it.value.contains(pointer) }
                        dragged = hit?.key
                        grabOffset = hit?.let { pointer - it.value.center } ?: Offset.Zero
                        if (hit != null) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { dragged = null },
                    onDragCancel = { dragged = null },
                    onDrag = { change, amount ->
                        if (dragged != null) {
                            change.consume()
                            pointer += amount
                            moveToTarget()
                        }
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        features.chunked(2).forEachIndexed { rowIndex, rowFeatures ->
            Row(Modifier.fillMaxWidth().zIndex(if (rowFeatures.any { it.route == dragged }) 1f else 0f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowFeatures.forEach { feature ->
                    val selected = feature.route == dragged
                    Box(Modifier.weight(1f).zIndex(if (selected) 1f else 0f).onGloballyPositioned {
                        feature.route?.let { route -> bounds[route] = it.boundsInRoot() }
                    }) {
                        ProgressiveWidget(rowIndex + 3, Modifier.fillMaxWidth().graphicsLayer {
                            if (selected) {
                                val center = bounds[feature.route]?.center ?: pointer
                                translationX = pointer.x - grabOffset.x - center.x
                                translationY = pointer.y - grabOffset.y - center.y
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 16.dp.toPx()
                            }
                        }) {
                            FeatureCard(feature = feature, onClick = { if (dragged == null) onClick(feature) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (rowFeatures.size == 1) Spacer(Modifier.weight(1f))
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
private fun FeaturesScreen(
    onOpenFeature: (FeatureRoute) -> Unit,
    onComingSoon: () -> Unit,
    contentBottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 28.dp + contentBottomPadding + NAVIGATION_CONTENT_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 画面外の機能も同時に構成し、一覧への入場時から順番に演出する。
        item(key = "features-content") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressiveWidget(0) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("機能一覧", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "利用可能な機能と、これから追加する機能です。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ProgressiveWidget(1) {
                    CategoryPanel(
                        title = "ダウンローダー",
                        description = "yt-dlpとFFmpegで動画・音声・画像を保存",
                        symbol = EssentialSymbol.Download,
                        accent = EssentialOrange,
                        onClick = { onOpenFeature(FeatureRoute.Downloader) },
                    )
                }
                ProgressiveWidget(2) {
                    CategoryPanel(
                        title = "スキャナー",
                        description = "QRコード・カメラや写真の文字を読み取る",
                        symbol = EssentialSymbol.Qr,
                        accent = EssentialLime,
                        onClick = { onOpenFeature(FeatureRoute.Scanner) },
                    )
                }
                ProgressiveWidget(3) {
                    CategoryPanel(
                        title = "行程表ジェネレーター",
                        description = "経由地を含む行程をPDF・文章・画像へ出力",
                        symbol = EssentialSymbol.Calendar,
                        accent = EssentialYellow,
                        onClick = { onOpenFeature(FeatureRoute.Schedule) },
                    )
                }
                ProgressiveWidget(4) {
                    CategoryPanel(
                        title = "ファイル参照",
                        description = "画像・動画・音声の圧縮、変換、切り取り",
                        symbol = EssentialSymbol.Media,
                        accent = EssentialRed,
                        onClick = { onOpenFeature(FeatureRoute.Files) },
                    )
                }
                ProgressiveWidget(5) {
                    CategoryPanel(
                        title = "ミニゲーム",
                        description = "言葉遊び・マインスイーパー",
                        symbol = EssentialSymbol.Game,
                        accent = Color(0xFF9C6BFF),
                        onClick = { onOpenFeature(FeatureRoute.MiniGame) },
                    )
                }
                ProgressiveWidget(6) {
                    CategoryPanel(
                        title = "日課",
                        description = "デイリー・ウィークリー目標とポイントを管理",
                        symbol = EssentialSymbol.Routine,
                        accent = Color(0xFF27B99A),
                        onClick = { onOpenFeature(FeatureRoute.Routine) },
                    )
                }
                ProgressiveWidget(8) {
                    CategoryPanel(
                        title = "まんなか！",
                        description = "何人でも、最寄り駅から集合場所と遊びを探す",
                        symbol = EssentialSymbol.Spark,
                        accent = Color(0xFFFF90B5),
                        onClick = { onOpenFeature(FeatureRoute.Mannaka) },
                    )
                }
                ProgressiveWidget(9) {
                    CategoryPanel(
                        title = "通知ログ",
                        description = "届いた通知を秒単位で記録。keepして保存",
                        symbol = EssentialSymbol.Notification,
                        accent = Color(0xFF78D9EF),
                        onClick = { onOpenFeature(FeatureRoute.NotificationLog) },
                    )
                }
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
private fun ProfileScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    appIcon: AppIconOption,
    appLevel: AppLevel,
    initialSetupRequired: Boolean,
    onProfileIconChange: (String) -> Unit,
    onProfileImagePathChange: (String?) -> Unit,
    onAppIconChange: (AppIconOption) -> Unit,
    motionFps: Int,
    onMotionFpsChange: (Int) -> Unit,
    homeShortcut: FeatureRoute,
    onHomeShortcutChange: (FeatureRoute) -> Unit,
    contentBottomPadding: Dp,
) {
    val context = LocalContext.current
    val profileStore = remember { ProfileStore(context.applicationContext) }
    val progressStore = remember { AppProgressStore(context.applicationContext) }
    var profileName by remember { mutableStateOf(profileStore.loadName()) }
    var profileIcon by remember { mutableStateOf(profileStore.loadIcon()) }
    var profileImagePath by remember { mutableStateOf(profileStore.loadImagePath()) }
    var profileBannerPath by remember { mutableStateOf(profileStore.loadBannerPath()) }
    var claimedRewards by remember { mutableStateOf(progressStore.loadClaimedRewards()) }
    var rewardsExpanded by rememberSaveable { mutableStateOf(false) }
    var cacheClearInProgress by remember { mutableStateOf(false) }
    var cacheClearMessage by remember { mutableStateOf<String?>(null) }
    val settingsScope = rememberCoroutineScope()
    var panel by rememberSaveable {
        mutableStateOf(if (initialSetupRequired) ProfilePanel.Arrange else ProfilePanel.Main)
    }
    BackHandler(enabled = panel != ProfilePanel.Main) { panel = ProfilePanel.Main }
    val profileImagePicker = rememberLauncherForActivityResult(EssentialMediaPickerContract()) { uri ->
        if (uri != null) {
            settingsScope.launch {
                val path = withContext(Dispatchers.IO) { profileStore.saveImage(uri) }
                if (path != null) {
                    profileImagePath = path
                    onProfileImagePathChange(path)
                } else {
                    android.widget.Toast.makeText(context, "画像を保存できません。64MB以下の画像を選択してください。", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val profileBannerPicker = rememberLauncherForActivityResult(EssentialMediaPickerContract()) { uri ->
        if (uri != null) settingsScope.launch {
            val path = withContext(Dispatchers.IO) { profileStore.saveBanner(uri) }
            if (path != null) profileBannerPath = path
            else android.widget.Toast.makeText(context, "背景を保存できません。64MB以下の画像を選択してください。", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = panel == ProfilePanel.Settings,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                (slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260))) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically(tween(380)) { -it / 12 })
            } else {
                (fadeIn(tween(280)) + slideInVertically(tween(380)) { -it / 12 }) togetherWith
                    (slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)))
            }
        },
        label = "設定画面の開閉",
    ) { settingsVisible ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 28.dp + contentBottomPadding + NAVIGATION_CONTENT_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (settingsVisible) {
            progressiveItem(0, keyPrefix = "profile-settings") {
                ProfilePanelHeader(title = "設定", onBack = { panel = ProfilePanel.Main })
            }
        } else {
            val panelKey = "profile-main"
            progressiveItem(0, keyPrefix = panelKey) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(212.dp)) {
                        ProfileBanner(
                            imagePath = profileBannerPath,
                            modifier = Modifier.fillMaxWidth().height(166.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { profileBannerPicker.launch(arrayOf("image/*")) },
                        )
                        ProfileAvatar(
                            profileIcon = profileIcon,
                            imagePath = profileImagePath,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp).size(100.dp)
                                .clip(CircleShape)
                                .clickable { profileImagePicker.launch(arrayOf("image/*")) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profileName.ifBlank { "ゲスト" },
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("LV ${appLevel.level}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))) {
                                Box(Modifier.fillMaxWidth(appLevel.progress.coerceIn(0f, 1f)).fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, CircleShape))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileActionButton(
                            icon = EssentialSymbol.Edit,
                            label = "プロフィール編集",
                            modifier = Modifier.weight(1f),
                            onClick = { panel = ProfilePanel.Arrange },
                        )
                        ProfileActionButton(
                            icon = EssentialSymbol.Settings,
                            label = "設定",
                            modifier = Modifier.weight(1f),
                            onClick = { panel = ProfilePanel.Settings },
                        )
                    }
                }
            }
            if (!settingsVisible) {
                progressiveItem(1, keyPrefix = panelKey) {
                    ProfileRewards(
                        currentLevel = appLevel,
                        claimedRewards = claimedRewards,
                        expanded = rewardsExpanded,
                        onExpandedChange = { rewardsExpanded = it },
                        onClaim = { rewardLevel ->
                            if (appLevel.level >= rewardLevel && rewardLevel !in claimedRewards) {
                                claimedRewards = progressStore.claimReward(rewardLevel)
                            }
                        },
                    )
                }
            }
        }
        if (settingsVisible) {
        progressiveItem(1, keyPrefix = "profile-settings") {
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
        progressiveItem(2, keyPrefix = "profile-settings") {
            AppIconSettingsCard(
                selected = appIcon,
                currentLevel = appLevel.level,
                onSelect = onAppIconChange,
            )
        }
        progressiveItem(3, keyPrefix = "profile-settings") {
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
        progressiveItem(4, keyPrefix = "profile-settings") {
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
        progressiveItem(5, keyPrefix = "profile-settings") {
            YtDlpUpdateSettingsCard()
        }
        item(key = "profile-settings-app-updates") {
            ProgressiveWidget(6) { jp.essential.app.update.AppUpdateSettingsCard() }
        }
        progressiveItem(7, keyPrefix = "profile-settings") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(28.dp)
                    .padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                SettingValue("アプリ", "Essential ${BuildConfig.VERSION_NAME}")
                SettingValue("UI", "Material 3 Expressive")
                SettingValue("状態", "3機能を搭載")
            }
        }
        progressiveItem(8, keyPrefix = "profile-settings") {
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
        progressiveItem(9, keyPrefix = "profile-settings") {
            Column(
                modifier = Modifier.fillMaxWidth().glassSurface(28.dp).padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("キャッシュ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "一時ファイルを削除します。プロフィールや設定は残ります。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        cacheClearInProgress = true
                        cacheClearMessage = null
                        settingsScope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { StorageMaintenance.clearUserCache(context) }
                            }
                            cacheClearMessage = result.fold(
                                onSuccess = { cleared ->
                                    if (cleared.removedEntries == 0) "削除するキャッシュはありません"
                                    else "キャッシュを削除しました（${cleared.reclaimedBytes / 1024} KiB）"
                                },
                                onFailure = { "キャッシュを削除できませんでした" },
                            )
                            cacheClearInProgress = false
                        }
                    },
                    enabled = !cacheClearInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (cacheClearInProgress) "削除中..." else "キャッシュをクリア")
                }
                cacheClearMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
    }
    }
    AnimatedVisibility(
        visible = panel == ProfilePanel.Arrange,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f))
            .clickable { panel = ProfilePanel.Main })
    }
    AnimatedVisibility(
        visible = panel == ProfilePanel.Arrange,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentBottomPadding),
        enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
    ) {
        ProfileEditorPanel(
            profileName = profileName,
            onProfileNameChange = { value ->
                profileName = value.take(15)
                profileStore.saveName(profileName)
            },
            profileIcon = profileIcon,
            profileImagePath = profileImagePath,
            profileBannerPath = profileBannerPath,
            onChooseImage = { profileImagePicker.launch(arrayOf("image/*")) },
            onChooseBanner = { profileBannerPicker.launch(arrayOf("image/*")) },
            onClearBanner = {
                profileStore.clearBanner()
                profileBannerPath = null
            },
            onDone = { panel = ProfilePanel.Main },
            onClearImage = {
                profileStore.clearImage()
                profileImagePath = null
                onProfileImagePathChange(null)
            },
            onIconSelected = { icon ->
                profileIcon = icon
                profileStore.saveIcon(icon)
                profileStore.clearImage()
                profileImagePath = null
                onProfileIconChange(icon)
                onProfileImagePathChange(null)
            },
        )
    }
    }
}

@Composable
private fun ProfileActionButton(
    icon: EssentialSymbol,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "プロフィール操作ボタンの押下",
    )
    Row(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EssentialSymbol(icon, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.size(20.dp), label)
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ProfilePanelHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBackButton(onClick = onBack)
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ProfileEditorPanel(
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    profileIcon: String,
    profileImagePath: String?,
    profileBannerPath: String?,
    onChooseImage: () -> Unit,
    onChooseBanner: () -> Unit,
    onClearBanner: () -> Unit,
    onDone: () -> Unit,
    onClearImage: () -> Unit,
    onIconSelected: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.8f).imePadding()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(colors.surface.copy(alpha = 0.96f)).glassSurface(32.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
            .clip(CircleShape).background(colors.onSurface.copy(alpha = 0.2f)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PROFILE", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                Text("プロフィール編集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDone) { Text("閉じる") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(170.dp)) {
                ProfileBanner(profileBannerPath, Modifier.fillMaxWidth().height(125.dp)
                    .clip(RoundedCornerShape(24.dp)).clickable(onClick = onChooseBanner))
                ProfileAvatar(profileIcon, profileImagePath, Modifier.align(Alignment.BottomStart)
                    .padding(start = 14.dp).size(84.dp).clip(CircleShape)
                    .border(4.dp, colors.surface, CircleShape).clickable(onClick = onChooseImage))
                Text("背景をタップして変更", Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedTextField(
                value = profileName, onValueChange = onProfileNameChange,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                label = { Text("表示名") }, supportingText = { Text("${profileName.length} / 15") }, singleLine = true,
            )
            Text("アイコン", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onChooseImage) { Text("写真から選ぶ") }
                if (profileImagePath != null) TextButton(onClick = onClearImage) { Text("絵文字に戻す") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🌱", "🌸", "🔥", "🌙", "⚡", "🧊", "🌹").forEach { icon ->
                    item(key = icon) {
                        val selected = icon == profileIcon && profileImagePath == null
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.primaryContainer else colors.onSurface.copy(alpha = 0.06f))
                            .border(1.dp, if (selected) colors.primary else colors.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .clickable { onIconSelected(icon) }, contentAlignment = Alignment.Center) {
                            Text(icon, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            if (profileBannerPath != null) TextButton(onClick = onClearBanner) { Text("背景を標準に戻す") }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp)) {
            Text("完了", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileBanner(imagePath: String?, modifier: Modifier = Modifier) {
    val drawable by produceState<android.graphics.drawable.Drawable?>(null, imagePath) {
        value = imagePath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 28) {
                        ImageDecoder.decodeDrawable(ImageDecoder.createSource(java.io.File(path))) { decoder, info, _ ->
                            // 高解像度の静止画・GIFを背景表示に必要な寸法へ抑え、復帰時のメモリ負荷を減らす。
                            val longest = maxOf(info.size.width, info.size.height)
                            if (longest > 1920) {
                                val scale = 1920f / longest
                                decoder.setTargetSize(
                                    (info.size.width * scale).toInt().coerceAtLeast(1),
                                    (info.size.height * scale).toInt().coerceAtLeast(1),
                                )
                            }
                        }
                    } else {
                        BitmapFactory.decodeFile(path)?.let {
                            android.graphics.drawable.BitmapDrawable(android.content.res.Resources.getSystem(), it)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    DisposableEffect(drawable) {
        onDispose { if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.stop() }
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFF1D8F91), Color(0xFF4779C1), Color(0xFF7559B4)),
            ),
        ),
    ) {
        if (drawable != null) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { imageView ->
                    if (imageView.drawable !== drawable) imageView.setImageDrawable(drawable)
                    if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.start()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    profileIcon: String,
    imagePath: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = imagePath) {
        value = imagePath?.let { path ->
            withContext(Dispatchers.IO) { decodeProfileAvatar(path) }
        }
    }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = 9.dp.toPx()
                shape = CircleShape
                clip = false
            }
            .background(
                Brush.sweepGradient(
                    listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary),
                ),
                CircleShape,
            )
            .padding(1.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val iconFontSize = (minOf(maxWidth, maxHeight).value * 0.60f).coerceIn(16f, 54f).sp
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "プロフィール画像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(profileIcon, fontSize = iconFontSize, maxLines = 1)
        }
    }
}

private fun decodeProfileAvatar(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) sampleSize *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null
    val rotation = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0)
    if (rotation == 0) return bitmap
    val rotated = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(rotation.toFloat()) },
        true,
    )
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

@Composable
private fun ProfileLevelCard(totalXp: Int, level: AppLevel) {
    val shape = RoundedCornerShape(36.dp, 36.dp, 18.dp, 36.dp)
    Box(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xCC21B79B), Color(0xCC5B8DFF), Color(0xAA8D6AFF))))
            .border(1.dp, Color.White.copy(alpha = 0.56f), shape)
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(70.dp).clip(RoundedCornerShape(25.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text("LV\n${level.level}", color = Color.White, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("アプリXP $totalXp", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(
                        if (level.level == 60) "MAX LEVEL" else "次のレベルまで ${level.pointsForNextLevel - level.pointsInLevel} XP",
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    if (level.level < 60) {
                        Text("必要XP ${level.pointsForNextLevel}", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            EssentialBubblyProgressBar(progress = level.progress, modifier = Modifier.fillMaxWidth())
            Text("日課とミニゲームのXPが、アプリレベルへ合算されます。", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileRewards(
    currentLevel: AppLevel,
    claimedRewards: Set<Int>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClaim: (Int) -> Unit,
) {
    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onExpandedChange(!expanded) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("レベル報酬", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(if (expanded) "プロフィールでのみ確認・受取できます" else "タップして報酬一覧を表示", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("${claimedRewards.size}/${appRewards.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(420, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280)),
            exit = shrinkVertically(animationSpec = tween(360, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(220)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                appRewards.forEach { reward ->
                    ProfileRewardCard(
                        reward = reward,
                        unlocked = currentLevel.level >= reward.level,
                        claimed = reward.level in claimedRewards,
                        onClaim = { onClaim(reward.level) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRewardCard(
    reward: AppReward,
    unlocked: Boolean,
    claimed: Boolean,
    onClaim: () -> Unit,
) {
    val shape = RoundedCornerShape(25.dp, 25.dp, 13.dp, 25.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (unlocked) MaterialTheme.colorScheme.surface.copy(alpha = 0.78f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
            .border(1.dp, if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.28f), shape)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(reward.imageRes),
                contentDescription = reward.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(reward.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(reward.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("LV${reward.level}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        }
        when {
            claimed -> Text("✓ 獲得済み", color = Color(0xFF168B74), fontWeight = FontWeight.Black)
            unlocked -> Button(onClick = onClaim, modifier = Modifier.fillMaxWidth()) { Text("報酬を受け取る") }
            else -> Text("LV${reward.level}で解放", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
    profileIcon: String,
    profileImagePath: String?,
    onSelected: (Destination) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalEssentialDark.current
    val glassShape = RoundedCornerShape(32.dp)
    val selectionPosition by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "ガラスの選択位置",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 8.dp)
            .height(66.dp)
            .graphicsLayer {
                shadowElevation = 20.dp.toPx()
                shape = glassShape
                clip = false
            }
            .clip(glassShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (dark) Color(0xFF17243D).copy(alpha = 0.48f) else Color.White.copy(alpha = 0.48f),
                        if (dark) Color(0xFF0C172D).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.58f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color(0xFFA9C6E4).copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.24f),
                    ),
                ),
                shape = glassShape,
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.34f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = if (dark) 0.12f else 0f),
                        1f to Color.Black.copy(alpha = if (dark) 0.24f else 0f),
                    ),
                    startY = size.height * 0.12f,
                    endY = size.height,
                ),
                topLeft = Offset.Zero,
                size = size,
            )
            val cellWidth = size.width / Destination.entries.size
            val selectionInset = 4.dp.toPx()
            val width = cellWidth - selectionInset * 2f
            val height = 58.dp.toPx()
            val left = cellWidth * selectionPosition + selectionInset
            val top = (size.height - height) / 2f
            val radius = androidx.compose.ui.geometry.CornerRadius(height / 2f)
            drawRoundRect(
                color = Color.Black.copy(alpha = if (dark) 0.16f else 0.08f),
                topLeft = Offset(left, top + 2.dp.toPx()),
                size = Size(width, height),
                cornerRadius = radius,
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        if (dark) Color(0xFFB9DFFF).copy(alpha = 0.18f) else Color(0xFFE8E8E8).copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.16f),
                        if (dark) Color(0xFFD8ECFF).copy(alpha = 0.14f) else Color(0xFFF3F3F3).copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.20f),
                    ),
                    start = Offset(left, top),
                    end = Offset(left + width, top + height),
                ),
                topLeft = Offset(left, top), size = Size(width, height), cornerRadius = radius,
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        if (dark) Color(0xFF6EBBFF).copy(alpha = 0.48f) else Color(0xFFBDBDBD),
                        Color.White.copy(alpha = 0.34f),
                        if (dark) Color(0xFFAED8FF).copy(alpha = 0.42f) else Color(0xFFBDBDBD),
                    ),
                    startX = left,
                    endX = left + width,
                ),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = radius,
                style = Stroke(width = 1.1.dp.toPx()),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
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
                    modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(24.dp))
                        .selectable(selected = isSelected, role = Role.Tab, interactionSource = interaction, indication = null) {
                        if (!isSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelected(destination)
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        BadgedBox(
                            badge = {
                                if (destination == Destination.Features) {
                                    Badge(containerColor = EssentialOrange)
                                }
                            },
                        ) {
                            if (destination == Destination.Profile) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer(scaleX = scale, scaleY = scale)
                                        .semantics { contentDescription = destination.label },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ProfileAvatar(
                                        profileIcon = profileIcon,
                                        imagePath = profileImagePath,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                val iconTint = if (!dark) {
                                    if (isSelected) Color(0xFF606060) else Color(0xFF8A8A8A)
                                } else if (isSelected) Color(0xFF9BCBFF) else colors.onSurface.copy(alpha = 0.96f)
                                EssentialSymbol(
                                    symbol = destination.symbol,
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(if (isSelected) 34.dp else 33.dp)
                                        .graphicsLayer(scaleX = scale, scaleY = scale),
                                    description = destination.label,
                                )
                            }
                        }
                    }
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
    val vectorResourceId = when (symbol) {
        EssentialSymbol.Home -> R.drawable.ic_home_outline
        EssentialSymbol.Download -> R.drawable.ic_download_outline
        else -> null
    }
    if (vectorResourceId != null) {
        Image(
            painter = painterResource(vectorResourceId),
            contentDescription = null,
            modifier = semanticsModifier,
            colorFilter = ColorFilter.tint(tint.copy(alpha = 1f)),
        )
        return
    }

    Canvas(modifier = semanticsModifier) {
        val stroke = Stroke(width = size.minDimension * 0.095f, cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        when (symbol) {
            EssentialSymbol.Home,
            EssentialSymbol.Download -> Unit
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
            EssentialSymbol.Edit -> {
                val path = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.76f)
                    lineTo(size.width * 0.28f, size.height * 0.56f)
                    lineTo(size.width * 0.68f, size.height * 0.16f)
                    lineTo(size.width * 0.84f, size.height * 0.32f)
                    lineTo(size.width * 0.44f, size.height * 0.72f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(
                    tint,
                    Offset(size.width * 0.22f, size.height * 0.76f),
                    Offset(size.width * 0.42f, size.height * 0.70f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            EssentialSymbol.Profile -> {
                drawCircle(tint, size.minDimension * 0.19f, Offset(center.x, size.height * 0.34f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.20f, size.height * 0.40f),
                    size = Size(size.width * 0.60f, size.height * 0.50f),
                    style = stroke,
                )
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
            EssentialSymbol.Notification -> drawNotificationLogLogo(tint, stroke)
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
            EssentialSymbol.TextScan -> {
                val edge = size.width * 0.13f
                drawRoundRect(tint, Offset(edge, edge), Size(size.width - edge * 2, size.height - edge * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f), style = stroke)
                drawLine(tint, Offset(size.width * 0.3f, size.height * 0.32f), Offset(size.width * 0.7f, size.height * 0.32f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.32f), Offset(size.width * 0.5f, size.height * 0.7f), stroke.width, StrokeCap.Round)
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
    FeatureRoute.NotificationLog -> "通知ログ"
    FeatureRoute.Mannaka -> "まんなか！"
    FeatureRoute.Downloader -> "ダウンローダー"
    FeatureRoute.Scanner -> "スキャナー"
    FeatureRoute.Schedule -> "行程表ジェネレーター"
    FeatureRoute.Files -> "ファイル参照"
    FeatureRoute.MiniGame -> "ミニゲーム"
    FeatureRoute.Routine -> "日課"
}

private fun FeatureRoute.symbol(): EssentialSymbol = when (this) {
    FeatureRoute.NotificationLog -> EssentialSymbol.Notification
    FeatureRoute.Mannaka -> EssentialSymbol.Spark
    FeatureRoute.Downloader -> EssentialSymbol.Download
    FeatureRoute.Scanner -> EssentialSymbol.Qr
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
                contentBottomPadding = 0.dp,
            )
        }
    }
}
