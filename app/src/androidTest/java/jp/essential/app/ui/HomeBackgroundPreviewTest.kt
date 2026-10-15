package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** 実ホームで鑑賞の往復、方向反転、従来のタブ移動を確認する。 */
class HomeBackgroundPreviewTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun previewRoundTripAndInterruptedDragPreserveHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithContentDescription("ホーム").performClick()
            compose.mainClock.advanceTimeBy(1500)
            val root = compose.onNodeWithTag("home-background-preview-root")
            repeat(2) { attempt ->
                root.performTouchInput {
                    swipe(Offset(width * .12f, height * .42f), Offset(width * .88f, height * .42f), 450)
                }
                compose.mainClock.advanceTimeBy(1500)
                compose.onNodeWithTag("home-background-preview").assertExists()
                compose.onNodeWithContentDescription("プロフィール").assertDoesNotExist()
                root.captureToImage().asAndroidBitmap().let { bitmap ->
                    java.io.File(context.getExternalFilesDir(null), "home-background-preview.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                compose.mainClock.autoAdvance = false
                try {
                    root.performTouchInput {
                        swipe(Offset(width * .88f, height * .42f), Offset(width * .12f, height * .42f),
                            if (attempt == 0) 80 else 450)
                    }
                    compose.mainClock.advanceTimeBy(32)
                    assertFalse("鑑賞からの復帰では高速でも入場演出を再生しない",
                        compose.onNodeWithTag("main-tabs").fetchSemanticsNode().config[TabMotionProgressive])
                    val widgets = compose.onAllNodes(SemanticsMatcher.keyIsDefined(ProgressiveMotionFraction),
                        useUnmergedTree = true).fetchSemanticsNodes()
                    assertTrue("復帰中もホーム項目は表示済みを維持する",
                        widgets.isNotEmpty() && widgets.all { it.config[ProgressiveMotionFraction] == 1f })
                } finally { compose.mainClock.autoAdvance = true }
                compose.mainClock.advanceTimeBy(1500)
                compose.onNodeWithTag("home-background-preview").assertDoesNotExist()
                compose.onNodeWithTag("home-artwork-shortcut").assertExists()
                compose.onNodeWithContentDescription("プロフィール").assertExists()
            }
            root.performTouchInput {
                down(Offset(width * .2f, height * .42f))
                moveTo(Offset(width * .65f, height * .42f), 200)
                moveTo(Offset(width * .22f, height * .42f), 200)
                up()
            }
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithTag("home-background-preview").assertDoesNotExist()
            root.performTouchInput {
                swipe(Offset(width * .85f, height * .42f), Offset(width * .15f, height * .42f), 450)
            }
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithTag("home-artwork-shortcut").assertDoesNotExist()
            compose.onNodeWithContentDescription("ホーム").performClick()
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithTag("home-artwork-shortcut").assertExists()
        } finally {
            scenario.close()
        }
    }
}
