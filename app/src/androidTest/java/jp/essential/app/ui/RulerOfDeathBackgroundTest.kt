package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import jp.essential.app.AppIconManager
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 背景の位相変化と停止時の同一描画を実際のComposeで確認する。 */
class RulerOfDeathBackgroundTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun eyeBlinksOncePerCycle() {
        assertTrue("通常は開いている", rulerEyeOpenness(.5f) == 1f)
        assertTrue("周期末尾に閉じる", rulerEyeOpenness(.968f) < .04f)
        assertTrue("次の周期で開く", rulerEyeOpenness(0f) == 1f)
    }

    @Test fun halfOfDustDisappearsAtTwoThirdsHeight() {
        val progress = 2f / 3f
        val visible = (0 until 64).count { rulerDustOpacity(progress, it < 32) > 0f }
        assertTrue("下端から2/3で半数の粒が消える", visible == 32)
        assertTrue("出現直後の粒は透明", rulerDustOpacity(0f, false) == 0f)
        assertTrue("上端で残りも透明", rulerDustOpacity(1f, false) == 0f)
    }

    @Test fun motionChangesAndStopsInBothModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = mutableStateOf(true)
        val dark = mutableStateOf(true)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = dark.value) {
                    Box(Modifier.fillMaxSize().testTag("ruler-background")) {
                        AnimatedBackdrop(AppIconManager.optionForId("reference"), motionEnabled = false)
                        ThemeMotifBackground("reference", dark.value, motionEnabled = enabled.value)
                    }
                }
            } }
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(1500)
            for (mode in listOf(true, false)) {
                compose.runOnIdle { dark.value = mode; enabled.value = true }
                compose.mainClock.advanceTimeBy(200)
                val first = compose.onNodeWithTag("ruler-background").captureToImage().asAndroidBitmap()
                compose.mainClock.advanceTimeBy(2000)
                val second = compose.onNodeWithTag("ruler-background").captureToImage().asAndroidBitmap()
                assertFalse("人物の動きで描画が変化する", first.sameAs(second))
                java.io.File(context.getExternalFilesDir(null), "ruler-${if (mode) "dark" else "light"}.png")
                    .outputStream().use { second.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                compose.runOnIdle { enabled.value = false }
                compose.mainClock.advanceTimeBy(100)
                val stopped = compose.onNodeWithTag("ruler-background").captureToImage().asAndroidBitmap()
                compose.mainClock.advanceTimeBy(2000)
                val later = compose.onNodeWithTag("ruler-background").captureToImage().asAndroidBitmap()
                assertTrue("停止中は描画が変わらない", stopped.sameAs(later))
                listOf(first, second, stopped, later).forEach { it.recycle() }
            }
        } finally { compose.mainClock.autoAdvance = true; scenario.close() }
    }
}
