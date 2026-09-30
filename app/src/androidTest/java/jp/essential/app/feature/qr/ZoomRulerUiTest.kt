package jp.essential.app.feature.qr

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.ln

class ZoomRulerUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun largeTickSelectsExactTwoTimesZoom() {
        val zoom = mutableFloatStateOf(1f)
        rule.setContent {
            MaterialTheme { ZoomRuler(zoom.floatValue, 0.5f, 10f, { zoom.floatValue = it }, Modifier.width(360.dp)) }
        }
        val ruler = rule.onNodeWithContentDescription("ズーム倍率")
        // 2x目盛りの描画位置と同じ対数座標をタップし、整数倍率への一致を確認する。
        val position = 0.5f + ln(2f / 0.5f) / ln(10f / 0.5f) - ln(1f / 0.5f) / ln(10f / 0.5f)
        ruler.performTouchInput { click(Offset(width * position, height * 0.6f)) }
        rule.runOnIdle { assertEquals(2f, zoom.floatValue, 0.0001f) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "zoom-ruler-supported-range.png").outputStream().use {
            ruler.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
