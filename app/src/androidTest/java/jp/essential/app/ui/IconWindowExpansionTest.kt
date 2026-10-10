package jp.essential.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 開閉途中で目標を反転しても画面のサイズが飛ばず、最後に起点へ戻ることを確認する。 */
class IconWindowExpansionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun expansionCanReverseAndClose() {
        var opened by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                IconWindowContent(if (opened) true else null, Modifier.fillMaxSize(),
                    icon = { Canvas(Modifier.fillMaxSize()) { drawCircle(Color.Red) } }, contentTopInset = 32.dp) { visible ->
                    if (visible == null) {
                        val (origin, click) = rememberWindowExpansionClick { opened = true }
                        Box(Modifier.fillMaxSize()) {
                            Button(onClick = click, modifier = Modifier.padding(24.dp).size(100.dp, 56.dp).then(origin)) { Text("起点") }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().testTag("expanded-window")) { Text("詳細") }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("起点").performClick()
        compose.mainClock.advanceTimeBy(96)
        assertRoundIconVisible()
        val openingWidth = compose.onNodeWithTag("expanded-window").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { opened = false }
        compose.mainClock.advanceTimeByFrame()
        val reversedWidth = compose.onNodeWithTag("expanded-window").fetchSemanticsNode().boundsInRoot.width
        assertTrue(kotlin.math.abs(reversedWidth - openingWidth) < 100f)
        compose.runOnIdle { opened = true }
        compose.mainClock.advanceTimeBy(1000)
        val fullWidth = compose.onNodeWithTag("expanded-window").fetchSemanticsNode().boundsInRoot.width
        assertTrue(fullWidth > openingWidth)
        val surface = compose.onNodeWithTag("icon-window-surface").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("expanded-window").fetchSemanticsNode().boundsInRoot
        assertTrue("ガラス面は画面上端まで届く", kotlin.math.abs(surface.top - root.top) < 1f)
        assertTrue("操作部分は安全領域を確保する", body.top > surface.top)
        compose.runOnIdle { opened = false }
        compose.mainClock.advanceTimeBy(96)
        assertRoundIconVisible()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("expanded-window").assertDoesNotExist()
        compose.onNodeWithText("起点").assertExists()
    }
    @Test fun capturedIconKeepsItsColorAndBackgroundRemainsTransparent() {
        var opened by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.Blue)) {
                    IconWindowContent(if (opened) true else null, icon = {
                        Canvas(Modifier.fillMaxSize()) { drawCircle(Color.Red) }
                    }) { visible ->
                        if (visible == null) {
                            val source = remember { WindowExpansionIconSource() }
                            val (origin, click) = rememberWindowExpansionClick({ opened = true }, source)
                            CompositionLocalProvider(LocalWindowExpansionIconSource provides source) {
                                Button(onClick = click, modifier = Modifier.padding(24.dp).size(150.dp, 80.dp).then(origin)) {
                                    Canvas(Modifier.size(24.dp).windowExpansionIcon()) { drawCircle(Color.Green) }
                                    Text("色の起点")
                                }
                            }
                        } else Box(Modifier.fillMaxSize().testTag("transparent-detail"))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("色の起点").performClick()
        compose.mainClock.advanceTimeBy(80)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var green = 0
        var red = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.green > .6f && color.red < .4f && color.blue < .4f) green++
            if (color.red > .6f && color.green < .4f && color.blue < .4f) red++
        }
        assertTrue("元の緑のアイコンで遷移する", green > 20)
        assertTrue("別の赤いアイコンに切り替わらない", red == 0)
        compose.mainClock.advanceTimeBy(1000)
        val full = compose.onRoot().captureToImage().toPixelMap()
        val center = full[full.width / 2, full.height / 2]
        assertTrue("開いた画面でも背面の青が透ける", center.blue > .8f && center.red < .25f)
        compose.runOnIdle { opened = false }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("色の起点").assertExists()
    }

    /** テスト用の赤い円から、遷移中の表示と縦横比補正を画素で確認する。 */
    private fun assertRoundIconVisible() {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var left = pixels.width
        var right = -1
        var top = pixels.height
        var bottom = -1
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.red > 0.65f && color.green < 0.45f && color.blue < 0.45f) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("遷移中のアイコンが表示される", right > left && bottom > top)
        assertTrue("縦横比が維持される", kotlin.math.abs((right - left) - (bottom - top)) <= 3)
    }

}
