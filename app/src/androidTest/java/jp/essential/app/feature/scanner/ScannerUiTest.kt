package jp.essential.app.feature.scanner

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
        capture("scanner-qr-ui.png")
        compose.onNodeWithTag("scanner-text-mode").performClick()
        compose.onNodeWithTag("scanner-auto").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Auto 使用不可").assertExists()
        compose.onNodeWithTag("scanner-text-mode").assertIsSelected()
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
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("scanner-text-mode").performClick()
            compose.mainClock.advanceTimeBy(96)
            compose.onNodeWithText("スキャナー", substring = false).assertIsDisplayed()
            val frame = compose.onNodeWithTag("scanner-qr-frame").fetchSemanticsNode().config[ScannerMotionProgress]
            assertTrue("QR枠が徐々に消える", frame > 0f && frame < 1f)
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
