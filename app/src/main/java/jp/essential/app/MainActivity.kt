package jp.essential.app

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import jp.essential.app.ui.EssentialRoot

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf<String?>(null)
    private val requestedFeature = mutableStateOf<String?>(null)
    private val motionFps = mutableStateOf(60)
    private val homeShortcut = mutableStateOf(FEATURE_DOWNLOADER)

    private fun applyMotionFps(value: Int) {
        val fps = value.takeIf { it in listOf(30, 60, 120) } ?: 60
        motionFps.value = fps
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("motion_fps", fps).apply()
        @Suppress("DEPRECATION")
        val rates = windowManager.defaultDisplay.supportedModes.map { it.refreshRate }
        // 古いAndroidでは対応Hzのみ指定する。新しいAndroidへは希望fpsを直接伝える。
        val requested = if (Build.VERSION.SDK_INT >= 34) fps.toFloat()
            else rates.minByOrNull { kotlin.math.abs(it - fps) } ?: 60f
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = 0
            preferredRefreshRate = requested
        }
    }

    private fun applyDarkAppearance(dark: Boolean) {
        getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark_theme", dark).apply()
        val light = ComponentName(this, "$packageName.LightLauncher")
        val night = ComponentName(this, "$packageName.DarkLauncher")
        val enabled = if (dark) night else light
        val disabled = if (dark) light else night
        val manager = packageManager
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        if (manager.getComponentEnabledSetting(enabled) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            manager.getComponentEnabledSetting(disabled) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(enabled, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(disabled, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP),
            ))
        } else {
            // 起動口が一時的にゼロにならないよう新しいアイコンを先に有効化する。
            manager.setComponentEnabledSetting(enabled, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            manager.setComponentEnabledSetting(disabled, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        homeShortcut.value = preferences.getString("home_shortcut_feature", FEATURE_DOWNLOADER) ?: FEATURE_DOWNLOADER
        val initialDark = preferences.getBoolean("dark_theme", resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        // 初回Compose描画とクロスフェードの下地を、保存済みテーマに合わせる。
        window.setBackgroundDrawable(android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            if (initialDark) intArrayOf(0xFF100B23.toInt(), 0xFF49307C.toInt())
            else intArrayOf(0xFFFFF9EB.toInt(), 0xFFD9F894.toInt()),
        ))
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

    companion object {
        const val EXTRA_OPEN_FEATURE = "open_feature"
        const val FEATURE_DOWNLOADER = "downloader"
        const val FEATURE_QR_SCANNER = "qr_scanner"
        const val FEATURE_SCHEDULE = "schedule"
        const val FEATURE_FILES = "files"

        private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
