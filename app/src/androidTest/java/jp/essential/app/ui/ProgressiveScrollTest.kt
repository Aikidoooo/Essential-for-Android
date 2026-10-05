package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.MainActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressiveScrollTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun offscreenCardFinishesEntranceBeforeScroll() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        var scrollState: ScrollState? = null
        try {
            scenario.onActivity { activity -> activity.setContent {
                ProgressiveMotionEntry {
                    CompositionLocalProvider(LocalProgressiveMotionEager provides false) {
                        val state = rememberScrollState()
                        SideEffect { scrollState = state }
                        Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(state)) {
                            ProgressiveWidget(0) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Blue)) }
                            Spacer(Modifier.height(1800.dp))
                            ProgressiveWidget(7) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red).testTag("last-motion-card")) }
                        }
                    }
                }
            } }
            compose.waitForIdle()
            // 待機中のコルーチンも含めて、入場演出の終了時刻まで進める。
            compose.mainClock.advanceTimeBy(1200)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            scenario.onActivity { activity -> activity.lifecycleScope.launch {
                scrollState!!.scrollTo(scrollState!!.maxValue)
            } }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("last-motion-card").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("スクロール直後からカードが完全に表示される", center.red > .95f && center.green < .05f)
        } finally { compose.mainClock.autoAdvance = true; scenario.close() }
    }
}
