package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
