package jp.essential.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import java.security.KeyStore
import jp.essential.app.ui.EssentialRoot
import jp.essential.app.update.GitHubUpdateRepository

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf<String?>(null)
    private val requestedFeature = mutableStateOf<String?>(null)
    private val motionFps = mutableStateOf(60)
    private val homeShortcut = mutableStateOf(FEATURE_DOWNLOADER)
    private var appIconOption: AppIconOption = AppIconManager.defaultLight

    private fun applyMotionFps(value: Int) {
        val fps = value.takeIf { it in listOf(30, 60, 120) } ?: 60
        motionFps.value = fps
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("motion_fps", fps).apply()
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val currentMode = display.mode
        val supportedModes = display.supportedModes
        // 解像度を変えず、端末が実際に持つ表示モードから希望fpsに最も近いものを選ぶ。
        val sameResolutionModes = supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }
        val preferredMode = sameResolutionModes
            .ifEmpty { supportedModes.toList() }
            .minByOrNull { kotlin.math.abs(it.refreshRate - fps.toFloat()) }
        val requested = preferredMode?.refreshRate ?: fps.toFloat()
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = preferredMode?.modeId ?: 0
            preferredRefreshRate = requested
        }
    }

    private fun applyDarkAppearance(dark: Boolean) {
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark_theme", dark).apply()
        val savedOption = AppIconManager.load(this)
        val isDefaultIcon = savedOption.id == AppIconManager.defaultLight.id || savedOption.id == AppIconManager.defaultDark.id
        appIconOption = if (isDefaultIcon) {
            AppIconManager.optionForId(if (dark) "default_dark" else "default_light")
        } else {
            savedOption
        }
        AppIconManager.apply(this, appIconOption)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        applyWindowBackground(appIconOption)
    }

    private fun applyAppIcon(styleId: String) {
        val option = AppIconManager.optionForId(styleId)
        appIconOption = option
        AppIconManager.apply(this, option)
        applyWindowBackground(option)
    }

    private fun applyWindowBackground(option: AppIconOption) {
        window.setBackgroundDrawable(android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(option.backgroundStart, option.backgroundEnd),
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanupRetiredClassiData()
        // 更新によるプロセス終了で結果通知が戻らない場合も、旧APKと.partを次回起動で回収する。
        GitHubUpdateRepository(applicationContext).cleanupAfterAppStart()
        enableEdgeToEdge()
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        homeShortcut.value = preferences.getString("home_shortcut_feature", FEATURE_DOWNLOADER) ?: FEATURE_DOWNLOADER
        val initialDark = preferences.getBoolean("dark_theme", resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        // 初回Compose描画とクロスフェードの下地を、保存済みテーマに合わせる。
        appIconOption = AppIconManager.load(this)
        AppIconManager.apply(this, appIconOption)
        applyWindowBackground(appIconOption)
        applyMotionFps(preferences.getInt("motion_fps", 60))
        handleIntent(intent)
        val firstSetup = !preferences.getBoolean("home_shortcut_configured", false) &&
            sharedUrl.value.isNullOrBlank() && requestedFeature.value.isNullOrBlank()
        setContent {
            EssentialRoot(
                sharedUrl = sharedUrl.value,
                requestedFeature = requestedFeature.value,
                initialDarkTheme = initialDark,
                onDarkThemeApplied = ::applyDarkAppearance,
                initialAppIconId = appIconOption.id,
                onAppIconChange = ::applyAppIcon,
                motionFps = motionFps.value,
                onMotionFpsChange = ::applyMotionFps,
                initialHomeShortcut = homeShortcut.value,
                initialSetupRequired = firstSetup,
                onHomeShortcutChange = { route ->
                    homeShortcut.value = route
                    preferences.edit().putString("home_shortcut_feature", route)
                        .putBoolean("home_shortcut_configured", true).apply()
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        requestedFeature.value = intent.getStringExtra(EXTRA_OPEN_FEATURE)
        if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            requestedFeature.value = FEATURE_QR_SCANNER
        }
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            sharedUrl.value = extractFirstHttpUrl(sharedText)
            if (sharedUrl.value != null) {
                requestedFeature.value = FEATURE_DOWNLOADER
            }
        }
    }

    private fun extractFirstHttpUrl(text: String): String? =
        URL_PATTERN.find(text)?.value

    /** 廃止したClassi機能の秘密情報、Cookie、ブラウザキャッシュだけを端末から回収する。 */
    private fun cleanupRetiredClassiData() {
        deleteSharedPreferences("classi_secure_credentials")
        listOf(
            filesDir.resolve("mozilla"),
            cacheDir.resolve("gecko_temp"),
        ).forEach { target ->
            runCatching { target.deleteRecursively() }
        }
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias("essential_classi_credentials")) {
                keyStore.deleteEntry("essential_classi_credentials")
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_FEATURE = "open_feature"
        const val FEATURE_DOWNLOADER = "downloader"
        const val FEATURE_QR_SCANNER = "qr_scanner"
        const val FEATURE_SCHEDULE = "schedule"
        const val FEATURE_FILES = "files"

        private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
