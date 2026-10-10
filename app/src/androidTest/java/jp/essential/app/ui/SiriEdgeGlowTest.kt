package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 光の実描画、時間変化、終了と操作透過を検証する。 */
class SiriEdgeGlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun shaderFlowsAndStopsWithoutCoveringCenter() {
        if (android.os.Build.VERSION.SDK_INT >= 33) android.graphics.RuntimeShader(EDGE_GLOW_SHADER)
        verifyRendering(false)
    }
    @Test fun canvasFallbackFlowsAndStopsWithoutCoveringCenter() = verifyRendering(true)

    private fun verifyRendering(fallback: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        val active = androidx.compose.runtime.mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        try {
            scenario.onActivity { activity -> activity.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    SiriEdgeGlow(active.value, forceCanvas = fallback)
                }
            } }
            compose.mainClock.advanceTimeBy(1200)
            compose.waitForIdle()
            val first = compose.onNodeWithTag("settings-edge-glow").captureToImage().toPixelMap()
            assertTrue("中央のコンテンツ領域を着色しない", first[first.width / 2, first.height / 2].red < .01f)
            val x = 2
            val y = first.height / 2
            val before = first[x, y]
            assertTrue("縁に明るい光を描く", maxOf(before.red, before.green, before.blue) > .15f)
            compose.mainClock.advanceTimeBy(2300)
            compose.waitForIdle()
            val second = compose.onNodeWithTag("settings-edge-glow").captureToImage().toPixelMap()[x, y]
            assertTrue("時間に応じて光の色が変わる", kotlin.math.abs(before.red - second.red) + kotlin.math.abs(before.green - second.green) + kotlin.math.abs(before.blue - second.blue) > .015f)
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), if (fallback) "edge-glow-canvas.png" else "edge-glow-shader.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            compose.runOnIdle { active.value = false }
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            val stopped = compose.onNodeWithTag("settings-edge-glow").captureToImage().toPixelMap()[x, y]
            assertTrue("終了時は光が消える", maxOf(stopped.red, stopped.green, stopped.blue) < .01f)
        } finally { scenario.close(); compose.mainClock.autoAdvance = true }
    }

    @Test fun previewToggleRemainsClickableUnderGlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent { EdgeGlowPreview() } }
            compose.onNodeWithText("光を停止").performClick()
            compose.onNodeWithText("光を起動").assertExists().performClick()
            compose.onNodeWithText("光を停止").assertExists()
        } finally { scenario.close() }
    }
}
