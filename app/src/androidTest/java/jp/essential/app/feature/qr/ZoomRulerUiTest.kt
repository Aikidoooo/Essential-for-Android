package jp.essential.app.feature.qr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class ZoomRulerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movesContinuouslyWithOnlyRequestedMajorLabels() {
        var requested = 10f
        compose.setContent {
            var zoom by remember { mutableFloatStateOf(10f) }
            ZoomRuler(zoom, 1f, 30f, { requested = it; zoom = it }, Modifier.fillMaxWidth())
        }
        compose.onNodeWithText("3.5x").assertDoesNotExist()
        compose.onNodeWithText("4x").assertDoesNotExist()
        compose.onNodeWithContentDescription("ズーム倍率").performTouchInput {
            swipe(center, center.copy(x = center.x - 73f), 300)
        }
        compose.waitForIdle()
        assertTrue(requested > 10f && requested < 30f)
        assertTrue(kotlin.math.abs(requested * 10f - kotlin.math.round(requested * 10f)) > 0.001f)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, "zoom-ruler-ui.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
