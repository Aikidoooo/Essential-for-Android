package jp.essential.app.feature.scanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScannerZoomControlTest {
    @get:Rule val compose = createComposeRule()

    @Test fun presetsExpandIntoContinuousRulerAndReturnAfterDrag() {
        var zoom by mutableStateOf(1f)
        compose.setContent { MaterialTheme { ScannerZoomControl(zoom, 1f, 30f) { zoom = it } } }
        compose.onNodeWithTag("scanner-zoom-presets").assertExists()
        compose.onNodeWithContentDescription("ズーム倍率").assertDoesNotExist()
        // プリセットの上で始めた横ドラッグが、表示切替後も連続して倍率を更新する。
        compose.onNodeWithTag("scanner-zoom-control").performTouchInput {
            down(Offset(width * 0.7f, height - 20f))
            moveTo(Offset(width * 0.5f, height - 20f), 100)
            moveTo(Offset(width * 0.3f, height - 20f), 100)
            up()
        }
        compose.runOnIdle { assertTrue(zoom > 1f && zoom < 30f) }
        compose.onNodeWithContentDescription("ズーム倍率").assertExists()
        compose.onNodeWithTag("scanner-zoom-presets").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithTag("scanner-zoom-presets").assertExists()
        compose.onNodeWithText("3.5x").performClick()
        compose.runOnIdle { assertTrue(kotlin.math.abs(zoom - 3.5f) < 0.001f) }
        compose.onNodeWithContentDescription("ズーム倍率").assertExists()
    }
}
