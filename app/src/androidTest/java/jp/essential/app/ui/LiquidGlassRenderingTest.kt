package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 背景の輪郭だけが柔らかくなり、前景は鮮明なままであることを実描画で確認する。 */
class LiquidGlassRenderingTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun bottomPullKeepsGlassBackdropFixed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                GlassBackdropScope(Modifier.fillMaxSize(), background = {
                    Canvas(Modifier.fillMaxSize()) {
                        val band = 18.dp.toPx()
                        repeat((size.height / band).toInt() + 1) { index ->
                            drawRect(if (index % 2 == 0) Color(0xFF243A65) else Color(0xFFC6D6F2),
                                Offset(0f, index * band), Size(size.width, band))
                        }
                    }
                }) {
                    LazyColumn(modifier = Modifier.fillMaxSize().elasticListEdges().testTag("edge-list"),
                        overscrollEffect = null, contentPadding = PaddingValues(bottom = 60.dp)) {
                        items(12) { Spacer(Modifier.height(100.dp)) }
                        item { Box(Modifier.padding(start = 40.dp).size(220.dp, 240.dp)
                            .liquidGlass(20.dp).testTag("edge-glass")) }
                    }
                }
            } }
            val list = compose.onNodeWithTag("edge-list")
            list.performScrollToIndex(12)
            compose.waitForIdle()
            val card = compose.onNodeWithTag("edge-glass")
            val beforeBounds = card.fetchSemanticsNode().boundsInRoot
            val before = compose.onRoot().captureToImage().toPixelMap()
            list.performTouchInput {
                down(Offset(width * .5f, height * .75f))
                moveTo(Offset(width * .5f, height * .35f), delayMillis = 300)
            }
            compose.waitForIdle()
            val heldBounds = card.fetchSemanticsNode().boundsInRoot
            assertTrue("下端でカードは伸縮する", kotlin.math.abs(beforeBounds.top - heldBounds.top) > 2f)
            val heldImage = compose.onRoot().captureToImage()
            val held = heldImage.toPixelMap()
            var difference = 0f
            var count = 0
            for (y in (maxOf(beforeBounds.top, heldBounds.top) + 35).toInt()
                until (minOf(beforeBounds.bottom, heldBounds.bottom) - 35).toInt() step 4) {
                for (x in (beforeBounds.left + 35).toInt() until (beforeBounds.right - 35).toInt() step 8) {
                    difference += kotlin.math.abs(before[x, y].red - held[x, y].red)
                    count++
                }
            }
            assertTrue("引っ張り中も背景は固定される: ${difference / count}", count > 0 && difference / count < .035f)
            java.io.File(context.getExternalFilesDir(null), "glass-bottom-pull-held.png").outputStream().use {
                heldImage.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            list.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1500)
            compose.waitForIdle()
            assertTrue("離すと元の位置へ戻る",
                kotlin.math.abs(beforeBounds.top - card.fetchSemanticsNode().boundsInRoot.top) < 1f)
        } finally {
            runCatching { compose.onNodeWithTag("edge-list").performTouchInput { up() } }
            scenario.close()
        }
    }

    @Test fun stretchingCardKeepsBackdropAtSameScreenPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        val stretched = mutableStateOf(false)
        try {
            scenario.onActivity { activity -> activity.setContent {
                GlassBackdropScope(Modifier.fillMaxSize(), background = {
                    Canvas(Modifier.fillMaxSize()) {
                        val band = 18.dp.toPx()
                        repeat((size.height / band).toInt() + 1) { index ->
                            drawRect(if (index % 2 == 0) Color(0xFF243A65) else Color(0xFFC6D6F2),
                                Offset(0f, index * band), Size(size.width, band))
                        }
                    }
                }) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleY = if (stretched.value) 1.06f else 1f
                        translationY = if (stretched.value) -24.dp.toPx() else 0f
                        transformOrigin = TransformOrigin(.5f, 1f)
                    }) {
                        Box(Modifier.padding(start = 40.dp, top = 160.dp)) {
                            Box(Modifier.size(220.dp, 240.dp).liquidGlass(20.dp).testTag("fixed-glass-background"))
                        }
                    }
                }
            } }
            compose.waitForIdle()
            val initialBounds = compose.onNodeWithTag("fixed-glass-background").fetchSemanticsNode().boundsInRoot
            val initial = compose.onRoot().captureToImage().toPixelMap()
            compose.runOnIdle { stretched.value = true }
            compose.waitForIdle()
            val movedBounds = compose.onNodeWithTag("fixed-glass-background").fetchSemanticsNode().boundsInRoot
            assertTrue("カードの伸縮は維持する", kotlin.math.abs(initialBounds.top - movedBounds.top) > 10f)
            val moved = compose.onRoot().captureToImage().toPixelMap()
            var difference = 0f
            var count = 0
            // 両状態のカード内にある同じ画面座標だけを比較し、境界線を除外する。
            for (y in (initialBounds.top + 35).toInt() until (movedBounds.bottom - 35).toInt() step 4) {
                for (x in (initialBounds.left + 35).toInt() until (initialBounds.right - 35).toInt() step 8) {
                    difference += kotlin.math.abs(initial[x, y].red - moved[x, y].red)
                    count++
                }
            }
            assertTrue("伸縮中もガラス内の背景が画面に固定される: ${difference / count}", difference / count < .035f)
        } finally { scenario.close() }
    }

    @Test fun backdropIsBlurredWhileForegroundStaysSharp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                GlassBackdropScope(Modifier.fillMaxSize(), background = {
                    Canvas(Modifier.fillMaxSize()) {
                        val band = 8.dp.toPx()
                        repeat((size.width / band).toInt() + 1) { index ->
                            drawRect(if (index % 2 == 0) Color.Black else Color.White,
                                Offset(index * band, 0f), Size(band, size.height))
                        }
                    }
                }) {
                    Box(Modifier.padding(start = 40.dp, top = 100.dp)) {
                        Box(Modifier.size(128.dp, 96.dp).liquidGlass(20.dp).testTag("glass-rendering")) {
                            Box(Modifier.align(Alignment.Center).size(16.dp).background(Color.Red))
                        }
                    }
                }
            } }
            compose.waitForIdle()
            val image = compose.onNodeWithTag("glass-rendering").captureToImage()
            val pixels = image.toPixelMap()
            val center = pixels[image.width / 2, image.height / 2]
            assertTrue("前景の赤がぼかされない", center.red > 0.98f && center.green < 0.02f && center.blue < 0.02f)
            val band = image.width / 16f
            val y = image.height / 3
            val values = (3..12).map { index -> pixels[((index + 0.12f) * band).toInt(), y].red }
            assertTrue("背景の白黒の輪郭がぼかされる: $values", values.min() > 0.12f && values.max() - values.min() < 0.85f)
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), "liquid-glass-rendering.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally { scenario.close() }
    }
}
