package jp.essential.app.feature.scanner

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ScannerUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var originalMode: Boolean? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepare() {
        val prefs = context.getSharedPreferences("scanner", Context.MODE_PRIVATE)
        originalMode = if (prefs.contains("text_mode")) prefs.getBoolean("text_mode", false) else null
        prefs.edit().putBoolean("text_mode", false).commit()
    }
    @After fun restore() {
        scenario?.close()
        context.getSharedPreferences("scanner", Context.MODE_PRIVATE).edit().apply {
            originalMode?.let { putBoolean("text_mode", it) } ?: remove("text_mode")
        }.commit()
    }
    private fun launch(id: String = "scanner") {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_FEATURE, id))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("scanner-shutter").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    /** カメラ映像と別に、操作UIがシステムバーとカットアウトを避けているか確認する。 */
    private fun assertSafeControls() {
        var topInset = 0
        var bottomInset = 0
        scenario?.onActivity { activity ->
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            topInset = insets?.top ?: 0
            bottomInset = insets?.bottom ?: 0
        }
        val header = compose.onNodeWithTag("scanner-top-controls").fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithTag("scanner-qr-mode").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val statusHeight = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val expectedTop = maxOf(topInset, if (statusHeight != 0) context.resources.getDimensionPixelSize(statusHeight) else 0, (32 * context.resources.displayMetrics.density).toInt())
        assertTrue("上部操作がカメラ下に収まる", header.top >= expectedTop)
        assertTrue("下部操作がナビゲーション領域を避ける", mode.bottom <= root.bottom - bottomInset)
    }

    @Test fun qrShadeReachesBothScreenEdges() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        scenario?.onActivity { activity -> activity.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                jp.essential.app.feature.qr.ScannerOverlay()
            }
        } }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertTrue("上端まで影が連続する", pixels[pixels.width / 2, 2].red < .8f)
        assertTrue("下端まで影が連続する", pixels[pixels.width / 2, pixels.height - 3].red < .8f)
        assertTrue("読み取り枠の内側は暗くしない", pixels[pixels.width / 2, pixels.height / 2].red > .95f)
    }

    @Test fun modesRememberLastSelectionAndKeepCameraControlLayout() {
        launch()
        compose.onNodeWithTag("scanner-qr-mode").assertIsSelected()
        compose.onNodeWithTag("scanner-auto").assertExists()
        val zoom = compose.onNodeWithTag("scanner-zoom-presets").fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithTag("scanner-shutter").fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithTag("scanner-qr-mode").fetchSemanticsNode().boundsInRoot
        assertTrue(zoom.bottom <= shutter.top)
        assertTrue(shutter.bottom <= mode.top)
        compose.onNodeWithContentDescription("写真を選ぶ").assertExists()
        assertSafeControls()
        capture("scanner-qr-ui.png")
        compose.onNodeWithTag("scanner-text-mode").performClick()
        compose.onNodeWithTag("scanner-auto").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Auto 使用不可").assertExists()
        compose.onNodeWithTag("scanner-text-mode").assertIsSelected()
        assertSafeControls()
        capture("scanner-text-ui.png")
        scenario?.close()
        launch()
        compose.onNodeWithTag("scanner-text-mode").assertIsSelected()
        compose.onNodeWithTag("scanner-auto").assertIsNotEnabled()
        compose.onNodeWithTag("scanner-qr-mode").performClick()
        compose.onNodeWithTag("scanner-qr-mode").assertIsSelected()
    }

    @Test fun legacyEntryAlsoOpensLastMode() {
        context.getSharedPreferences("scanner", Context.MODE_PRIVATE).edit().putBoolean("text_mode", true).commit()
        launch("qr_scanner")
        compose.onNodeWithTag("scanner-text-mode").assertIsSelected()
        scenario?.close()
        launch("text_scan")
        compose.onNodeWithTag("scanner-text-mode").assertIsSelected()
    }

    @Test fun qrShutterIsDisabledAndTextShutterBecomesAvailable() {
        launch()
        compose.onNodeWithTag("scanner-shutter").assertIsNotEnabled()
        compose.onNodeWithTag("scanner-shutter-disabled-mark", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("scanner-text-mode").performClick()
        compose.waitUntil(15000) {
            compose.onNodeWithTag("scanner-shutter").fetchSemanticsNode().config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == null
        }
        compose.onNodeWithTag("scanner-shutter").assertIsEnabled()
        compose.onNodeWithTag("scanner-shutter-disabled-mark", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("scanner-qr-mode").performClick()
        compose.onNodeWithTag("scanner-shutter").assertIsNotEnabled()
    }
    @Test fun modeElementsAnimateTogetherWithoutScreenReload() {
        launch()
        val originalBounds = compose.onNodeWithTag("scanner-qr-frame").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("scanner-text-mode").performClick()
            compose.mainClock.advanceTimeBy(96)
            compose.onNodeWithText("スキャナー", substring = false).assertIsDisplayed()
            val frame = compose.onNodeWithTag("scanner-qr-frame").fetchSemanticsNode().config[ScannerMotionProgress]
            assertTrue("QR枠が徐々に消える", frame > 0f && frame < 1f)
            assertEquals("モードを切り替えても影の全画面座標を維持", originalBounds, compose.onNodeWithTag("scanner-qr-frame").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("scanner-qr-mode").performClick()
            compose.mainClock.advanceTimeBy(32)
            val reversed = compose.onNodeWithTag("scanner-qr-frame").fetchSemanticsNode().config[ScannerMotionProgress]
            assertTrue("途中反転で影が瞬間的に全表示されない", reversed > 0f && reversed < 1f)
            compose.onNodeWithTag("scanner-text-mode").performClick()
            compose.onNodeWithTag("scanner-shutter-disabled-mark", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("scanner-auto-disabled-mark", useUnmergedTree = true).assertExists()
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithTag("scanner-shutter-disabled-mark", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("scanner-auto").assertIsNotEnabled()
            compose.onNodeWithTag("scanner-qr-mode").performClick()
            compose.mainClock.advanceTimeBy(96)
            compose.onNodeWithText("スキャナー", substring = false).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithTag("scanner-shutter").assertIsNotEnabled()
            compose.onNodeWithTag("scanner-auto").assertIsEnabled()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

}
