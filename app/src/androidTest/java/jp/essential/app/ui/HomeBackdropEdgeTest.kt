package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** 両一覧の下端を伸ばした後、カードと背景が元の状態へ戻ることを確認する。 */
class HomeBackdropEdgeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun bottomDragSettlesInHomeAndFeatures() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        val root = compose.onRoot()
        try {
            for ((label, name) in listOf("ホーム" to "home", "機能一覧" to "features")) {
                compose.onNodeWithContentDescription(label).performClick()
                repeat(6) {
                    root.performTouchInput { swipeUp(startY = height * .65f, endY = height * .2f, durationMillis = 250) }
                    compose.waitForIdle()
                }
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                val before = root.captureToImage()
                val beforeBounds = compose.onNodeWithText("チューニング", substring = false).fetchSemanticsNode().boundsInRoot
                root.performTouchInput {
                    down(Offset(width * .5f, height * .65f))
                    moveTo(Offset(width * .5f, height * .3f), delayMillis = 250)
                }
                compose.waitForIdle()
                val heldBounds = compose.onNodeWithText("チューニング", substring = false).fetchSemanticsNode().boundsInRoot
                assertTrue("$label でカードの伸縮を維持する", abs(beforeBounds.top - heldBounds.top) > 1f)
                val held = root.captureToImage()
                java.io.File(context.getExternalFilesDir(null), "$name-backdrop-edge-held.png").outputStream().use {
                    held.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                root.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                compose.onNodeWithText("チューニング", substring = false).assertExists()
                val settled = root.captureToImage()
                val baseline = before.toPixelMap()
                val current = settled.toPixelMap()
                var difference = 0f
                var count = 0
                for (y in (before.height * .2f).toInt() until (before.height * .8f).toInt() step 7) {
                    for (x in (before.width * .1f).toInt() until (before.width * .9f).toInt() step 7) {
                        val a = baseline[x, y]; val b = current[x, y]
                        difference += abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
                        count += 3
                    }
                }
                assertTrue("$label の端の伸縮後に元の描画へ戻る: ${difference / count}", difference / count < .025f)
                for ((suffix, image) in listOf("before" to before, "settled" to settled)) {
                    java.io.File(context.getExternalFilesDir(null), "$name-backdrop-edge-$suffix.png").outputStream().use {
                        image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        } finally {
            runCatching { root.performTouchInput { up() } }
            scenario.close()
        }
    }
}
