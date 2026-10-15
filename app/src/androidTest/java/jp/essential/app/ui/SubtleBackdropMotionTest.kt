package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.AppIconManager
import jp.essential.app.MainActivity
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** 両モードで色が緩やかに動き、停止指定で背景が静止することを実描画で確認する。 */
class SubtleBackdropMotionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun backgroundMovesSubtlyAndCanPauseInBothModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        val dark = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val theme = mutableStateOf("sky")
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = dark.value) {
                    AnimatedBackdrop(AppIconManager.optionForId(theme.value), motionEnabled = enabled.value)
                }
            } }
            compose.mainClock.autoAdvance = false
            for (themeId in listOf("sky", "ocean", "violet", "ruby")) for (mode in listOf(false, true)) {
                compose.runOnUiThread { theme.value = themeId; dark.value = mode; enabled.value = true }
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                val initial = compose.onRoot().captureToImage().toPixelMap()
                compose.mainClock.advanceTimeBy(14_000)
                val moved = compose.onRoot().captureToImage().toPixelMap()
                var difference = 0f
                var count = 0
                for (y in 0 until initial.height step 16) for (x in 0 until initial.width step 16) {
                    val a = initial[x, y]; val b = moved[x, y]
                    difference += abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
                    count += 3
                }
                assertTrue("色の変化は小さいが継続して動く: ${difference / count}", difference / count in .00005f.. .012f)
                compose.runOnUiThread { enabled.value = false }
                compose.mainClock.advanceTimeBy(1000)
                val stopped = compose.onRoot().captureToImage().toPixelMap()
                compose.mainClock.advanceTimeBy(14_000)
                val still = compose.onRoot().captureToImage().toPixelMap()
                assertTrue("停止中は同じ色を保つ", stopped[stopped.width / 3, stopped.height / 3] == still[still.width / 3, still.height / 3])
            }
        } finally {
            compose.mainClock.autoAdvance = true
            scenario.close()
        }
    }
}
